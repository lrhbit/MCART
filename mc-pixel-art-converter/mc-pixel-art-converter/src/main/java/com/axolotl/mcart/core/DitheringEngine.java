package com.axolotl.mcart.core;

/**
 * 抖动 / 量化引擎，支持九种方式：
 * - NONE（原始放缩）：直接最近色匹配，不做任何抖动
 * - BLUE_NOISE（蓝噪声混合）：OKLab 感知空间内双色混合 + 蓝噪声阈值 +
 *   蛇形误差扩散（衰减 0.85）+ 边缘保护，远看可混合出调色板外的中间色，
 *   浅色层次最细腻，无规则网格纹（推荐照片/渐变图使用，默认备选第二位）
 * - FLOYD_STEINBERG：经典误差扩散 7/16,3/16,5/16,1/16
 * - BURKES：误差扩散核更大（分母 32），比 FS 颗粒更细腻、横向拖尾更轻
 * - ATKINSON：扩散到 6 个邻居各 1/8，剩余 2/8 丢弃，对比更强、颗粒更明显
 * - STUCKI：三行核（分母 42），扩散范围大、过渡平滑，细节略软
 * - JARVIS（Jarvis-Judice-Ninke）：三行核（分母 48），大面积渐变非常细腻
 * - SIERRA（Sierra-3）：三行核（分母 32），兼顾 JJN 的细腻与更低的竖向拖尾
 * - ORDERED：Bayer 8×8 有序抖动，规则网点、无横向拖尾
 *
 * 其中 FS/BURKES/ATKINSON/STUCKI/JARVIS/SIERRA 共用同一套前向误差扩散实现，
 * 仅扩散核（权重与作用范围）不同。输出 PaletteEntry 网格（含匹配到的方块基色与
 * shade），全流程 RGB，不经过 CMYK。
 */
public final class DitheringEngine {

    private DitheringEngine() {}

    /** Bayer 8×8 阈值矩阵 */
    private static final int[][] BAYER_8 = {
        { 0, 32,  8, 40,  2, 34, 10, 42},
        {48, 16, 56, 24, 50, 18, 58, 26},
        {12, 44,  4, 36, 14, 46,  6, 38},
        {60, 28, 52, 20, 62, 30, 54, 22},
        { 3, 35, 11, 43,  1, 33,  9, 41},
        {51, 19, 59, 27, 49, 17, 57, 25},
        {15, 47,  7, 39, 13, 45,  5, 37},
        {63, 31, 55, 23, 61, 29, 53, 21}
    };

    /** 按指定方法量化，返回每个像素匹配到的调色板条目 */
    public static ColorMatcher.PaletteEntry[][] quantize(
            int[][] pixels, int width, int height,
            ColorMatcher matcher, DitherMethod method) {
        return switch (method) {
            case NONE            -> noDither(pixels, width, height, matcher);
            case BLUE_NOISE      -> blueNoiseHybrid(pixels, width, height, matcher);
            case FLOYD_STEINBERG -> floydSteinberg(pixels, width, height, matcher);
            case BURKES          -> burkes(pixels, width, height, matcher);
            case ATKINSON        -> atkinson(pixels, width, height, matcher);
            case STUCKI          -> stucki(pixels, width, height, matcher);
            case JARVIS          -> jarvisJudiceNinke(pixels, width, height, matcher);
            case SIERRA          -> sierra(pixels, width, height, matcher);
            case ORDERED         -> ordered(pixels, width, height, matcher);
        };
    }

