package com.axolotl.mcart.core;

import java.util.Arrays;
import java.util.Random;

/**
 * 蓝噪声阈值矩阵（Blue Noise Dither Array）。
 *
 * 采用 Ulichney 的 void-and-cluster 方法在类加载时一次性生成
 * 64×64、可环形平铺（toroidal）的 rank 矩阵，取值 0..4095 的均匀排列。
 *
 * 相比 Bayer 有序抖动，蓝噪声把量化误差分散到高频且无规则周期的位置，
 * 远看（眼睛对相邻像素做平均）更自然，不会出现规则网格纹；
 * 环形高斯核保证 64×64 平铺时边界无缝。
 *
 * 用法：threshold(y,x) = RANK[y &amp; 63][x &amp; 63] / 4096.0，范围 [0,1)。
 */
public final class BlueNoise {

    public static final int N = 64;
    private static final int TOTAL = N * N;
    private static final int HALF = N / 2;

    /** rank 矩阵：每个值 0..4095 恰好出现一次 */
    public static final int[][] RANK = new int[N][N];

    static {
        generate();
    }

    private BlueNoise() {}

    private static void generate() {
        // 环形高斯能量核（toroidal），位移取 -32..31，sigma≈1.9 像素
        final double sigma = 1.9;
        double[] kernel = new double[TOTAL];
        for (int dy = -HALF; dy < HALF; dy++) {
            for (int dx = -HALF; dx < HALF; dx++) {
                double d2 = dx * dx + dy * dy;
                kernel[(dy & (N - 1)) * N + (dx & (N - 1))] =
                        Math.exp(-d2 / (2.0 * sigma * sigma));
            }
        }

        // 1. 固定种子随机二元图案，初始密度 10%
        boolean[] pattern = new boolean[TOTAL];
        Random rnd = new Random(0x9E3779B97F4A7C15L);
        int[] order = new int[TOTAL];
        for (int i = 0; i < TOTAL; i++) order[i] = i;
        for (int i = TOTAL - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = order[i]; order[i] = order[j]; order[j] = t;
        }
        int m = TOTAL / 10;
        double[] energy = new double[TOTAL];
        for (int i = 0; i < m; i++) {
            pattern[order[i]] = true;
            bump(energy, kernel, order[i], +1.0);
        }

        // 2. homogenize：移除最密点 → 在最疏空位补点；
        //    若移除后原位本身就是最疏位置（没有更该去的空位），放回并结束。
        while (true) {
            int tightest = extreme(pattern, energy, true);
            double tightEnergy = energy[tightest];
            pattern[tightest] = false;
            bump(energy, kernel, tightest, -1.0);

            int loosest = extreme(pattern, energy, false);
            double looseEnergy = energy[loosest];
            if (loosest == tightest || tightEnergy <= looseEnergy) {
                pattern[tightest] = true;
                bump(energy, kernel, tightest, +1.0);
                break;
            }
            pattern[loosest] = true;
            bump(energy, kernel, loosest, +1.0);
        }

        boolean[] homogenized = pattern.clone();
        int[] rank = new int[TOTAL];
        Arrays.fill(rank, -1);

        // 3a. 从均匀化图案中逐个移除最密的 1，分配 rank m-1 … 0
        int ones = m;
        while (ones > 0) {
            int tightest = extreme(pattern, energy, true);
            rank[tightest] = ones - 1;
            pattern[tightest] = false;
            bump(energy, kernel, tightest, -1.0);
            ones--;
        }

        // 3b. 从均匀化图案出发，逐个在最疏空位加点，分配 rank m … TOTAL-1
        pattern = homogenized.clone();
        Arrays.fill(energy, 0.0);
        for (int i = 0; i < TOTAL; i++) if (pattern[i]) bump(energy, kernel, i, +1.0);
        for (int rv = m; rv < TOTAL; rv++) {
            int loosest = extreme(pattern, energy, false);
            rank[loosest] = rv;
            pattern[loosest] = true;
            bump(energy, kernel, loosest, +1.0);
        }

        // 校验 rank 完整（0..TOTAL-1 各一次）
        boolean[] seen = new boolean[TOTAL];
        for (int r : rank) {
            if (r < 0 || r >= TOTAL || seen[r]) {
                throw new IllegalStateException("蓝噪声矩阵生成异常");
            }
            seen[r] = true;
        }

        for (int y = 0; y < N; y++) {
            System.arraycopy(rank, y * N, RANK[y], 0, N);
        }
    }

    /** site 处增加/移除一个点对所有位置能量的贡献（环形位移） */
    private static void bump(double[] energy, double[] kernel, int site, double sign) {
        int sy = site / N, sx = site % N;
        for (int dy = -HALF; dy < HALF; dy++) {
            int yy = (sy + dy) & (N - 1);
            int row = yy * N;
            int krow = (dy & (N - 1)) * N;
            for (int dx = -HALF; dx < HALF; dx++) {
                energy[row + ((sx + dx) & (N - 1))] +=
                        sign * kernel[krow + (dx & (N - 1))];
            }
        }
    }

    /** wantOne=true 找能量最大的 1（最密 cluster）；false 找能量最小的 0（最疏 void） */
    private static int extreme(boolean[] pattern, double[] energy, boolean wantOne) {
        int best = -1;
        double bestE = wantOne ? -Double.MAX_VALUE : Double.MAX_VALUE;
        for (int i = 0; i < TOTAL; i++) {
            if (pattern[i] != wantOne) continue;
            if (wantOne ? energy[i] > bestE : energy[i] < bestE) {
                bestE = energy[i];
                best = i;
            }
        }
        return best;
    }

    /** 阈值 [0,1) */
    public static double threshold(int x, int y) {
        return RANK[y & (N - 1)][x & (N - 1)] / (double) TOTAL;
    }
}
