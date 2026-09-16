package com.dsh.refreshswitch;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前台应用监测 + 按应用自动切换刷新率。
 *
 * 规则存在 cfg 的 auto_rules 里，格式 "pkg=fps;pkg=fps;"。
 * 进入命中规则的应用时：记住当前刷新率与锁定状态 → 切到规则值并临时锁定；
 * 离开该应用时：恢复记住的刷新率与锁定状态。修改规则与恢复时给 toast 提示。
 */
public final class AutoRules {

    private static final String KEY_AUTO = "auto_enabled";
    private static final String KEY_SHOW_SYSTEM = "auto_show_system";
    private static final String KEY_RULES = "auto_rules";

    private static final Pattern PKG_IN_DUMP = Pattern.compile("([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+)/");

    // ---------------- 运行时状态 ----------------
    private static String lastFg = null;        // 上一次的前台包名
    private static String activePkg = null;     // 当前正在被自动化接管的应用
    private static int savedFps = -1;           // 接管前的刷新率
    private static boolean savedLock = true;    // 接管前的锁定开关
    private static int savedLockFps = -1;       // 接管前的锁定目标

    private AutoRules() {}

    // ---------------- 偏好 ----------------

    public static boolean isEnabled(Context ctx) {
        return SwitchService.prefs(ctx).getBoolean(KEY_AUTO, false);
    }

    public static void setEnabled(Context ctx, boolean on) {
        SwitchService.prefs(ctx).edit().putBoolean(KEY_AUTO, on).apply();
        if (!on) reset();
        Daemon.sync(ctx);
    }

    public static boolean isShowSystem(Context ctx) {
        return SwitchService.prefs(ctx).getBoolean(KEY_SHOW_SYSTEM, false);
    }

    public static void setShowSystem(Context ctx, boolean show) {
        SwitchService.prefs(ctx).edit().putBoolean(KEY_SHOW_SYSTEM, show).apply();
    }