    /**
     * Floyd-Steinberg 误差扩散
     *           *   7/16
     *     3/16  5/16  1/16
     */
    public static ColorMatcher.PaletteEntry[][] floydSteinberg(
            int[][] pixels, int width, int height, ColorMatcher matcher) {
        double[][] rErr = new double[height][width];
        double[][] gErr = new double[height][width];
        double[][] bErr = new double[height][width];
        ColorMatcher.PaletteEntry[][] result = new ColorMatcher.PaletteEntry[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = clamp255((int) Math.round(((pixels[y][x] >> 16) & 0xFF) + rErr[y][x]));
                int g = clamp255((int) Math.round(((pixels[y][x] >> 8) & 0xFF) + gErr[y][x]));
                int b = clamp255((int) Math.round((pixels[y][x] & 0xFF) + bErr[y][x]));

                ColorMatcher.PaletteEntry matched = matcher.match(r, g, b);
                result[y][x] = matched;

                int errR = r - matched.r, errG = g - matched.g, errB = b - matched.b;
                distribute(rErr, gErr, bErr, x + 1, y,     width, height, errR, errG, errB, 7.0 / 16.0);
                distribute(rErr, gErr, bErr, x - 1, y + 1, width, height, errR, errG, errB, 3.0 / 16.0);
                distribute(rErr, gErr, bErr, x,     y + 1, width, height, errR, errG, errB, 5.0 / 16.0);
                distribute(rErr, gErr, bErr, x + 1, y + 1, width, height, errR, errG, errB, 1.0 / 16.0);
            }
        }
        return result;
    }

    /**
     * Atkinson 误差扩散
     *           *   1/8  1/8
     *     1/8   1/8  1/8
     * 只扩散 6/8，丢弃 2/8（经典 Atkinson 为 6 个邻居各 1/8），暗部/亮部更干净
     */
    public static ColorMatcher.PaletteEntry[][] atkinson(
            int[][] pixels, int width, int height, ColorMatcher matcher) {
        double[][] rErr = new double[height][width];
        double[][] gErr = new double[height][width];
        double[][] bErr = new double[height][width];
        ColorMatcher.PaletteEntry[][] result = new ColorMatcher.PaletteEntry[height][width];
        final double w = 1.0 / 8.0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = clamp255((int) Math.round(((pixels[y][x] >> 16) & 0xFF) + rErr[y][x]));
                int g = clamp255((int) Math.round(((pixels[y][x] >> 8) & 0xFF) + gErr[y][x]));
                int b = clamp255((int) Math.round((pixels[y][x] & 0xFF) + bErr[y][x]));

                ColorMatcher.PaletteEntry matched = matcher.match(r, g, b);
                result[y][x] = matched;

                int errR = r - matched.r, errG = g - matched.g, errB = b - matched.b;
                distribute(rErr, gErr, bErr, x + 1, y,     width, height, errR, errG, errB, w);
                distribute(rErr, gErr, bErr, x + 2, y,     width, height, errR, errG, errB, w);
                distribute(rErr, gErr, bErr, x - 1, y + 1, width, height, errR, errG, errB, w);
                distribute(rErr, gErr, bErr, x,     y + 1, width, height, errR, errG, errB, w);
                distribute(rErr, gErr, bErr, x + 1, y + 1, width, height, errR, errG, errB, w);
                distribute(rErr, gErr, bErr, x,     y + 2, width, height, errR, errG, errB, w);
            }
        }
        return result;
    }

    // ============================================================
    // 通用前向误差扩散：Burkes / Stucki / Jarvis-Judice-Ninke / Sierra
    // 与 Floyd-Steinberg 同口径（sRGB 空间、从左到右从上到下扫描），仅扩散核不同。
    // 核每项为 {dx, dy, weight}，weight 已除以分母；只向右、向下扩散。
    // ============================================================

    /** Burkes 核（/32）：当前行右 8,4；下一行 x-2..2 = 2,4,8,4,2 */
    private static final double[][] BURKES_KERNEL = {
            {1, 0, 8. / 32}, {2, 0, 4. / 32},
            {-2, 1, 2. / 32}, {-1, 1, 4. / 32}, {0, 1, 8. / 32}, {1, 1, 4. / 32}, {2, 1, 2. / 32}
    };
    /** Stucki 核（/42）：三行，扩散范围大、过渡平滑 */
    private static final double[][] STUCKI_KERNEL = {
            {1, 0, 8. / 42}, {2, 0, 4. / 42},
            {-2, 1, 2. / 42}, {-1, 1, 4. / 42}, {0, 1, 8. / 42}, {1, 1, 4. / 42}, {2, 1, 2. / 42},
            {-2, 2, 1. / 42}, {-1, 2, 2. / 42}, {0, 2, 4. / 42}, {1, 2, 2. / 42}, {2, 2, 1. / 42}
    };
    /** Jarvis-Judice-Ninke 核（/48）：三行，大面积渐变最细腻 */
    private static final double[][] JARVIS_KERNEL = {
            {1, 0, 7. / 48}, {2, 0, 5. / 48},
            {-2, 1, 3. / 48}, {-1, 1, 5. / 48}, {0, 1, 7. / 48}, {1, 1, 5. / 48}, {2, 1, 3. / 48},
            {-2, 2, 1. / 48}, {-1, 2, 3. / 48}, {0, 2, 5. / 48}, {1, 2, 3. / 48}, {2, 2, 1. / 48}
    };
    /** Sierra-3 核（/32）：三行，末行仅 x-1..1，竖向拖尾更轻 */
    private static final double[][] SIERRA_KERNEL = {
            {1, 0, 5. / 32}, {2, 0, 3. / 32},
            {-2, 1, 2. / 32}, {-1, 1, 4. / 32}, {0, 1, 5. / 32}, {1, 1, 4. / 32}, {2, 1, 2. / 32},
            {-1, 2, 2. / 32}, {0, 2, 3. / 32}, {1, 2, 2. / 32}
    };

    public static ColorMatcher.PaletteEntry[][] burkes(
            int[][] pixels, int width, int height, ColorMatcher matcher) {
        return errorDiffusion(pixels, width, height, matcher, BURKES_KERNEL);
    }

    public static ColorMatcher.PaletteEntry[][] stucki(
            int[][] pixels, int width, int height, ColorMatcher matcher) {
        return errorDiffusion(pixels, width, height, matcher, STUCKI_KERNEL);
    }

    public static ColorMatcher.PaletteEntry[][] jarvisJudiceNinke(
            int[][] pixels, int width, int height, ColorMatcher matcher) {
        return errorDiffusion(pixels, width, height, matcher, JARVIS_KERNEL);
    }

    public static ColorMatcher.PaletteEntry[][] sierra(
            int[][] pixels, int width, int height, ColorMatcher matcher) {
        return errorDiffusion(pixels, width, height, matcher, SIERRA_KERNEL);
    }

    /** 通用前向误差扩散（sRGB 空间，与 Floyd-Steinberg 同口径，仅核不同） */
    private static ColorMatcher.PaletteEntry[][] errorDiffusion(
            int[][] pixels, int width, int height, ColorMatcher matcher, double[][] kernel) {
        double[][] rErr = new double[height][width];
        double[][] gErr = new double[height][width];
        double[][] bErr = new double[height][width];
        ColorMatcher.PaletteEntry[][] result = new ColorMatcher.PaletteEntry[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = clamp255((int) Math.round(((pixels[y][x] >> 16) & 0xFF) + rErr[y][x]));
                int g = clamp255((int) Math.round(((pixels[y][x] >> 8) & 0xFF) + gErr[y][x]));
                int b = clamp255((int) Math.round((pixels[y][x] & 0xFF) + bErr[y][x]));

                ColorMatcher.PaletteEntry matched = matcher.match(r, g, b);
                result[y][x] = matched;

                int errR = r - matched.r, errG = g - matched.g, errB = b - matched.b;
                for (double[] k : kernel) {
                    distribute(rErr, gErr, bErr,
                            x + (int) k[0], y + (int) k[1], width, height,
                            errR, errG, errB, k[2]);
                }
            }
        }
        return result;
    }

    /**
     * Bayer 8×8 有序抖动：用周期性阈值矩阵对像素做偏移后再匹配最近色
     */
    public static ColorMatcher.PaletteEntry[][] ordered(
            int[][] pixels, int width, int height, ColorMatcher matcher) {
        ColorMatcher.PaletteEntry[][] result = new ColorMatcher.PaletteEntry[height][width];
        final double amplitude = 36.0; // 抖动幅度（0~255 量纲）

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double t = BAYER_8[y & 7][x & 7] / 64.0 - 0.5; // [-0.5, 0.5)
                int r = clamp255((int) Math.round(((pixels[y][x] >> 16) & 0xFF) + t * amplitude));
                int g = clamp255((int) Math.round(((pixels[y][x] >> 8) & 0xFF) + t * amplitude));
                int b = clamp255((int) Math.round((pixels[y][x] & 0xFF) + t * amplitude));

                result[y][x] = matcher.match(r, g, b);
            }
        }
        return result;
    }

    /** 无抖动直接量化（最近色匹配）——即“原始放缩” */
    public static ColorMatcher.PaletteEntry[][] noDither(
            int[][] pixels, int width, int height, ColorMatcher matcher) {
        ColorMatcher.PaletteEntry[][] result = new ColorMatcher.PaletteEntry[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (pixels[y][x] >> 16) & 0xFF;
                int g = (pixels[y][x] >> 8) & 0xFF;
                int b = pixels[y][x] & 0xFF;
                result[y][x] = matcher.match(r, g, b);
            }
        }
        return result;
    }

    private static void distribute(
            double[][] rErr, double[][] gErr, double[][] bErr,
            int x, int y, int width, int height,
            int errR, int errG, int errB, double weight) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        rErr[y][x] += errR * weight;
        gErr[y][x] += errG * weight;
        bErr[y][x] += errB * weight;
    }

    // ============================================================
    // 蓝噪声混合抖动（推荐方案）
    //
    // 思路：地图画远看时相邻像素会被眼睛平均，因此不把每像素只映到最近色，
    // 而是把相邻像素当作视觉混合单元，用两种调色板颜色在空间上混合出
    // 调色板里不存在的中间色（浅色层次尤其受益）。
    //
    //   sRGB → OKLab 感知空间（色度权重 1.2，浅色区域亮度权重加大）
    //     → 每像素取最近 K 个候选色，找目标到“候选色连线”距离最小的一对 c1/c2
    //       （距离相同时优先两端都贴近目标的短色对，避免拉入远色）
    //     → 求混合比例 α，用 64×64 蓝噪声阈值在 c1/c2 间抉择（无规则网格）
    //     → 残差在 OKLab 内做蛇形 Floyd-Steinberg 扩散（衰减 0.85，抑制蠕虫纹）
    //     → 梯度大的边缘/线条处关闭混合，直接用最近色，保护细节
    // ============================================================

    private static final int BN_NEIGHBORS = 12; // 最近候选数 K
    private static final double BN_ERR_DECAY = 0.85; // 误差扩散衰减
    private static final double BN_EDGE = 0.05;      // OKLab 亮度梯度边缘阈值
    private static final double BN_ALPHA_EPS = 0.06; // 混合比例过于接近端点时退化为最近色
    // 色对评分中对“线段长度”的惩罚：点到线段距离相同时，优先选两端都贴近目标的短色对，
    // 避免拉入距离目标很远的颜色（如用“铁块+雪”跨过中性灰），实测显著降低混合偏差。
    private static final double BN_PAIR_LEN_PENALTY = 0.05;

    public static ColorMatcher.PaletteEntry[][] blueNoiseHybrid(
            int[][] pixels, int width, int height, ColorMatcher matcher) {

        java.util.List<ColorMatcher.PaletteEntry> pal = matcher.getPalette();
        int n = pal.size();

        // 调色板预计算 OKLab
        double[][] plab = new double[n][3];
        for (int i = 0; i < n; i++) {
            ColorMatcher.PaletteEntry e = pal.get(i);
            plab[i] = ColorConverter.rgbToOklab(e.r, e.g, e.b);
        }

        // 源图 → OKLab
        double[][][] src = new double[height][width][3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                src[y][x] = ColorConverter.rgbToOklab(
                        (pixels[y][x] >> 16) & 0xFF,
                        (pixels[y][x] >> 8) & 0xFF,
                        pixels[y][x] & 0xFF);
            }
        }

        ColorMatcher.PaletteEntry[][] result = new ColorMatcher.PaletteEntry[height][width];
        double[][][] err = new double[height][width][3];
        int[] cand = new int[BN_NEIGHBORS];
        double[] candDist = new double[BN_NEIGHBORS];

        for (int y = 0; y < height; y++) {
            boolean leftToRight = (y & 1) == 0; // 蛇形扫描
            for (int ii = 0; ii < width; ii++) {
                int x = leftToRight ? ii : width - 1 - ii;

                double[] s = src[y][x];
                double tL = s[0] + err[y][x][0];
                double tA = s[1] + err[y][x][1];
                double tB = s[2] + err[y][x][2];

                // 浅色区域亮度权重更高（浅色方块少，亮度偏差比色相偏差更显眼）
                double wL = tL > 0.82 ? 1.3 : 1.0;
                final double wC = 1.2;

                // 1) 最近 K 个候选色（加权 OKLab 距离，有序插入）
                java.util.Arrays.fill(candDist, Double.MAX_VALUE);
                java.util.Arrays.fill(cand, -1);
                for (int p = 0; p < n; p++) {
                    double d = wDist2(tL, tA, tB, plab[p], wL, wC);
                    if (d < candDist[BN_NEIGHBORS - 1]) {
                        int slot = BN_NEIGHBORS - 1;
                        while (slot > 0 && d < candDist[slot - 1]) {
                            candDist[slot] = candDist[slot - 1];
                            cand[slot] = cand[slot - 1];
                            slot--;
                        }
                        candDist[slot] = d;
                        cand[slot] = p;
                    }
                }
                int chosen = cand[0];

                // 2) 边缘检测（OKLab 亮度梯度）：边缘直接用最近色，不做混合
                if (!isEdge(src, x, y, width, height)) {
                    // 3) 在候选对中找目标到线段距离最小的一对，计算混合比例 α
                    double bestDist2 = Double.MAX_VALUE;
                    int bestI = -1, bestJ = -1;
                    double bestAlpha = 0;
                    for (int a = 0; a < BN_NEIGHBORS && cand[a] >= 0; a++) {
                        double[] c1 = plab[cand[a]];
                        for (int b = a + 1; b < BN_NEIGHBORS && cand[b] >= 0; b++) {
                            double[] c2 = plab[cand[b]];
                            double dL = c2[0] - c1[0], dA = c2[1] - c1[1], dB = c2[2] - c1[2];
                            double len2 = wL * dL * dL + wC * (dA * dA + dB * dB);
                            if (len2 < 1e-8) continue;
                            double vL = tL - c1[0], vA = tA - c1[1], vB = tB - c1[2];
                            double dot = wL * vL * dL + wC * (vA * dA + vB * dB);
                            double alpha = Math.max(0, Math.min(1, dot / len2));
                            double proj = Math.max(0, Math.min(len2, dot));
                            // 点到线段的垂直距离² + 线段长度惩罚（优先短色对，避免远端颜色混入）
                            double dist2 = wDist2(tL, tA, tB, c1, wL, wC)
                                    - proj * proj / len2 + BN_PAIR_LEN_PENALTY * len2;
                            if (dist2 < bestDist2) {
                                bestDist2 = dist2;
                                bestI = cand[a];
                                bestJ = cand[b];
                                bestAlpha = alpha;
                            }
                        }
                    }
                    // 4) 蓝噪声阈值抉择 c1/c2；α 接近端点则直接最近色
                    if (bestI >= 0 && bestAlpha >= BN_ALPHA_EPS && bestAlpha <= 1 - BN_ALPHA_EPS) {
                        double threshold = BlueNoise.threshold(x, y);
                        chosen = threshold < bestAlpha ? bestJ : bestI;
                    }
                }

                result[y][x] = pal.get(chosen);

                // 5) OKLab 残差蛇形 Floyd-Steinberg 扩散（7/3/5/1，衰减 0.85）
                double eL = (tL - plab[chosen][0]) * BN_ERR_DECAY;
                double eA = (tA - plab[chosen][1]) * BN_ERR_DECAY;
                double eB = (tB - plab[chosen][2]) * BN_ERR_DECAY;
                if (leftToRight) {
                    addLabErr(err, x + 1, y,     width, height, eL, eA, eB, 7.0 / 16.0);
                    addLabErr(err, x - 1, y + 1, width, height, eL, eA, eB, 3.0 / 16.0);
                    addLabErr(err, x,     y + 1, width, height, eL, eA, eB, 5.0 / 16.0);
                    addLabErr(err, x + 1, y + 1, width, height, eL, eA, eB, 1.0 / 16.0);
                } else {
                    addLabErr(err, x - 1, y,     width, height, eL, eA, eB, 7.0 / 16.0);
                    addLabErr(err, x + 1, y + 1, width, height, eL, eA, eB, 3.0 / 16.0);
                    addLabErr(err, x,     y + 1, width, height, eL, eA, eB, 5.0 / 16.0);
                    addLabErr(err, x - 1, y + 1, width, height, eL, eA, eB, 1.0 / 16.0);
                }
            }
        }
        return result;
    }

    private static double wDist2(double tL, double tA, double tB, double[] c,
                                 double wL, double wC) {
        double dl = tL - c[0], da = tA - c[1], db = tB - c[2];
        return wL * dl * dl + wC * (da * da + db * db);
    }

    private static void addLabErr(double[][][] err, int x, int y, int w, int h,
                                  double eL, double eA, double eB, double weight) {
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        err[y][x][0] += eL * weight;
        err[y][x][1] += eA * weight;
        err[y][x][2] += eB * weight;
    }

    /** 基于 OKLab 亮度的简单梯度（前后邻居差分），梯度大视为边缘/线条 */
    private static boolean isEdge(double[][][] lab, int x, int y, int w, int h) {
        double gx;
        if (x > 0 && x < w - 1) {
            gx = Math.abs(lab[y][x + 1][0] - lab[y][x - 1][0]) * 0.5;
        } else {
            gx = 0;
        }
        double gy;
        if (y > 0 && y < h - 1) {
            gy = Math.abs(lab[y + 1][x][0] - lab[y - 1][x][0]) * 0.5;
        } else {
            gy = 0;
        }
        return gx + gy > BN_EDGE;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
