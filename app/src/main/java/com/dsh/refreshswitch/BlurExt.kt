package com.dsh.refreshswitch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏 / 底栏毛玻璃（与 KernelSU 的 BlurExt.kt 完全同款实现，全部使用 MIUIX 官方 API）：
 *   - rememberLayerBackdrop 记录底层内容
 *   - textureBlur(blurRadius = 25f) 对记录的层做实时模糊
 *   - BlurColors / BlendColorEntry 叠加一层半透明 surface 让文字保持可读
 * 设备不支持 RenderEffect 时自动退化为不模糊。
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    // 官方 blur.md：blur / blendColors / textureBlur 路径必须用 isRuntimeShaderSupported() 门控（API 33+）
    if (!enableBlur || !isRuntimeShaderSupported() || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 把内容放进毛玻璃容器（backdrop 为 null 时就是普通 Box）。 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.8f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

/** 修饰符版：直接把毛玻璃效果挂到任何接受 modifier 的官方组件上（如 TopAppBar / NavigationBar）。 */
@Composable
fun Modifier.barBlur(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) {
        this.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 25f,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f)),
                ),
            ),
        )
    } else this
/** 内容层：把滚动内容作为毛玻璃的取样源（backdrop 为 null 时原样返回）。 */
@Composable
fun Modifier.contentBlur(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this
/** 当前页面的毛玻璃采样源（由外壳提供；为 null 表示模糊关闭或设备不支持）。 */
val LocalBlurBackdrop = compositionLocalOf<LayerBackdrop?> { null }
/**
 * 模糊预览卡 —— **完全照 MIUIX 官方 demo（example/…/component/BlurSection.kt）的可用形态**：
 * 一个**固定尺寸**的父容器里放两个 **matchParentSize 的兄弟层**：
 *   ① 背景层：`layerBackdrop(backdrop)` 记录要模糊的内容；
 *   ② 模糊层：`textureBlur(backdrop = …, colors = BlurDefaults.blurColors(…))` 盖在它上面。
 * 两层尺寸与位置完全一致 → 采样不会错位（这是官方示例里唯一被验证可用的结构）。
 */
@Composable
fun MiuixBlurPreviewCard() {
    val backdrop = rememberLayerBackdrop()
    val surface = MiuixTheme.colorScheme.surface
    Card(cornerRadius = 16.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "模糊预览",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                // ① 背景层（被 layerBackdrop 记录）
                Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF3B82F6), Color(0xFF22C55E),
                                        Color(0xFFEAB308), Color(0xFFEF4444),
                                    ),
                                ),
                            ),
                    )
                    Text(
                        text = "MIUIX blur\nmiuix-blur / textureBlur",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                // ② 模糊层（与背景层同尺寸同位置的兄弟节点）
                Box(
                    Modifier
                        .matchParentSize()
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(12.dp),
                            blurRadius = 25f,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    BlendColorEntry(color = surface.copy(0.8f)),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "模糊生效时这里应看不到清晰文字",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}