package com.dsh.refreshswitch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 设置 ⇄ 主题设置：独立整页切换（不是弹窗）。
 * 打开状态由外壳持有（themeOpen / onThemeOpenChange），以便二级页时隐藏外壳顶栏。
 * 转场按 MIUIX 官方预设 NavTransitions.MiuixDefault 的三条特征复刻：
 *   ① 全宽滑动  ② 被覆盖层 ¼ 宽视差  ③ 被覆盖层轻微透明衰减；并接系统返回键。
 */
@Composable
fun SettingsNavHost(
    themeOpen: Boolean,
    onThemeOpenChange: (Boolean) -> Unit,
    settings: @Composable (onOpenTheme: () -> Unit) -> Unit,
    theme: @Composable (onBack: () -> Unit) -> Unit,
) {
    BackHandler(enabled = themeOpen) { onThemeOpenChange(false) }

    AnimatedContent(
        targetState = themeOpen,
        transitionSpec = {
            val dur = 320
            if (targetState) {
                (slideInHorizontally(tween(dur)) { it } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(dur)) { -it / 4 } + fadeOut(tween(240)))
            } else {
                (slideInHorizontally(tween(dur)) { -it / 4 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(dur)) { it } + fadeOut(tween(240)))
            }
        },
        label = "settingsNav",
    ) { open ->
        Box(Modifier.fillMaxSize()) {
            if (open) theme { onThemeOpenChange(false) } else settings { onThemeOpenChange(true) }
        }
    }
}