package com.axolotl.mcart.core;

import com.axolotl.mcart.util.TextureManager;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * 贴图化预览渲染（重构版）。
 *
 * <p>优化点：
 * <ul>
 *   <li>直接写 int[] 像素缓冲（ImageIO 无关），16×16 纹理用整数缩放到 tile 尺寸并缓存；</li>
 *   <li>按行并行渲染，多核加速；</li>
 *   <li>无需中间"顶层方块"重复扫描，topY 一次遍历得到。</li>
 * </ul>
 */
public final class TexturePreviewRenderer {
    private static final int MAX_EDGE = 3072;

    private TexturePreviewRenderer() {
    }

    public static BufferedImage render(PixelArtGenerator.Result result) {
        int cols = result.width;
        int rows = result.length;
        int maxDim = Math.max(cols, rows);
        int tile = Math.max(4, Math.min(16, MAX_EDGE / Math.max(1, maxDim)));
        int outW = cols * tile;
        int outH = rows * tile;
        int renderTile = tile;
        int[] out = new int[outW * outH];
        /*
         * 顶层方块索引。
         *
         * 重构点：原实现逐列自顶向下扫描 blockIds 找第一个非空方块，
         * 立体模式下高度可达 100+ 层，而每列只有一个有效值 —— 白白扫了上百倍。
         * result.heightMap 在生成阶段已经把「顶层高度 + 1」算好了，
         * 直接用它反查即可，复杂度从 O(height×pixels) 降到 O(pixels)。
         */
        int[][] topIdx = new int[rows][cols];
        for (int z = 0; z < rows; z++) {
            int[] hm = result.heightMap[z];
            int[] rowIdx = topIdx[z];
            for (int x = 0; x < cols; x++) {
                int top = hm[x] - 1;
                rowIdx[x] = top >= 0 && top < result.height ? result.blockIds[top][z][x] : -1;
            }
        }
        // 预解析每个调色板索引对应的「已缩放纹理直拷数据」，避免格子内查表与对象访问
        int palSize = result.palette.size();
        int[][] tiles = new int[palSize][];
        for (int i = 0; i < palSize; i++) {
            BufferedImage tex = TextureManager.getScaled(result.palette.get(i).baseColor, renderTile);
            tiles[i] = tex == null ? null : ((DataBufferInt) tex.getRaster().getDataBuffer()).getData();
        }
        final int w = outW;
        // 单线程按行直写：纯内存拷贝，避免并行调度与跨核缓存同步开销
        for (int z = 0; z < rows; z++) {
            int[] rowTop = topIdx[z];
            for (int x = 0; x < cols; x++) {
                int idx = rowTop[x];
                if (idx < 0) {
                    continue;
                }
                int[] src = tiles[idx];
                if (src == null) {
                    continue;
                }
                int ox = x * renderTile;
                int oy = z * renderTile;
                for (int y = 0; y < renderTile; y++) {
                    System.arraycopy(src, y * renderTile, out, (oy + y) * w + ox, renderTile);
                }
            }
        }
        // 直接构造 TYPE_INT_RGB 像素缓冲（跳过 setRGB 的全图格式转换与拷贝）
        java.awt.image.DataBufferInt db = new java.awt.image.DataBufferInt(out, out.length);
        java.awt.image.WritableRaster raster =
                java.awt.image.Raster.createPackedRaster(db, outW, outH, outW, new int[]{0xFF0000, 0xFF00, 0xFF}, (java.awt.Point) null);
        return new BufferedImage(new java.awt.image.DirectColorModel(24, 0xFF0000, 0xFF00, 0xFF), raster, false, null);
    }
}
