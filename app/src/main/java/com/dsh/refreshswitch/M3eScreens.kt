@file:OptIn(ExperimentalMaterial3Api::class)

package com.dsh.refreshswitch

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.icon.extended.Forward
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Settings as SettingsIcon
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Update

// =====================================================================
// M3E 组件 —— 参照 KernelSU（tiann/KernelSU, manager/.../ui/component/material/*）：
//   TonalCard          → Card + CardDefaults.cardColors + MaterialTheme.shapes.large
//   ExpressiveSwitch   → Switch + thumbContent(Check/Close) + SwitchDefaults.colors
//   版式：扁平顶栏 / primary hero 卡 / 双 tonal 统计卡 / 无分割线列表卡 / 悬浮胶囊底栏
// =====================================================================

/** 扁平顶栏：应用图标 + 粗标题 + 右侧动作。 */
@Composable
fun M3eTopBar(title: String, icon: ImageVector, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** 通用 tonal 卡（KernelSU TonalCard 的等价物）。 */
@Composable
fun M3eCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

/** hero 主色卡（对应 KSU 的「工作中 / 版本」卡）。 */
@Composable
fun M3eHeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val heroModifier = Modifier.fillMaxWidth()
    val heroShape = MaterialTheme.shapes.large
    val heroColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = heroModifier,
            shape = heroShape,
            colors = heroColors,
        ) {
            M3eHeroBody(title, subtitle, icon, badge)
        }
    } else {
        Card(
            modifier = heroModifier,
            shape = heroShape,
            colors = heroColors,
        ) {
            M3eHeroBody(title, subtitle, icon, badge)
        }
    }
}

@Composable
private fun M3eHeroBody(title: String, subtitle: String, icon: ImageVector, badge: String?) {
    Column {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(badge, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
            }
            }
        }
    }

/** 统计卡（对应 KSU 的「超级用户 7」）。 */
@Composable
fun M3eStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** 列表行：前置图标 + 标题 + 说明 + 尾部内容（卡片行，无分割线）。 */
@Composable
fun M3eRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val alpha = if (enabled) 1f else 0.38f
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable {
                        Haptics.click(view)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** M3E 单选行：单击弹出锚定在行下方的 Material3 选项框。 */
/** M3E 单选行：单击在行下方弹出 Material3 风格选项框。 */
/** M3E 单选行：单击在行下方弹出 Material3 原生下拉菜单（锚定 + 动画，无箭头）。 */
@Composable
fun M3ePickerRow(
    title: String,
    value: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var tapX by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                tapX = offset.x
                Haptics.click(view)
                expanded = true
            }
        },
    ) {
        M3eRow(
            icon = null,
            title = title,
            subtitle = value,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(with(density) { tapX.toDp() }, 0.dp),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = MiuixIcons.Basic.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        Haptics.tick(view)
                        expanded = false
                        onPick(index)
                    },
                )
            }
        }
    }
}


/** KernelSU 的 ExpressiveSwitch：Switch + 拇指图标（开=对勾，关=叉）。 */
@Composable
fun M3eSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        thumbContent = {
            Icon(
                imageVector = if (checked) MiuixIcons.Basic.Check else MiuixIcons.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
        colors = expressiveSwitchColors(),
    )
}

/** 对应 KernelSU 的 expressiveSwitchColors()。 */
@Composable
private fun expressiveSwitchColors(
    checkedIconColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
): SwitchColors = SwitchDefaults.colors(
    checkedIconColor = checkedIconColor,
    uncheckedIconColor = uncheckedIconColor,
)

