package com.axolotl.mcart.core;

/**
 * 图像预处理设置（对齐 mapartcraft 的「图像预处理」面板）。
 *
 * <p>处理时机：原图高质量缩放到目标方块分辨率之后、颜色量化之前。
 * 滤镜链严格按 W3C CSS Filter Effects 在 sRGB 空间执行，顺序为
 * brightness → contrast → saturate，与 mapartcraft 的
 * {@code brightness(B%) contrast(C%) saturate(S%)} 完全一致。</p>
 *
 * <p>三个滑块范围均为 0..200、默认 100（= 不改变图像）；
 * {@link #enabled} 默认 false，因此默认情况下输出与不做预处理时逐像素一致。</p>
 */
public final class PreprocessSettings {

    /** 背景处理模式（对应 mapartcraft backgroundColourModes） */
    public enum BackgroundMode {
        /** 不铺底色（透明区域保持默认） */
        OFF("关"),
        /** 直接铺指定颜色，后续抖动会把该颜色量化成方块混合 */
        DITHERED("抖动"),
        /** 铺「在当前已选方块颜色中与指定颜色最接近的纯色」 */
        SMOOTH("平滑");

        public final String label;

        BackgroundMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** 默认背景色（mapartcraft 默认 #151515） */
    public static final int DEFAULT_BACKGROUND_RGB = 0x151515;

    public final boolean enabled;
    public final int brightness;    // 0..200，100 = 原始
    public final int contrast;      // 0..200，100 = 原始
    public final int saturation;    // 0..200，100 = 原始
    public final BackgroundMode backgroundMode;
    public final int backgroundRgb; // 0xRRGGBB

    public PreprocessSettings(boolean enabled, int brightness, int contrast, int saturation,
                              BackgroundMode backgroundMode, int backgroundRgb) {
        this.enabled = enabled;
        this.brightness = clamp(brightness);
        this.contrast = clamp(contrast);
        this.saturation = clamp(saturation);
        this.backgroundMode = backgroundMode == null ? BackgroundMode.OFF : backgroundMode;
        this.backgroundRgb = backgroundRgb & 0xFFFFFF;
    }

    /** 默认（关闭）：不改变任何像素，保证与历史输出一致 */
    public static PreprocessSettings disabled() {
        return new PreprocessSettings(false, 100, 100, 100, BackgroundMode.OFF, DEFAULT_BACKGROUND_RGB);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(200, v));
    }

    /** 滤镜是否为恒等（三项都 100） */
    public boolean isIdentityFilter() {
        return brightness == 100 && contrast == 100 && saturation == 100;
    }
}
