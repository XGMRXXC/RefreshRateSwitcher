package com.dsh.refreshswitch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 顶栏高度（内容让位用）。 */
private val PageTopBarHeight = 64.dp

/**
 * 页面脚手架：**页面铺满 + 顶栏覆盖层**。
 *
 * 为什么不用嵌套 Scaffold / 不用 Column 包内容（两者都试过，结果都是页面滚动容器失去高度约束 → 滑不动）：
 *  - 页面必须拿到与重构前**完全一致**的尺寸约束（由外层 HorizontalPager 提供），
 *    这样页面自己的 Column(verticalScroll) / LazyColumn 才能正常滚动；
 *  - 顶栏以覆盖层形式压在内容之上（视觉与 KSU / MIUIX demo 一致）；
 *  - 让位通过 contentPadding 交给页面，由页面在**自己的滚动内容**里使用（随内容滚动），
 *    这样首个条目不会被顶栏永久遮挡，毛玻璃也才有内容可采样。
 */
@Composable
fun MiuixPageScaffold(
    title: String,
    backdrop: LayerBackdrop?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Box(Modifier.fillMaxSize()) {
        // 内容：铺满（约束与重构前一致）→ 页面自身滚动正常
        content(PaddingValues(top = PageTopBarHeight), Modifier.fillMaxSize())
        // 顶栏：覆盖层
        Box(Modifier.align(Alignment.TopStart)) {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    actions = actions,
                )
            }
        }
    }
}
