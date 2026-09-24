package com.dsh.refreshswitch

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用状态：由 IO 线程每秒刷新一次，Compose 侧只读取 State（避免在主线程执行 su）。
 */
class AppState(private val ctx: Context) {

    var fps by mutableIntStateOf(ModeUtil.currentFps(ctx))
    var lockEnabled by mutableStateOf(SwitchService.isLockEnabled(ctx))
    var lockedFps by mutableIntStateOf(SwitchService.lockedFps(ctx))
    var hideIcon by mutableStateOf(SwitchService.isHideIcon(ctx))
    var hideRecents by mutableStateOf(SwitchService.isHideRecents(ctx))
    var notifyEnabled by mutableStateOf(SwitchService.isNotifyEnabled(ctx))
    var bottomBarStyle by mutableIntStateOf(SwitchService.getBottomBarStyle(ctx))
    var autoEnabled by mutableStateOf(AutoRules.isEnabled(ctx))
    var autoShowSystem by mutableStateOf(AutoRules.isShowSystem(ctx))
    var blurEnabled by mutableStateOf(SwitchService.isBlur(ctx))
    var resendEnabled by mutableStateOf(SwitchService.isResendEnabled(ctx))
    var screen by mutableStateOf(ScreenInfo.read(ctx))
    var displays by mutableStateOf(ScreenInfo.displayList(ctx))
    var targetDisplay by mutableIntStateOf(SwitchService.getTargetDisplay(ctx))
    var capFps by mutableIntStateOf(SwitchService.getCapFps(ctx))
    var toastEnabled by mutableStateOf(SwitchService.isToastEnabled(ctx))
    var daemonPid by mutableIntStateOf(-1)
    var modes by mutableStateOf(ModeUtil.listModes(ctx))
    var overlayGranted by mutableStateOf(OverlayPanel.canShow(ctx))
    var batteryWhitelisted by mutableStateOf(false)
    var root by mutableStateOf(false)
    var rootChecked by mutableStateOf(false)

    /** 挡位表签名：变化即代表显示模式表变了（换屏/系统更新/外部显示），需要重新适配。 */
    private var modesSig = ""
    private var lastRootCheckAt = 0L

    /** 重新枚举主屏的可用挡位并重新检测 root（打开应用、屏幕变化时调用）。 */
    fun refreshModes() {
        val list = ModeUtil.listModes(ctx)
        val sig = list.joinToString(",") { "${it.id}:${it.fpsInt()}" }
        if (sig != modesSig) {
            modesSig = sig
            modes = list
        }
    }

    /** 重新检测 root（未授权时会重试，授权后无需重装即可生效）。 */
    private fun checkRoot(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && root && rootChecked) return
        if (!force && now - lastRootCheckAt < 5000) return
        lastRootCheckAt = now
        ModeUtil.resetRootCache()
        val r = ModeUtil.hasRoot()
        if (r != root || !rootChecked) {
            root = r
            rootChecked = true
        }
        if (r) {

            ModeUtil.whitelistBattery()
        }
    }

    /** 在 IO 线程调用：拉取最新刷新率与各项设置。 */
    fun refresh() {
        val f = ModeUtil.currentFps(ctx)
        if (f != fps) fps = f
        // 每秒检查挡位表（主屏 display 0）：换屏 / 模式表变化时自动适配
        refreshModes()
        checkRoot(false)
        val lock = SwitchService.isLockEnabled(ctx)
        if (lock != lockEnabled) lockEnabled = lock
        val lf = SwitchService.lockedFps(ctx)
        if (lf != lockedFps) lockedFps = lf
        val hi = SwitchService.isHideIcon(ctx)
        if (hi != hideIcon) hideIcon = hi
        val hr = SwitchService.isHideRecents(ctx)
        if (hr != hideRecents) hideRecents = hr
        val ne = SwitchService.isNotifyEnabled(ctx)
        if (ne != notifyEnabled) notifyEnabled = ne
        val bs = SwitchService.getBottomBarStyle(ctx)
        if (bs != bottomBarStyle) bottomBarStyle = bs
        val ae = AutoRules.isEnabled(ctx)
        if (ae != autoEnabled) autoEnabled = ae
        val asys = AutoRules.isShowSystem(ctx)
        if (asys != autoShowSystem) autoShowSystem = asys
        val rs = SwitchService.isResendEnabled(ctx)
        if (rs != resendEnabled) resendEnabled = rs
        val bl = SwitchService.isBlur(ctx)
        if (bl != blurEnabled) blurEnabled = bl
        // 屏幕信息 / LTPO 判定（内部有 3 秒缓存）
        val si = ScreenInfo.read(ctx)
        if (si.renderFps != screen.renderFps || si.modeFps != screen.modeFps || si.arr != screen.arr) screen = si
        // LTPO 屏幕不支持锁定：检测到就自动解除（界面层兜底，服务层也会做）
        if (si.arr && SwitchService.isLockEnabled(ctx)) {
            SwitchService.unlock(ctx)
        }
        val td = SwitchService.getTargetDisplay(ctx)
        if (td != targetDisplay) targetDisplay = td
        val cf = SwitchService.getCapFps(ctx)
        if (cf != capFps) capFps = cf
        val te = SwitchService.isToastEnabled(ctx)
        if (te != toastEnabled) toastEnabled = te
        val ov = OverlayPanel.canShow(ctx)
        if (ov != overlayGranted) overlayGranted = ov

        if (!rootChecked) refreshModes()
        if (root) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            batteryWhitelisted = pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false
        }
        if (!ne) {
            val p = Daemon.pid()
            if (p != daemonPid) daemonPid = p
        } else if (daemonPid != -1) {
            daemonPid = -1
        }
    }
}
