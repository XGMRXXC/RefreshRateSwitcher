package com.dsh.refreshswitch

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpOffset
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 的下拉弹层（SuperDropdown / SuperListPopup）需要
 * [LocalNavigationEventDispatcherOwner]，否则会抛
 * "No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner"。
 * MIUIX 页面统一用这个包一层，就能使用 MIUIX 原生的 popup 组件。
 */
@Composable
fun MiuixPopupHost(content: @Composable () -> Unit) {
    // 传 null 表示创建根 dispatcher（否则它要求上层已经提供 owner）
    val owner = rememberNavigationEventDispatcherOwner(parent = null)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
        content()
    }
}

/**
 * 用 Compose 自带的 DropdownMenu 承载小菜单（例如右上角「更多」），
 * 配色映射到 MIUIX 调色板，避免出现 Material 默认紫。
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
