@file:OptIn(ExperimentalMaterial3Api::class)

package com.dsh.refreshswitch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import top.yukonga.miuix.kmp.basic.PullToRefresh
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 可选挡位：0 = 关闭（不自动切换）。 */
private fun ruleOptions(st: AppState): List<Pair<Int, String>> =
    listOf(0 to "关闭") + st.modes.map { it.fpsInt() to "${it.fpsInt()}Hz" }

/**
 * 应用列表 + 图标缓存。
 * 放在 object 里而不是 remember：横竖屏切换、底栏切页都不会丢，
 * 因此不会每次重建列表、也不会出现「图标空白一下」。
 * 只有 showSystem 变化或手动 reload（reload 计数变化）时才重新加载。
 */
private object AppListCache {
    private var key: String = ""
    var apps: List<Array<String>> = emptyList()
        private set
    var icons: Map<String, ImageBitmap> = emptyMap()
        private set

    fun needsLoad(showSystem: Boolean, reload: Int): Boolean = key != "$showSystem|$reload"

    fun store(showSystem: Boolean, reload: Int, a: List<Array<String>>, i: Map<String, ImageBitmap>) {
        key = "$showSystem|$reload"
        apps = a
        icons = i
    }
}

/** 应用列表 + 图标（root 读包名，PackageManager 取名称与图标）。 */
@Composable
private fun rememberAppList(
    showSystem: Boolean,
    reload: Int,
): Triple<List<Array<String>>, Map<String, ImageBitmap>, Boolean> {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(AppListCache.apps) }
    var icons by remember { mutableStateOf(AppListCache.icons) }
    var loading by remember { mutableStateOf(AppListCache.apps.isEmpty()) }

    LaunchedEffect(showSystem, reload) {
        if (!AppListCache.needsLoad(showSystem, reload)) {
            // 命中缓存：直接用，不再重新读取与解码图标
            list = AppListCache.apps
            icons = AppListCache.icons
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            val loadedApps = AutoRules.installedApps(ctx, showSystem)
            // 已配置规则的应用置顶，并按设定的刷新率从高到低；其余保持名称排序
            val rules = AutoRules.allRules(ctx)
            // 每次「加载」时排一次：被配置规则的应用置顶、按 fps 降序
            val apps = sortApps(loadedApps, rules)
            val map = HashMap<String, ImageBitmap>()
            for (a in apps) {
                val bmp = AutoRules.appIcon(ctx, a[0]) ?: continue
                map[a[0]] = bmp.asImageBitmap()
            }
            apps to map
        }
        AppListCache.store(showSystem, reload, loaded.first, loaded.second)
        list = loaded.first
        icons = loaded.second
        loading = false
    }
    return Triple(list, icons, loading)
}
/** 已配置规则的应用置顶，并按设定的刷新率从高到低；其余保持原顺序。 */
private fun sortApps(apps: List<Array<String>>, rules: Map<String, Int>): List<Array<String>> {
    val ruled = apps.filter { (rules[it[0]] ?: 0) > 0 }.sortedByDescending { rules[it[0]] ?: 0 }
    val rest = apps.filter { (rules[it[0]] ?: 0) <= 0 }
    return ruled + rest
}
/** 按名称或包名过滤。 */
private fun filterApps(apps: List<Array<String>>, query: String): List<Array<String>> {
    val q = query.trim()
    if (q.isEmpty()) return apps
    return apps.filter {
        it[1].contains(q, ignoreCase = true) || it[0].contains(q, ignoreCase = true)
    }
}

/** 包名过长时截断显示，避免把右侧挡位挤成两行。 */
private fun shortPkg(pkg: String, max: Int = 26): String =
    if (pkg.length <= max) pkg else pkg.take(max - 1) + "…"

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
// 顶栏右侧动作（参照 MIUI「应用设置」：纯图标、无背景圈）
// =====================================================================

