package com.dsh.refreshswitch;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 常驻前台服务：
 *   1) 常驻通知（当前刷新率 + 快捷挡位 + 锁定）
 *   2) 通知点击 → 弹出悬浮窗面板（无权限时降级为对话框）
 *   3) 刷新率锁定：DisplayListener 即时 + 轮询兜底
 *   4) 自启动守护：START_STICKY / onTaskRemoved 闹钟 / JobScheduler 周期检查
 */
public class SwitchService extends Service {

    public static final String CHANNEL_ID = "refresh_switch";
    public static final int NOTIF_ID = 0x5252;

    public static final String ACTION_QUICK = "com.dsh.refreshswitch.QUICK";
    public static final String ACTION_SHOW_PANEL = "com.dsh.refreshswitch.SHOW_PANEL";
    public static final String ACTION_TOGGLE_LOCK = "com.dsh.refreshswitch.TOGGLE_LOCK";
    public static final String ACTION_RESTART = "com.dsh.refreshswitch.RESTART";
    public static final String ACTION_STOP = "com.dsh.refreshswitch.STOP";
    public static final String EXTRA_MODE_ID = "mode_id";

    private static final String PREFS = "cfg";
    private static final String KEY_LOCK = "lock_enabled";
    private static final String KEY_LOCK_ID = "locked_id";
    private static final String KEY_LOCK_FPS = "locked_fps";
    private static final String KEY_ENABLED = "service_enabled";
    private static final String KEY_HIDE_ICON = "hide_icon";
    private static final String KEY_HIDE_RECENTS = "hide_recents";
    private static final String KEY_NOTIFY = "notify_enabled";
    private static final String KEY_UI_STYLE = "ui_style";
    private static final String KEY_BOTTOM_BAR = "bottom_bar_style";
    private static final String KEY_TOAST = "toast_enabled";
    private static final String KEY_DISPLAY = "target_display";
    private static final String KEY_CAP_FPS = "cap_fps";

    private static final long POLL_MS = 1000L;
    private static final int FPS_TOLERANCE = 1;
    private static final int FPS_TOLERANCE_LOCK = 0;

    private Handler handler;
    private Runnable ticker;
    private int lastShownFps = Integer.MIN_VALUE;
    private DisplayManager displayManager;
    private DisplayManager.DisplayListener displayListener;
    private long lastApplyAt = 0L;
    private boolean stoppedByUser = false;
    private boolean daemonMode = false;   // true = 本次实例不承担前台服务（通知已关闭）

    // ---------------- 静态接口 ----------------

    /** 是否保留常驻通知（前台服务）。关闭后改用 root 守护进程。 */
    public static boolean isNotifyEnabled(Context ctx) { return prefs(ctx).getBoolean(KEY_NOTIFY, true); }

    /** 界面风格：0 = MIUIX（默认），1 = M3E。 */
    public static int getUiStyle(Context ctx) { return prefs(ctx).getInt(KEY_UI_STYLE, 0); }

    public static void setUiStyle(Context ctx, int style) {
        prefs(ctx).edit().putInt(KEY_UI_STYLE, style).apply();
    }

    /** 底栏样式：0 = 标准（贴底），1 = 悬浮胶囊，2 = 液态玻璃（半透明磨砂）。 */
    public static int getBottomBarStyle(Context ctx) { return prefs(ctx).getInt(KEY_BOTTOM_BAR, 0); }

    public static void setBottomBarStyle(Context ctx, int style) {
        prefs(ctx).edit().putInt(KEY_BOTTOM_BAR, style).apply();
    }

