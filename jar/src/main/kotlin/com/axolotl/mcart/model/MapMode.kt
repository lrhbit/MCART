package com.axolotl.mcart.model

/**
 * 地图画模式
 */
enum class MapMode(val label: String) {
    /** 平面模式：单层方块，贴墙或贴地 */
    FLAT("平面地图画"),

    /** 立体模式：按亮度映射方块高度，产生浮雕效果 */
    RELIEF("立体地图画"),
}
