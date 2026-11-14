package com.axolotl.mcart.core

/**
 * 预处理参数（不可变）。
 *
 * 字段使用 @JvmField，Java 侧可保持 `s.brightness` 式的直接字段访问；
 * 规整逻辑（clamp / 默认背景模式 / 掩码）统一在公共构造器委托时完成。
 *
 * 私有主构造器额外带一个哨兵参数，用于与公共 12 参构造器区分 JVM 签名
 * （BackgroundMode 可空与否擦除后类型相同，否则会产生平台声明冲突）。
 */
class PreprocessSettings private constructor(
    @JvmField val enabled: Boolean,
    @JvmField val brightness: Int,
    @JvmField val contrast: Int,
    @JvmField val saturation: Int,
    @JvmField val gamma: Int,
    @JvmField val redGain: Int,
    @JvmField val greenGain: Int,
    @JvmField val blueGain: Int,
    @JvmField val posterize: Int,
    @JvmField val grayscale: Boolean,
    @JvmField val backgroundMode: BackgroundMode,
    @JvmField val backgroundRgb: Int,
    marker: Any,
) {
    /** 简化构造器：未提供的通道增益 / 色阶 / 灰度参数取默认值 */
    constructor(
        enabled: Boolean,
        brightness: Int,
        contrast: Int,
        saturation: Int,
        backgroundMode: BackgroundMode?,
        backgroundRgb: Int,
    ) : this(
        enabled,
        brightness, contrast, saturation,
        100, 100, 100, 100,
        0, false,
        backgroundMode ?: BackgroundMode.OFF,
        backgroundRgb,
        SENTINEL,
    )

    /** 完整构造器：对原始入参做规整后委托私有主构造器 */
    constructor(
        enabled: Boolean,
        brightness: Int,
        contrast: Int,
        saturation: Int,
        gamma: Int,
        redGain: Int,
        greenGain: Int,
        blueGain: Int,
        posterize: Int,
        grayscale: Boolean,
        backgroundMode: BackgroundMode?,
        backgroundRgb: Int,
    ) : this(
        enabled,
        brightness.clamp(),
        contrast.clamp(),
        saturation.clamp(),
        gamma.clamp(),
        redGain.clamp(),
        greenGain.clamp(),
        blueGain.clamp(),
        if (posterize <= 0) 0 else posterize.coerceIn(2, 32),
        grayscale,
        backgroundMode ?: BackgroundMode.OFF,
        backgroundRgb and 0xFFFFFF,
        SENTINEL,
    )

    fun isIdentityFilter(): Boolean =
        brightness == 100 && contrast == 100 && saturation == 100 &&
            gamma == 100 && redGain == 100 && greenGain == 100 && blueGain == 100 &&
            posterize == 0 && !grayscale

    /** 背景处理模式 */
    enum class BackgroundMode(val label: String) {
        OFF("关"),
        DITHERED("抖动"),
        SMOOTH("平滑"),
        ;

        override fun toString(): String = label
    }

    companion object {
        const val DEFAULT_BACKGROUND_RGB: Int = 1381653

        /** 私有哨兵对象，仅用于区分主构造器签名 */
        private val SENTINEL: Any = Any()

        @JvmStatic
        fun disabled(): PreprocessSettings = PreprocessSettings(
            enabled = false,
            brightness = 100, contrast = 100, saturation = 100,
            gamma = 100, redGain = 100, greenGain = 100, blueGain = 100,
            posterize = 0, grayscale = false,
            backgroundMode = BackgroundMode.OFF,
            backgroundRgb = DEFAULT_BACKGROUND_RGB,
        )

        private fun Int.clamp(): Int = coerceIn(0, 200)
    }
}
