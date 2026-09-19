package com.dsh.refreshswitch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar as M3NavigationBar
import androidx.compose.material3.NavigationBarItem as M3NavigationBarItem
import androidx.compose.material3.NavigationRail as M3NavigationRail
import androidx.compose.material3.NavigationRailItem as M3NavigationRailItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 应用外壳：按风格分别使用 MIUIX 或 Material3 组件；横屏时底栏自动移到左侧导航栏。 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SwitchService.start(this)
        RestartJobService.schedule(this)
        requestNotifPermission()
        setContent { AppRoot() }
    }

    /** "隐藏后台任务"：离开界面即结束并移除任务。 */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && SwitchService.isHideRecents(this)) {
            finishAndRemoveTask()
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val st = remember { AppState(ctx) }
    val style = UiStyle.of(SwitchService.getUiStyle(ctx))

    // 打开应用先立刻适配一次（重新枚举主屏 display 0 的挡位、重新检测 root），
    // 之后每秒在 IO 线程刷新（读 sysfs / 调用 su 不能放主线程）
    // 首次进入若未授予悬浮窗权限，直接带用户去系统设置授权（不再使用 root 授权）
    LaunchedEffect(Unit) {
        if (!com.dsh.refreshswitch.OverlayPanel.canShow(ctx)) {
            kotlinx.coroutines.delay(600)
            toast(ctx, "请授予「显示在其他应用上层」权限")
            SysActions.openOverlaySettings(ctx)
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            st.refreshModes()
            st.refresh()
        }
        while (true) {
            withContext(Dispatchers.IO) { st.refresh() }
            delay(1000)
        }
    }

    when (style) {
        UiStyle.MIUIX -> AppTheme {
            ApplySystemBarAppearance()
            // MIUIX 原生下拉弹层需要 NavigationEventDispatcherOwner
            MiuixPopupHost { MiuixShell(st) }
        }
        UiStyle.M3E -> M3eTheme {
            ApplySystemBarAppearance()
            M3eShell(st)
        }
    }
}

/** 页码状态（两种风格共用，支持左右滑动）。 */
@Composable
private fun rememberTabPager(pageCount: Int): PagerState =
    rememberPagerState(pageCount = { pageCount })

/** 可左右滑动的页面容器（裁剪，避免滑出时与侧栏串台）。 */
@Composable
private fun TabPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    HorizontalPager(
        state = state,
        modifier = modifier.clipToBounds(),
    ) { index -> content(index) }
}

// =====================================================================
// MIUIX 外壳
// =====================================================================

