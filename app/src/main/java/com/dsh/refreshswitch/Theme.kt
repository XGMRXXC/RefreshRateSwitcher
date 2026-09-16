package com.dsh.refreshswitch

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import android.app.Activity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 界面风格：MIUIX（HyperOS / compose-miuix-ui）与 M3E（Material 3 Expressive）。 */
enum class UiStyle(val id: Int, val label: String) {
    MIUIX(0, "MIUIX"),
    M3E(1, "M3E"),
    ;

    companion object {
        fun of(id: Int): UiStyle = if (id == M3E.id) M3E else MIUIX
    }
}

/** MIUIX 侧的形状令牌（M3E 使用 Material3 自身形状，不在此列）。 */
data class StyleTokens(
    val cardRadius: Dp = 16.dp,
    val chipRadius: Dp = 14.dp,
    val itemSpacing: Dp = 8.dp,
)

val LocalStyleTokens = staticCompositionLocalOf { StyleTokens() }

/** MIUIX 主题（HyperOS 固定配色）。 */
@Composable
fun AppTheme(style: UiStyle = UiStyle.MIUIX, content: @Composable () -> Unit) {
    val controller = remember(style) {
        ThemeController(colorSchemeMode = ColorSchemeMode.System)
    }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalStyleTokens provides StyleTokens()) {
            content()
        }
    }
}

/**
 * M3E 主题：Material 3 **Expressive** —— Material You 动态取色（壁纸取色）。
 * 注：Compose Multiplatform 的 material3 包装把 MaterialExpressiveTheme 标为 internal，
 * 因此表达性开关等形态在 M3eScreens 中自行实现（见 M3eSwitch）。
 */
@Composable
fun M3eTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = remember(dark) {
        when {
            Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
            Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
    // 注：MaterialExpressiveTheme 需要 material3 1.5.0-alpha+（KernelSU 用的版本），
    // 本项目固定在 1.4.0（该 API 为 internal），表达性形态由 M3eSwitch 等自绘组件承担。
    MaterialTheme(colorScheme = scheme) {
        // M3E 的容器是 Box(background(...))，不像 Scaffold/Surface 会自动设置内容色，
        // 不显式提供的话 LocalContentColor 保持默认黑色 → 深色模式下文字发黑。
        CompositionLocalProvider(
            LocalContentColor provides scheme.onBackground,
        ) {
            content()
        }
    }
}

/**
 * 状态栏 / 导航栏图标反色（对应 KernelSU MaterialTheme.kt 里的 WindowInsetsControllerCompat 设置）：
 * 浅色主题用深色图标，深色主题用浅色图标。两种风格共用。
 */
@Composable
fun ApplySystemBarAppearance() {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    LaunchedEffect(dark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** MIUIX 分割线（默认 0.75dp/#E0E0E0 实机上几乎看不见，这里改为 1dp 深灰）。 */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = if (dark) Color(0xFF353535) else Color(0xFFD2D2D2),
    )
}

/** M3E 分割线。 */
@Composable
fun M3eDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 当前是否横屏（宽 > 高）。 */
@Composable
fun isWideScreen(): Boolean {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    return cfg.screenWidthDp > cfg.screenHeightDp
}

internal fun ctxOf(c: Context): Context = c