    private static Map<String, Integer> rules(Context ctx) {
        Map<String, Integer> map = new HashMap<>();
        String raw = SwitchService.prefs(ctx).getString(KEY_RULES, "");
        if (raw == null) return map;
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try {
                map.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1).trim()));
            } catch (Throwable ignored) {
            }
        }
        return map;
    }

    private static void saveRules(Context ctx, Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        SwitchService.prefs(ctx).edit().putString(KEY_RULES, sb.toString()).apply();
    }

    /** 全部规则（只读副本）。 */
    public static Map<String, Integer> allRules(Context ctx) { return new HashMap<>(rules(ctx)); }

    /** 该应用的规则值（fps），没有规则返回 -1。 */
    public static int ruleFps(Context ctx, String pkg) {
        Integer v = rules(ctx).get(pkg);
        return v == null ? -1 : v;
    }

    public static int ruleCount(Context ctx) {
        return rules(ctx).size();
    }

    /** 设置/清除某应用的规则（fps <= 0 表示清除），并 toast 提示。 */
    public static void setRule(Context ctx, String pkg, int fps) {
        Map<String, Integer> map = rules(ctx);
        if (fps <= 0) map.remove(pkg);
        else map.put(pkg, fps);
        saveRules(ctx, map);

        String label = appLabel(ctx, pkg);
        String msg = fps <= 0
                ? "已关闭「" + label + "」的自动切换"
                : "「" + label + "」打开时自动切到 " + fps + "Hz";
        toast(ctx, msg);

        // 规则被改的应用正好在前台：立即生效
        if (fps > 0 && pkg.equals(lastFg)) {
            if (!pkg.equals(activePkg)) apply(ctx, pkg, fps, true);
        } else if (fps <= 0 && pkg.equals(activePkg)) {
            restore(ctx, true);
        }
    }

    // ---------------- 前台应用 ----------------

    /** 读取当前前台包名（需 root）。 */
    public static String foregroundPackage() {
        String out = ModeUtil.suOut("dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'");
        String pkg = extract(out);
        if (pkg == null) {
            out = ModeUtil.suOut("dumpsys window | grep -m1 -E 'mCurrentFocus|mFocusedApp'");
            pkg = extract(out);
        }
        return pkg;
    }

    private static String extract(String dump) {
        if (dump == null || dump.isEmpty()) return null;
        Matcher m = PKG_IN_DUMP.matcher(dump);
        while (m.find()) {
            String p = m.group(1);
            // 过滤掉 dump 里的类名/进程名噪声
            if (p.startsWith("android.") || p.startsWith("com.android.internal")) continue;
            return p;
        }
        return null;
    }

    // ---------------- 主循环 ----------------

    /** 每秒调用一次；allowToast 仅在前台服务进程里为 true。 */
    public static void tick(Context ctx, boolean allowToast) {
        if (!isEnabled(ctx)) {
            if (activePkg != null) restore(ctx, allowToast);
            lastFg = null;
            return;
        }
        String fg;
        try {
            fg = foregroundPackage();
        } catch (Throwable t) {
            return;
        }
        if (fg == null || fg.equals(lastFg)) return;

        String prev = lastFg;
        lastFg = fg;

        // 离开被接管的应用：若新前台自身也有规则，直接换规则，不必先恢复
        if (activePkg != null && !fg.equals(activePkg)) {
            int newRule = ruleFps(ctx, fg);
            if (newRule <= 0) restore(ctx, allowToast);
            else activePkg = null;
        }

        int fps = ruleFps(ctx, fg);
        if (fps > 0 && !fg.equals(activePkg)) {
            apply(ctx, fg, fps, allowToast);
        }
        if (prev != null && prev.equals(fg)) lastFg = fg;
    }

    private static void apply(Context ctx, String pkg, int fps, boolean allowToast) {
        // 记住进入前的状态
        savedFps = ModeUtil.currentFps(ctx);
        savedLock = SwitchService.isLockEnabled(ctx);
        savedLockFps = SwitchService.lockedFps(ctx);
        activePkg = pkg;

        ModeUtil.Mode m = ModeUtil.findByFps(ctx, fps, 1);
        if (m == null) {
            Log.w(ModeUtil.TAG, "auto: no mode for " + fps + "Hz");
            return;
        }
        SwitchService.lockTo(ctx, m.id, fps);
        OverlayPanel.refresh();
        Log.i(ModeUtil.TAG, "auto: " + pkg + " -> " + fps + "Hz (saved " + savedFps + ")");
        if (allowToast) toast(ctx, "「" + appLabel(ctx, pkg) + "」已自动切到 " + fps + "Hz");
    }

    private static void restore(Context ctx, boolean allowToast) {
        if (activePkg == null) {
            savedFps = -1;
            return;
        }
        String from = appLabel(ctx, activePkg);
        int back = savedFps;
        boolean lock = savedLock;
        int lockFps = savedLockFps;
        reset();

        if (back <= 0) back = ModeUtil.currentFps(ctx);
        ModeUtil.Mode m = ModeUtil.findByFps(ctx, back, 1);
        if (m != null) {
            if (lock && lockFps > 0) {
                SwitchService.lockTo(ctx, m.id, lockFps);
            } else {
                SwitchService.setLockEnabled(ctx, false);
                ModeUtil.applyMode(m.id);
            }
        }
        OverlayPanel.refresh();
        Log.i(ModeUtil.TAG, "auto: restore after " + from + " -> " + back + "Hz");
        if (allowToast) toast(ctx, "已退出「" + from + "」，刷新率恢复 " + back + "Hz");
    }

    private static void reset() {
        activePkg = null;
        savedFps = -1;
        savedLock = true;
        savedLockFps = -1;
    }

    // ---------------- 工具 ----------------

    public static String appLabel(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) return l.toString();
        } catch (Throwable ignored) {
        }
        return pkg;
    }

    /** 应用图标（失败返回 null）。 */
    public static android.graphics.Bitmap appIcon(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            android.graphics.drawable.Drawable d = pm.getApplicationIcon(pkg);
            int w = Math.max(1, d.getIntrinsicWidth());
            int h = Math.max(1, d.getIntrinsicHeight());
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(canvas);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void toast(Context ctx, String msg) {
        if (!SwitchService.isToastEnabled(ctx)) return;
        try {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(ctx.getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
        } catch (Throwable ignored) {
        }
    }

    /** 应用列表（root 读取包名 + PackageManager 取名称）。 */
    public static List<String[]> installedApps(Context ctx, boolean includeSystem) {
        List<String[]> list = new ArrayList<>();
        String out = ModeUtil.suOut("pm list packages" + (includeSystem ? "" : " -3"));
        if (out == null) out = "";
        PackageManager pm = ctx.getPackageManager();
        Map<String, String> labels = new HashMap<>();
        for (String line : out.split("\n")) {
            line = line.trim();
            if (!line.startsWith("package:")) continue;
            String pkg = line.substring("package:".length()).trim();
            if (pkg.isEmpty()) continue;
            String label = appLabel(ctx, pkg);
            list.add(new String[]{pkg, label});
            labels.put(pkg, label);
        }
        if (list.isEmpty()) {
            // root 不可用时退回 PackageManager
            List<ApplicationInfo> infos = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : infos) {
                boolean sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (sys && !includeSystem) continue;
                String pkg = ai.packageName;
                CharSequence l = pm.getApplicationLabel(ai);
                list.add(new String[]{pkg, l == null ? pkg : l.toString()});
            }
        }
        Collections.sort(list, new Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) {
                return a[1].compareToIgnoreCase(b[1]);
            }
        });
        return list;
    }
}