@Composable
private fun MiuixShell(st: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val auto = st.autoEnabled
    var autoQuery by remember { mutableStateOf("") }
    var autoSearching by remember { mutableStateOf(false) }
    val pageCount = if (auto) 3 else 2
    val pager = rememberPagerState(pageCount = { pageCount })
    val tab = pager.currentPage.coerceAtMost(pageCount - 1)
    val wide = isWideScreen()
    // 关掉自动化后当前页可能越界，回到最后一页
    LaunchedEffect(pageCount) { if (pager.currentPage > pageCount - 1) pager.scrollToPage(pageCount - 1) }
    val title = when (tab) {
        0 -> "刷新率"
        1 -> if (auto) "自动化" else "设置"
        else -> "设置"
    }
    val openOverlay: () -> Unit = {
        if (!OverlayPanel.show(ctx)) toast(ctx, "需要悬浮窗权限")
    }
    val page: @Composable (Int) -> Unit = { t ->
        when (t) {
            0 -> HomeScreen(st, openOverlay)
            1 -> if (auto) MiuixAutomationScreen(st, autoQuery) else SettingsScreen(st)
            else -> SettingsScreen(st)
        }
    }
    val navItems = if (auto) {
        listOf(HomeIcon to "主页", MiuixIcons.Tune to "自动化", MiuixIcons.Settings to "设置")
    } else {
        listOf(HomeIcon to "主页", MiuixIcons.Settings to "设置")
    }
    val go: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i) } }

    // 横屏：侧栏独立占满整列（不被顶栏截断）；竖屏用 Scaffold
    if (wide) {
        // 横屏也用 MiuixScaffold 包一层：SuperDropdown 的原生弹层需要 Scaffold 作为宿主，
        // 否则横屏下 ⌃⌄ 弹不出来
        MiuixScaffold { scaffoldPadding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(MiuixTheme.colorScheme.surface),
        ) {
            if (st.bottomBarStyle == BarStyle.FLOATING) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixFloatingNav(navItems, tab, go, vertical = true)
                }
            } else {
                MiuixNavigationRail {
                    navItems.forEachIndexed { i, (icon, label) ->
                        MiuixNavigationRailItem(
                            selected = tab == i,
                            onClick = { go(i) },
                            icon = icon,
                            label = label,
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                Box {
                Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)          // 固定高度：三个页面顶栏高度一致
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (autoSearching && tab == 1) "" else title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (autoSearching && tab == 1) {
                        MiuixAutoSearchField(
                            query = autoQuery,
                            onQueryChange = { autoQuery = it },
                            onClose = { autoQuery = ""; autoSearching = false },
                        )
                    } else if (auto && tab == 1) {
                        MiuixAutoTopActions(
                            showSystem = st.autoShowSystem,
                            onSearch = { autoSearching = true },
                            onToggleSystem = {
                                AutoRules.setShowSystem(ctx, !st.autoShowSystem)
                                st.refresh()
                            },
                        )
                    } else if (false) {
                        MiuixAutoTopActions(
                            showSystem = st.autoShowSystem,
                            onSearch = { autoSearching = true },
                            onToggleSystem = {
                                AutoRules.setShowSystem(ctx, !st.autoShowSystem)
                                st.refresh()
                            },
                        )
                    }
                }
                }
                }
                Box {
                Box {
                    TabPager(pager, Modifier.fillMaxSize()) { page(it) }
                }
                }
            }
        }
        }
    } else {
        MiuixScaffold(
            topBar = { MiuixSmallTopAppBar(
                    title = if (autoSearching && tab == 1) "" else title,
                    actions = {
                        if (auto && tab == 1) {
                            AnimatedVisibility(
                                visible = autoSearching,
                                enter = fadeIn(animationSpec = tween(140)) +
                                    slideInHorizontally(animationSpec = tween(220), initialOffsetX = { it / 3 }),
                                exit = fadeOut(animationSpec = tween(90)),
                            ) {
                                MiuixAutoSearchField(
                                    query = autoQuery,
                                    onQueryChange = { autoQuery = it },
                                        onClose = { autoQuery = ""; autoSearching = false },
                                    )
                                }
                                AnimatedVisibility(
                                    visible = !autoSearching,
                                    enter = fadeIn(animationSpec = tween(150)),
                                    exit = fadeOut(animationSpec = tween(100)),
                                ) {
                                    MiuixAutoTopActions(
                                        showSystem = st.autoShowSystem,
                                        onSearch = { autoSearching = true },
                                        onToggleSystem = {
                                            AutoRules.setShowSystem(ctx, !st.autoShowSystem)
                                            st.refresh()
                                        },
                                    )
                                }
                            }
                        },
                    )
                },
            bottomBar = {
                if (st.bottomBarStyle != BarStyle.FLOATING) {
                    MiuixNavigationBar {
                        navItems.forEachIndexed { i, (icon, label) ->
                            MiuixNavigationBarItem(
                                selected = tab == i,
                                onClick = { go(i) },
                                icon = icon,
                                label = label,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                TabPager(pager, Modifier.fillMaxSize()) { page(it) }
                if (st.bottomBarStyle == BarStyle.FLOATING) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                    ) {
                        MiuixFloatingNav(navItems, tab, go)
                    }
                }
            }
        }
    }
}

// =====================================================================
// M3E 外壳（Material 3）
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun M3eShell(st: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val auto = st.autoEnabled
    var autoQuery by remember { mutableStateOf("") }
    var autoSearching by remember { mutableStateOf(false) }
    val pageCount = if (auto) 3 else 2
    val pager = rememberPagerState(pageCount = { pageCount })
    val tab = pager.currentPage.coerceAtMost(pageCount - 1)
    val wide = isWideScreen()
    LaunchedEffect(pageCount) { if (pager.currentPage > pageCount - 1) pager.scrollToPage(pageCount - 1) }
    val title = when (tab) {
        0 -> "刷新率"
        1 -> if (auto) "自动化" else "设置"
        else -> "设置"
    }
    val pageIcon = when (tab) {
        0 -> HomeIcon
        1 -> if (auto) MiuixIcons.Tune else MiuixIcons.Settings
        else -> MiuixIcons.Settings
    }
    val openOverlay: () -> Unit = {
        if (!OverlayPanel.show(ctx)) toast(ctx, "需要悬浮窗权限")
    }
    val page: @Composable (Int) -> Unit = { t ->
        when (t) {
            0 -> M3eHomeScreen(st, openOverlay)
            1 -> if (auto) M3eAutomationScreen(st, autoQuery) else M3eSettingsScreen(st)
            else -> M3eSettingsScreen(st)
        }
    }
    val navItems = if (auto) {
        listOf(HomeIcon to "主页", MiuixIcons.Tune to "自动化", MiuixIcons.Settings to "设置")
    } else {
        listOf(HomeIcon to "主页", MiuixIcons.Settings to "设置")
    }
    val go: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i) } }

    val floating = st.bottomBarStyle == BarStyle.FLOATING

    if (wide) {
        Row(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            if (floating) {
                // 横屏 + 悬浮底栏：胶囊竖排到左侧
                Box(
                    Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    M3eFloatingNav(navItems, tab, go, vertical = true)
                }
            } else {
                // 横屏 + 贴地底栏：M3 NavigationRail（自带窗口 inset）
                M3NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    navItems.forEachIndexed { i, (icon, label) ->
                        M3NavigationRailItem(
                            selected = tab == i,
                            onClick = { go(i) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
                        ),
                    ),
            ) {
                M3eTopBar(
                title = if (autoSearching && tab == 1) "" else title,
                icon = pageIcon,
                actions = {
                    if (autoSearching && tab == 1) {
                        M3eAutoSearchField(
                            query = autoQuery,
                            onQueryChange = { autoQuery = it },
                            onClose = { autoQuery = ""; autoSearching = false },
                        )
                    } else if (auto && tab == 1) {
                        M3eAutoTopActions(
                            showSystem = st.autoShowSystem,
                            onSearch = { autoSearching = true },
                            onToggleSystem = {
                                AutoRules.setShowSystem(ctx, !st.autoShowSystem)
                                st.refresh()
                            },
                        )
                    }
                },
            )
                Box {
                    TabPager(pager, Modifier.fillMaxSize()) { page(it) }
                }
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                M3eTopBar(
                title = if (autoSearching && tab == 1) "" else title,
                icon = pageIcon,
                actions = {
                    if (autoSearching && tab == 1) {
                        M3eAutoSearchField(
                            query = autoQuery,
                            onQueryChange = { autoQuery = it },
                            onClose = { autoQuery = ""; autoSearching = false },
                        )
                    } else if (auto && tab == 1) {
                        M3eAutoTopActions(
                            showSystem = st.autoShowSystem,
                            onSearch = { autoSearching = true },
                            onToggleSystem = {
                                AutoRules.setShowSystem(ctx, !st.autoShowSystem)
                                st.refresh()
                            },
                        )
                    }
                },
            )
                Box {
                    TabPager(pager, Modifier.fillMaxSize()) { page(it) }
                }
            }
            if (floating) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(bottom = 12.dp),
                ) {
                    M3eFloatingNav(navItems, tab, go, vertical = false)
                }
            } else {
                // 贴地底栏：容器色覆盖到手势条区域，避免底部出现异色条
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                ) {
                    M3NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        navItems.forEachIndexed { i, (icon, label) ->
                            M3NavigationBarItem(
                                selected = tab == i,
                                onClick = { go(i) },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

