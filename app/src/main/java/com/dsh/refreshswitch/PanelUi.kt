package com.dsh.refreshswitch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.foundation.layout.PaddingValues
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 悬浮面板的进出场动画：淡入 + 轻微放大（不浮夸，约 170/160ms）。 */
@Composable
fun PanelEnterExit(dismissing: Boolean, content: @Composable () -> Unit) {
    var entered by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { entered = true }
    androidx.compose.animation.AnimatedVisibility(
        visible = entered && !dismissing,
        enter = androidx.compose.animation.fadeIn(
            androidx.compose.animation.core.tween(170),
        ) + androidx.compose.animation.scaleIn(
            animationSpec = androidx.compose.animation.core.tween(
                210,
                easing = androidx.compose.animation.core.LinearOutSlowInEasing,
            ),
            initialScale = 0.93f,
        ),
        exit = androidx.compose.animation.fadeOut(
            androidx.compose.animation.core.tween(130),
        ) + androidx.compose.animation.scaleOut(
            animationSpec = androidx.compose.animation.core.tween(
                160,
                easing = androidx.compose.animation.core.FastOutLinearInEasing,
            ),
            targetScale = 0.96f,
        ),
    ) {
        content()
    }
}

/** 悬浮面板内容（悬浮窗与降级对话框共用）。 */
@Composable
fun PanelContent(
    st: AppState,
    modifier: Modifier = Modifier,
    onOpenApp: () -> Unit,
    onResetPosition: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val t = LocalStyleTokens.current

    fun asyncRefresh() {
        scope.launch { withContext(Dispatchers.IO) { st.refresh() } }
    }

    val locked = st.lockEnabled && st.lockedFps > 0

    val panelShape = RoundedCornerShape(t.cardRadius)
    Card(
        modifier
            .shadow(16.dp, panelShape)                                    // 投影：与背后内容区分
            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), panelShape),
        cornerRadius = t.cardRadius,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // ---- 标题行：小标题 + 进入应用 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "刷新率",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.weight(1f),
                )
                // MIUIX 原生图标按钮（不再自绘圆形点击区）
                IconButton(
                    onClick = { Haptics.click(view); onResetPosition() },
                    cornerRadius = 16.dp,
                    minWidth = 32.dp,
                    minHeight = 32.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Reset,
                        contentDescription = "恢复默认位置",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { Haptics.click(view); onOpenApp() },
                    cornerRadius = 16.dp,
                    minWidth = 32.dp,
                    minHeight = 32.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Forward,
                        contentDescription = "进入应用",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
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
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = " Hz",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                // MIUIX 原生 Badge（内容走 content 槽）
                Badge(
                    containerColor = if (locked) MiuixTheme.colorScheme.primaryContainer
                    else MiuixTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = when {
                            locked -> "已锁定 ${st.lockedFps}Hz"
                            st.screen.arr && st.capFps >= 60 -> "最高到 ${st.capFps}Hz"
                            else -> "未锁定"
                        },
                        fontSize = 11.sp,
                        color = if (locked) MiuixTheme.colorScheme.onPrimaryContainer
                        else MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }

            // ---- 挡位 ----
            val perRow = 3
            st.modes.chunked(perRow).forEachIndexed { rowIndex, rowModes ->
                if (rowIndex > 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowModes.forEach { m ->
                        PanelChip(
                            label = m.shortLabel(),
                            active = st.fps > 0 && m.fpsInt() == st.fps,
                            locked = locked && m.fpsInt() == st.lockedFps,
                            radius = t.chipRadius,
                            modifier = Modifier.weight(1f),
                        ) {
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
                            asyncRefresh()
                        }
                    }
                    repeat(perRow - rowModes.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // LTPO 屏幕不支持锁定：整块隐藏
            if (!st.screen.arr) {
            // ---- 锁定开关 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "锁定刷新率",
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                // 与主题一致的 MIUIX 原生开关
                MiuixSwitch(
                    checked = locked,
                    onCheckedChange = { _ ->
                        Haptics.confirm(view)
                        if (SwitchService.isLockEnabled(ctx)) {
                            SwitchService.unlock(ctx)
                        } else {
                            val m = ModeUtil.findByFps(ctx, ModeUtil.currentFps(ctx), 1)
                            if (m != null) SwitchService.lockTo(ctx, m.id, m.fpsInt())
                            else SwitchService.setLockEnabled(ctx, true)
                        }
                        if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                        asyncRefresh()
                    },
                )
            }
            }

            Text(
                text = rememberForegroundRuleText() + "\n长按面板可拖动位置",
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}
@Composable
private fun PanelChip(
    label: String,
    active: Boolean,
    locked: Boolean,
    radius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // MIUIX 原生 Button（未选中用次级容器色，深色下也有底色）
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            color = if (active) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.secondaryContainerVariant,
            contentColor = if (active) MiuixTheme.colorScheme.onPrimary
            else MiuixTheme.colorScheme.onSurface,
        ),
        cornerRadius = radius,
        minHeight = 38.dp,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
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
