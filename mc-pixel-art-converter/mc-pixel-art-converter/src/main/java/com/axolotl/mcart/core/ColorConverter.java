package com.axolotl.mcart.core;

/**
 * 颜色空间转换工具：sRGB → CIELAB
 * 全流程直接使用 sRGB，不再经过 CMYK（CMYK 色域更窄，会丢失鲜艳颜色）。
 * 在人眼感知均匀的 LAB 空间做颜色匹配。
 */
public final class ColorConverter {

    private ColorConverter() {}

    /** D65 标准光源 */
    private static final double Xn = 95.047;
    private static final double Yn = 100.000;
    private static final double Zn = 108.883;

    /**
     * sRGB (0-255) → CIELAB (L*: 0-100, a*: -128~127, b*: -128~127)
     */
    public static double[] rgbToLab(int r, int g, int b) {
        // sRGB → 线性RGB
        double rs = r / 255.0;
        double gs = g / 255.0;
        double bs = b / 255.0;

        double rLinear = (rs > 0.04045) ? Math.pow((rs + 0.055) / 1.055, 2.4) : rs / 12.92;
        double gLinear = (gs > 0.04045) ? Math.pow((gs + 0.055) / 1.055, 2.4) : gs / 12.92;
        double bLinear = (bs > 0.04045) ? Math.pow((bs + 0.055) / 1.055, 2.4) : bs / 12.92;

        // 线性RGB → XYZ
        double x = rLinear * 0.4124564 + gLinear * 0.3575761 + bLinear * 0.1804375;
        double y = rLinear * 0.2126729 + gLinear * 0.7151522 + bLinear * 0.0721750;
        double z = rLinear * 0.0193339 + gLinear * 0.1191920 + bLinear * 0.9503041;

        // XYZ → LAB
        x *= 100; y *= 100; z *= 100;

        double fx = f(x / Xn);
        double fy = f(y / Yn);
        double fz = f(z / Zn);

        double L = 116 * fy - 16;
        double a = 500 * (fx - fy);
        double bb = 200 * (fy - fz);

        return new double[]{L, a, bb};
    }

    private static double f(double t) {
        final double delta = 6.0 / 29.0;
        if (t > Math.pow(delta, 3)) {
            return Math.cbrt(t);
        } else {
            return t / (3 * delta * delta) + 4.0 / 29.0;
        }
    }

    // ============================================================
    // OKLab（Björn Ottosson, 2020）：比 CIELAB 更符合人眼感知，
    // 供「蓝噪声混合」抖动在感知均匀空间内做最近色与误差扩散。
    // L ∈ [0,1]，a/b 大致 ∈ [-0.5,0.5]。
    // ============================================================

    /** sRGB 分量(0~255) → 线性光 RGB(0~1) */
    private static double srgbToLinear(int c) {
        double s = c / 255.0;
        return s > 0.04045 ? Math.pow((s + 0.055) / 1.055, 2.4) : s / 12.92;
    }

    /** sRGB (0-255) → OKLab，返回 [L, a, b] */
    public static double[] rgbToOklab(int r, int g, int b) {
        double R = srgbToLinear(r);
        double G = srgbToLinear(g);
        double B = srgbToLinear(b);

        double l = 0.4122214708 * R + 0.5363325363 * G + 0.0514459929 * B;
        double m = 0.2119034982 * R + 0.6806995451 * G + 0.1073969566 * B;
        double s = 0.0883024619 * R + 0.2817188376 * G + 0.6299787005 * B;

        double lc = Math.cbrt(l);
        double mc = Math.cbrt(m);
        double sc = Math.cbrt(s);

        return new double[]{
                0.2104542553 * lc + 0.7936177850 * mc - 0.0040720468 * sc,
                1.9779984951 * lc - 2.4285922050 * mc + 0.4505937099 * sc,
                0.0259040371 * lc + 0.7827717662 * mc - 0.8086757660 * sc
        };
    }

    /**
     * 计算两个LAB颜色的 Delta E (CIE76) 欧氏距离
     * 值越小颜色越接近
     */
    public static double deltaE(double[] lab1, double[] lab2) {
        double dl = lab1[0] - lab2[0];
        double da = lab1[1] - lab2[1];
        double db = lab1[2] - lab2[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }
}
