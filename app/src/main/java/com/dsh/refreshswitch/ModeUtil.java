package com.dsh.refreshswitch;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 显示模式枚举 / SurfaceFlinger 切换 / sysfs 读取 / su 封装。 */
public final class ModeUtil {

    public static final String TAG = "RefreshSwitch";

    private static final String PANEL_FPS_PATH =
            "/sys/devices/virtual/mi_display/disp_feature/disp-DSI-0/dynamic_fps";

    /** 本应用只调节主屏：displayId = 0。 */
    public static final int MAIN_DISPLAY_ID = Display.DEFAULT_DISPLAY;

    /** SurfaceFlinger 设置显示模式的事务码（HyperOS 实测有效） */
    private static final int SF_SET_MODE_CODE = 1035;

    private static long lastFpsReadAt = 0L;
    private static int lastFpsValue = -1;
    private static Boolean suOk = null;

    public static final class Mode {
        public final int id;
        public final float fps;
        public Mode(int id, float fps) { this.id = id; this.fps = fps; }
        public String label() {
            int r = Math.round(fps);
            if (Math.abs(fps - r) < 0.2f) return r + " Hz";
            return String.format(java.util.Locale.US, "%.2f Hz", fps);
        }
        public String shortLabel() { return Math.round(fps) + "Hz"; }
        public int fpsInt() { return Math.round(fps); }
    }

    private ModeUtil() {}

    /** 当前要调节的屏幕（默认主屏 display 0，多屏时可在主页切换）。 */
    public static Display defaultDisplay(Context ctx) {
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return null;
        Display d = dm.getDisplay(SwitchService.getTargetDisplay(ctx));
        return d != null ? d : dm.getDisplay(MAIN_DISPLAY_ID);
    }

    /**
     * 支持的显示模式，**按刷新率由低到高排序**（不按 modeId）。
     */
    public static List<Mode> listModes(Context ctx) {
        List<Mode> out = new ArrayList<>();
        Display d = defaultDisplay(ctx);
        if (d != null) {
            for (Display.Mode m : d.getSupportedModes()) {
                // 同一刷新率可能有多个模式（例如小米 17 Pro 有 120Hz/120Hz@360vsync），按 fps 去重
                boolean dup = false;
                int fps = Math.round(m.getRefreshRate());
                for (Mode u : out) if (u.fpsInt() == fps) { dup = true; break; }
                if (!dup) out.add(new Mode(m.getModeId(), m.getRefreshRate()));
            }
        }
        Collections.sort(out, (a, b) -> {
            int c = Float.compare(a.fps, b.fps);
            return c != 0 ? c : Integer.compare(a.id, b.id);
        });
        return out;
    }

    /** framework 上报的当前模式（部分固件不跟随 SF，仅作兜底）。 */
    public static Mode currentMode(Context ctx) {
        Display d = defaultDisplay(ctx);
        if (d == null) return null;
        Display.Mode m = d.getMode();
        if (m == null) return null;
        return new Mode(m.getModeId(), m.getRefreshRate());
    }

