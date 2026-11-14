package com.axolotl.mcart.core

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 图像预处理（重构版）。
 *
 * 关键优化：
 *  - 不再逐像素调用 BufferedImage.getRGB（每次要做类型判断 + 像素转换），
 *    改为一次性把整幅图读入 int[] 后线性遍历；
 *  - gamma 映射预生成 256 项 LUT，热路径彻底消除 Math.pow；
 *  - 亮度 / 对比度 / gamma 合成单张 256 项表，三通道共享；
 *  - posterize 也预处理成 256 项整型表，去掉逐像素除法；
 *  - 参数为默认值时直接短路，不进入浮点运算链。
 *
 * 输出语义与原实现一致（相同运算顺序与取整方式）。
 */
object ImagePreprocessor {

    @JvmStatic
    fun toPixels(
        scaled: BufferedImage,
        set: PreprocessSettings?,
        palette: List<ColorMatcher.PaletteEntry>,
    ): Array<IntArray> {
        val w = scaled.width
        val h = scaled.height
        val out = Array(h) { IntArray(w) }
        val row = IntArray(w)

        if (set == null || !set.enabled) {
            for (y in 0 until h) {
                scaled.getRGB(0, y, w, 1, row, 0, w)
                val dst = out[y]
                for (x in 0 until w) dst[x] = row[x] and 0xFFFFFF
            }
            return out
        }

        val hasBg = set.backgroundMode != PreprocessSettings.BackgroundMode.OFF
        var bg = set.backgroundRgb
        if (set.backgroundMode == PreprocessSettings.BackgroundMode.SMOOTH) {
            bg = closestPaletteColor(bg, palette)
        }
        val bgR = (bg shr 16) and 0xFF
        val bgG = (bg shr 8) and 0xFF
        val bgB = bg and 0xFF

        val bk = set.brightness / 100.0f
        val ck = set.contrast / 100.0f
        val sk = set.saturation / 100.0f
        val gammaK = 100.0f / maxOf(1, set.gamma)
        val rk = set.redGain / 100.0f
        val gk = set.greenGain / 100.0f
        val bkCh = set.blueGain / 100.0f
        val levels = set.posterize
        val gray = set.grayscale
        val contrastOffset = 127.5f * (1.0f - ck)

        val identityTone = bk == 1.0f && ck == 1.0f && gammaK == 1.0f
        val toneLut: FloatArray? = if (identityTone) null else toneLut(bk, ck, contrastOffset, gammaK)
        val posterLut: IntArray? = if (levels >= 2) posterLut(levels) else null

        if (toneLut == null && posterLut == null && !hasBg && !gray && sk == 1.0f
            && rk == 1.0f && gk == 1.0f && bkCh == 1.0f
        ) {
            // 全部为恒等变换：直接读像素，跳过整条浮点链
            for (y in 0 until h) {
                scaled.getRGB(0, y, w, 1, row, 0, w)
                val dst = out[y]
                for (x in 0 until w) dst[x] = row[x] and 0xFFFFFF
            }
            return out
        }

        for (y in 0 until h) {
            scaled.getRGB(0, y, w, 1, row, 0, w)
            val dst = out[y]
            for (x in 0 until w) {
                val argb = row[x]
                val a = (argb ushr 24) and 0xFF

                var r2: Float
                var g2: Float
                var b2: Float
                if (toneLut != null) {
                    r2 = toneLut[(argb shr 16) and 0xFF]
                    g2 = toneLut[(argb shr 8) and 0xFF]
                    b2 = toneLut[argb and 0xFF]
                } else {
                    r2 = ((argb shr 16) and 0xFF).toFloat()
                    g2 = ((argb shr 8) and 0xFF).toFloat()
                    b2 = (argb and 0xFF).toFloat()
                }

                if (hasBg && a < 255) {
                    val af = a / 255.0f
                    r2 = r2 * af + bgR * (1.0f - af)
                    g2 = g2 * af + bgG * (1.0f - af)
                    b2 = b2 * af + bgB * (1.0f - af)
                }

                r2 *= rk
                g2 *= gk
                b2 *= bkCh

                if (gray) {
                    val luma = 0.2126f * r2 + 0.7152f * g2 + 0.0722f * b2
                    r2 = luma; g2 = luma; b2 = luma
                }

                if (sk != 1.0f) {
                    val luma = 0.2126f * r2 + 0.7152f * g2 + 0.0722f * b2
                    r2 = luma + sk * (r2 - luma)
                    g2 = luma + sk * (g2 - luma)
                    b2 = luma + sk * (b2 - luma)
                }

                val rr: Int
                val gg: Int
                val bb: Int
                if (posterLut != null) {
                    rr = posterLut[clamp(r2.roundToInt())]
                    gg = posterLut[clamp(g2.roundToInt())]
                    bb = posterLut[clamp(b2.roundToInt())]
                } else {
                    rr = clamp(r2.roundToInt())
                    gg = clamp(g2.roundToInt())
                    bb = clamp(b2.roundToInt())
                }
                dst[x] = (rr shl 16) or (gg shl 8) or bb
            }
        }
        return out
    }

    /** 合成「亮度 → 对比度 → gamma」三步的 256 项 float 表（与逐像素实现同序同精度） */
    private fun toneLut(bk: Float, ck: Float, contrastOffset: Float, gammaK: Float): FloatArray {
        val lut = FloatArray(256)
        for (v in 0..255) {
            val v2 = v * bk * ck + contrastOffset
            lut[v] = if (gammaK == 1.0f) {
                v2
            } else {
                val n = maxOf(0.0f, minOf(255.0f, v2)) / 255.0f
                (255.0 * n.pow(gammaK)).toFloat()
            }
        }
        return lut
    }

    /** posterize 的 256 项输入 → 输出表 */
    private fun posterLut(levels: Int): IntArray {
        val step = 255.0f / (levels - 1)
        val lut = IntArray(256)
        for (v in 0..255) {
            val bucket = Math.round(v / step)
            lut[v] = clamp(Math.round(bucket * step))
        }
        return lut
    }

    private fun closestPaletteColor(target: Int, palette: List<ColorMatcher.PaletteEntry>): Int {
        val tr = (target shr 16) and 0xFF
        val tg = (target shr 8) and 0xFF
        val tb = target and 0xFF
        var best = target
        var bestD = Long.MAX_VALUE
        for (e in palette) {
            val dr = e.r - tr
            val dg = e.g - tg
            val db = e.b - tb
            val d = (dr.toLong() * dr + dg.toLong() * dg + db.toLong() * db)
            if (d < bestD) {
                bestD = d
                best = (e.r shl 16) or (e.g shl 8) or e.b
            }
        }
        return best
    }

    private fun clamp(v: Int): Int = when {
        v < 0 -> 0
        v > 255 -> 255
        else -> v
    }
}
