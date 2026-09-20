package com.axolotl.mcart.core;

import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.model.MapMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 颜色匹配引擎：将任意RGB颜色匹配到最接近的MC地图颜色。
 * 使用 CIELAB Delta E 距离，人眼感知最准确。
 *
 * 调色板按地图画模式展开 shade 变体：
 * - FLAT 平面：所有方块等高，地图只显示 shade 1（×220），故每色仅 1 个变体；
 * - RELIEF 立体：每色展开 shade 0/1/2（×180/×220/×255），由高度差产生；
 *   shade 3（×135）生存不可达，不使用；水的 shade 由水深决定，固定只给 shade 1。
 */
public class ColorMatcher {

    /**
     * 调色板条目：基色方块 + shade 变体 + 实际地图显示RGB
     */
    public static class PaletteEntry {
        public final BlockColor baseColor;
        public final int shade; // 0/1/2
        public final int r, g, b;
        public final double[] lab;

        public PaletteEntry(BlockColor base, int shade) {
            this.baseColor = base;
            this.shade = shade;
            int[] rgb = base.getActualRgb(shade);
            this.r = rgb[0];
            this.g = rgb[1];
            this.b = rgb[2];
            this.lab = ColorConverter.rgbToLab(r, g, b);
        }
    }

    private final List<PaletteEntry> palette;
    private final Map<String, PaletteEntry> byKey = new HashMap<>();

    public ColorMatcher(List<BlockColor> availableBlocks) {
        this(availableBlocks, MapMode.FLAT);
    }

    public ColorMatcher(List<BlockColor> availableBlocks, MapMode mode) {
        this.palette = expand(availableBlocks, mode);
        for (PaletteEntry e : palette) {
            byKey.put(key(e.baseColor, e.shade), e);
        }
    }

    private static String key(BlockColor bc, int shade) {
        return bc.blockId() + "#" + shade;
    }

    /**
     * 按模式展开 shade 变体
     */
    private List<PaletteEntry> expand(List<BlockColor> blocks, MapMode mode) {
        List<PaletteEntry> result = new ArrayList<>();
        for (BlockColor bc : blocks) {
            if (mode == MapMode.RELIEF) {
                if (bc.isWater()) {
                    result.add(new PaletteEntry(bc, 1)); // 水：固定普通shade
                } else {
                    result.add(new PaletteEntry(bc, 0));
                    result.add(new PaletteEntry(bc, 1));
                    result.add(new PaletteEntry(bc, 2));
                }
            } else {
                // 平面：全部等高，只有 shade 1
                result.add(new PaletteEntry(bc, 1));
            }
        }
        return result;
    }

    /**
     * 匹配最近的调色板颜色
     */
    public PaletteEntry match(int r, int g, int b) {
        double[] targetLab = ColorConverter.rgbToLab(r, g, b);
        PaletteEntry best = null;
        double bestDist = Double.MAX_VALUE;

        for (PaletteEntry entry : palette) {
            double dist = ColorConverter.deltaE(targetLab, entry.lab);
            if (dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }
        return best;
    }

    /** 取指定基色方块的某个 shade 条目（若该 shade 不在调色板中，回退到最接近的可用 shade） */
    public PaletteEntry require(BlockColor bc, int wantedShade) {
        PaletteEntry e = byKey.get(key(bc, wantedShade));
        if (e != null) return e;
        for (int s : new int[]{wantedShade, 2, 1, 0}) {
            e = byKey.get(key(bc, s));
            if (e != null) return e;
        }
        throw new IllegalStateException("调色板中缺少方块 " + bc.blockId());
    }

    public List<PaletteEntry> getPalette() {
        return palette;
    }

    public int getPaletteSize() {
        return palette.size();
    }
}
