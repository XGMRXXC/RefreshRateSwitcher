package com.dsh.refreshswitch;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * 免 root 切换刷新率：叠加一个 1x1 的透明窗口，并把
 * {@link WindowManager.LayoutParams#preferredDisplayModeId} 设为目标模式。
 *
 * 这是 AOSP 上媒体类应用强制刷新率（例如 24Hz 放电影）的标准做法：
 * WindowManager 会把这个窗口的偏好模式作为 "app request" 交给 DisplayModeDirector，
 * 显示模式随之切换，不需要 root，也不依赖 SurfaceFlinger 事务码。
 * 用的是 framework 的 {@code Display.Mode.getModeId()}，跨机型一致。
 */
public final class OverlayModeSwitch {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static View view;
    private static WindowManager wm;
    private static int currentModeId = Integer.MIN_VALUE;

    private OverlayModeSwitch() {}

    /** 是否有悬浮窗权限（没有它就无法免 root 切换）。 */
    public static boolean supported(Context ctx) {
        try {
            return Settings.canDrawOverlays(ctx);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int currentModeId() {
        return currentModeId;
    }

    /** 要请求的固定帧率（>0 时用 preferredRefreshRate 钉住 app request 范围）。 */
    private static volatile int requestFps = -1;

    /**
     * 请求「切到该模式」+「以固定帧率请求该刷新率」。
     * 后者会让系统把 app request 的 render 范围钉成 [fps, fps]，
     * 从而抬高 LTPO 的下限（空闲时不再掉到 1~30Hz）。
     */
    public static void request(Context ctx, int modeId, int fps) {
        requestFps = fps;
        request(ctx, modeId);
    }

    /** 请求切到指定模式（framework 的 Display.Mode id）。 */
    public static void request(Context ctx, int modeId) {
        if (modeId <= 0) return;
        Context app = ctx.getApplicationContext();
        MAIN.post(() -> apply(app, modeId));
    }

    private static void apply(Context ctx, int modeId) {
        if (!supported(ctx)) return;
        try {
            if (wm == null) wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = 0;
            lp.y = 0;
            lp.alpha = 0.01f;
            lp.preferredDisplayModeId = modeId;
            // 关键：以「固定帧率」请求该刷新率，让系统把 app request 范围钉成 [fps, fps]，
            // 这样 LTPO 就不会在空闲时把刷新率降下去（等于抬高了下限）
            if (requestFps > 0) lp.preferredRefreshRate = requestFps;
            lp.setTitle("RefreshSwitchMode");

            if (view == null) {
                view = new View(ctx);
                view.setBackgroundColor(0x01000000);
                wm.addView(view, lp);
            } else {
                wm.updateViewLayout(view, lp);
            }
            currentModeId = modeId;
            Log.i(ModeUtil.TAG, "overlay mode request -> modeId " + modeId);
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "overlay mode switch failed", t);
        }
    }

    /** 移除窗口（切换回 root 路径或退出时调用）。 */
    public static void release() {
        MAIN.post(() -> {
            try {
                if (view != null && wm != null) wm.removeView(view);
            } catch (Throwable ignored) {
            }
            view = null;
            wm = null;
            currentModeId = Integer.MIN_VALUE;
        });
    }
}
