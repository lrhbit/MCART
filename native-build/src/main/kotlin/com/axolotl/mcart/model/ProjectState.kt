package com.axolotl.mcart.model

import com.axolotl.mcart.core.DitherMethod
import com.axolotl.mcart.core.PreprocessSettings

/**
 * 工程参数的不可变快照：用于撤销 / 重做、工程快照 A/B 对比。
 * 注意：源图片本身不进快照（像素编辑除外），快照只记录参数与选区。
 *
 * 全部字段 @JvmField，Java 侧可直接字段访问；blockIds 构造时做防御性拷贝。
 */
class ProjectState(
    @JvmField val mode: MapMode,
    @JvmField val dither: DitherMethod,
    /** 0..100 */
    @JvmField val ditherStrength: Int,
    @JvmField val mapsW: Int,
    @JvmField val mapsH: Int,
    @JvmField val supportBlockId: String,
    @JvmField val exportFormat: Int,
    @JvmField val previewMode: Int,
    blockIds: Set<String>,
    @JvmField val preprocess: PreprocessSettings,
    @JvmField val roiEnabled: Boolean,
    @JvmField val roiX: Float,
    @JvmField val roiY: Float,
    @JvmField val roiW: Float,
    @JvmField val roiH: Float,
) {
    /** 保持插入顺序的选区副本 */
    @JvmField
    val blockIds: Set<String> = LinkedHashSet(blockIds)

    /** 与另一状态的参数是否完全一致（用于合并连续相同快照） */
    fun sameParams(o: ProjectState): Boolean =
        mode == o.mode && dither == o.dither && ditherStrength == o.ditherStrength &&
            mapsW == o.mapsW && mapsH == o.mapsH &&
            supportBlockId == o.supportBlockId &&
            exportFormat == o.exportFormat && previewMode == o.previewMode &&
            blockIds == o.blockIds &&
            preprocessEquals(preprocess, o.preprocess) &&
            roiEnabled == o.roiEnabled &&
            roiX == o.roiX && roiY == o.roiY && roiW == o.roiW && roiH == o.roiH

    companion object {
        @JvmStatic
        fun preprocessEquals(a: PreprocessSettings?, b: PreprocessSettings?): Boolean {
            if (a === b) return true
            if (a == null || b == null) return false
            return a.enabled == b.enabled && a.brightness == b.brightness &&
                a.contrast == b.contrast && a.saturation == b.saturation &&
                a.gamma == b.gamma && a.redGain == b.redGain &&
                a.greenGain == b.greenGain && a.blueGain == b.blueGain &&
                a.posterize == b.posterize && a.grayscale == b.grayscale &&
                a.backgroundMode == b.backgroundMode &&
                a.backgroundRgb == b.backgroundRgb
        }
    }
}