    /** 切换常驻通知：开 → 前台服务；关 → root 守护进程（不占用通知栏）。 */
    public static void setNotifyEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_NOTIFY, on).apply();
        if (on) {
            Daemon.stop();
            start(ctx);
        } else {
            setEnabled(ctx, true);
            try { ctx.stopService(new Intent(ctx, SwitchService.class)); } catch (Throwable ignored) {}
            Daemon.start(ctx);
            // 防锁死：通知与桌面图标不能同时消失，否则用户没有任何入口
            if (isHideIcon(ctx)) {
                setHideIcon(ctx, false);
                try {
                    android.widget.Toast.makeText(ctx, "关闭通知前需保留桌面入口",
                            android.widget.Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
            }
        }
    }

    public static void start(Context ctx) {
        setEnabled(ctx, true);
        // 关闭常驻通知时完全不启动前台服务，交给 root 守护进程
        if (!isNotifyEnabled(ctx)) {
            if (Daemon.pid() > 0) Daemon.sync(ctx);   // 已在运行：只同步目标，不重启
            else Daemon.start(ctx);
            return;
        }
        if (Daemon.pid() > 0) Daemon.stop();
        Intent i = new Intent(ctx, SwitchService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "start service failed", t);
        }
    }

    public static void stop(Context ctx) {
        setEnabled(ctx, false);
        ctx.stopService(new Intent(ctx, SwitchService.class));
    }

    public static boolean isEnabled(Context ctx) { return prefs(ctx).getBoolean(KEY_ENABLED, true); }
    public static void setEnabled(Context ctx, boolean on) { prefs(ctx).edit().putBoolean(KEY_ENABLED, on).apply(); }

    public static boolean isLockEnabled(Context ctx) { return prefs(ctx).getBoolean(KEY_LOCK, true); }
    public static int lockedId(Context ctx) { return prefs(ctx).getInt(KEY_LOCK_ID, -1); }
    public static int lockedFps(Context ctx) { return prefs(ctx).getInt(KEY_LOCK_FPS, -1); }

    public static void setLockEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_LOCK, on).apply();
        Daemon.sync(ctx);
    }

    /**
     * 设定锁定目标。
     * 只持久化 **刷新率(Hz)**，不依赖 modeId —— 模式表的 id 可能随固件/显示重配变化，
     * 每次下发时按当前模式表重新解析出正确的 id，避免锁定失效或锁错档位。
     */
    public static void lockTo(Context ctx, int modeId, int fps) {
        prefs(ctx).edit().putBoolean(KEY_LOCK, true)
                .putInt(KEY_LOCK_FPS, fps)
                .putInt(KEY_LOCK_ID, -1)     // 不再使用持久化 id
                .apply();
        ModeUtil.applyFps(ctx, fps);
        start(ctx);
        Daemon.sync(ctx);                    // 同步给 root 守护（若在运行）
    }

    public static void unlock(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_LOCK, false).apply();
        // 解除锁定：清掉上限键，交还给系统的自适应
        try {
            ModeUtil.clearRefreshRateCap();
        } catch (Throwable ignored) {
        }
        Daemon.sync(ctx);
    }

    /** 未锁定时选择的刷新率上限（= 最高到 N Hz）；-1 表示未设置。 */
    public static int getCapFps(Context ctx) { return prefs(ctx).getInt(KEY_CAP_FPS, -1); }

    public static void setCapFps(Context ctx, int fps) {
        prefs(ctx).edit().putInt(KEY_CAP_FPS, fps).apply();
    }

    /** 悬浮面板相对屏幕左上角的偏移（竖屏/横屏分别记忆）。 */
    public static int[] getPanelOffsetPx(Context ctx, boolean landscape) {
        String key = landscape ? "panel_off_land" : "panel_off_port";
        String v = prefs(ctx).getString(key, "0,0");
        try {
            String[] p = v.split(",");
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (Throwable t) {
            return new int[]{0, 0};
        }
    }

    public static void setPanelOffsetPx(Context ctx, boolean landscape, int x, int y) {
        String key = landscape ? "panel_off_land" : "panel_off_port";
        prefs(ctx).edit().putString(key, x + "," + y).apply();
    }
    /** 要调节的屏幕 id（默认主屏 display 0）。 */
    public static int getTargetDisplay(Context ctx) {
        return prefs(ctx).getInt(KEY_DISPLAY, android.view.Display.DEFAULT_DISPLAY);
    }

    public static void setTargetDisplay(Context ctx, int id) {
        prefs(ctx).edit().putInt(KEY_DISPLAY, id).apply();
    }

    /** 全部 toast 提示的总开关（默认开）。 */
    public static boolean isToastEnabled(Context ctx) { return prefs(ctx).getBoolean(KEY_TOAST, true); }

    public static void setToastEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_TOAST, on).apply();
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- 界面可见性设置 ----------------

    public static boolean isHideIcon(Context ctx) { return prefs(ctx).getBoolean(KEY_HIDE_ICON, false); }
    public static boolean isHideRecents(Context ctx) { return prefs(ctx).getBoolean(KEY_HIDE_RECENTS, false); }

    public static void setHideIcon(Context ctx, boolean hide) {
        prefs(ctx).edit().putBoolean(KEY_HIDE_ICON, hide).apply();
        applyLauncherIcon(ctx, hide);
    }

    public static void setHideRecents(Context ctx, boolean hide) {
        prefs(ctx).edit().putBoolean(KEY_HIDE_RECENTS, hide).apply();
    }

    /** 隐藏/显示桌面图标：启停 LauncherActivity 组件（MainActivity 仍可被通知/悬浮窗打开）。 */
    public static void applyLauncherIcon(Context ctx, boolean hide) {
        try {
            ComponentName cn = new ComponentName(ctx, LauncherActivity.class);
            ctx.getPackageManager().setComponentEnabledSetting(cn,
                    hide ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                         : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Log.i(ModeUtil.TAG, "launcher icon " + (hide ? "hidden" : "shown"));
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "applyLauncherIcon failed", t);
        }
    }

    /** 打开 App 主界面（遵循"隐藏后台任务"设置）。 */
    public static void openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (isHideRecents(ctx)) i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try { ctx.startActivity(i); } catch (Throwable t) { Log.e(ModeUtil.TAG, "openApp failed", t); }
    }

    // ---------------- 生命周期 ----------------

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        // 通知已关闭：本实例不承担前台服务，直接交给 root 守护进程
        if (!isNotifyEnabled(this)) {
            daemonMode = true;
            Daemon.start(this);
            stopSelf();
            return;
        }

        // 有 root 时清理老版本可能遗留的 1x1 透明悬浮窗（它会触发系统的「上层显示」提示）
        if (ModeUtil.hasRoot()) {
            OverlayModeSwitch.release();
        }
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        registerDisplayListener();
        scheduleTicker();
        RestartJobService.schedule(this);

        // 尝试用 root 自我授予悬浮窗权限，让通知点击可直接弹悬浮窗
        if (!OverlayPanel.canShow(this) && ModeUtil.hasRoot()) {
        }
        // 首次运行：把当前刷新率作为锁定目标，实现"一直设定"
        if (isLockEnabled(this) && lockedFps(this) <= 0) {
            int cur = ModeUtil.currentFps(this);
            if (cur > 0) {
                prefs(this).edit().putInt(KEY_LOCK_FPS, cur).apply();
                Log.i(ModeUtil.TAG, "adopt current fps as lock target: " + cur);
            }
        }
        handler.postDelayed(this::enforceLock, 500);

        // 恢复桌面图标可见性设置（防止组件状态被系统重置）
        applyLauncherIcon(this, isHideIcon(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        Log.i(ModeUtil.TAG, "onStartCommand action=" + action);

        if (daemonMode || !isNotifyEnabled(this)) {
            Daemon.start(this);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(action)) {
            stoppedByUser = true;
            setLockEnabled(this, false);
            setEnabled(this, false);
            RestartJobService.cancel(this);
            cancelNotification();
            stopSelf();
            return START_NOT_STICKY;
        }

        stoppedByUser = false;

        if (ACTION_SHOW_PANEL.equals(action)) {
            Haptics.click(this);
            showPanel();
        } else if (ACTION_QUICK.equals(action) && intent.hasExtra(EXTRA_MODE_ID)) {
            Haptics.tick(this);
            int id = intent.getIntExtra(EXTRA_MODE_ID, -1);
            if (id >= 0) {
                ModeUtil.Mode m = ModeUtil.findById(ModeUtil.listModes(this), id);
                if (m != null) lockTo(this, id, m.fpsInt());
                else ModeUtil.applyMode(this, id);
                OverlayPanel.refresh();
            }
        } else if (ACTION_TOGGLE_LOCK.equals(action)) {
            Haptics.click(this);
            if (isLockEnabled(this)) {
                unlock(this);
            } else {
                int fps = ModeUtil.currentFps(this);
                ModeUtil.Mode m = ModeUtil.findByFps(this, fps, FPS_TOLERANCE);
                if (m != null) lockTo(this, m.id, m.fpsInt());
                else setLockEnabled(this, true);
            }
            OverlayPanel.refresh();
        }

        notifyNow(true);
        return START_STICKY;
    }

    /** 通知点击：优先悬浮窗，不可用则降级为对话框 Activity（不跳主页面）。 */
    private void showPanel() {
        // 通知栏窗口层级高于应用悬浮窗，若不收起会挡住「点空白处关闭」的点击
        if (ModeUtil.hasRoot()) ModeUtil.suOut("cmd statusbar collapse");
        if (OverlayPanel.show(this)) return;
        if (ModeUtil.hasRoot()) {
            if (OverlayPanel.show(this)) return;
        }
        // 悬浮窗不可用（未授权）时只提示；不再回退到独立 Activity 面板，避免出现第二套面板
        Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
    }

    /** App 被从最近任务划掉时：安排一次快速重启（配合自启动权限）。 */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.i(ModeUtil.TAG, "onTaskRemoved -> schedule restart");
        if (stoppedByUser || !isEnabled(this)) return;
        if (!isNotifyEnabled(this)) { Daemon.start(this); return; }
        scheduleRestartAlarm(1500);
    }

    @Override
    public void onDestroy() {
        if (handler != null && ticker != null) handler.removeCallbacks(ticker);
        if (displayManager != null && displayListener != null) {
            try { displayManager.unregisterDisplayListener(displayListener); } catch (Throwable ignored) {}
        }
        OverlayPanel.hide();
        if (daemonMode || !isNotifyEnabled(this)) {
            // 通知模式已关闭：撤掉通知，确保 root 守护在跑，不做前台服务自救
            cancelNotification();
            if (isEnabled(this) && ModeUtil.hasRoot()) Daemon.start(this);
            super.onDestroy();
            return;
        }
        cancelNotification();
        // 非用户主动停止 → 尝试自救
        if (!stoppedByUser && isEnabled(this)) scheduleRestartAlarm(3000);
        super.onDestroy();
    }

    private void cancelNotification() {
        try { stopForeground(true); } catch (Throwable ignored) {}
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF_ID);
        } catch (Throwable ignored) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void scheduleRestartAlarm(long delayMs) {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(this, SwitchService.class).setAction(ACTION_RESTART);
            int pf = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) pf |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = (Build.VERSION.SDK_INT >= 26)
                    ? PendingIntent.getForegroundService(this, 9, i, pf)
                    : PendingIntent.getService(this, 9, i, pf);
            long at = System.currentTimeMillis() + delayMs;
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
            Log.i(ModeUtil.TAG, "restart alarm set +" + delayMs + "ms");
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "scheduleRestartAlarm failed", t);
        }
    }

    // ---------------- 锁定核心 ----------------

    private boolean enforceLock() {
        if (!isLockEnabled(this)) return false;
        int lfps = lockedFps(this);
        if (lfps <= 0) return false;

        // 按当前模式表重新解析目标 id（不信任持久化 id）
        ModeUtil.Mode target = ModeUtil.findByFps(this, lfps, 0);
        if (target == null) return false;

        int real = ModeUtil.realPanelFps();
        boolean mismatch;
        if (real > 0) {
            mismatch = Math.abs(real - lfps) > FPS_TOLERANCE_LOCK;
        } else {
            ModeUtil.Mode cur = ModeUtil.currentMode(this);
            mismatch = (cur == null) || (Math.abs(cur.fpsInt() - lfps) > FPS_TOLERANCE_LOCK);
        }
        if (!mismatch) return false;

        long now = System.currentTimeMillis();
        if (now - lastApplyAt < 300) return false;
        lastApplyAt = now;
        Log.i(ModeUtil.TAG, "lock enforce: real=" + real + " locked=" + lfps + " -> modeId " + target.id);
        ModeUtil.applyMode(this, target.id);
        OverlayPanel.refresh();
        return true;
    }

    private void registerDisplayListener() {
        try {
            displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) return;
            displayListener = new DisplayManager.DisplayListener() {
                @Override public void onDisplayAdded(int displayId) {}
                @Override public void onDisplayRemoved(int displayId) {}
                @Override public void onDisplayChanged(int displayId) {
                    if (displayId != Display.DEFAULT_DISPLAY) return;
                    if (enforceLock()) notifyNow(true);
                    else OverlayPanel.refresh();
                }
            };
            displayManager.registerDisplayListener(displayListener, handler);
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "registerDisplayListener failed", t);
        }
    }

    private void scheduleTicker() {
        ticker = new Runnable() {
            @Override public void run() {
                try { AutoRules.tick(SwitchService.this, true); } catch (Throwable t) { Log.e(ModeUtil.TAG, "auto tick failed", t); }
                // LTPO 屏幕不支持锁定刷新率：检测到就自动解除，避免留下无效的锁定状态
                try {
                    if (ScreenInfo.read(SwitchService.this).isLtpo() && SwitchService.isLockEnabled(SwitchService.this)) {
                        Log.i(ModeUtil.TAG, "LTPO detected -> auto unlock");
                        SwitchService.unlock(SwitchService.this);
                    }
                } catch (Throwable t) {
                    Log.e(ModeUtil.TAG, "ltpo auto unlock failed", t);
                }
                enforceLock();
                notifyNow(false);
                if (OverlayPanel.isShowing()) OverlayPanel.refresh();
                handler.postDelayed(this, POLL_MS);
            }
        };
        handler.postDelayed(ticker, POLL_MS);
    }

    private void notifyNow(boolean force) {
        int fps = ModeUtil.realPanelFps();
        if (!force && fps == lastShownFps) return;
        lastShownFps = fps;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try { nm.notify(NOTIF_ID, buildNotification()); } catch (Throwable ignored) {}
        }
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.channel_desc));
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
    }

    private PendingIntent svcIntent(String action, int modeId, int req) {
        Intent i = new Intent(this, SwitchService.class).setAction(action);
        if (modeId >= 0) i.putExtra(EXTRA_MODE_ID, modeId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, req, i, flags);
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        int curFps = ModeUtil.realPanelFps();
        ModeUtil.Mode cur = ModeUtil.currentMode(this);
        String curText = curFps > 0 ? (curFps + " Hz") : (cur != null ? cur.label() : "未知");

        boolean lock = isLockEnabled(this);
        int lockFps = lockedFps(this);
        boolean locked = lock && lockFps > 0;
        String text = "当前 " + curText;
        if (locked) text += "   🔒 锁定 " + lockFps + "Hz";
        else text += "   🔓 未锁定";
        text += "    点按弹出面板";

        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat)
         .setContentTitle(getString(R.string.notif_title))
         .setContentText(text)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setShowWhen(false)
         .setContentIntent(svcIntent(ACTION_SHOW_PANEL, -1, 1));

        if (Build.VERSION.SDK_INT >= 21) b.setVisibility(Notification.VISIBILITY_PUBLIC);

        // 通知不再提供长按选项（快捷挡位 / 锁定），仅保留点击弹出面板
        return b.build();
    }
}
