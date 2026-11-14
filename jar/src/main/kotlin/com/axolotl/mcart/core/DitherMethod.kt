package com.axolotl.mcart.core

/**
 * 抖动方法。
 * label 供界面下拉框直接显示（[toString] 返回 label）。
 */
enum class DitherMethod(val label: String) {
    NONE("原始放缩"),
    BLUE_NOISE("蓝噪声混合"),
    FLOYD_STEINBERG("Floyd-Steinberg"),
    BURKES("Burkes"),
    ATKINSON("Atkinson"),
    STUCKI("Stucki"),
    JARVIS("Jarvis-Judice-Ninke"),
    SIERRA("Sierra"),
    ORDERED("有序抖动");

    override fun toString(): String = label
}
