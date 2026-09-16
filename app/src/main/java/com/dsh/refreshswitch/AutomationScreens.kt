@file:OptIn(ExperimentalMaterial3Api::class)

package com.dsh.refreshswitch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 可选挡位：0 = 关闭（不自动切换）。 */
private fun ruleOptions(st: AppState): List<Pair<Int, String>> =
    listOf(0 to "关闭") + st.modes.map { it.fpsInt() to "${it.fpsInt()}Hz" }

/** 应用列表 + 图标（root 读包名，PackageManager 取名称与图标）。 */
@Composable
private fun rememberAppList(
    showSystem: Boolean,
    reload: Int,
): Triple<List<Array<String>>, Map<String, ImageBitmap>, Boolean> {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<Array<String>>>(emptyList()) }
    var icons by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(showSystem, reload) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            val loaded = AutoRules.installedApps(ctx, showSystem)
            // 已配置规则的应用置顶，并按设定的刷新率从高到低；其余保持名称排序
            val rules = AutoRules.allRules(ctx)
            val ruled = loaded.filter { (rules[it[0]] ?: 0) > 0 }
                .sortedByDescending { rules[it[0]] ?: 0 }
            val rest = loaded.filter { (rules[it[0]] ?: 0) <= 0 }
            val apps = ruled + rest
            val map = HashMap<String, ImageBitmap>()
            for (a in apps) {
                val bmp = AutoRules.appIcon(ctx, a[0]) ?: continue
                map[a[0]] = bmp.asImageBitmap()
            }
            apps to map
        }
        list = loaded.first
        icons = loaded.second
        loading = false
    }
    return Triple(list, icons, loading)
}

/** 应用图标（M3E 配色占位）。 */
@Composable
private fun AppIconM3e(icon: ImageBitmap?, alpha: Float) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
        }
    }
}

/** 应用图标（MIUIX 配色占位）。 */
@Composable
private fun AppIconMiuix(icon: ImageBitmap?, alpha: Float) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(34.dp))
        }
    }
}

// =====================================================================
// MIUIX 自动化页
// =====================================================================

@Composable
fun MiuixAutomationScreen(st: AppState) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val t = LocalStyleTokens.current
    var reload by remember { mutableIntStateOf(0) }
    val enabled = st.autoEnabled
    val (apps, icons, loading) = rememberAppList(st.autoShowSystem, reload)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            SmallTitle("自动化")
            Card(cornerRadius = t.cardRadius) {
                SuperSwitch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        Haptics.click(view)
                        AutoRules.setEnabled(ctx, on)
                        toast(ctx, if (on) "自动化已开启" else "自动化已关闭")
                        reload++
                    },
                    title = "按应用自动切换刷新率",
                    summary = if (enabled) "已开启 · 共 ${AutoRules.ruleCount(ctx)} 条规则" else "关闭时下方设置全部不可用",
                )
                SuperSwitch(
                    checked = st.autoShowSystem,
                    onCheckedChange = { on ->
                        Haptics.click(view)
                        AutoRules.setShowSystem(ctx, on)
                        reload++
                    },
                    title = "显示系统应用",
                    summary = if (st.autoShowSystem) "列表包含系统应用" else "仅显示第三方应用",
                    enabled = enabled,
                )
            }
        }

        if (!st.notifyEnabled) {
            item {
                SmallTitle("")
                Card(cornerRadius = t.cardRadius) {
                    Text(
                        text = "自动化依赖常驻服务：当前「常驻通知」已关闭，请到设置页开启后再使用。",
                        fontSize = 11.5.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }

        item {
            SmallTitle("应用列表" + if (loading) "（读取中…）" else "（${apps.size}）")
        }

        items(apps, key = { it[0] }) { app ->
            val pkg = app[0]
            val label = app[1]
            val current = AutoRules.ruleFps(ctx, pkg).let { if (it > 0) it else 0 }
            MiuixAppRuleRow(
                label = label,
                pkg = pkg,
                icon = icons[pkg],
                current = current,
                options = ruleOptions(st),
                enabled = enabled,
                onPick = { fps ->
                    AutoRules.setRule(ctx, pkg, fps)
                    reload++
                },
            )
        }

        if (loading && apps.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** MIUIX 应用规则行：图标 + 名称 + 包名，右侧当前值 ⌃⌄，菜单从最右侧展开。 */
@Composable
private fun MiuixAppRuleRow(
    label: String,
    pkg: String,
    icon: ImageBitmap?,
    current: Int,
    options: List<Pair<Int, String>>,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    // 展开选项框时整条变灰（反馈当前正在操作哪一行）
    val alpha = when {
        expanded -> 0.45f
        enabled -> 1f
        else -> 0.38f
    }
    val rowBg = if (expanded) MiuixTheme.colorScheme.surfaceContainerHigh
    else MiuixTheme.colorScheme.surface

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .clickable(enabled = enabled) {
                Haptics.click(view)
                expanded = !expanded
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconMiuix(icon, alpha)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
            )
            Text(
                text = pkg,
                fontSize = 10.5.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = alpha),
                maxLines = 1,
            )
        }
        Box {
            Row(
                Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = options.firstOrNull { it.first == current }?.second ?: "关闭",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = alpha),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowUpDown,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = alpha),
                    modifier = Modifier.size(16.dp),
                )
            }
            MiuixDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (fps, text) ->
                    val selected = fps == current
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = text,
                                fontSize = 14.sp,
                                color = if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                            )
                        },
                        trailingIcon = {
                            if (selected) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                        onClick = {
                            Haptics.tick(view)
                            expanded = false
                            onPick(fps)
                        },
                    )
                }
            }
        }
    }
}

