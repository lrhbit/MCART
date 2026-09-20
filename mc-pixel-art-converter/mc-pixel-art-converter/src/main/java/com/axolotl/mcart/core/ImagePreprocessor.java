package com.axolotl.mcart.core;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 图像预处理引擎：在颜色量化前对缩放后的图像做背景填充与亮度/对比度/饱和度调整。
 *
 * <p>行为对齐 mapartcraft（mapPreview.js 的 updateCanvas_source）：</p>
 * <ol>
 *   <li>背景在「无滤镜」状态下铺设，因此背景色本身不被亮度/对比度/饱和度改变；</li>
 *   <li>随后对源图应用 CSS 滤镜链 brightness → contrast → saturate；</li>
 *   <li>源图按 alpha 与背景做 source-over 合成（不透明图片的背景不可见）。</li>
 * </ol>
 *
 * <p>「平滑」背景取当前已选方块在当前模式下所有可用 shade 的实际显示色中，
 * 与用户指定颜色 RGB 欧氏距离最近的一个（平面模式调色板仅含 normal shade，
 * 立体模式含 dark/normal/light，与 mapartcraft 按 staircasing 选 tone 一致）。</p>
 */
public final class ImagePreprocessor {

    private ImagePreprocessor() {
    }

    /**
     * 将（已缩放到目标分辨率的）图像转换为量化输入像素。
     *
     * @param scaled  缩放后的图像；背景模式非 OFF 时应带 alpha（TYPE_INT_ARGB）
     * @param set     预处理设置
     * @param palette 当前已选方块展开 shade 后的实际调色板（用于「平滑」背景取色）
     * @return pixels[y][x] = 0xRRGGBB
     */
    public static int[][] toPixels(BufferedImage scaled, PreprocessSettings set,
                                   List<ColorMatcher.PaletteEntry> palette) {
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        int[][] out = new int[h][w];
        if (set == null || !set.enabled) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out[y][x] = scaled.getRGB(x, y) & 0xFFFFFF;
                }
            }
            return out;
        }

        final boolean hasBg = set.backgroundMode != PreprocessSettings.BackgroundMode.OFF;
        int bg = set.backgroundRgb;
        if (set.backgroundMode == PreprocessSettings.BackgroundMode.SMOOTH) {
            bg = closestPaletteColor(bg, palette);
        }
        int bgR = (bg >> 16) & 0xFF, bgG = (bg >> 8) & 0xFF, bgB = bg & 0xFF;

        float bk = set.brightness / 100f;
        float ck = set.contrast / 100f;
        float sk = set.saturation / 100f;
        float contrastOffset = 127.5f * (1f - ck);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = scaled.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int sr = (argb >> 16) & 0xFF, sg = (argb >> 8) & 0xFF, sb = argb & 0xFF;

                // —— CSS 滤镜链（作用于源图，sRGB 0..255）——
                // brightness
                float r1 = sr * bk, g1 = sg * bk, b1 = sb * bk;
                // contrast
                float r2 = r1 * ck + contrastOffset;
                float g2 = g1 * ck + contrastOffset;
                float b2 = b1 * ck + contrastOffset;
                // saturate（Rec709 luma）
                float luma = 0.2126f * r2 + 0.7152f * g2 + 0.0722f * b2;
                float fr = luma + sk * (r2 - luma);
                float fg = luma + sk * (g2 - luma);
                float fb = luma + sk * (b2 - luma);

                int rr, gg, bb;
                if (hasBg) {
                    // 滤镜后的源图按 alpha 合成到未滤镜的背景上
                    float af = a / 255f;
                    rr = Math.round(fr * af + bgR * (1f - af));
                    gg = Math.round(fg * af + bgG * (1f - af));
                    bb = Math.round(fb * af + bgB * (1f - af));
                } else {
                    rr = Math.round(fr);
                    gg = Math.round(fg);
                    bb = Math.round(fb);
                }
                out[y][x] = (clamp(rr) << 16) | (clamp(gg) << 8) | clamp(bb);
            }
        }
        return out;
    }

    /** 在调色板实际显示色（含 shade）中找 RGB 欧氏最近色 */
    private static int closestPaletteColor(int target, List<ColorMatcher.PaletteEntry> palette) {
        int tr = (target >> 16) & 0xFF, tg = (target >> 8) & 0xFF, tb = target & 0xFF;
        int best = target;
        long bestD = Long.MAX_VALUE;
        if (palette != null) {
            for (ColorMatcher.PaletteEntry e : palette) {
                int dr = e.r - tr, dg = e.g - tg, db = e.b - tb;
                long d = (long) dr * dr + (long) dg * dg + (long) db * db;
                if (d < bestD) {
                    bestD = d;
                    best = (e.r << 16) | (e.g << 8) | e.b;
                }
            }
        }
        return best;
    }

    private static int clamp(float v) {
        int i = Math.round(v);
        return Math.max(0, Math.min(255, i));
    }
}
