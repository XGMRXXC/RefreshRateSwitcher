package com.dsh.refreshswitch

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 主页：当前刷新率 + 锁定 + 挡位 + 快捷操作。 */
@Composable
fun HomeScreen(st: AppState, onOpenOverlay: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val t = LocalStyleTokens.current

    fun asyncRefresh() {
        scope.launch { withContext(Dispatchers.IO) { st.refresh() } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---------- 状态卡 ----------
        Card(cornerRadius = t.cardRadius) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (st.fps > 0) "${st.fps}" else "—",
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = " Hz",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = if (st.lockEnabled) {
                        "已锁定 " + (if (st.lockedFps > 0) "${st.lockedFps} Hz" else "当前挡位") +
                            " · 系统改动会被自动纠正"
                    } else {
                        "未锁定 · 仅按手动选择切换"
                    },
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            SuperSwitch(
                checked = st.lockEnabled,
                onCheckedChange = { on ->
                    Haptics.click(view)
                    if (on) {
                        val m = ModeUtil.findByFps(ctx, ModeUtil.currentFps(ctx), 1)
                        if (m != null) {
                            SwitchService.lockTo(ctx, m.id, m.fpsInt())
                            toast(ctx, "已锁定 " + m.shortLabel())
                        } else {
                            SwitchService.setLockEnabled(ctx, true)
                        }
                    } else {
                        SwitchService.unlock(ctx)
                        toast(ctx, "已解除锁定")
                    }
                    if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                    asyncRefresh()
                },
                title = "锁定刷新率",
                summary = "开启后系统或应用改回都会被自动纠正",
            )
        }

        // ---------- 挡位 ----------
        SmallTitle("选择挡位")
        Card(cornerRadius = t.cardRadius) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                val modes = st.modes
                val perRow = if (modes.size > 5) 4 else 3
                modes.chunked(perRow).forEachIndexed { rowIndex, rowModes ->
                    if (rowIndex > 0) Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowModes.forEach { m ->
                            ModeChip(
                                mod = m,
                                active = st.fps > 0 && m.fpsInt() == st.fps,
                                locked = st.lockEnabled && st.lockedFps > 0 && m.fpsInt() == st.lockedFps,
                                radius = t.chipRadius,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    Haptics.tick(view)
                                    if (SwitchService.isLockEnabled(ctx)) {
                                        SwitchService.lockTo(ctx, m.id, m.fpsInt())
                                    } else {
                                        ModeUtil.applyMode(m.id)
                                    }
                                    if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                                    toast(ctx, "已切换到 " + m.label())
                                    asyncRefresh()
                                },
                            )
                        }
                        repeat(perRow - rowModes.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "点击即切换；若锁定已开启，同时会把它设为锁定目标",
                    fontSize = 11.5.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        // ---------- 快捷操作 ----------
        SmallTitle("快捷操作")
        Card(cornerRadius = t.cardRadius) {
            BasicComponent(
                title = "弹出悬浮面板",
                summary = "不用离开当前应用即可切换刷新率",
                onClick = {
                    Haptics.click(view)
                    onOpenOverlay()
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 挡位胶囊（MIUIX：选中主色填充，未选中浅灰填充）。 */
@Composable
private fun ModeChip(
    mod: ModeUtil.Mode,
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
            .height(46.dp)
            .background(bg, RoundedCornerShape(radius))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (if (locked) "🔒 " else "") + mod.shortLabel(),
            color = fg,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun toast(ctx: Context, s: String) {
    if (!SwitchService.isToastEnabled(ctx)) return
    Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
}

