package top.yukonga.miuix.kmp.extra

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * MIUIX 0.9.x 兼容层：0.9.x 删除了 SuperSwitch / SuperDropdown（extra 包整体消失），
 * 这里用**同名同包**的方式基于 BasicComponent 重新实现，使项目里原有调用点零改动。
 */
@Composable
fun SuperSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        endActions = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

@Composable
fun SuperDropdown(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    summary: String? = null,
    showValue: Boolean = true,
    @Suppress("UNUSED_PARAMETER") renderInRootScaffold: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    BasicComponent(
        title = title,
        summary = summary,
        startAction = startAction,
        onClick = { expanded = true },
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showValue) {
                    Text(
                        text = items.getOrNull(selectedIndex) ?: "",
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowUpDown,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                )
            }
        },
    )

    WindowListPopup(
        show = expanded,
        onDismissRequest = { expanded = false },
        enableWindowDim = false,
        maxHeight = 420.dp,
    ) {
        Column(Modifier.width(220.dp).padding(vertical = 6.dp)) {
            items.forEachIndexed { index, label ->
                BasicComponent(
                    title = label,
                    onClick = {
                        expanded = false
                        onSelectedIndexChange(index)
                    },
                )
            }
        }
    }
}