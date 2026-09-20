package com.axolotl.mcart.core;

import com.axolotl.mcart.util.TextureManager;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 方块纹理预览渲染器：
 * 把生成结果的每个格子用真实MC方块顶面纹理贴出来（俯视效果）。
 * 立体模式取每一列最顶层的非空气方块。
 * 贴图尺寸自适应：格子越多每格越小，总图边长控制在约3072像素以内。
 */
public final class TexturePreviewRenderer {

    private TexturePreviewRenderer() {}

    private static final int MAX_EDGE = 3072;

    public static BufferedImage render(PixelArtGenerator.Result result) {
        int cols = result.width;
        int rows = result.length;
        int maxDim = Math.max(cols, rows);

        int tile = Math.max(4, Math.min(16, MAX_EDGE / Math.max(1, maxDim)));

        BufferedImage out = new BufferedImage(
                cols * tile, rows * tile, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_SPEED);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = topBlock(result, col, row);
                if (idx < 0) continue; // 空气：留空
                ColorMatcher.PaletteEntry entry = result.palette.get(idx);
                BufferedImage tex = TextureManager.get(entry.baseColor);
                g.drawImage(tex, col * tile, row * tile, tile, tile, null);
            }
        }
        g.dispose();
        return out;
    }

    /** 取该列最顶层的非空气方块索引 */
    private static int topBlock(PixelArtGenerator.Result result, int x, int z) {
        for (int y = result.height - 1; y >= 0; y--) {
            int idx = result.blockIds[y][z][x];
            if (idx >= 0) return idx;
        }
        return -1;
    }
}
