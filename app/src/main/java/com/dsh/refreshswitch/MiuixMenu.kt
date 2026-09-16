package com.dsh.refreshswitch

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 用 Compose 自带的 DropdownMenu 承载 MIUIX 的选项框（不再手绘 Popup），
 * 并把配色映射到 MIUIX 调色板，避免出现 Material 默认紫。
 */
@Composable
fun MiuixDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val dark = isSystemInDarkTheme()
    val miuix = MiuixTheme.colorScheme
    val scheme: ColorScheme = if (dark) {
        darkColorScheme(
            primary = miuix.primary,
            onPrimary = miuix.onPrimary,
            surfaceContainer = miuix.surface,
            surface = miuix.surface,
            onSurface = miuix.onSurface,
            onSurfaceVariant = miuix.onBackgroundVariant,
        )
    } else {
        lightColorScheme(
            primary = miuix.primary,
            onPrimary = miuix.onPrimary,
            surfaceContainer = miuix.surface,
            surface = miuix.surface,
            onSurface = miuix.onSurface,
            onSurfaceVariant = miuix.onBackgroundVariant,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismissRequest,
            offset = offset,
            content = content,
        )
    }
}
