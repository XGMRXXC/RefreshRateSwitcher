package com.dsh.refreshswitch

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** MIUIX 底栏样式：仅「标准」与「悬浮底栏」。 */
object BarStyle {
    const val STANDARD = 0
    const val FLOATING = 1

    val labels = listOf("标准", "悬浮底栏")
}

/**
 * MIUIX 悬浮底栏 —— 使用**官方 API** `FloatingNavigationBar` + `FloatingNavigationBarItem`
 * （横屏时退化为官方 `NavigationRail`，因为官方悬浮栏只支持水平方向）。
 */
@Composable
fun MiuixFloatingNav(
    items: List<Pair<ImageVector, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean = false,
) {
    val view = LocalView.current
    if (vertical) {
        // 官方可展开侧栏（折叠 + 选中项文本弹出）
        val railState = rememberNavigationRailState(NavigationRailValue.Collapsed)
        NavigationRail(state = railState) {
            items.forEachIndexed { i, (icon, label) ->
                NavigationRailItem(
                    selected = i == selected,
                    onClick = { Haptics.click(view); onSelect(i) },
                    icon = icon,
                    label = label,
                )
            }
        }
        return
    }
    FloatingNavigationBar(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { i, (icon, label) ->
            FloatingNavigationBarItem(
                selected = i == selected,
                onClick = {
                    Haptics.click(view)
                    onSelect(i)
                },
                icon = icon,
                label = label,
            )
        }
    }
}


/** MIUIX 单选行：直接用 MIUIX 原生 SuperDropdown（行 + ⌃⌄ + 原生 popup）。 */
@Composable
fun MiuixPickerRow(
    title: String,
    value: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
) {
    top.yukonga.miuix.kmp.extra.SuperDropdown(
        items = options,
        selectedIndex = selectedIndex,
        title = title,
        summary = null,
        showValue = true,
        renderInRootScaffold = true,
        onSelectedIndexChange = { onPick(it) },
    )
}