/** 悬浮胶囊底栏（KernelSU ShortNavigationBar 视觉：圆角胶囊 + 选中 pill）。 */
@Composable
fun M3eFloatingNav(
    items: List<Pair<ImageVector, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean = false,
) {
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        if (vertical) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { i, (icon, label) ->
                    NavPill(icon, label, i == selected, 72.dp) {
                        Haptics.click(view)
                        onSelect(i)
                    }
                }
            }
        } else {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { i, (icon, label) ->
                    NavPill(icon, label, i == selected, 76.dp) {
                        Haptics.click(view)
                        onSelect(i)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "navPill",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
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
        Text(label, fontSize = 10.sp, color = fg, maxLines = 1)
    }
}

// =====================================================================
// M3E 页面
// =====================================================================

@Composable
private fun m3eAsyncRefresh(st: AppState): () -> Unit {
    val scope = rememberCoroutineScope()
    return { scope.launch { withContext(Dispatchers.IO) { st.refresh() } } }
}

/** M3E 主页。 */
@Composable
fun M3eHomeScreen(st: AppState, onOpenOverlay: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val refresh = m3eAsyncRefresh(st)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        M3eHeroCard(
            onClick = {
                Haptics.click(view)
                if (st.screen.arr) {
                    toast(ctx, "LTPO 屏不支持锁定")
                    return@M3eHeroCard
                }
                if (st.lockEnabled) {
                    SwitchService.unlock(ctx)
                    toast(ctx, "已解锁")
                } else {
                    val m = ModeUtil.findByFps(ctx, ModeUtil.currentFps(ctx), 1)
                    if (m != null) {
                        SwitchService.lockTo(ctx, m.id, m.fpsInt())
                        toast(ctx, "已锁定 " + m.shortLabel())
                    } else {
                        SwitchService.setLockEnabled(ctx, true)
                    }
                }
                if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                refresh()
            },
            title = when {
                st.lockEnabled -> "已锁定 ${if (st.lockedFps > 0) "${st.lockedFps}Hz" else "当前挡位"}"
                st.capFps >= 60 -> "最高到 ${st.capFps}Hz"
                st.capFps > 0 -> "已切到 ${st.capFps}Hz"
                else -> "未锁定"
            },
            subtitle = buildString {
                append("当前 ")
                append(if (st.fps > 0) "${st.fps} Hz" else "未知")
                append(" · ")
                // 只有开启锁定时才会自动纠正系统改动，未锁定时不要这么写
                append(
                    when {
                        st.lockEnabled -> "系统改动会被自动纠正"
                        st.screen.isLtpo -> "已进入 LTPO 刷新率模式（实际 ${st.screen.renderFps}Hz）"
                        else -> "未锁定，仅按手动选择的挡位切换"
                    },
                )
            },
            icon = if (st.lockEnabled) MiuixIcons.Lock else MiuixIcons.Unlock,
            badge = when {
                st.lockEnabled -> "持续强制"
                st.screen.isLtpo -> "LTPO"
                else -> null
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            M3eStatCard(
                "当前刷新率",
                if (st.screen.arr) "由 LTPO 控制"
                else if (st.fps > 0) "${st.fps} Hz" else "—",
                Modifier.weight(1f),
            )
            M3eStatCard(
                if (st.lockEnabled && !st.screen.arr) "锁定挡位" else if (st.capFps >= 60) "最高到" else "已选择",
                if (st.lockEnabled) {
                    if (st.lockedFps > 0) "${st.lockedFps} Hz" else "—"
                } else if (st.capFps > 0) "${st.capFps} Hz" else if (st.fps > 0) "${st.fps} Hz" else "—",
                Modifier.weight(1f),
            )
        }

        M3eCard {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("选择挡位", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                val perRow = 3   // 4 个会把「120Hz」截断，改 3 个
                st.modes.chunked(perRow).forEachIndexed { idx, rowModes ->
                    if (idx > 0) Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowModes.forEach { m ->
                            FilterChip(
                                selected = !st.screen.isLtpo && st.fps > 0 && m.fpsInt() == st.fps,
                                onClick = {
                                    Haptics.tick(view)
                                    SwitchService.setCapFps(ctx, m.fpsInt())
                                    if (SwitchService.isLockEnabled(ctx)) {
                                        SwitchService.lockTo(ctx, m.id, m.fpsInt())
                                    } else {
                                        if (!ModeUtil.applyMode(ctx, m.id)) {
                                            toast(ctx, "需要 root 权限")
                                        }
                                    }
                                    if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                                    refresh()
                                },
                                label = {
                                    Text(
                                        text = m.shortLabel(),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(perRow - rowModes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "点击即切换；若锁定已开启，同时会把它设为锁定目标",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        M3eCard {
            M3eRow(null, "分辨率", if (st.screen.width > 0) "${st.screen.width} × ${st.screen.height}" else "—")
            M3eRow(null, "刷新率范围", st.screen.rangeText())
            M3eRow(null, "当前模式", if (st.screen.modeFps > 0) "${st.screen.modeFps} Hz" else "—")
            M3eRow(null, "实际渲染", if (st.screen.renderFps > 0) "${st.screen.renderFps} Hz" else "—")
            M3eRow(null, "面板类型", if (st.screen.arr) "LTPO / 可变刷新率" else "固定刷新率")
            M3eRow(null, "屏幕密度", if (st.screen.densityDpi > 0) "${st.screen.densityDpi} dpi" else "—")
        }



        Spacer(Modifier.height(96.dp))
    }
}

/** M3E 设置页。 */
@Composable
fun M3eSettingsScreen(st: AppState) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val refresh = m3eAsyncRefresh(st)
    val style = UiStyle.of(SwitchService.getUiStyle(ctx))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        M3eCard {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                M3ePickerRow(
                    title = "界面风格",
                    value = style.label,
                    options = UiStyle.entries.map { it.label },
                    selectedIndex = style.id,
                ) { index ->
                    SwitchService.setUiStyle(ctx, index)
                    (ctx as? Activity)?.recreate()
                }
                M3ePickerRow(
                    title = "底栏样式",
                    value = BarStyle.labels.getOrElse(st.bottomBarStyle) { BarStyle.labels[0] },
                    options = BarStyle.labels,
                    selectedIndex = st.bottomBarStyle,
                ) { index ->
                    SwitchService.setBottomBarStyle(ctx, index)
                    refresh()
                }
            }
        }

        M3eCard {
            M3eRow(
                icon = MiuixIcons.Tune,
                title = "自动化",
                subtitle = if (st.autoEnabled) "已开启 · 共 ${AutoRules.ruleCount(ctx)} 条规则，底栏显示「自动化」页" else "打开后底栏才会出现「自动化」页",
                trailing = {
                    M3eSwitch(st.autoEnabled, onCheckedChange = { on ->
                        Haptics.click(view)
                        AutoRules.setEnabled(ctx, on)
                        toast(ctx, if (on) "自动化已开" else "自动化已关")
                        refresh()
                    })
                },
            )
        }

        M3eCard {
            M3eRow(
                icon = MiuixIcons.Messages,
                title = "常驻通知",
                subtitle = "在通知栏显示当前刷新率与快捷挡位按钮",
                trailing = {
                    M3eSwitch(st.notifyEnabled, { on ->
                        Haptics.click(view)
                        SwitchService.setNotifyEnabled(ctx, on)
                        toast(ctx, if (on) "通知已开" else "通知已关")
                        refresh()
                    })
                },
            )
            M3eRow(
                icon = MiuixIcons.Messages,
                title = "Toast 提示",
                subtitle = if (st.toastEnabled) "显示全部操作提示" else "已关闭，所有 toast 都不再弹出",
                trailing = {
                    M3eSwitch(st.toastEnabled, onCheckedChange = { on ->
                        Haptics.click(view)
                        SwitchService.setToastEnabled(ctx, on)
                        if (on) toast(ctx, "提示已开")
                        refresh()
                    })
                },
            )
            M3eRow(
                icon = MiuixIcons.Update,
                title = "守护状态",
                subtitle = if (st.notifyEnabled) "通知栏常驻 · 前台服务运行中"
                else if (st.daemonPid > 0) "root 守护运行中 · PID ${st.daemonPid}"
                else "root 守护未运行（需 root 权限）",
            )
        }

        M3eCard {
            M3eRow(
                icon = MiuixIcons.Hide,
                title = "隐藏桌面图标",
                subtitle = "隐藏后仍可从悬浮窗右上角进入本应用",
                trailing = {
                    M3eSwitch(st.hideIcon, { on ->
                        Haptics.click(view)
                        SwitchService.setHideIcon(ctx, on)
                        toast(ctx, if (on) "图标已隐藏" else "图标已显示")
                        refresh()
                    })
                },
            )
            M3eRow(
                icon = MiuixIcons.ListView,
                title = "隐藏后台任务",
                subtitle = "最近任务列表里看不到本应用，服务照常运行",
                trailing = {
                    M3eSwitch(st.hideRecents, { on ->
                        Haptics.click(view)
                        SwitchService.setHideRecents(ctx, on)
                        toast(ctx, if (on) "已隐藏后台任务" else "已显示后台任务")
                        refresh()
                    })
                },
            )
        }

        M3eCard {
            M3eRow(
                icon = MiuixIcons.Layers,
                title = "悬浮窗权限",
                subtitle = if (st.overlayGranted) "已开启，通知点击直接弹出悬浮面板" else "未开启，将降级为对话框面板",
                trailing = {
                    TextButton(onClick = {
                        SysActions.openOverlaySettings(ctx)
                        refresh()
                    }) { Text(if (st.overlayGranted) "已开启" else "去开启") }
                },
            )
            M3eRow(
                icon = MiuixIcons.Timer,
                title = "电池优化白名单",
                subtitle = if (st.batteryWhitelisted) "已加入，不易被系统回收" else "未加入，可能被系统回收",
                trailing = {
                    TextButton(onClick = { SysActions.openBatterySettings(ctx) }) {
                        Text(if (st.batteryWhitelisted) "已完成" else "去设置")
                    }
                },
            )
            M3eRow(
                icon = MiuixIcons.Refresh,
                title = "自启动（HyperOS）",
                subtitle = "允许后台自启，被杀后可自动恢复",
                trailing = {
                    TextButton(onClick = {
                        Haptics.click(view)
                        SysActions.openAutostart(ctx)
                    }) { Text("去设置") }
                },
            )
            M3eRow(
                icon = MiuixIcons.Reset,
                title = "重启常驻服务",
                subtitle = "立即重新拉起服务与守护任务",
                trailing = {
                    TextButton(onClick = {
                        Haptics.click(view)
                        SwitchService.start(ctx)
                        RestartJobService.schedule(ctx)
                        toast(ctx, "已重启服务")
                        refresh()
                    }) { Text("执行") }
                },
            )
        }

        M3eCard {
            M3eRow(MiuixIcons.Info, "版本", "4.2.2")
            M3eRow(
                if (st.root) MiuixIcons.Lock else MiuixIcons.Unlock,
                "root",
                when {
                    !st.rootChecked -> "检测中…"
                    st.root -> "已授权 ✓"
                    else -> "未授权 ✗"
                },
            )
            Text(
                text = "Powered by Deepseek v4.1 flash",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp),
            )
        }

        Spacer(Modifier.height(96.dp))
    }
}

// =====================================================================
// M3E 悬浮面板
// =====================================================================

/** M3E 悬浮面板外壳：全屏透明 + 居中卡片，点空白处关闭。 */
@Composable
fun M3ePanelRoot(onClose: () -> Unit, onOpenApp: () -> Unit, onResetPosition: () -> Unit = {}) {
    val ctx = LocalContext.current
    val st = remember { AppState(ctx) }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { st.refresh() }
            delay(1000)
        }
    }

    val dm = ctx.resources.displayMetrics
    val cardWidthPx = minOf(dm.widthPixels * 0.66f, 320f * dm.density)
    val cardWidth = with(density) { cardWidthPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        PanelEnterExit(dismissing = OverlayPanel.dismissingState.value) {
            M3ePanelContent(
                st = st,
                modifier = Modifier
                    .width(cardWidth)
                    .shadow(16.dp, RoundedCornerShape(28.dp))
                    .pointerInput(Unit) { detectTapGestures { } },
                onOpenApp = onOpenApp,
                onResetPosition = onResetPosition,
            )
        }
    }

    // 退场动画播完即移除窗口
    LaunchedEffect(OverlayPanel.dismissingState.value) {
        if (OverlayPanel.dismissingState.value) {
            delay(200)
            OverlayPanel.finishHide()
        }
    }
}

/** M3E 悬浮面板内容。 */
@Composable
fun M3ePanelContent(
    st: AppState,
    modifier: Modifier = Modifier,
    onOpenApp: () -> Unit,
    onResetPosition: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val refresh = m3eAsyncRefresh(st)
    val locked = st.lockEnabled && st.lockedFps > 0

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // ---- 标题行：小标题 + 进入应用 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "刷新率",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = MiuixIcons.Reset,
                    contentDescription = "恢复默认位置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { Haptics.click(view); onResetPosition() }
                        .padding(7.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = MiuixIcons.Forward,
                    contentDescription = "进入应用",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { Haptics.click(view); onOpenApp() }
                        .padding(7.dp),
                )
            }

            // ---- 当前刷新率（大字）+ 锁定状态 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (st.fps > 0) "${st.fps}" else "--",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " Hz",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (locked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                    locked -> "已锁定 ${st.lockedFps}Hz"
                    st.screen.arr && st.capFps >= 60 -> "最高到 ${st.capFps}Hz"
                    else -> "未锁定"
                },
                        fontSize = 11.sp,
                        color = if (locked) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 挡位 ----
            val perRow = 3   // 4 个会把「120Hz」截断，改 3 个
            st.modes.chunked(perRow).forEachIndexed { idx, rowModes ->
                if (idx > 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowModes.forEach { m ->
                        FilterChip(
                            selected = !st.screen.isLtpo && st.fps > 0 && m.fpsInt() == st.fps,
                            onClick = {
                                Haptics.tick(view)
                                SwitchService.setCapFps(ctx, m.fpsInt())
                                if (SwitchService.isLockEnabled(ctx)) {
                                    SwitchService.lockTo(ctx, m.id, m.fpsInt())
                                } else {
                                    if (!ModeUtil.applyMode(ctx, m.id)) {
                                        toast(ctx, "需要 root 权限")
                                    }
                                }
                                if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                                refresh()
                            },
                            label = {
                                Text(
                                    text = m.shortLabel(),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(perRow - rowModes.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ---- 锁定开关 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // LTPO 屏幕不支持锁定：整块隐藏
            if (!st.screen.arr) {
                Text("锁定刷新率", fontSize = 14.sp, modifier = Modifier.weight(1f))
                M3eSwitch(locked, onCheckedChange = {
                    Haptics.confirm(view)
                    if (SwitchService.isLockEnabled(ctx)) {
                        SwitchService.unlock(ctx)
                    } else {
                        val m = ModeUtil.findByFps(ctx, ModeUtil.currentFps(ctx), 1)
                        if (m != null) SwitchService.lockTo(ctx, m.id, m.fpsInt())
                        else SwitchService.setLockEnabled(ctx, true)
                    }
                    if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                    refresh()
                })
            }
            }

            Text(
                text = rememberForegroundRuleText() + "\n长按面板可拖动位置",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }


}

/** 悬浮窗底部提示：当前前台应用是否有自动化规则。 */
@Composable
private fun rememberForegroundRuleText(): String {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("当前应用不处于自动化设置中") }
    LaunchedEffect(Unit) {
        while (true) {
            val t = withContext(Dispatchers.IO) {
                val pkg = AutoRules.foregroundPackage()
                val fps = if (pkg.isNullOrEmpty()) 0 else AutoRules.ruleFps(ctx, pkg)
                if (fps > 0) "当前应用处于自动化设置中，设置的刷新率为 ${fps}Hz"
                else "当前应用不处于自动化设置中"
            }
            text = t
            kotlinx.coroutines.delay(1000)
        }
    }
    return text
}