package com.axolotl.mcart.core;

import com.axolotl.mcart.model.MapMode;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 像素画生成器：将输入图片转换为 MC 方块布局。
 *
 * 立体（RELIEF）模式严格依据 Minecraft 地图物品的 shade 机制：
 * <ul>
 *   <li>地图只画每一列最高的非透明方块；</li>
 *   <li>该方块的 shade 由它与北侧（z-1）邻居顶层方块的高度差决定：
 *       低→shade 0（×180），等高→shade 1（×220），高→shade 2（×255）；</li>
 *   <li>最北沿（z=0）北侧没有方块（视为空气），恒为 shade 2；</li>
 *   <li>高度沿 z 方向逐列积分：ΔH = shade - 1（-1/0/+1），形成自然的阶梯地形，
 *       错落有致，整体高度由图像内容决定；</li>
 *   <li>顶层为显示方块，其下方全部用支撑方块填充（地图不可见）。</li>
 * </ul>
 * 平面（FLAT）模式所有方块等高，地图恒显示 shade 1。
 */
public class PixelArtGenerator {

    /** blockIds 中表示空气 */
    public static final int AIR = -1;
    /** blockIds 中表示支撑方块（顶层显示方块的下方填充） */
    public static final int SUPPORT = -2;

    /**
     * 生成结果
     */
    public static class Result {
        public final int width;          // X方向方块数
        public final int height;         // Y方向方块数（立体模式下>1）
        public final int length;         // Z方向方块数
        public final int[][][] blockIds; // [y][z][x] → 显示调色板索引，AIR(-1)=空气，SUPPORT(-2)=支撑方块
        public final int[][] previewPixels;   // 地图实际显示RGB [z][x]（已含shade）
        public final int[][] heightMap;  // 每列方块层数 [z][x]
        public final ColorMatcher.PaletteEntry[][] topEntries; // 每列顶层显示条目
        public final List<ColorMatcher.PaletteEntry> palette;  // 实际使用的显示方块调色板
        public final String supportBlockId;                    // 支撑方块ID

        public Result(int width, int height, int length,
                      int[][][] blockIds, int[][] previewPixels, int[][] heightMap,
                      ColorMatcher.PaletteEntry[][] topEntries,
                      List<ColorMatcher.PaletteEntry> palette,
                      String supportBlockId) {
            this.width = width;
            this.height = height;
            this.length = length;
            this.blockIds = blockIds;
            this.previewPixels = previewPixels;
            this.heightMap = heightMap;
            this.topEntries = topEntries;
            this.palette = palette;
            this.supportBlockId = supportBlockId;
        }
    }

    private final ColorMatcher matcher;
    private final DitherMethod ditherMethod;
    private final MapMode mode;
    private final String supportBlockId;
    private final PreprocessSettings preprocess;

    public PixelArtGenerator(ColorMatcher matcher, DitherMethod ditherMethod, MapMode mode) {
        this(matcher, ditherMethod, mode, "minecraft:stone", null);
    }

    public PixelArtGenerator(ColorMatcher matcher, DitherMethod ditherMethod,
                             MapMode mode, String supportBlockId) {
        this(matcher, ditherMethod, mode, supportBlockId, null);
    }

    public PixelArtGenerator(ColorMatcher matcher, DitherMethod ditherMethod,
                             MapMode mode, String supportBlockId, PreprocessSettings preprocess) {
        this.matcher = matcher;
        this.ditherMethod = ditherMethod == null ? DitherMethod.FLOYD_STEINBERG : ditherMethod;
        this.mode = mode;
        this.supportBlockId = supportBlockId == null ? "minecraft:stone" : supportBlockId;
        this.preprocess = preprocess == null ? PreprocessSettings.disabled() : preprocess;
    }

    /**
     * 生成像素画（高度按原图宽高比自动计算；目标比例与原图一致，拉伸不变形）。
     * @param source 源图片（RGB）
     * @param targetWidth 目标宽度（方块数）
     */
    public Result generate(BufferedImage source, int targetWidth) {
        int targetHeight = Math.max(1,
                Math.round((float) source.getHeight() / source.getWidth() * targetWidth));
        return generate(source, targetWidth, targetHeight);
    }

    /**
     * 生成像素画（严格按用户设置的地图张数比例输出）。
     *
     * 输出尺寸固定为 {@code targetWidth × targetHeight}（分别 = 地图宽张数×128、
     * 高张数×128），因此生成/预览/导出的宽高比严格等于用户设置的张数比
     * （例如 2×2 必为 1:1 正方形）。原图直接<b>拉伸缩放</b>到该尺寸、完整保留全部内容，
     * 不做裁剪；当原图宽高比与张数比不一致时画面会相应拉伸（这是用户明确选择的行为）。
     *
     * @param source 源图片（RGB）
     * @param targetWidth 目标宽度（方块数）= 地图宽张数 × 128
     * @param targetHeight 目标高度（方块数）= 地图高张数 × 128
     */
    public Result generate(BufferedImage source, int targetWidth, int targetHeight) {
        targetWidth = Math.max(1, targetWidth);
        targetHeight = Math.max(1, targetHeight);

        // 1. 高质量双线性拉伸缩放到目标方块分辨率（完整保留原图，不裁剪，允许拉伸）。
        //    启用预处理且需要铺背景时保留 alpha 通道（供透明区域与背景合成），
        //    否则沿用 TYPE_INT_RGB。
        boolean argb = preprocess.enabled
                && preprocess.backgroundMode != PreprocessSettings.BackgroundMode.OFF;
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight,
                argb ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        // 1b. 图像预处理（背景填充 + 亮度/对比度/饱和度），关闭时为恒等映射
        int[][] pixels = ImagePreprocessor.toPixels(scaled, preprocess, matcher.getPalette());

        // 2. 抖动 + 颜色量化（直接得到含 shade 的方块条目）
        ColorMatcher.PaletteEntry[][] matched = DitheringEngine.quantize(
                pixels, targetWidth, targetHeight, matcher, ditherMethod);

        // 3. 构建方块布局
        return mode == MapMode.RELIEF
                ? buildRelief(matched, targetWidth, targetHeight)
                : buildFlat(matched, targetWidth, targetHeight);
    }

