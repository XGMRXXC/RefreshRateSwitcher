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

    public static Display defaultDisplay(Context ctx) {
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return null;
        return dm.getDisplay(Display.DEFAULT_DISPLAY);
    }

    /**
     * 支持的显示模式，**按刷新率由低到高排序**（不按 modeId）。
     */
    public static List<Mode> listModes(Context ctx) {
        List<Mode> out = new ArrayList<>();
        Display d = defaultDisplay(ctx);
        if (d != null) {
            for (Display.Mode m : d.getSupportedModes()) {
                boolean dup = false;
                for (Mode u : out) if (u.id == m.getModeId()) { dup = true; break; }
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
     * 切换显示模式。
     * DisplayManager 的 modeId 比 SurfaceFlinger 的模式索引大 1（本机实测），故减 1。
     */
    public static boolean applyMode(int modeId) {
        int sfIndex = modeId - 1;
        return exec("service call SurfaceFlinger " + SF_SET_MODE_CODE + " i32 " + sfIndex);
    }

    public static boolean applyFps(Context ctx, int fps) {
        for (Mode m : listModes(ctx)) {
            if (m.fpsInt() == fps) return applyMode(m.id);
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