    /**
     * 实际面板刷新率（Hz），失败返回 -1。结果缓存 800ms。
     * 直读 sysfs 会被 SELinux 拦截，故回退到 root cat。
     */
    public static int realPanelFps() {
        long now = System.currentTimeMillis();
        if (now - lastFpsReadAt < 800) return lastFpsValue;
        lastFpsReadAt = now;

        try (BufferedReader r = new BufferedReader(new java.io.FileReader(PANEL_FPS_PATH))) {
            String s = r.readLine();
            if (s != null) { lastFpsValue = Integer.parseInt(s.trim()); return lastFpsValue; }
        } catch (Throwable ignored) {}

        if (hasRoot()) {
            String s = suOut("cat " + PANEL_FPS_PATH);
            if (s != null) {
                for (String line : s.split("\\s+")) {
                    try { lastFpsValue = Integer.parseInt(line.trim()); return lastFpsValue; }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        lastFpsValue = -1;
        return -1;
    }

    /** 当前刷新率的 Hz；优先 sysfs，其次 framework。 */
    public static int currentFps(Context ctx) {
        int p = realPanelFps();
        if (p > 0) return p;
        Mode m = currentMode(ctx);
        return m == null ? -1 : m.fpsInt();
    }

    /**
     * 切换显示模式（多路径，按能力自动选择）：
     *
     * 1. **免 root**：悬浮层 `preferredDisplayModeId`（公开 API，跨机型用 framework 的 modeId）
     * 2. **root**：`cmd display set-user-preferred-display-mode W H FPS 0`（Android 14+，最稳）
     * 3. **root 兜底**：SurfaceFlinger 事务码 1035，索引优先从 dumpsys 解析出的 sfModeId，取不到再退回 modeId-1
     *
     * 注意：不同机型 framework 的 modeId 与 SF 索引并不总是 id = index + 1
     * （例如小米 17 Pro 是 1→0、3→5、4→6），所以不能一律减一。
     */
    public static boolean applyMode(Context ctx, int modeId) {
        // 0) 只要能用悬浮层：同时发「模式 + 固定帧率」请求。
        //    这是抬高 LTPO 下限的关键 —— 系统会把 app request 范围钉成 [fps, fps]，
        //    空闲时不会再把刷新率降下去。
        Mode want = findById(listModes(ctx), modeId);
        int wantFps = want != null ? want.fpsInt() : -1;
        if (wantFps > 0 && OverlayModeSwitch.supported(ctx)) {
            OverlayModeSwitch.request(ctx, modeId, wantFps);
        }

        // 1) 有 root：再叠加 cmd display（全局生效、按 framework modeId）
        if (!hasRoot()) {
            return wantFps > 0 && OverlayModeSwitch.supported(ctx);
        }
        boolean ok = false;
        Display.Mode target = frameworkModeById(ctx, modeId);
        if (target != null) {
            String cmd = "cmd display set-user-preferred-display-mode "
                    + target.getPhysicalWidth() + " " + target.getPhysicalHeight() + " "
                    + Math.round(target.getRefreshRate()) + " " + SwitchService.getTargetDisplay(ctx);
            ok = exec(cmd);
        }

        // 1.5) HyperOS：同时写安全设置里的刷新率上限。
        // 这类 LTPO 机型上只切 SurfaceFlinger 模式会被自适应逻辑覆盖，写 miui_refresh_rate 才真正生效。
        if (hasRoot()) {
            Mode capMode = findById(listModes(ctx), modeId);
            if (capMode != null) setRefreshRateCap(capMode.fpsInt());
        }

        // 2) root 兜底：SurfaceFlinger 事务（索引尽量解析准确）
        int sfIndex = sfIndexFor(ctx, modeId);
        if (sfIndex >= 0) {
            ok = exec("service call SurfaceFlinger " + SF_SET_MODE_CODE + " i32 " + sfIndex) || ok;
        }
        return ok;
    }

    /** framework 的 Display.Mode（按 modeId 找）。 */
    private static Display.Mode frameworkModeById(Context ctx, int modeId) {
        Display d = defaultDisplay(ctx);
        if (d == null) return null;
        for (Display.Mode m : d.getSupportedModes()) {
            if (m.getModeId() == modeId) return m;
        }
        return null;
    }

    /**
     * modeId → SurfaceFlinger 模式索引。
     * 先从 dumpsys display 里解析 sfModeId（root 可用），失败则退回 modeId-1。
     */
    public static int sfIndexFor(Context ctx, int modeId) {
        try {
            // 只取第一段 DisplayDeviceInfo（即主屏 display 0）的 supportedModes，
            // 避免解析到第二块屏幕（本机有两块屏）导致索引错位
            String dump = suOut("dumpsys display | grep -m1 'supportedModes'");
            if (dump != null) {
                // 解析 supportedModes 列表：{id=1, ..., sfModeId=0, ...}
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("id=(\\d+)[^}]*?sfModeId=(\\d+)").matcher(dump);
                while (m.find()) {
                    if (Integer.parseInt(m.group(1)) == modeId) {
                        return Integer.parseInt(m.group(2));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return modeId - 1;
    }

    /**
     * 写 HyperOS 的刷新率上限（= 最高到 N Hz）。需要 root。
     *
     * 实测本机（HyperOS 4 / 小米 17 Pro）：这三个键都能收窄 SurfaceFlinger 的
     * render 范围上限（dumpsys display → mDisplayModeSpecs 的 render: (0.0 N)），
     * 也就是把 LTPO 的自适应上限钉死。系统里没有可用的「下限」杠杆
     * （AOSP 的 min_refresh_rate 被忽略、cmd display 无 min 参数、SF 事务会被 director 覆盖），
     * 因此上限钉住是这台机器上能拿到的最强控制。
     */
    public static void setRefreshRateCap(int fps) {
        if (fps <= 0 || !hasRoot()) return;
        exec("settings put secure miui_refresh_rate " + fps
                + "; settings put secure user_refresh_rate " + fps
                + "; settings put system plugin_refresh_rate " + fps
                + "; setprop persist.sys.refreshswitch.lock_fps " + fps);
    }

    /** 恢复出厂的自适应行为（解除锁定时调用）。 */
    public static void clearRefreshRateCap() {
        if (!hasRoot()) return;
        exec("settings delete secure miui_refresh_rate"
                + "; settings delete secure user_refresh_rate"
                + "; settings put system plugin_refresh_rate -1"
                + "; setprop persist.sys.refreshswitch.lock_fps 0");
    }
    public static boolean applyFps(Context ctx, int fps) {
        for (Mode m : listModes(ctx)) {
            if (m.fpsInt() == fps) return applyMode(ctx, m.id);
        }
        return false;
    }

    public static Mode findByFps(Context ctx, int fps, int tolerance) {
        for (Mode m : listModes(ctx)) {
            if (Math.abs(m.fpsInt() - fps) <= tolerance) return m;
        }
        return null;
    }

    public static Mode findById(List<Mode> modes, int id) {
        for (Mode m : modes) if (m.id == id) return m;
        return null;
    }

    // ---------- root ----------

    public static boolean hasRoot() {
        if (suOk == null) suOk = (suOut("id") != null);
        return suOk;
    }

    public static void resetRootCache() { suOk = null; }

    /** 自我授予悬浮窗权限（需 root）。 */
    public static boolean grantOverlay() {
        return exec("appops set " + "com.dsh.refreshswitch" + " SYSTEM_ALERT_WINDOW allow");
    }

    /** 加入电池优化白名单，降低被系统杀掉的概率（需 root）。 */
    public static boolean whitelistBattery() {
        return exec("dumpsys deviceidle whitelist +com.dsh.refreshswitch");
    }

    public static String suOut(String cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            if (p.waitFor() != 0) { Log.w(TAG, "su rc!=0 cmd=" + cmd); return null; }
            return sb.toString();
        } catch (Throwable t) {
            Log.e(TAG, "su failed: " + t);
            if (p != null) p.destroy();
            return null;
        }
    }

    private static boolean exec(String cmd) {
        String o = suOut(cmd);
        boolean ok = o != null;
        Log.i(TAG, "exec " + cmd + " -> " + (ok ? "ok" : "FAIL"));
        return ok;
    }


}
