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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
    onOpenSystemSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val t = LocalStyleTokens.current

    fun asyncRefresh() {
        scope.launch { withContext(Dispatchers.IO) { st.refresh() } }
    }

    Card(modifier, cornerRadius = t.cardRadius) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // ---- 标题行：标题 + 进入 App ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "刷新率",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .clickable {
                            Haptics.click(view)
                            onOpenApp()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "⚙", fontSize = 13.sp)
                }
            }

            // ---- 当前值 ----
            Text(
                text = buildString {
                    append("当前 ")
                    append(if (st.fps > 0) "${st.fps} Hz" else "未知")
                    append("   ·   ")
                    if (st.lockEnabled && st.lockedFps > 0) append("🔒 ${st.lockedFps}Hz") else append("未锁定")
                },
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
            )

            // ---- 挡位 ----
            val perRow = 4
            st.modes.chunked(perRow).forEachIndexed { rowIndex, rowModes ->
                if (rowIndex > 0) Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowModes.forEach { m ->
                        PanelChip(
                            label = m.shortLabel(),
                            active = st.fps > 0 && m.fpsInt() == st.fps,
                            locked = st.lockEnabled && st.lockedFps > 0 && m.fpsInt() == st.lockedFps,
                            radius = t.chipRadius,
                            modifier = Modifier.weight(1f),
                        ) {
                            Haptics.tick(view)
                            if (SwitchService.isLockEnabled(ctx)) {
                                SwitchService.lockTo(ctx, m.id, m.fpsInt())
                            } else {
                                ModeUtil.applyMode(m.id)
                            }
                            if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                            asyncRefresh()
                        }
                    }
                    repeat(perRow - rowModes.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ---- 锁定行 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "锁定刷新率",
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                val locked = st.lockEnabled && st.lockedFps > 0
                Box(
                    modifier = Modifier
                        .background(
                            if (locked) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable {
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
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (locked) "已开启" else "已关闭",
                        fontSize = 11.sp,
                        color = if (locked) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSurface,
                    )
                }
            }

            // ---- 系统设置 ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(999.dp))
                    .clickable {
                        Haptics.click(view)
                        onOpenSystemSettings()
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "系统设置",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
            }

            Text(
                text = "点按面板外空白处关闭",
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
    val accent = MiuixTheme.colorScheme.primary
    val bg = if (active) accent else MiuixTheme.colorScheme.surfaceContainerHigh
    val fg = if (active) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .height(38.dp)
            .background(bg, RoundedCornerShape(radius))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (if (locked) "🔒" else "") + label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
