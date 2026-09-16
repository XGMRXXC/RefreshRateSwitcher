package com.dsh.refreshswitch;

import android.content.Context;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 屏幕信息 + LTPO 判定。
 *
 * LTPO/VRR 屏在未锁定时会跟随内容把面板刷新率降到 1~120Hz 之间动态变化，
 * 此时 framework 上报的仍是模式标称值（例如 120），但实际渲染帧率（renderFrameRate）更低。
 * 因此判定条件：支持可变刷新率（hasArrSupport / alternativeRefreshRates）且
 * renderFrameRate 明显低于模式标称值 → 认为进入了 LTPO 动态刷新率模式。
 */
public final class ScreenInfo {

    public static final class Info {
        public int modeFps = -1;      // 当前模式的标称刷新率
        public int renderFps = -1;    // 实际渲染帧率
        public boolean arr = false;   // 是否支持可变刷新率
        public int width = 0;
        public int height = 0;
        public int densityDpi = 0;
        public int minFps = -1;       // 支持的最低刷新率
        public int maxFps = -1;       // 支持的最高刷新率

        /**
         * 该刷新率挡位是否属于 LTPO 可变挡位。
         * 实测：LTPO 面板只有「最高挡位」（如 120）与 60Hz 会走动态刷新率，
         * 其余挡位（24/30/90 等）是固定值，不应显示为「最高到」。
         */
        public boolean isLtpoRate(int fps) {
            if (!arr || fps <= 0) return false;
            return fps == maxFps || fps == 60;
        }
        /** 是否处于 LTPO 动态刷新率模式（未锁定时才由 UI 结合锁定状态判断）。 */
        public boolean isLtpo() {
            return arr && renderFps > 0 && modeFps > 0 && renderFps + 1 < modeFps;
        }

        public String rangeText() {
            if (minFps > 0 && maxFps > 0) return minFps + " – " + maxFps + " Hz";
            return "—";
        }
    }

    private static final Pattern MODE_ID = Pattern.compile("modeId (\\d+)");
    private static final Pattern RENDER = Pattern.compile("renderFrameRate ([\\d.]+)");
    private static final Pattern ARR = Pattern.compile("hasArrSupport (true|false)");
    private static final Pattern SIZE = Pattern.compile("(\\d+) x (\\d+)");
    private static final Pattern DENSITY = Pattern.compile("density (\\d+)");
    private static final Pattern MODE_ENTRY = Pattern.compile("\\{id=(\\d+),[^}]*?fps=([\\d.]+)");
    private static final Pattern RATE = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private static Info cached;
    private static long cachedAt = 0L;

    private ScreenInfo() {}

    /** 读取屏幕信息（root，结果缓存 3 秒）。 */
    public static Info read(Context ctx) {
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAt < 3000) return cached;

        Info info = new Info();
        try {
            String dump = ModeUtil.suOut("dumpsys display | grep -m1 'DisplayDeviceInfo'");
            if (dump != null && !dump.isEmpty()) {
                Matcher m;
                int modeId = -1;
                m = MODE_ID.matcher(dump);
                if (m.find()) modeId = parseIntSafe(m.group(1));

                m = RENDER.matcher(dump);
                if (m.find()) info.renderFps = Math.round(parseFloatSafe(m.group(1)));

                m = ARR.matcher(dump);
                if (m.find()) info.arr = "true".equals(m.group(1));

                m = SIZE.matcher(dump);
                if (m.find()) {
                    info.width = parseIntSafe(m.group(1));
                    info.height = parseIntSafe(m.group(2));
                }
                m = DENSITY.matcher(dump);
                if (m.find()) info.densityDpi = parseIntSafe(m.group(1));

                // 模式表：找当前 modeId 的 fps，并统计最低/最高
                Matcher me = MODE_ENTRY.matcher(dump);
                while (me.find()) {
                    int id = parseIntSafe(me.group(1));
                    int fps = Math.round(parseFloatSafe(me.group(2)));
                    if (fps <= 0) continue;
                    if (id == modeId) info.modeFps = fps;
                    if (info.minFps <= 0 || fps < info.minFps) info.minFps = fps;
                    if (fps > info.maxFps) info.maxFps = fps;
                }
                // 注意：不能用 alternativeRefreshRates 判断可变刷新率 ——
                // 普通固定刷新率面板也会列出 alternatives（实测 K Pad hasArrSupport=false 但仍有 alternatives），
                // 只用 DisplayDeviceInfo 的 hasArrSupport 作为 LTPO/ARR 的判据。
            }
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "ScreenInfo.read failed", t);
        }
        if (info.modeFps <= 0) {
            // 兜底：用 framework 上报的当前模式
            ModeUtil.Mode cur = ModeUtil.currentMode(ctx);
            if (cur != null) info.modeFps = cur.fpsInt();
        }
        cached = info;
        cachedAt = now;
        return info;
    }

    /** 一块屏：id / 宽 / 高 / 名称 / 是否主屏。 */
    public static final class DisplayEntry {
        public final int id;
        public final int width;
        public final int height;
        public final String name;
        public final boolean main;

        public DisplayEntry(int id, int width, int height, String name, boolean main) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.name = name;
            this.main = main;
        }

        public String label() {
            return (main ? "主屏" : "副屏") + " · display " + id + "  (" + width + "×" + height + ")";
        }
    }

    /** 系统里的所有屏幕（无需 root）。 */
    public static java.util.List<DisplayEntry> displayList(Context ctx) {
        java.util.List<DisplayEntry> out = new java.util.ArrayList<>();
        try {
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) return out;
            for (android.view.Display d : dm.getDisplays()) {
                if (d == null) continue;
                android.util.DisplayMetrics m = new android.util.DisplayMetrics();
                d.getRealMetrics(m);
                int id = d.getDisplayId();
                out.add(new DisplayEntry(id, m.widthPixels, m.heightPixels, String.valueOf(d.getName()), id == android.view.Display.DEFAULT_DISPLAY));
            }
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "displayList failed", t);
        }
        java.util.Collections.sort(out, new java.util.Comparator<DisplayEntry>() {
            @Override public int compare(DisplayEntry a, DisplayEntry b) { return Integer.compare(a.id, b.id); }
        });
        return out;
    }
    public static void invalidate() {
        cached = null;
        cachedAt = 0L;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return -1;
        }
    }

    private static float parseFloatSafe(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Throwable t) {
            return -1f;
        }
    }
}
