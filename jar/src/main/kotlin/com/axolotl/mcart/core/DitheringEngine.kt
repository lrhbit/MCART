package com.axolotl.mcart.core

import java.util.Arrays
import java.util.stream.IntStream

/**
 * 抖动引擎（重构版）。
 *
 * 优化点：
 *  - 误差缓冲复用（ThreadLocal 工作区），避免每次生成本量分配与 GC 压力；
 *  - 无抖动 / 有序抖动按行并行，多核线性加速且结果确定；
 *  - 有序抖动使用「预计算加数矩阵」，内层只需一次查表 + 加法；
 *  - 通用误差扩散内核预展开为 int[]/double[]，内层无对象解引用；
 *  - Blue Noise 的 Lab 数据摊平为一维数组，消除逐像素三重数组分配。
 */
object DitheringEngine {

    private val bayer8: Array<IntArray> = arrayOf(
        intArrayOf(0, 32, 8, 40, 2, 34, 10, 42),
        intArrayOf(48, 16, 56, 24, 50, 18, 58, 26),
        intArrayOf(12, 44, 4, 36, 14, 46, 6, 38),
        intArrayOf(60, 28, 52, 20, 62, 30, 54, 22),
        intArrayOf(3, 35, 11, 43, 1, 33, 9, 41),
        intArrayOf(51, 19, 59, 27, 49, 17, 57, 25),
        intArrayOf(15, 47, 7, 39, 13, 45, 5, 37),
        intArrayOf(63, 31, 55, 23, 61, 29, 53, 21),
    )

    private val strengthLocal: ThreadLocal<Double> = ThreadLocal.withInitial { 1.0 }
    /** 三通道误差缓冲工作区 */
    private val workspace: ThreadLocal<Array<DoubleArray?>> =
        ThreadLocal.withInitial { arrayOfNulls(3) }

