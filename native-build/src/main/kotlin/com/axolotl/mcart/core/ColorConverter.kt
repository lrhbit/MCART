package com.axolotl.mcart.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 颜色空间转换（重构版）。
 *
 * 关键优化：
 *  - sRGB → 线性 RGB 预计算 256 项 LUT，彻底移除逐像素 Math.pow；
 *  - Lab 的立方根采用「查表 + 一次牛顿迭代」，精度约 1e-7，显著快于 Math.cbrt；
 *  - 提供无分配重载，避免热路径产生垃圾（GC 抖动是界面卡顿主因之一）。
 */
object ColorConverter {

    private const val Xn: Double = 95.047
    private const val Yn: Double = 100.0
    private const val Zn: Double = 108.883

    /** sRGB(0..255) → 线性 RGB */
    private val srgbToLinear: DoubleArray = DoubleArray(256)
    /** 立方根查表：0..1 均匀采样 */
    private val cbrtCache: DoubleArray = DoubleArray(4096)
    private val cbrtN: Int = cbrtCache.size - 1

    init {
        for (c in 0..255) {
            val s = c / 255.0
            srgbToLinear[c] = if (s > 0.04045) ((s + 0.055) / 1.055).pow(2.4) else s / 12.92
        }
        for (i in 0..cbrtN) {
            cbrtCache[i] = Math.cbrt(i.toDouble() / cbrtN)
        }
    }

    @JvmStatic
    fun rgbToLab(r: Int, g: Int, b: Int): DoubleArray =
        rgbToLab(r, g, b, DoubleArray(3))

    /** 无分配版本：结果写入 out（长度 >= 3）。 */
    @JvmStatic
    fun rgbToLab(r: Int, g: Int, b: Int, out: DoubleArray): DoubleArray {
        val rLinear = srgbToLinear[r and 0xFF]
        val gLinear = srgbToLinear[g and 0xFF]
        val bLinear = srgbToLinear[b and 0xFF]
        val x = (rLinear * 0.4124564 + gLinear * 0.3575761 + bLinear * 0.1804375) * 100.0
        val y = (rLinear * 0.2126729 + gLinear * 0.7151522 + bLinear * 0.0721750) * 100.0
        val z = (rLinear * 0.0193339 + gLinear * 0.1191920 + bLinear * 0.9503041) * 100.0
        val fx = f(x / Xn)
        val fy = f(y / Yn)
        val fz = f(z / Zn)
        out[0] = 116.0 * fy - 16.0
        out[1] = 500.0 * (fx - fy)
        out[2] = 200.0 * (fy - fz)
        return out
    }

    /** f(t)：t>δ³ 时走快速立方根，否则线性段 */
    private fun f(t: Double): Double {
        val delta = 6.0 / 29.0
        return if (t > delta * delta * delta) {
            fastCbrt(t)
        } else {
            t / (3.0 * delta * delta) + 4.0 / 29.0
        }
    }

    /** 立方根：查表线性插值 + 一次牛顿迭代（误差 < 1e-6） */
    private fun fastCbrt(t: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= 1.0) return Math.cbrt(t)
        val fi = t * cbrtN
        val i = fi.toInt()
        val frac = fi - i
        val y0 = cbrtCache[i] + (cbrtCache[i + 1] - cbrtCache[i]) * frac
        return y0 - (y0 * y0 * y0 - t) / (3.0 * y0 * y0)
    }

    @JvmStatic
    fun rgbToOklab(r: Int, g: Int, b: Int): DoubleArray {
        val R = srgbToLinear[r and 0xFF]
        val G = srgbToLinear[g and 0xFF]
        val B = srgbToLinear[b and 0xFF]
        val l = 0.4122214708 * R + 0.5363325363 * G + 0.0514459929 * B
        val m = 0.2119034982 * R + 0.6806995451 * G + 0.1073969566 * B
        val s = 0.0883024619 * R + 0.2817188376 * G + 0.6299787005 * B
        val lc = fastCbrt(l)
        val mc = fastCbrt(m)
        val sc = fastCbrt(s)
        return doubleArrayOf(
            0.2104542553 * lc + 0.7936177850 * mc - 0.0040720468 * sc,
            1.9779984951 * lc - 2.4285922050 * mc + 0.4505937099 * sc,
            0.0259040371 * lc + 0.7827717662 * mc - 0.8086757660 * sc,
        )
    }

    @JvmStatic
    fun deltaE(lab1: DoubleArray, lab2: DoubleArray): Double {
        val dl = lab1[0] - lab2[0]
        val da = lab1[1] - lab2[1]
        val db = lab1[2] - lab2[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    @JvmStatic
    fun pack(r: Int, g: Int, b: Int): Int =
        ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
