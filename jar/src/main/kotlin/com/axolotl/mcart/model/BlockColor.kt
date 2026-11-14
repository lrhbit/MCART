package com.axolotl.mcart.model

import com.axolotl.mcart.i18n.I18n

/**
 * 方块颜色条目：一个 MC 方块在地图上显示的基色（MapColor base color）。
 *
 * 使用 data class：自动获得结构相等、复制、解构与字符串表示；
 * 属性通过 @get:JvmName 保留原 record 风格的访问器，Java 侧无需改动。
 */
data class BlockColor(
    /** MC 地图颜色 ID (0-61+) */
    @get:JvmName("colorId") val colorId: Int,
    /** 颜色名称 */
    @get:JvmName("name") val name: String,
    /** 基色 RGB (0-255)，对应地图 shade=2（最亮） */
    @get:JvmName("r") val r: Int,
    @get:JvmName("g") val g: Int,
    @get:JvmName("b") val b: Int,
    /** 对应方块 ID，如 minecraft:white_wool */
    @get:JvmName("blockId") val blockId: String,
    /** 方块类别 */
    @get:JvmName("category") val category: Category,
) {
    /** 方块类别 */
    enum class Category(val label: String, val labelEn: String) {
        WOOL("羊毛", "Wool"),
        CONCRETE("混凝土", "Concrete"),
        TERRACOTTA("陶瓦", "Terracotta"),
        GLAZED_TERRACOTTA("带釉陶瓦", "Glazed Terracotta"),
        STAINED_GLASS("染色玻璃", "Stained Glass"),
        NATURAL("自然方块", "Natural"),
        MINERAL("矿物块", "Mineral Blocks"),
        SPECIAL("特殊方块", "Special"),
        ;

        /** 按当前界面语言返回分类名称 */
        fun displayName(): String = if (I18n.isEn()) labelEn else label
    }

    /** 水：shade 不由高度差决定，而由水深决定，立体高度映射时只使用 shade 1 */
    fun isWater(): Boolean = blockId == "minecraft:water"

    /**
     * 获取指定 shade 下地图实际显示的 RGB。
     * MC 源码逐通道向下取整：floor(c * factor / 255)。
     * @param shade 0-3
     */
    fun getActualRgb(shade: Int): IntArray {
        val f = SHADE_FACTORS[shade and 3]
        return intArrayOf(r * f / 255, g * f / 255, b * f / 255)
    }

    /**
     * 获取带亮度变体的完整方块状态 ID。
     * 亮度变化只影响地图显示，方块本身不变，直接返回基础 blockId。
     */
    fun getBlockStateId(shade: Int): String = blockId

    companion object {
        /**
         * MC 地图四个 shade 的亮度系数（/255）：
         *   shade 0 = 180（比北侧邻居低，暗）
         *   shade 1 = 220（与北侧邻居等高，普通）
         *   shade 2 = 255（比北侧邻居高，最亮，即基色本身）
         *   shade 3 = 135（无天空光照，生存不可达）
         */
        @JvmField
        val SHADE_FACTORS: IntArray = intArrayOf(180, 220, 255, 135)
    }
}