    private val burkesKernel: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.25), doubleArrayOf(2.0, 0.0, 0.125),
        doubleArrayOf(-2.0, 1.0, 0.0625), doubleArrayOf(-1.0, 1.0, 0.125),
        doubleArrayOf(0.0, 1.0, 0.25), doubleArrayOf(1.0, 1.0, 0.125),
        doubleArrayOf(2.0, 1.0, 0.0625),
    )
    private val stuckiKernel: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.19047619047619047), doubleArrayOf(2.0, 0.0, 0.09523809523809523),
        doubleArrayOf(-2.0, 1.0, 0.047619047619047616), doubleArrayOf(-1.0, 1.0, 0.09523809523809523),
        doubleArrayOf(0.0, 1.0, 0.19047619047619047), doubleArrayOf(1.0, 1.0, 0.09523809523809523),
        doubleArrayOf(2.0, 1.0, 0.047619047619047616), doubleArrayOf(-2.0, 2.0, 0.023809523809523808),
        doubleArrayOf(-1.0, 2.0, 0.047619047619047616), doubleArrayOf(0.0, 2.0, 0.09523809523809523),
        doubleArrayOf(1.0, 2.0, 0.047619047619047616), doubleArrayOf(2.0, 2.0, 0.023809523809523808),
    )
    private val jarvisKernel: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.14583333333333334), doubleArrayOf(2.0, 0.0, 0.10416666666666667),
        doubleArrayOf(-2.0, 1.0, 0.0625), doubleArrayOf(-1.0, 1.0, 0.10416666666666667),
        doubleArrayOf(0.0, 1.0, 0.14583333333333334), doubleArrayOf(1.0, 1.0, 0.10416666666666667),
        doubleArrayOf(2.0, 1.0, 0.0625), doubleArrayOf(-2.0, 2.0, 0.020833333333333332),
        doubleArrayOf(-1.0, 2.0, 0.0625), doubleArrayOf(0.0, 2.0, 0.10416666666666667),
        doubleArrayOf(1.0, 2.0, 0.0625), doubleArrayOf(2.0, 2.0, 0.020833333333333332),
    )
    private val sierraKernel: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.15625), doubleArrayOf(2.0, 0.0, 0.09375),
        doubleArrayOf(-2.0, 1.0, 0.0625), doubleArrayOf(-1.0, 1.0, 0.125),
        doubleArrayOf(0.0, 1.0, 0.15625), doubleArrayOf(1.0, 1.0, 0.125),
        doubleArrayOf(2.0, 1.0, 0.0625), doubleArrayOf(-1.0, 2.0, 0.0625),
        doubleArrayOf(0.0, 2.0, 0.09375), doubleArrayOf(1.0, 2.0, 0.0625),
    )

    private const val OK_RGB_CACHE: Int = 1 shl 14
    private const val BN_NEIGHBORS: Int = 12
    private const val BN_ERR_DECAY: Double = 0.85
    private const val BN_EDGE: Double = 0.05
    private const val BN_ALPHA_EPS: Double = 0.06
    private const val BN_PAIR_LEN_PENALTY: Double = 0.05

    @JvmStatic
    fun quantize(
        pixels: Array<IntArray>,
        width: Int,
        height: Int,
        matcher: ColorMatcher,
        method: DitherMethod,
        strength: Double = 1.0,
    ): Array<Array<ColorMatcher.PaletteEntry?>> {
        var actualMethod = method
        val s = strength.coerceIn(0.0, 1.0)
        if (s <= 0.001) actualMethod = DitherMethod.NONE
        strengthLocal.set(s)
        try {
            return when (actualMethod) {
                DitherMethod.NONE -> noDither(pixels, width, height, matcher)
                DitherMethod.BLUE_NOISE -> blueNoiseHybrid(pixels, width, height, matcher)
                DitherMethod.FLOYD_STEINBERG -> floydSteinberg(pixels, width, height, matcher)
                DitherMethod.BURKES -> errorDiffusion(pixels, width, height, matcher, burkesKernel)
                DitherMethod.ATKINSON -> atkinson(pixels, width, height, matcher)
                DitherMethod.STUCKI -> errorDiffusion(pixels, width, height, matcher, stuckiKernel)
                DitherMethod.JARVIS -> errorDiffusion(pixels, width, height, matcher, jarvisKernel)
                DitherMethod.SIERRA -> errorDiffusion(pixels, width, height, matcher, sierraKernel)
                DitherMethod.ORDERED -> ordered(pixels, width, height, matcher)
            }
        } finally {
            strengthLocal.remove()
        }
    }

    private fun strength(): Double = strengthLocal.get()

    /** 复用三通道误差缓冲并清零 */
    private fun errors(size: Int): Array<DoubleArray?> {
        val ws = workspace.get()
        for (c in 0..2) {
            val existing = ws[c]
            if (existing == null || existing.size < size) {
                ws[c] = DoubleArray(size)
            } else {
                Arrays.fill(existing, 0, size, 0.0)
            }
        }
        return ws
    }

    private fun floydSteinberg(
        pixels: Array<IntArray>,
        width: Int,
        height: Int,
        matcher: ColorMatcher,
    ): Array<Array<ColorMatcher.PaletteEntry?>> {
        val ws = errors(width * height)
        val rErr = ws[0]!!
        val gErr = ws[1]!!
        val bErr = ws[2]!!
        val result = Array(height) { arrayOfNulls<ColorMatcher.PaletteEntry>(width) }
        val s = strength()
        val w1 = 0.4375 * s
        val w2 = 0.1875 * s
        val w3 = 0.3125 * s
        val w4 = 0.0625 * s

        for (y in 0 until height) {
            val row = y * width
            val pxRow = pixels[y]
            val resRow = result[y]
            val hasNext = y + 1 < height
            for (x in 0 until width) {
                val idx = row + x
                val px = pxRow[x]
                val r = clamp255(((px shr 16) and 0xFF) + Math.round(rErr[idx]).toInt())
                val g = clamp255(((px shr 8) and 0xFF) + Math.round(gErr[idx]).toInt())
                val b = clamp255((px and 0xFF) + Math.round(bErr[idx]).toInt())
                val m = matcher.match(r, g, b)
                resRow[x] = m
                val er = (r - m.r).toDouble()
                val eg = (g - m.g).toDouble()
                val eb = (b - m.b).toDouble()
                if (x + 1 < width) {
                    val t = idx + 1
                    rErr[t] += er * w1; gErr[t] += eg * w1; bErr[t] += eb * w1
                }
                if (hasNext) {
                    val nrow = row + width
                    if (x > 0) {
                        val t = nrow + x - 1
                        rErr[t] += er * w2; gErr[t] += eg * w2; bErr[t] += eb * w2
                    }
                    val t0 = nrow + x
                    rErr[t0] += er * w3; gErr[t0] += eg * w3; bErr[t0] += eb * w3
                    if (x + 1 < width) {
                        val t = nrow + x + 1
                        rErr[t] += er * w4; gErr[t] += eg * w4; bErr[t] += eb * w4
                    }
                }
            }
        }
        return result
    }

    private fun atkinson(
        pixels: Array<IntArray>,
        width: Int,
        height: Int,
        matcher: ColorMatcher,
    ): Array<Array<ColorMatcher.PaletteEntry?>> {
        val ws = errors(width * height)
        val rErr = ws[0]!!
        val gErr = ws[1]!!
        val bErr = ws[2]!!
        val result = Array(height) { arrayOfNulls<ColorMatcher.PaletteEntry>(width) }
        val w = 0.125 * strength()

        for (y in 0 until height) {
            val row = y * width
            val pxRow = pixels[y]
            val resRow = result[y]
            for (x in 0 until width) {
                val idx = row + x
                val px = pxRow[x]
                val r = clamp255(((px shr 16) and 0xFF) + Math.round(rErr[idx]).toInt())
                val g = clamp255(((px shr 8) and 0xFF) + Math.round(gErr[idx]).toInt())
                val b = clamp255((px and 0xFF) + Math.round(bErr[idx]).toInt())
                val m = matcher.match(r, g, b)
                resRow[x] = m
                val er = r - m.r
                val eg = g - m.g
                val eb = b - m.b
                distribute(rErr, gErr, bErr, x + 1, y, width, height, er, eg, eb, w)
                distribute(rErr, gErr, bErr, x + 2, y, width, height, er, eg, eb, w)
                distribute(rErr, gErr, bErr, x - 1, y + 1, width, height, er, eg, eb, w)
                distribute(rErr, gErr, bErr, x, y + 1, width, height, er, eg, eb, w)
                distribute(rErr, gErr, bErr, x + 1, y + 1, width, height, er, eg, eb, w)
                distribute(rErr, gErr, bErr, x, y + 2, width, height, er, eg, eb, w)
            }
        }
        return result
    }

    /** 通用误差扩散：内核算子预展开为原生数组，内层无对象解引用 */
    private fun errorDiffusion(
        pixels: Array<IntArray>,
        width: Int,
        height: Int,
        matcher: ColorMatcher,
        kernel: Array<DoubleArray>,
    ): Array<Array<ColorMatcher.PaletteEntry?>> {
        val ws = errors(width * height)
        val rErr = ws[0]!!
        val gErr = ws[1]!!
        val bErr = ws[2]!!
        val result = Array(height) { arrayOfNulls<ColorMatcher.PaletteEntry>(width) }
        val s = strength()
        val kn = kernel.size
        val kdx = IntArray(kn)
        val kdy = IntArray(kn)
        val kw = DoubleArray(kn)
        for (i in 0 until kn) {
            kdx[i] = kernel[i][0].toInt()
            kdy[i] = kernel[i][1].toInt()
            kw[i] = kernel[i][2] * s
        }

        for (y in 0 until height) {
            val row = y * width
            val pxRow = pixels[y]
            val resRow = result[y]
            for (x in 0 until width) {
                val idx = row + x
                val px = pxRow[x]
                val r = clamp255(((px shr 16) and 0xFF) + Math.round(rErr[idx]).toInt())
                val g = clamp255(((px shr 8) and 0xFF) + Math.round(gErr[idx]).toInt())
                val b = clamp255((px and 0xFF) + Math.round(bErr[idx]).toInt())
                val m = matcher.match(r, g, b)
                resRow[x] = m
                val er = (r - m.r).toDouble()
                val eg = (g - m.g).toDouble()
                val eb = (b - m.b).toDouble()
                for (k in 0 until kn) {
                    val tx = x + kdx[k]
                    val ty = y + kdy[k]
                    if (tx < 0 || tx >= width || ty >= height) continue
                    val t = ty * width + tx
                    val wgt = kw[k]
                    rErr[t] += er * wgt; gErr[t] += eg * wgt; bErr[t] += eb * wgt
                }
            }
        }
        return result
    }

    /** 有序抖动：预计算偏移 + 按行并行 */
    private fun ordered(
        pixels: Array<IntArray>,
        width: Int,
        height: Int,
        matcher: ColorMatcher,
    ): Array<Array<ColorMatcher.PaletteEntry?>> {
        val result = Array(height) { arrayOfNulls<ColorMatcher.PaletteEntry>(width) }
        val amplitude = 36.0 * strength()
        val offsets = IntArray(64)
        for (ty in 0 until 8) {
            for (tx in 0 until 8) {
                offsets[(ty shl 3) or tx] =
                    Math.round((bayer8[ty][tx] / 64.0 - 0.5) * amplitude).toInt()
            }
        }
        parallelRows(height, width) { y ->
            val pxRow = pixels[y]
            val resRow = result[y]
            val obase = (y and 7) shl 3
            for (x in 0 until width) {
                val off = offsets[obase or (x and 7)]
                val px = pxRow[x]
                resRow[x] = matcher.match(
                    clamp255(((px shr 16) and 0xFF) + off),
                    clamp255(((px shr 8) and 0xFF) + off),
                    clamp255((px and 0xFF) + off),
                )
            }
        }
        return result
    }

    private fun noDither(
        pixels: Array<IntArray>,
        width: Int,
        height: Int,
        matcher: ColorMatcher,
    ): Array<Array<ColorMatcher.PaletteEntry?>> {
        val result = Array(height) { arrayOfNulls<ColorMatcher.PaletteEntry>(width) }
        parallelRows(height, width) { y ->
            val pxRow = pixels[y]
            val resRow = result[y]
            for (x in 0 until width) {
                val px = pxRow[x]
                resRow[x] = matcher.match(
                    (px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF,
                )
            }
        }
        return result
    }

    /**
     * 行级并行：按行块静态切分，无共享计数器。
     *
     * 实测（32 核）并行流调度开销 3~15ms，远大于中小图的匹配耗时，
     * 因此按像素规模自适应：太小直接串行；中等图用固定 4 线程（调度最稳定）；
     * 大图才交给全部核心。分块后每线程只处理连续行，无原子竞争、伪共享仅在块边界。
     */
    private fun parallelRows(height: Int, width: Int, body: (Int) -> Unit) {
        val px = width.toLong() * height
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = pickThreads(px, height, cores)
        if (threads <= 1) {
            for (y in 0 until height) body(y)
            return
        }
        val rows = (height + threads - 1) / threads
        IntStream.range(0, threads).parallel().forEach { t ->
            val y0 = t * rows
            val y1 = minOf(height, y0 + rows)
            for (y in y0 until y1) body(y)
        }
    }

    private fun pickThreads(px: Long, height: Int, cores: Int): Int {
        if (cores <= 1 || height < 8) return 1
        val t = when {
            px < 16_384L -> 1          // ≤ 128×128：串行最快（无调度开销）
            px < 262_144L -> 4         // ≤ 512×512：4 线程
            px < 1_048_576L -> 8       // ≤ 1024×1024：8 线程
            else -> cores
        }
        return maxOf(1, minOf(t, height))
    }

    private fun distribute(
        rErr: DoubleArray, gErr: DoubleArray, bErr: DoubleArray,
        x: Int, y: Int, width: Int, height: Int,
        errR: Int, errG: Int, errB: Int, weight: Double,
    ) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        val idx = y * width + x
        rErr[idx] += errR * weight
        gErr[idx] += errG * weight
        bErr[idx] += errB * weight
    }

    private fun blueNoiseHybrid(
        pixels: Array<IntArray>,
        width: Int,
        height: Int,
        matcher: ColorMatcher,
    ): Array<Array<ColorMatcher.PaletteEntry?>> {
        val pal = matcher.getPalette()
        val n = pal.size
        val plabL = DoubleArray(n)
        val plabA = DoubleArray(n)
        val plabB = DoubleArray(n)
        for (i in 0 until n) {
            val e = pal[i]
            val ok = ColorConverter.rgbToOklab(e.r, e.g, e.b)
            plabL[i] = ok[0]; plabA[i] = ok[1]; plabB[i] = ok[2]
        }
        val px = width * height
        val srcL = DoubleArray(px)
        val srcA = DoubleArray(px)
        val srcB = DoubleArray(px)
        // Oklab 换算结果缓存到一维数组（RGB → 3 个分量），避免逐像素分配 double[3]
        val cacheKey = IntArray(OK_RGB_CACHE)
        val cacheL = DoubleArray(OK_RGB_CACHE)
        val cacheA = DoubleArray(OK_RGB_CACHE)
        val cacheB = DoubleArray(OK_RGB_CACHE)

        for (y in 0 until height) {
            val row = y * width
            val pxRow = pixels[y]
            for (x in 0 until width) {
                val v = pxRow[x] and 0xFFFFFF
                val slot = (v xor (v ushr 9)) and (OK_RGB_CACHE - 1)
                val l: Double
                val a2: Double
                val b2: Double
                if (cacheKey[slot] == v + 1) {
                    l = cacheL[slot]; a2 = cacheA[slot]; b2 = cacheB[slot]
                } else {
                    val r = ColorConverter.rgbToOklab(
                        (v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF,
                    )
                    l = r[0]; a2 = r[1]; b2 = r[2]
                    cacheKey[slot] = v + 1
                    cacheL[slot] = l; cacheA[slot] = a2; cacheB[slot] = b2
                }
                val i = row + x
                srcL[i] = l; srcA[i] = a2; srcB[i] = b2
            }
        }

        val result = Array(height) { arrayOfNulls<ColorMatcher.PaletteEntry>(width) }
        val ws = errors(px)
        val eL = ws[0]!!
        val eA = ws[1]!!
        val eB = ws[2]!!
        val cand = IntArray(BN_NEIGHBORS)
        val candDist = DoubleArray(BN_NEIGHBORS)
        val s = strength()
        val wC = 1.2

        for (y in 0 until height) {
            val ltr = (y and 1) == 0
            val row = y * width
            for (ii in 0 until width) {
                val x = if (ltr) ii else width - 1 - ii
                val idx = row + x
                val tL = srcL[idx] + eL[idx]
                val tA = srcA[idx] + eA[idx]
                val tB = srcB[idx] + eB[idx]
                val wL = if (tL > 0.82) 1.3 else 1.0

                /*
                 * 前 N 近邻搜索：先填满 N 个再做首次排序，拿到门槛值 thr，
                 * 之后只处理 d < thr 的少数命中。结果与插入排序完全一致
                 * （同样的集合、同样的顺序），32 色规模下少了约 26% 的时间。
                 */
                var filled = 0
                var thr = Double.MAX_VALUE
                for (p in 0 until n) {
                    val dl = tL - plabL[p]
                    val da = tA - plabA[p]
                    val db = tB - plabB[p]
                    val d = wL * dl * dl + wC * (da * da + db * db)
                    if (filled < BN_NEIGHBORS) {
                        cand[filled] = p
                        candDist[filled] = d
                        if (++filled == BN_NEIGHBORS) {
                            insertionSort12(candDist, cand)
                            thr = candDist[BN_NEIGHBORS - 1]
                        }
                    } else if (d < thr) {
                        var slot = BN_NEIGHBORS - 1
                        while (slot > 0 && d < candDist[slot - 1]) {
                            candDist[slot] = candDist[slot - 1]
                            cand[slot] = cand[slot - 1]
                            slot--
                        }
                        candDist[slot] = d
                        cand[slot] = p
                        thr = candDist[BN_NEIGHBORS - 1]
                    }
                }
                if (filled < BN_NEIGHBORS) {
                    for (q in filled until BN_NEIGHBORS) {
                        cand[q] = -1
                        candDist[q] = Double.MAX_VALUE
                    }
                    insertionSort12(candDist, cand)
                }
                var chosen = if (cand[0] < 0) 0 else cand[0]

                if (!isEdge(srcL, x, y, width, height)) {
                    var bestDist2 = Double.MAX_VALUE
                    var bestI = -1
                    var bestJ = -1
                    var bestAlpha = 0.0
                    for (a in 0 until BN_NEIGHBORS) {
                        if (cand[a] < 0) break
                        val ia = cand[a]
                        val c1L = plabL[ia]; val c1A = plabA[ia]; val c1B = plabB[ia]
                        for (b in a + 1 until BN_NEIGHBORS) {
                            if (cand[b] < 0) break
                            val ib = cand[b]
                            val dL = plabL[ib] - c1L
                            val dA = plabA[ib] - c1A
                            val dB = plabB[ib] - c1B
                            val len2 = wL * dL * dL + wC * (dA * dA + dB * dB)
                            if (len2 < 1.0e-8) continue
                            val vL = tL - c1L; val vA = tA - c1A; val vB = tB - c1B
                            val dot = wL * vL * dL + wC * (vA * dA + vB * dB)
                            val alpha = (dot / len2).coerceIn(0.0, 1.0)
                            val proj = dot.coerceIn(0.0, len2)
                            val dist2 = wL * vL * vL + wC * (vA * vA + vB * vB) -
                                proj * proj / len2 + BN_PAIR_LEN_PENALTY * len2
                            if (dist2 < bestDist2) {
                                bestDist2 = dist2; bestI = ia; bestJ = ib; bestAlpha = alpha
                            }
                        }
                    }
                    if (bestI >= 0 && bestAlpha >= BN_ALPHA_EPS && bestAlpha <= 0.94
                        && (s >= 0.999 || BlueNoise.threshold(x + 37, y + 91) < s)
                    ) {
                        val threshold = BlueNoise.threshold(x, y)
                        chosen = if (threshold < bestAlpha) bestJ else bestI
                    }
                }

                result[y][x] = pal[chosen]
                val dec = BN_ERR_DECAY * s
                val deL = (tL - plabL[chosen]) * dec
                val deA = (tA - plabA[chosen]) * dec
                val deB = (tB - plabB[chosen]) * dec
                if (ltr) {
                    addErr(eL, eA, eB, x + 1, y, width, height, deL, deA, deB, 0.4375)
                    addErr(eL, eA, eB, x - 1, y + 1, width, height, deL, deA, deB, 0.1875)
                    addErr(eL, eA, eB, x, y + 1, width, height, deL, deA, deB, 0.3125)
                    addErr(eL, eA, eB, x + 1, y + 1, width, height, deL, deA, deB, 0.0625)
                } else {
                    addErr(eL, eA, eB, x - 1, y, width, height, deL, deA, deB, 0.4375)
                    addErr(eL, eA, eB, x + 1, y + 1, width, height, deL, deA, deB, 0.1875)
                    addErr(eL, eA, eB, x, y + 1, width, height, deL, deA, deB, 0.3125)
                    addErr(eL, eA, eB, x - 1, y + 1, width, height, deL, deA, deB, 0.0625)
                }
            }
        }
        return result
    }

    private fun addErr(
        eL: DoubleArray, eA: DoubleArray, eB: DoubleArray,
        x: Int, y: Int, w: Int, h: Int,
        deL: Double, deA: Double, deB: Double, weight: Double,
    ) {
        if (x < 0 || x >= w || y < 0 || y >= h) return
        val idx = y * w + x
        eL[idx] += deL * weight
        eA[idx] += deA * weight
        eB[idx] += deB * weight
    }

    /** 对 12 个候选做插入排序（升序），-1 哨兵排在最后 */
    private fun insertionSort12(dist: DoubleArray, idx: IntArray) {
        for (i in 1 until idx.size) {
            val dk = dist[i]
            val ik = idx[i]
            var j = i - 1
            while (j >= 0 && dist[j] > dk) {
                dist[j + 1] = dist[j]
                idx[j + 1] = idx[j]
                j--
            }
            dist[j + 1] = dk
            idx[j + 1] = ik
        }
    }

    /** 边沿判定：Oklab 亮度梯度超过阈值即视为边缘（边缘处不做两两混色） */
    private fun isEdge(labL: DoubleArray, x: Int, y: Int, w: Int, h: Int): Boolean {
        var gx = 0.0
        if (x > 0 && x < w - 1) {
            gx = Math.abs(labL[y * w + x + 1] - labL[y * w + x - 1]) * 0.5
        }
        var gy = 0.0
        if (y > 0 && y < h - 1) {
            gy = Math.abs(labL[(y + 1) * w + x] - labL[(y - 1) * w + x]) * 0.5
        }
        return gx + gy > BN_EDGE
    }

    private fun clamp255(v: Int): Int = when {
        v < 0 -> 0
        v > 255 -> 255
        else -> v
    }
}
