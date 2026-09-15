package com.dsh.refreshswitch;

import android.content.Context;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * 触感反馈工具 —— **完全适配系统震动设置**：
 *   1) 走 View.performHapticFeedback（系统会读取「触摸时震动」开关与触感强度）
 *   2) 悬浮窗等场景若上者不可用，回退到 Vibrator 的**系统预定义效果**
 *      （EFFECT_CLICK / EFFECT_TICK，即厂商调校过的点击触感）并标注 USAGE_TOUCH
 *   3) 任何路径都先检查 Settings.System.HAPTIC_FEEDBACK_ENABLED —— 用户关了震动就绝不震
 */
public final class Haptics {

    private Haptics() {}

    /** 系统「触摸时震动」是否开启 */
    public static boolean systemEnabled(Context c) {
        if (c == null) return false;
        try {
            return Settings.System.getInt(c.getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
        } catch (Throwable t) {
            return true;
        }
    }

    /** 轻点（挡位切换） */
    public static void tick(View v) {
        fb(v, HapticFeedbackConstants.CLOCK_TICK);
    }

    /** 按键（按钮 / 开关） */
    public static void click(View v) {
        fb(v, HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** 确认（锁定等状态变更） */
    public static void confirm(View v) {
        fb(v, Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** 无 View 场景（通知快捷按钮等） */
    public static void click(Context c) {
        fallback(c, false);
    }

    public static void tick(Context c) {
        fallback(c, true);
    }

    private static void fb(View v, int constant) {
        if (v == null) return;
        boolean ok = false;
        try {
            // FLAG_IGNORE_VIEW_SETTING：忽略视图级开关，但**保留系统全局设置**
            ok = v.performHapticFeedback(constant,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Throwable ignored) {}
        if (!ok) {
            fallback(v.getContext(), constant == HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    /** 系统预定义触感效果（厂商调校），仅在系统开启震动时执行 */
    private static void fallback(Context c, boolean isTick) {
        if (c == null || !systemEnabled(c)) return;
        try {
            Vibrator vb = vibrator(c);
            if (vb == null || !vb.hasVibrator()) return;
            int effect = isTick ? VibrationEffect.EFFECT_TICK : VibrationEffect.EFFECT_CLICK;
            VibrationEffect e = VibrationEffect.createPredefined(effect);
            if (Build.VERSION.SDK_INT >= 33) {
                vb.vibrate(e, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
            } else {
                vb.vibrate(e);
            }
        } catch (Throwable ignored) {}
    }

    private static Vibrator vibrator(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
