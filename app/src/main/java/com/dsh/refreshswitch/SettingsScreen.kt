package com.dsh.refreshswitch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页：界面风格 / 通知 / 界面显示 / 权限与守护 / 关于。 */
@Composable
fun SettingsScreen(st: AppState) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val t = LocalStyleTokens.current
    val style = UiStyle.of(SwitchService.getUiStyle(ctx))

    fun asyncRefresh() {
        scope.launch { withContext(Dispatchers.IO) { st.refresh() } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---------------- 界面与底栏 ----------------
        SmallTitle("界面")
        Card(cornerRadius = t.cardRadius) {
            // 让 MIUIX 组件自己撑满卡片（自带 insideMargin），否则按压高亮会被内缩、与卡片圆角对不上
            Column(Modifier.clip(RoundedCornerShape(t.cardRadius))) {
                MiuixPickerRow(
                    title = "界面风格",
                    value = style.label,
                    options = UiStyle.entries.map { it.label },
                    selectedIndex = style.id,
                ) { index ->
                    SwitchService.setUiStyle(ctx, index)
                    (ctx as? android.app.Activity)?.recreate()
                }
                MiuixPickerRow(
                    title = "底栏样式",
                    value = BarStyle.labels.getOrElse(st.bottomBarStyle) { BarStyle.labels[0] },
                    options = BarStyle.labels,
                    selectedIndex = st.bottomBarStyle,
                ) { index ->
                    SwitchService.setBottomBarStyle(ctx, index)
                    asyncRefresh()
                }
            }
        }
        // ---------------- 通知 ----------------
        // ---------------- 自动化 ----------------
        SmallTitle("自动化")
        Card(cornerRadius = t.cardRadius) {
            SuperSwitch(
                checked = st.autoEnabled,
                onCheckedChange = { on ->
                    Haptics.click(view)
                    AutoRules.setEnabled(ctx, on)
                    toast(ctx, if (on) "自动化已开" else "自动化已关")
                    asyncRefresh()
                },
                title = "按应用自动切换刷新率",
                summary = if (st.autoEnabled) "已开启 · 共 ${AutoRules.ruleCount(ctx)} 条规则，底栏显示「自动化」页"
                else "打开后底栏才会出现「自动化」页",
            )
        }

        SmallTitle("通知")
        Card(cornerRadius = t.cardRadius) {
            SuperSwitch(
                checked = st.notifyEnabled,
                onCheckedChange = { on ->
                    Haptics.click(view)
                    SwitchService.setNotifyEnabled(ctx, on)
                    toast(ctx, if (on) "通知已开" else "通知已关")
                    asyncRefresh()
                },
                title = "常驻通知",
                summary = "在通知栏显示当前刷新率与快捷挡位按钮",
            )
            SuperSwitch(
                checked = st.toastEnabled,
                onCheckedChange = { on ->
                    Haptics.click(view)
                    SwitchService.setToastEnabled(ctx, on)
                    if (on) toast(ctx, "提示已开")
                    asyncRefresh()
                },
                title = "Toast 提示",
                summary = if (st.toastEnabled) "显示全部操作提示" else "已关闭，所有 toast 都不再弹出",
            )
            BasicComponent(
                title = "守护状态",
                summary = if (st.notifyEnabled) {
                    "通知栏常驻 · 前台服务运行中"
                } else if (st.daemonPid > 0) {
                    "root 守护运行中 · PID ${st.daemonPid} · 锁定不依赖本应用进程"
                } else {
                    "root 守护未运行（需 root 权限，可点下方「重启常驻服务」）"
                },
            )
        }

        // ---------------- 界面显示 ----------------
        SmallTitle("界面显示")
        Card(cornerRadius = t.cardRadius) {
            SuperSwitch(
                checked = st.hideIcon,
                onCheckedChange = { on ->
                    Haptics.click(view)
                    SwitchService.setHideIcon(ctx, on)
                    toast(ctx, if (on) "图标已隐藏" else "图标已显示")
                    asyncRefresh()
                },
                title = "隐藏桌面图标",
                summary = "隐藏后仍可从悬浮窗右上角进入本应用",
            )
            SuperSwitch(
                checked = st.hideRecents,
                onCheckedChange = { on ->
                    Haptics.click(view)
                    SwitchService.setHideRecents(ctx, on)
                    toast(ctx, if (on) "已隐藏后台任务" else "已显示后台任务")
                    asyncRefresh()
                },
                title = "隐藏后台任务",
                summary = "最近任务列表里看不到本应用，服务照常运行",
            )
        }

        // ---------------- 权限与守护 ----------------
        SmallTitle("权限与守护")
        Card(cornerRadius = t.cardRadius) {
            ActionRow(
                title = "悬浮窗权限",
                summary = if (st.overlayGranted) "已开启，通知点击直接弹出悬浮面板" else "未开启，将降级为对话框面板",
                action = if (st.overlayGranted) "已开启" else "去开启",
            ) {
                SysActions.openOverlaySettings(ctx)
                asyncRefresh()
            }
            ActionRow(
                title = "电池优化白名单",
                summary = if (st.batteryWhitelisted) "已加入，不易被系统回收" else "未加入，可能被系统回收",
                action = if (st.batteryWhitelisted) "已完成" else "去设置",
            ) { SysActions.openBatterySettings(ctx) }
            ActionRow(
                title = "自启动（HyperOS）",
                summary = "允许后台自启，被杀后可自动恢复",
                action = "去设置",
            ) {
                Haptics.click(view)
                SysActions.openAutostart(ctx)
            }
            ActionRow(
                title = "重启常驻服务",
                summary = "立即重新拉起服务与守护任务",
                action = "执行",
            ) {
                Haptics.click(view)
                SwitchService.start(ctx)
                RestartJobService.schedule(ctx)
                toast(ctx, "已重启服务")
                asyncRefresh()
            }
        }

        // ---------------- 关于 ----------------
        SmallTitle("关于")
        Card(cornerRadius = t.cardRadius) {
            BasicComponent(title = "版本", summary = "4.2.1.5")
            BasicComponent(
                title = "root",
                summary = when {
                    !st.rootChecked -> "检测中…"
                    st.root -> "已授权 ✓"
                    else -> "未授权 ✗（请在 KernelSU 中允许）"
                },
            )
            Text(
                text = "Powered by Deepseek v4.1 flash",
                fontSize = 11.5.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StyleOption(
    label: String,
    selected: Boolean,
    t: StyleTokens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MiuixTheme.colorScheme.primary
    val bg = if (selected) accent else MiuixTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = modifier
            .height(46.dp)
            .background(bg, RoundedCornerShape(t.chipRadius))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    summary: String,
    action: String,
    onClick: () -> Unit,
) {    BasicComponent(
        title = title,
        summary = summary,
        onClick = onClick,
        endActions = {
            Text(
                text = action,
                color = MiuixTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { onClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
    )
}

/** 跳转系统设置页。 */
object SysActions {

    fun openOverlaySettings(ctx: Context) {
        try {
            val i = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + ctx.packageName),
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (t: Throwable) {
            toast(ctx, "需要悬浮窗权限")
        }
    }

    fun openBatterySettings(ctx: Context) {
        try {
            val i = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + ctx.packageName),
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (t: Throwable) {
            toast(ctx, "请关闭电池优化")
        }
    }

    fun openAutostart(ctx: Context) {
        val targets = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.securitycenter" to "com.miui.powercenter.PowerSettings",
        )
        for ((pkg, cls) in targets) {
            try {
                val i = Intent().setComponent(ComponentName(pkg, cls))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return
            } catch (ignored: Throwable) {
            }
        }
        try {
            val i = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.packageName),
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (t: Throwable) {
            toast(ctx, "请开启自启动")
        }
    }
}