// =====================================================================
// M3E 自动化页
// =====================================================================

@Composable
fun M3eAutomationScreen(st: AppState) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var reload by remember { mutableIntStateOf(0) }
    val enabled = st.autoEnabled
    val (apps, icons, loading) = rememberAppList(st.autoShowSystem, reload)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(2.dp))
            M3eCard {
                M3eRow(
                    icon = MiuixIcons.Tune,
                    title = "按应用自动切换刷新率",
                    subtitle = if (enabled) "已开启 · 共 ${AutoRules.ruleCount(ctx)} 条规则" else "关闭时下方设置全部不可用",
                    trailing = {
                        M3eSwitch(enabled, onCheckedChange = { on ->
                            Haptics.click(view)
                            AutoRules.setEnabled(ctx, on)
                            toast(ctx, if (on) "自动化已开启" else "自动化已关闭")
                            reload++
                        })
                    },
                )
                M3eRow(
                    icon = MiuixIcons.Layers,
                    title = "显示系统应用",
                    subtitle = if (st.autoShowSystem) "列表包含系统应用" else "仅显示第三方应用",
                    enabled = enabled,
                    trailing = {
                        M3eSwitch(st.autoShowSystem, onCheckedChange = { on ->
                            Haptics.click(view)
                            AutoRules.setShowSystem(ctx, on)
                            reload++
                        }, enabled = enabled)
                    },
                )
            }
        }

        if (!st.notifyEnabled) {
            item {
                M3eCard {
                    Text(
                        text = "自动化依赖常驻服务：当前「常驻通知」已关闭，请到设置页开启后再使用。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }

        item {
            Text(
                text = "应用列表" + if (loading) "（读取中…）" else "（${apps.size}）",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

        items(apps, key = { it[0] }) { app ->
            val pkg = app[0]
            val label = app[1]
            val current = AutoRules.ruleFps(ctx, pkg).let { if (it > 0) it else 0 }
            M3eAppRuleRow(
                label = label,
                pkg = pkg,
                icon = icons[pkg],
                current = current,
                options = ruleOptions(st),
                enabled = enabled,
                onPick = { fps ->
                    AutoRules.setRule(ctx, pkg, fps)
                    reload++
                },
            )
        }

        if (loading && apps.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * M3E 应用规则行：图标 + 名称 + 包名，右侧当前值 ⌃⌄；
 * 菜单从**手指点击的位置**弹出（按点击点换算水平偏移）。
 */
@Composable
private fun M3eAppRuleRow(
    label: String,
    pkg: String,
    icon: ImageBitmap?,
    current: Int,
    options: List<Pair<Int, String>>,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var tapX by remember { mutableFloatStateOf(0f) }
    val alpha = when {
        expanded -> 0.45f
        enabled -> 1f
        else -> 0.38f
    }
    val rowBg = if (expanded) MaterialTheme.colorScheme.surfaceContainerHighest
    else androidx.compose.ui.graphics.Color.Transparent

    Box(
        Modifier.pointerInput(enabled) {
            if (enabled) {
                detectTapGestures { offset ->
                    tapX = offset.x
                    Haptics.click(view)
                    expanded = true
                }
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(rowBg)
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconM3e(icon, alpha)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                )
                Text(
                    text = pkg,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = options.firstOrNull { it.first == current }?.second ?: "关闭",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(with(density) { tapX.toDp() }, 0.dp),
        ) {
            options.forEach { (fps, text) ->
                val selected = fps == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
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
                        onPick(fps)
                    },
                )
            }
        }
    }
}
