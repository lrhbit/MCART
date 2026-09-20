package com.axolotl.mcart.core;

/**
 * 抖动（量化）算法枚举。
 *
 * 下拉列表顺序即声明顺序：
 * <ol>
 *   <li>原始放缩：不抖动，直接最近色；</li>
 *   <li>蓝噪声混合：OKLab 双色混合 + 蓝噪声 + 残差扩散，浅色层次最好（推荐）；</li>
 *   <li>其余为经典误差扩散家族（扩散范围依次增大，颗粒/拖尾表现各不同）；</li>
 *   <li>有序抖动：Bayer 规则网点。</li>
 * </ol>
 */
public enum DitherMethod {
    NONE("原始放缩"),
    BLUE_NOISE("蓝噪声混合"),
    FLOYD_STEINBERG("Floyd-Steinberg"),
    BURKES("Burkes"),
    ATKINSON("Atkinson"),
    STUCKI("Stucki"),
    JARVIS("Jarvis-Judice-Ninke"),
    SIERRA("Sierra"),
    ORDERED("有序抖动");

    public final String label;
    DitherMethod(String label) { this.label = label; }

    @Override public String toString() { return label; }
}
