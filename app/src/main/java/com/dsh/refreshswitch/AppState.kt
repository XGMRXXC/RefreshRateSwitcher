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
    var daemonPid by mutableIntStateOf(-1)
    var modes by mutableStateOf(ModeUtil.listModes(ctx))
    var overlayGranted by mutableStateOf(OverlayPanel.canShow(ctx))
    var batteryWhitelisted by mutableStateOf(false)
    var root by mutableStateOf(false)
    var rootChecked by mutableStateOf(false)

    /** 在 IO 线程调用：拉取最新刷新率与各项设置。 */
    fun refresh() {
        val f = ModeUtil.currentFps(ctx)
        if (f != fps) fps = f
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
        val ov = OverlayPanel.canShow(ctx)
        if (ov != overlayGranted) overlayGranted = ov

        if (modes.isEmpty()) modes = ModeUtil.listModes(ctx)
        if (!rootChecked) {
            val r = ModeUtil.hasRoot()
            root = r
            rootChecked = true
            if (r) {
                if (!OverlayPanel.canShow(ctx)) ModeUtil.grantOverlay()
                ModeUtil.whitelistBattery()
            }
        }
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