/** MIUIX 顶栏右侧：搜索 + 更多（含「显示系统应用」菜单）。 */
@Composable
fun MiuixAutoTopActions(showSystem: Boolean, onSearch: () -> Unit, onToggleSystem: () -> Unit) {
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    // 必须自己成行：AnimatedVisibility 的内容不是 RowScope，否则两个图标会叠在一起
    Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
        Modifier.size(44.dp).clickable { Haptics.click(view); onSearch() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Basic.Search,
            contentDescription = "搜索",
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
    Box {
        Box(
            Modifier.size(44.dp).clickable { Haptics.click(view); menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.More,
                contentDescription = "更多",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        // MIUIX 原生弹层：WindowListPopup 在独立窗口里渲染，
        // 不会被顶栏父布局约束成非法尺寸（SuperListPopup 会崩的原因）
        top.yukonga.miuix.kmp.window.WindowListPopup(
            show = menu,
            onDismissRequest = { menu = false },
            enableWindowDim = true,
            maxHeight = 320.dp,
        ) {
            // 不用 ListPopupColumn（它的 maxIntrinsicWidth 测量在顶栏约束下会算出非法尺寸），
            // 用普通 Column 承载 MIUIX 的 BasicComponent，外观仍由 MIUIX 弹层容器决定
            Column(Modifier.width(200.dp).padding(vertical = 6.dp)) {
                top.yukonga.miuix.kmp.basic.BasicComponent(
                    title = "显示系统应用",
                    onClick = { Haptics.click(view); menu = false; onToggleSystem() },
                    endActions = {
                        if (showSystem) {
                            Icon(
                                imageVector = MiuixIcons.Basic.Check,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
    }
}

/** MIUIX 顶栏搜索态：灰色胶囊输入框 + 圆形 ✕（参照 MIUI 搜索栏）。 */
@Composable
fun MiuixAutoSearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 胶囊输入框
        Row(
            Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "搜索应用",
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                        inner()
                    },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 圆形 ✕
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .clickable { Haptics.click(view); onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Close,
                contentDescription = "退出搜索",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** M3E 顶栏右侧：搜索 + 更多。 */
@Composable
fun M3eAutoTopActions(showSystem: Boolean, onSearch: () -> Unit, onToggleSystem: () -> Unit) {
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier.size(44.dp).clickable { Haptics.click(view); onSearch() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Basic.Search,
            contentDescription = "搜索",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
    Box {
        Box(
            Modifier.size(44.dp).clickable { Haptics.click(view); menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.More,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("显示系统应用") },
                trailingIcon = {
                    if (showSystem) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = { Haptics.click(view); menu = false; onToggleSystem() },
            )
        }
    }
}

/** M3E 顶栏搜索态。 */
@Composable
fun M3eAutoSearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = MiuixIcons.Basic.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "搜索应用名或包名",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { Haptics.click(view); onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Close,
                contentDescription = "退出搜索",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// =====================================================================
// MIUIX 自动化页
// =====================================================================

@Composable
fun MiuixAutomationScreen(st: AppState, query: String) {
    val ctx = LocalContext.current
    val t = LocalStyleTokens.current
    var reload by remember { mutableIntStateOf(0) }
    // 当前规则快照：改挡位只更新它（刷新该行显示），**不重排、不重载列表**
    var rules by remember { mutableStateOf(AutoRules.allRules(ctx)) }
    val listState = rememberLazyListState()
    var refreshRequested by remember { mutableStateOf(false) }
    val (allApps, icons, loading) = rememberAppList(st.autoShowSystem, reload)
    val apps = remember(allApps, query) { filterApps(allApps, query) }
    // 列表由用户手动下拉刷新；刷新完成后自动归顶
    LaunchedEffect(allApps) { rules = AutoRules.allRules(ctx) }
    // 刷新「完成」后再归顶：列表项有 key，触发瞬间滚会被位置保持覆盖掉
    LaunchedEffect(loading) {
        if (!loading && refreshRequested) {
            listState.scrollToItem(0)
            refreshRequested = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(6.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(t.cardRadius))
                .background(MiuixTheme.colorScheme.surfaceContainer),
        ) {
            PullToRefresh(
                isRefreshing = loading,
                onRefresh = { refreshRequested = true; reload++ },
                refreshTexts = listOf("下拉刷新", "松开立即刷新", "正在刷新…"),
            ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it[0] }) { app ->
                    val pkg = app[0]
                    val label = app[1]
                    val current = rules[pkg] ?: 0
                    MiuixAppRuleRow(
                        label = label,
                        pkg = pkg,
                        icon = icons[pkg],
                        current = current,
                        options = ruleOptions(st),
                        onPick = { fps ->
                            AutoRules.setRule(ctx, pkg, fps)
                            rules = AutoRules.allRules(ctx)
                        },
                    )
                }
                if (loading && allApps.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(color = top.yukonga.miuix.kmp.basic.PullToRefreshDefaults.color)
                        }
                    }
                }
                if (!loading && apps.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "没有匹配的应用",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
            }
        }
    }
}
   /** MIUIX 应用规则行：用 MIUIX 官方 SuperDropdown（行 + ⌃⌄ + 原生 popup），带应用图标。 */
@Composable
private fun MiuixAppRuleRow(
    label: String,
    pkg: String,
    icon: ImageBitmap?,
    current: Int,
    options: List<Pair<Int, String>>,
    onPick: (Int) -> Unit,
) {
    top.yukonga.miuix.kmp.extra.SuperDropdown(
        items = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == current }.coerceAtLeast(0),
        title = label,
        summary = shortPkg(pkg),
        showValue = true,
        renderInRootScaffold = true,
        startAction = { AppIconMiuix(icon, 1f) },
        onSelectedIndexChange = { index ->
            onPick(options.getOrNull(index)?.first ?: 0)
        },
    )
}

// =====================================================================
// M3E 自动化页
// =====================================================================

@Composable
fun M3eAutomationScreen(st: AppState, query: String) {
    val ctx = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    // 当前规则快照：改挡位只更新它（刷新该行显示），**不重排、不重载列表**
    var rules by remember { mutableStateOf(AutoRules.allRules(ctx)) }
    val listState = rememberLazyListState()
    var refreshRequested by remember { mutableStateOf(false) }
    val (allApps, icons, loading) = rememberAppList(st.autoShowSystem, reload)
    val apps = remember(allApps, query) { filterApps(allApps, query) }
    // 列表由用户手动下拉刷新；刷新完成后自动归顶
    LaunchedEffect(allApps) { rules = AutoRules.allRules(ctx) }
    // 刷新「完成」后再归顶：列表项有 key，触发瞬间滚会被位置保持覆盖掉
    LaunchedEffect(loading) {
        if (!loading && refreshRequested) {
            listState.scrollToItem(0)
            refreshRequested = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            PullToRefresh(
                isRefreshing = loading,
                onRefresh = { refreshRequested = true; reload++ },
                refreshTexts = listOf("下拉刷新", "松开立即刷新", "正在刷新…"),
            ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it[0] }) { app ->
                    val pkg = app[0]
                    val label = app[1]
                    val current = rules[pkg] ?: 0
                    M3eAppRuleRow(
                        label = label,
                        pkg = pkg,
                        icon = icons[pkg],
                        current = current,
                        options = ruleOptions(st),
                        onPick = { fps ->
                            AutoRules.setRule(ctx, pkg, fps)
                            rules = AutoRules.allRules(ctx)
                        },
                    )
                }
                if (loading && allApps.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(color = top.yukonga.miuix.kmp.basic.PullToRefreshDefaults.color)
                        }
                    }
                }
                if (!loading && apps.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "没有匹配的应用",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
            }
        }
    }
}

/**
 * M3E 应用规则行：图标 + 名称 + 包名（单行省略），右侧挡位不换行；
 * 菜单从**手指点击的位置**弹出（按点击点换算水平偏移）。
 */
@Composable
private fun M3eAppRuleRow(
    label: String,
    pkg: String,
    icon: ImageBitmap?,
    current: Int,
    options: List<Pair<Int, String>>,
    onPick: (Int) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var tapX by remember { mutableFloatStateOf(0f) }
    val alpha = if (expanded) 0.45f else 1f
    val rowBg = if (expanded) MaterialTheme.colorScheme.surfaceContainerHighest
    else androidx.compose.ui.graphics.Color.Transparent

    Box(
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                tapX = offset.x
                Haptics.click(view)
                expanded = true
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
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortPkg(pkg),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = options.firstOrNull { it.first == current }?.second ?: "关闭",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                maxLines = 1,
                softWrap = false,
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
