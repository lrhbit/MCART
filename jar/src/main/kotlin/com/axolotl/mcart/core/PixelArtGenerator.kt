package com.axolotl.mcart.core

import com.axolotl.mcart.model.MapMode
import com.axolotl.mcart.model.PaletteService
import com.axolotl.mcart.util.TextureManager
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

/**
 * 像素画生成器：缩放 → 预处理 → 抖动量化 → 平面 / 立体结果组装。
 *
 * 主构造器参数带默认值并加 @JvmOverloads，一次覆盖原 Java 的 4 个构造器重载；
 * 可空入参在构造时规整为非空默认值，类内部字段全部非空。
 */
class PixelArtGenerator @JvmOverloads constructor(
    private val matcher: ColorMatcher,
    ditherMethod: DitherMethod?,
    private val mode: MapMode,
    supportBlockId: String? = null,
    preprocess: PreprocessSettings? = null,
    ditherStrength: Double = 1.0,
    private val roi: Rectangle2D.Float? = null,
) {
    private val ditherMethod: DitherMethod = ditherMethod ?: DitherMethod.FLOYD_STEINBERG
    private val supportBlockId: String = supportBlockId ?: "minecraft:stone"
    private val preprocess: PreprocessSettings = preprocess ?: PreprocessSettings.disabled()
    private val ditherStrength: Double = ditherStrength.coerceIn(0.0, 1.0)

    fun generate(source: BufferedImage, targetWidth: Int): Result {
        val targetHeight = maxOf(
            1,
            Math.round(source.height.toFloat() / source.width.toFloat() * targetWidth),
        )
        return generate(source, targetWidth, targetHeight)
    }

    fun generate(source: BufferedImage, targetWidth: Int, targetHeight: Int): Result {
        val w = maxOf(1, targetWidth)
        val h = maxOf(1, targetHeight)

        var src = source
        if (roi != null && roi.width > 0.01f && roi.height > 0.01f) {
            val sw = source.width
            val sh = source.height
            val cx = maxOf(0, minOf(sw - 1, Math.round(roi.x * sw)))
            val cy = maxOf(0, minOf(sh - 1, Math.round(roi.y * sh)))
            val cw = maxOf(1, minOf(sw - cx, Math.round(roi.width * sw)))
            val ch = maxOf(1, minOf(sh - cy, Math.round(roi.height * sh)))
            src = source.getSubimage(cx, cy, cw, ch)
        }

        val argb = preprocess.enabled &&
            preprocess.backgroundMode != PreprocessSettings.BackgroundMode.OFF
        // 保留 alpha 通道：背景合成发生在预处理阶段（与原语义一致）
        val scaled = BufferedImage(
            w, h,
            if (argb) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB,
        )
        val g: Graphics2D = scaled.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()

        val pixels = ImagePreprocessor.toPixels(scaled, preprocess, matcher.getPalette())
        val matched = DitheringEngine.quantize(
            pixels, w, h, matcher, ditherMethod, ditherStrength,
        )
        return if (mode == MapMode.RELIEF) buildRelief(matched, w, h)
        else buildFlat(matched, w, h)
    }

    private fun buildFlat(
        matched: Array<Array<ColorMatcher.PaletteEntry?>>,
        width: Int,
        rows: Int,
    ): Result {
        val used = mutableListOf<ColorMatcher.PaletteEntry>()
        val remap = IntArray(matcher.getPaletteSize()).also { it.fill(-1) }
        val data = Array(1) { Array(rows) { IntArray(width) } }
        val preview = Array(rows) { IntArray(width) }
        val heightMap = Array(rows) { IntArray(width) }

        for (z in 0 until rows) {
            val src = matched[z]
            val dst = data[0][z]
            val rowPx = preview[z]
            val rowH = heightMap[z]
            for (x in 0 until width) {
                val e = src[x]!!
                dst[x] = matcher.indexOf(e, used, remap)
                rowPx[x] = (e.r shl 16) or (e.g shl 8) or e.b
                rowH[x] = 1
            }
        }
        return Result(width, 1, rows, data, preview, heightMap, matched, used, supportBlockId)
    }

    private fun buildRelief(
        matched: Array<Array<ColorMatcher.PaletteEntry?>>,
        width: Int,
        rows: Int,
    ): Result {
        for (x in 0 until width) {
            val e = matched[0][x]!!
            if (e.shade != 2 && !e.baseColor.isWater()) {
                matched[0][x] = matcher.require(e.baseColor, 2)
            }
        }

        val topY = Array(rows) { IntArray(width) }
        var globalMax = Int.MIN_VALUE

        for (xx in 0 until width) {
            var h = 0
            val raw = IntArray(rows)
            raw[0] = 0

            for (z in 1 until rows) {
                val e = matched[z][xx]!!
                val delta = if (e.baseColor.isWater()) 0 else e.shade - 1
                h += delta
                raw[z] = h
            }

            var colMin = Int.MAX_VALUE
            var colMax = Int.MIN_VALUE
            for (z in 0 until rows) {
                if (raw[z] < colMin) colMin = raw[z]
                if (raw[z] > colMax) colMax = raw[z]
            }
            for (z in 0 until rows) topY[z][xx] = raw[z] - colMin
            if (colMax - colMin > globalMax) globalMax = colMax - colMin
        }

        val totalH = globalMax + 1
        val used = mutableListOf<ColorMatcher.PaletteEntry>()
        val remap = IntArray(matcher.getPaletteSize()).also { it.fill(-1) }
        val data = Array(totalH) { Array(rows) { IntArray(width).also { it.fill(-1) } } }
        val preview = Array(rows) { IntArray(width) }
        val heightMap = Array(rows) { IntArray(width) }

        for (z in 0 until rows) {
            val src = matched[z]
            val rowPx = preview[z]
            val rowH = heightMap[z]
            for (xx in 0 until width) {
                val e = src[xx]!!
                val idx = matcher.indexOf(e, used, remap)
                val top = topY[z][xx]
                data[top][z][xx] = idx
                for (y in 0 until top) data[y][z][xx] = SUPPORT

                rowPx[xx] = (e.r shl 16) or (e.g shl 8) or e.b
                rowH[xx] = top + 1
            }
        }
        return Result(width, totalH, rows, data, preview, heightMap, matched, used, supportBlockId)
    }

    /**
     * 生成结果。字段全部 @JvmField，Java 侧可直接字段访问。
     */
    class Result(
        @JvmField val width: Int,
        @JvmField val height: Int,
        @JvmField val length: Int,
        @JvmField val blockIds: Array<Array<IntArray>>,
        @JvmField val previewPixels: Array<IntArray>,
        @JvmField val heightMap: Array<IntArray>,
        @JvmField val topEntries: Array<Array<ColorMatcher.PaletteEntry?>>,
        @JvmField val palette: List<ColorMatcher.PaletteEntry>,
        @JvmField val supportBlockId: String,
    )

    companion object {
        const val AIR: Int = -1
        const val SUPPORT: Int = -2
    }
}
