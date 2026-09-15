package com.dsh.refreshswitch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 自绘「主页」图标（MIUIX 图标集中没有 Home，用同一套 24dp 视口补齐）。 */
val HomeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2.8f)
            lineTo(21.4f, 10.6f)
            lineTo(21.4f, 21.6f)
            lineTo(14.6f, 21.6f)
            lineTo(14.6f, 15.2f)
            lineTo(9.4f, 15.2f)
            lineTo(9.4f, 21.6f)
            lineTo(2.6f, 21.6f)
            lineTo(2.6f, 10.6f)
            close()
        }
    }.build()
}
