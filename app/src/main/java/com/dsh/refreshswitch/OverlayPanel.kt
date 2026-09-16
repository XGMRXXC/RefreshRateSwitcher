package com.dsh.refreshswitch

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.min

/**
 * 悬浮窗面板（MIUIX Compose 版）：
 * 全屏透明窗口 + 居中卡片，**不压暗背景**；点卡片外任意空白处关闭。
 */
object OverlayPanel {

    @Volatile private var wm: WindowManager? = null
    @Volatile private var hostView: View? = null
    @Volatile private var owner: OverlayOwner? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 退出动画进行中：Compose 侧据此播放淡出。 */
    internal val dismissingState = androidx.compose.runtime.mutableStateOf(false)

    @JvmStatic
    fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    @JvmStatic
    fun isShowing(): Boolean = hostView != null

    @JvmStatic
    fun show(ctx: Context): Boolean {
        if (hostView != null) return true
        if (!canShow(ctx)) return false
        dismissingState.value = false
        return try {
            val w = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val o = OverlayOwner()
            val cv = ComposeView(ctx).apply {
                setViewTreeLifecycleOwner(o)
                setViewTreeViewModelStoreOwner(o)
                setViewTreeSavedStateRegistryOwner(o)
                setContent {
                    when (UiStyle.of(SwitchService.getUiStyle(ctx))) {
                        UiStyle.MIUIX -> AppTheme {
                            PanelRoot(
                                onClose = { hide() },
                                onOpenApp = {
                                    SwitchService.openApp(ctx)
                                    hide()
                                },
                            )
                        }
                        UiStyle.M3E -> M3eTheme {
                            M3ePanelRoot(
                                onClose = { hide() },
                                onOpenApp = {
                                    SwitchService.openApp(ctx)
                                    hide()
                                },
                            )
                        }
                    }
                }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT,
            )
            lp.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            w.addView(cv, lp)
            o.start()
            wm = w
            hostView = cv
            owner = o
            true
        } catch (t: Throwable) {
            Log.e(ModeUtil.TAG, "overlay show failed", t)
            hostView = null
            wm = null
            owner = null
            false
        }
    }

    /** Compose 侧自带轮询，无需外部刷新。 */
    @JvmStatic
    fun refresh() {}

    /** 请求关闭：先让 Compose 播放退场动画，动画结束后（或超时兜底）真正移除窗口。 */
    @JvmStatic
    fun hide() {
        if (hostView == null) return
        if (dismissingState.value) return
        dismissingState.value = true
        // 兜底：Compose 未在运行/动画被打断时也能移除
        handler.postDelayed({ finishHide() }, 280)
    }

    /** 真正移除悬浮窗（幂等）。 */
    @JvmStatic
    fun finishHide() {
        try {
            val v = hostView
            val w = wm
            if (v != null && w != null) w.removeView(v)
        } catch (t: Throwable) {
            Log.e(ModeUtil.TAG, "overlay removeView failed", t)
        }
        try {
            owner?.destroy()
        } catch (ignored: Throwable) {
        }
        hostView = null
        wm = null
        owner = null
        dismissingState.value = false
    }

    @JvmStatic
    fun openSystemSettings(ctx: Context) {
        try {
            val i = Intent("miui.intent.action.SCREEN_REFRESH")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (t: Throwable) {
            try {
                val i = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (ignored: Throwable) {
            }
        }
        hide()
    }
}

@androidx.compose.runtime.Composable
private fun PanelRoot(onClose: () -> Unit, onOpenApp: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember { AppState(ctx) }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { st.refresh() }
            delay(1000)
        }
    }

    val dm = ctx.resources.displayMetrics
    val cardWidthPx = min(dm.widthPixels * 0.62f, 300f * dm.density)
    val cardWidth = with(density) { cardWidthPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        PanelEnterExit(dismissing = OverlayPanel.dismissingState.value) {
            PanelContent(
                st = st,
                modifier = Modifier
                    .width(cardWidth)
                    .pointerInput(Unit) { detectTapGestures { /* 吞掉卡片内的空白点击 */ } },
                onOpenApp = onOpenApp,
            )
        }
    }

    // 退场动画播完即移除窗口（比 280ms 兜底更早）
    androidx.compose.runtime.LaunchedEffect(OverlayPanel.dismissingState.value) {
        if (OverlayPanel.dismissingState.value) {
            delay(200)
            OverlayPanel.finishHide()
        }
    }
}

/** 供非 Activity 窗口使用的 Compose 宿主（Lifecycle + ViewModelStore + SavedState）。 */
private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
