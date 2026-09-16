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

/** MIUIX 悬浮底栏（胶囊浮在内容之上）。 */
@Composable
fun MiuixFloatingNav(
    items: List<Pair<ImageVector, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean = false,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        if (vertical) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { i, (icon, label) ->
                    MiuixNavPill(icon, label, i == selected, 74.dp) {
                        Haptics.click(view)
                        onSelect(i)
                    }
                }
            }
        } else {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { i, (icon, label) ->
                    MiuixNavPill(icon, label, i == selected, 74.dp) {
                        Haptics.click(view)
                        onSelect(i)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixNavPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "miuixNavPill",
    )
    val fg = if (selected) MiuixTheme.colorScheme.onPrimaryContainer
    else MiuixTheme.colorScheme.onBackgroundVariant
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = fg, maxLines = 1, textAlign = TextAlign.Center)
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