    /** 平面：单层，全部 shade 1 */
    private Result buildFlat(ColorMatcher.PaletteEntry[][] matched, int width, int rows) {
        List<ColorMatcher.PaletteEntry> used = new ArrayList<>();
        int[] remap = new int[matcher.getPaletteSize()];
        java.util.Arrays.fill(remap, -1);

        int[][][] data = new int[1][rows][width];
        int[][] preview = new int[rows][width];
        int[][] heightMap = new int[rows][width];

        for (int z = 0; z < rows; z++) {
            for (int x = 0; x < width; x++) {
                ColorMatcher.PaletteEntry e = matched[z][x];
                int idx = register(used, remap, e);
                data[0][z][x] = idx;
                preview[z][x] = (e.r << 16) | (e.g << 8) | e.b;
                heightMap[z][x] = 1;
            }
        }
        return new Result(width, 1, rows, data, preview, heightMap, matched, used, supportBlockId);
    }

    /**
     * 立体：shade → 高度积分 → 顶层显示方块 + 下方支撑填充。
     */
    private Result buildRelief(ColorMatcher.PaletteEntry[][] matched, int width, int rows) {
        // 最北沿（z=0）在游戏中恒为 shade 2（北侧是地图外空气）。
        // 保持量化基色不变，只把 shade 修正为 2，保证预览与游戏内一致。
        for (int x = 0; x < width; x++) {
            ColorMatcher.PaletteEntry e = matched[0][x];
            if (e.shade != 2 && !e.baseColor.isWater()) {
                matched[0][x] = matcher.require(e.baseColor, 2);
            }
        }

        // 1. 沿 z 方向逐列积分高度：ΔH = shade - 1；水不参与高度变化（ΔH=0）
        int[][] topY = new int[rows][width];
        int globalMin = Integer.MAX_VALUE, globalMax = Integer.MIN_VALUE;
        for (int x = 0; x < width; x++) {
            int h = 0;
            int[] raw = new int[rows];
            raw[0] = 0;
            for (int z = 1; z < rows; z++) {
                ColorMatcher.PaletteEntry e = matched[z][x];
                int delta = e.baseColor.isWater() ? 0 : e.shade - 1;
                h += delta;
                raw[z] = h;
            }
            int colMin = Integer.MAX_VALUE, colMax = Integer.MIN_VALUE;
            for (int z = 0; z < rows; z++) {
                colMin = Math.min(colMin, raw[z]);
                colMax = Math.max(colMax, raw[z]);
            }
            // 每列独立下沉到 0 起步（列与列之间高度不要求连续，各自积分即可）
            for (int z = 0; z < rows; z++) {
                topY[z][x] = raw[z] - colMin;
            }
            globalMin = 0;
            globalMax = Math.max(globalMax, colMax - colMin);
        }

        int totalH = globalMax + 1;

        List<ColorMatcher.PaletteEntry> used = new ArrayList<>();
        int[] remap = new int[matcher.getPaletteSize()];
        java.util.Arrays.fill(remap, -1);

        int[][][] data = new int[totalH][rows][width];
        for (int y = 0; y < totalH; y++) {
            for (int z = 0; z < rows; z++) {
                java.util.Arrays.fill(data[y][z], AIR);
            }
        }
        int[][] preview = new int[rows][width];
        int[][] heightMap = new int[rows][width];

        for (int z = 0; z < rows; z++) {
            for (int x = 0; x < width; x++) {
                ColorMatcher.PaletteEntry e = matched[z][x];
                int idx = register(used, remap, e);
                int top = topY[z][x];
                data[top][z][x] = idx;          // 顶层：显示方块
                for (int y = 0; y < top; y++) {
                    data[y][z][x] = SUPPORT;    // 下方：支撑方块
                }
                preview[z][x] = (e.r << 16) | (e.g << 8) | e.b;
                heightMap[z][x] = top + 1;
            }
        }
        return new Result(width, totalH, rows, data, preview, heightMap, matched, used, supportBlockId);
    }

    private int register(List<ColorMatcher.PaletteEntry> used, int[] remap,
                         ColorMatcher.PaletteEntry e) {
        int oldIdx = matcher.getPalette().indexOf(e);
        if (oldIdx < 0) {
            // 来自 require(...) 的等价条目：按 方块+shade 去重
            for (int i = 0; i < used.size(); i++) {
                ColorMatcher.PaletteEntry u = used.get(i);
                if (u.baseColor == e.baseColor && u.shade == e.shade) return i;
            }
            used.add(e);
            return used.size() - 1;
        }
        if (remap[oldIdx] < 0) {
            remap[oldIdx] = used.size();
            used.add(e);
        }
        return remap[oldIdx];
    }
}
