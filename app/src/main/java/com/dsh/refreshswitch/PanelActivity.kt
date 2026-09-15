package com.dsh.refreshswitch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 悬浮窗权限不可用时的降级面板：对话框样式，点外部/返回关闭。 */
class PanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        setContent {
            val ctx = LocalContext.current
            val st = remember { AppState(ctx) }
            LaunchedEffect(Unit) {
                while (true) {
                    withContext(Dispatchers.IO) { st.refresh() }
                    delay(1000)
                }
            }
            when (UiStyle.of(SwitchService.getUiStyle(ctx))) {
                UiStyle.MIUIX -> AppTheme { PanelDialogBody(st) { finish() } }
                UiStyle.M3E -> M3eTheme { PanelDialogBody(st) { finish() } }
            }
        }
    }

    @Composable
    private fun PanelDialogBody(st: AppState, onClose: () -> Unit) {
        val ctx = LocalContext.current
        var dismissing by remember { androidx.compose.runtime.mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .pointerInput(Unit) { detectTapGestures { dismissing = true } },
            contentAlignment = Alignment.Center,
        ) {
            val cardModifier = Modifier
                .width(300.dp)
                .pointerInput(Unit) { detectTapGestures { } }
            PanelEnterExit(dismissing = dismissing) {
                when (UiStyle.of(SwitchService.getUiStyle(ctx))) {
                    UiStyle.MIUIX -> PanelContent(
                        st = st,
                        modifier = cardModifier,
                        onOpenApp = {
                            SwitchService.openApp(ctx)
                            dismissing = true
                        },
                        onOpenSystemSettings = {
                            OverlayPanel.openSystemSettings(ctx)
                            dismissing = true
                        },
                    )
                    UiStyle.M3E -> M3ePanelContent(
                        st = st,
                        modifier = cardModifier,
                        onOpenApp = {
                            SwitchService.openApp(ctx)
                            dismissing = true
                        },
                        onOpenSystemSettings = {
                            OverlayPanel.openSystemSettings(ctx)
                            dismissing = true
                        },
                    )
                }
            }
        }
        // 退场动画播完后关闭 Activity
        androidx.compose.runtime.LaunchedEffect(dismissing) {
            if (dismissing) {
                kotlinx.coroutines.delay(200)
                onClose()
            }
        }
    }
}
