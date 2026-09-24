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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Unlock
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxHeight
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
fun HomeScreen(st: AppState, onOpenOverlay: () -> Unit, inset: PaddingValues = PaddingValues(0.dp), scrollMod: Modifier = Modifier) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val t = LocalStyleTokens.current

    fun asyncRefresh() {
        scope.launch { withContext(Dispatchers.IO) { st.refresh() } }
    }

    // 根容器照 KernelSU：LazyColumn（尺寸自管理 → 永远可滚动）；
    // 让位用 contentPadding（随内容滚动），页面内容整体作为单个 item。
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp),
        contentPadding = inset,
    ) {
        item {
        Column(
            Modifier.fillMaxWidth(),
        ) {

        // ---------- hero 卡：MIUIX 浅绿卡 + 大号水印 + Tilt 3D 按压 ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = t.cardRadius,
            insideMargin = PaddingValues(0.dp),
            // 固定绿色（与 InstallerX 那种 MIUIX 绿卡一致，不跟随壁纸取色）
            colors = CardDefaults.defaultColors(
                color = HomeHeroGreenContainer,
                contentColor = HomeHeroGreenContent,
            ),
            pressFeedbackType = PressFeedbackType.Tilt,
            onClick = {
                Haptics.click(view)
                if (st.screen.arr) {
                    toast(ctx, "LTPO 屏不支持锁定")
                    return@Card
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
                asyncRefresh()
            },
        ) {
            Box(Modifier.fillMaxWidth()) {
                // 大号水印图标（右下，随卡片裁切）
                Icon(
                    imageVector = if (st.lockEnabled) MiuixIcons.Lock else MiuixIcons.Unlock,
                    contentDescription = null,
                    tint = HomeHeroGreenContent.copy(alpha = 0.26f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(132.dp)
                        .offset(x = 26.dp, y = 22.dp),
                )
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        text = when {
                            st.lockEnabled -> "已锁定 " + (if (st.lockedFps > 0) "${st.lockedFps}Hz" else "当前挡位")
                            st.capFps >= 60 -> "最高到 ${st.capFps}Hz"
                            st.capFps > 0 -> "已切到 ${st.capFps}Hz"
                            else -> "未锁定"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            st.lockEnabled -> "系统改动会被自动纠正"
                            st.screen.isLtpo -> "已进入 LTPO 刷新率模式（实际 ${st.screen.renderFps}Hz）"
                            else -> "仅按手动选择的挡位切换"
                        },
                        fontSize = 13.sp,
                        color = HomeHeroGreenContent.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = when {
                            st.lockEnabled -> "持续强制"
                            st.screen.isLtpo -> "LTPO"
                            else -> "点击卡片可切换锁定"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeHeroGreenContent.copy(alpha = 0.85f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- 两张统计卡（对应 KernelSU 的「超级用户 / 模块」）----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatBlock(
                "当前刷新率",
                if (st.screen.arr) "由 LTPO 控制"
                else if (st.fps > 0) "${st.fps} Hz" else "—",
                t.cardRadius,
                Modifier.weight(1f),
            )
            StatBlock(
                if (st.lockEnabled && !st.screen.arr) "锁定挡位" else if (st.capFps >= 60) "最高到" else "已选择",
                if (st.lockEnabled) {
                    if (st.lockedFps > 0) "${st.lockedFps} Hz" else "—"
                } else if (st.capFps > 0) "${st.capFps} Hz" else if (st.fps > 0) "${st.fps} Hz" else "—",
                t.cardRadius,
                Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(6.dp))

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
                                active = !st.screen.isLtpo && st.fps > 0 && m.fpsInt() == st.fps,
                                locked = st.lockEnabled && st.lockedFps > 0 && m.fpsInt() == st.lockedFps,
                                radius = t.chipRadius,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    Haptics.tick(view)
                                    SwitchService.setCapFps(ctx, m.fpsInt())
                                    if (SwitchService.isLockEnabled(ctx)) {
                                        SwitchService.lockTo(ctx, m.id, m.fpsInt())
                                    } else if (!ModeUtil.applyMode(ctx, m.id)) {
                                        toast(ctx, "需要 root 权限")
                                    }
                                    if (!SwitchService.isNotifyEnabled(ctx)) Daemon.sync(ctx)
                                    toast(ctx, "已切到 " + m.label())
                                    asyncRefresh()
                                },
                            )
                        }
                        repeat(perRow - rowModes.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (st.screen.isLtpo && !st.lockEnabled)
                        "LTPO 动态刷新率中（实际 ${st.screen.renderFps}Hz），挡位高亮暂时隐藏；点任一挡位即可固定"
                    else "点击即切换；若锁定已开启，同时会把它设为锁定目标",
                    fontSize = 11.5.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        // ---------- 屏幕信息 ----------
        SmallTitle("屏幕信息")
        Card(cornerRadius = t.cardRadius) {

            InfoRow("分辨率", if (st.screen.width > 0) "${st.screen.width} × ${st.screen.height}" else "—")
            InfoRow("刷新率范围", st.screen.rangeText())
            InfoRow("当前模式", if (st.screen.modeFps > 0) "${st.screen.modeFps} Hz" else "—")
            InfoRow("实际渲染", if (st.screen.renderFps > 0) "${st.screen.renderFps} Hz" else "—")
            InfoRow("面板类型", if (st.screen.arr) "LTPO / 可变刷新率" else "固定刷新率")
            InfoRow("屏幕密度", if (st.screen.densityDpi > 0) "${st.screen.densityDpi} dpi" else "—")
        }

        Spacer(Modifier.height(24.dp))
        }
        }
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
    // 未选中用 MIUIX 的「次级容器色」，深色模式下也有明确底色（不用描边）
    val unselectedBg = MiuixTheme.colorScheme.secondaryContainerVariant
    val unselectedFg = MiuixTheme.colorScheme.onSurface
    val bg = if (active) accent else unselectedBg
    val fg = if (active) MiuixTheme.colorScheme.onPrimary else unselectedFg
    Box(
        modifier = modifier
            .height(46.dp)
            .background(bg, RoundedCornerShape(radius))
                        .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mod.shortLabel(),
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

/** 统计块：标题 + 数值，居中（对应 KernelSU 首页的两张小卡）。 */
@Composable
private fun StatBlock(
    label: String,
    value: String,
    radius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Card(modifier, cornerRadius = radius) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }

        Spacer(Modifier.height(24.dp))
}

/** 主页 hero 卡固定绿色（浅绿底 + 深绿字，与 InstallerX 的 MIUIX 绿卡一致）。 */
private val HomeHeroGreenContainer = Color(0xFFC9E7CB)
private val HomeHeroGreenContent = Color(0xFF14532D)

/** 主页「屏幕信息」的键值行。 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

/** 主页「控制屏幕」选择行（仅多屏设备显示）：改用 MIUIX 官方 WindowDropdownPreference。 */
@Composable
private fun DisplayPickerRow(st: AppState) {
    val ctx = LocalContext.current
    val current = st.displays.firstOrNull { it.id == st.targetDisplay } ?: st.displays.firstOrNull()
    val labels = st.displays.map { it.label() }
    val selectedIndex = st.displays.indexOfFirst { it.id == st.targetDisplay }.coerceAtLeast(0)

    WindowDropdownPreference(
        items = labels,
        selectedIndex = selectedIndex,
        title = "控制屏幕",
        summary = current?.label() ?: "—",
        showValue = false,
        onSelectedIndexChange = { index ->
            val d = st.displays.getOrNull(index) ?: return@WindowDropdownPreference
            SwitchService.setTargetDisplay(ctx, d.id)
            ScreenInfo.invalidate()
            toast(ctx, "已切到 " + d.label())
            st.refresh()
        },
    )
}
