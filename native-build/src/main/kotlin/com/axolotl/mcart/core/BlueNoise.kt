package com.axolotl.mcart.core

import java.util.Random
import kotlin.math.exp

/**
 * 64×64 蓝噪声阈值矩阵（void-and-cluster 算法离线生成）。
 * 类加载时一次性计算，之后只做查表。
 */
object BlueNoise {

    const val N: Int = 64
    private const val total: Int = 4096
    private const val half: Int = 32

    @JvmField
    val rank: Array<IntArray> = Array(64) { IntArray(64) }

    init {
        generate()
    }

    private fun generate() {
        val sigma = 1.9
        val kernel = DoubleArray(total)

        for (dy in -half until half) {
            for (dx in -half until half) {
                val d2 = (dx * dx + dy * dy).toDouble()
                kernel[(dy and 63) * 64 + (dx and 63)] = exp(-d2 / 7.22)
            }
        }

        var pattern = BooleanArray(total)
        val rnd = Random(-7046029254386353131L)
        val order = IntArray(total) { it }

        // Fisher-Yates 洗牌
        for (ix in (total - 1) downTo 1) {
            val j = rnd.nextInt(ix + 1)
            val t = order[ix]
            order[ix] = order[j]
            order[j] = t
        }

        val initial = 409
        val energy = DoubleArray(total)

        for (ix in 0 until initial) {
            pattern[order[ix]] = true
            bump(energy, kernel, order[ix], 1.0)
        }

        while (true) {
            val tightest = extreme(pattern, energy, true)
            val tightEnergy = energy[tightest]
            pattern[tightest] = false
            bump(energy, kernel, tightest, -1.0)
            val loosest = extreme(pattern, energy, false)
            val looseEnergy = energy[loosest]
            if (loosest == tightest || tightEnergy <= looseEnergy) {
                pattern[tightest] = true
                bump(energy, kernel, tightest, 1.0)
                val savedPattern = pattern.copyOf()
                val r = IntArray(total).also { it.fill(-1) }

                for (ones in initial downTo 1) {
                    val site = extreme(pattern, energy, true)
                    r[site] = ones - 1
                    pattern[site] = false
                    bump(energy, kernel, site, -1.0)
                }

                pattern = savedPattern.copyOf()
                energy.fill(0.0)

                for (ix in 0 until total) {
                    if (pattern[ix]) bump(energy, kernel, ix, 1.0)
                }

                for (rv in initial until total) {
                    val site = extreme(pattern, energy, false)
                    r[site] = rv
                    pattern[site] = true
                    bump(energy, kernel, site, 1.0)
                }

                val seen = BooleanArray(total)
                for (value in r) {
                    check(value in 0 until total && !seen[value]) { "蓝噪声矩阵生成异常" }
                    seen[value] = true
                }

                for (y in 0 until 64) {
                    System.arraycopy(r, y * 64, rank[y], 0, 64)
                }
                return
            }

            pattern[loosest] = true
            bump(energy, kernel, loosest, 1.0)
        }
    }

    private fun bump(energy: DoubleArray, kernel: DoubleArray, site: Int, sign: Double) {
        val sy = site / 64
        val sx = site % 64

        for (dy in -half until half) {
            val yy = (sy + dy) and 63
            val row = yy * 64
            val krow = (dy and 63) * 64

            for (dx in -half until half) {
                val idx = row + ((sx + dx) and 63)
                energy[idx] = energy[idx] + sign * kernel[krow + (dx and 63)]
            }
        }
    }

    private fun extreme(pattern: BooleanArray, energy: DoubleArray, wantOne: Boolean): Int {
        var best = -1
        var bestE = if (wantOne) -Double.MAX_VALUE else Double.MAX_VALUE

        for (i in 0 until total) {
            if (pattern[i] == wantOne) {
                val e = energy[i]
                if (wantOne) {
                    if (e > bestE) { bestE = e; best = i }
                } else {
                    if (e < bestE) { bestE = e; best = i }
                }
            }
        }
        return best
    }

    @JvmStatic
    fun threshold(x: Int, y: Int): Double =
        rank[y and 63][x and 63].toDouble() / total.toDouble()
}
