package top.yukonga.miuix.kmp.extra

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/**
 * MIUIX 0.9.x 兼容层。
 *
 * 0.9.x 把 Super* 组件从 `miuix-ui` 移到了 **`miuix-preference`**
 * （`SwitchPreference` / `WindowDropdownPreference` / `WindowSpinnerPreference` …）。
 * 这里保持**同名同包 + 同参数**，调用点零改动，但内部**全部委托给 MIUIX 原生组件** ——
 * 不再手搓弹层与定位（手搓版本的下拉位置不正确）。
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
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary,
        enabled = enabled,
        modifier = modifier,
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
    WindowDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        summary = summary,
        showValue = showValue,
        startAction = startAction,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}
