package com.axolotl.mcart.model;

/**
 * 方块颜色条目：一个MC方块在地图上显示的基色（MapColor base color）。
 */
public record BlockColor(
        int colorId,        // MC地图颜色ID (0-61+)
        String name,        // 颜色名称
        int r, int g, int b,// 基色RGB (0-255)，对应地图 shade=2（最亮）
        String blockId,     // 对应方块ID，如 minecraft:white_wool
        Category category   // 方块类别
) {
    public enum Category {
        WOOL("羊毛"),
        CONCRETE("混凝土"),
        TERRACOTTA("陶瓦"),
        GLAZED_TERRACOTTA("带釉陶瓦"),
        STAINED_GLASS("染色玻璃"),
        NATURAL("自然方块"),
        MINERAL("矿物块"),
        SPECIAL("特殊方块");

        public final String label;
        Category(String label) { this.label = label; }
    }

    /**
     * MC 地图四个 shade 的亮度系数（/255）：
     *   shade 0 = 180（比北侧邻居低，暗）
     *   shade 1 = 220（与北侧邻居等高，普通）
     *   shade 2 = 255（比北侧邻居高，最亮，即基色本身）
     *   shade 3 = 135（无天空光照，生存不可达）
     */
    public static final int[] SHADE_FACTORS = {180, 220, 255, 135};

    /** 水：shade 不由高度差决定，而由水深决定，立体高度映射时只使用 shade 1 */
    public boolean isWater() {
        return "minecraft:water".equals(blockId);
    }

    /**
     * 获取指定 shade 下地图实际显示的 RGB。
     * MC 源码逐通道向下取整：floor(c * factor / 255)。
     * @param shade 0-3
     */
    public int[] getActualRgb(int shade) {
        int f = SHADE_FACTORS[shade & 3];
        return new int[]{
                (r * f) / 255,
                (g * f) / 255,
                (b * f) / 255
        };
    }

    /**
     * 获取带亮度变体的完整方块状态ID。
     * 亮度变化只影响地图显示，方块本身不变，直接返回基础 blockId。
     */
    public String getBlockStateId(int shade) {
        return blockId;
    }
}
