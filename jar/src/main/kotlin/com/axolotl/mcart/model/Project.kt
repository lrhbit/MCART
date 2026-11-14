package com.axolotl.mcart.model

import com.axolotl.mcart.core.DitherMethod
import com.axolotl.mcart.core.PixelArtGenerator
import com.axolotl.mcart.core.PreprocessSettings
import com.axolotl.mcart.util.MiniJson
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 一个地图画工程：源图片 + 全部生成参数 + 生成结果 + 撤销/重做栈 + 命名快照。
 * 多标签文档（MainFrame 可同时持有多个 Project）。
 *
 * 重构说明：参数统一用 Kotlin 属性表达，setter 内集中处理 dirty 标记与规整；
 * 自动生成的 getXxx/setXxx 与原 Java 方法名一致，Java 侧无需改动。
 */
class Project() {

    /** 命名快照（A/B 对比用） */
    class NamedSnapshot(
        @JvmField val name: String,
        @JvmField val state: ProjectState,
    ) {
        @JvmField
        var previewPng: ByteArray? = null
    }

    var name: String = "未命名"
    var projectFile: File? = null
    var dirty: Boolean = false
        @JvmName("isDirty") get
        private set

    private var sourceImage: BufferedImage? = null
    private var sourceImageName: String? = null
    /** PNG 编码（.mcpx 保存用） */
    private var sourceBytes: ByteArray? = null

    // —— 参数（setter 统一标记 dirty） ——
    var mode: MapMode = MapMode.FLAT
        set(v) { field = v; dirty = true }
    var dither: DitherMethod = DitherMethod.FLOYD_STEINBERG
        set(v) { field = v; dirty = true }
    var ditherStrength: Int = 100
        set(v) { field = v.coerceIn(0, 100); dirty = true }
    var mapsW: Int = 2
        set(v) { field = v; dirty = true }
    var mapsH: Int = 2
        set(v) { field = v; dirty = true }
    var supportBlockId: String = "minecraft:stone"
        set(v) { field = v; dirty = true }
    var exportFormat: Int = 0
        set(v) { field = v; dirty = true }
    var previewMode: Int = 0
    var selectedBlockIds: MutableSet<String> = LinkedHashSet(PaletteService.defaultSelection())
    var preprocess: PreprocessSettings = PreprocessSettings.disabled()
        set(v) { field = v; dirty = true }
    var roiEnabled: Boolean = false
    var roiX: Float = 0f
    var roiY: Float = 0f
    var roiW: Float = 1f
    var roiH: Float = 1f

    // —— 运行时结果（不保存，Java 直接字段访问） ——
    @JvmField
    var result: PixelArtGenerator.Result? = null
    @JvmField
    var mcColorImage: BufferedImage? = null
    @JvmField
    var mcTextureImage: BufferedImage? = null

    // —— 撤销 / 重做 ——
    private val undoStack: ArrayDeque<ProjectState> = ArrayDeque()
    private val redoStack: ArrayDeque<ProjectState> = ArrayDeque()

    // —— 命名快照 ——
    @JvmField
    val snapshots: MutableList<NamedSnapshot> = mutableListOf()

    constructor(name: String) : this() {
        this.name = name
    }

    // ================= 基本方法 =================

    fun markDirty() { dirty = true }
    fun clearDirty() { dirty = false }

    fun getSourceImage(): BufferedImage? = sourceImage
    fun getSourceImageName(): String? = sourceImageName
    fun getSourceBytes(): ByteArray? = sourceBytes

    fun setSourceImage(img: BufferedImage, imageName: String?) {
        sourceImage = img
        sourceImageName = imageName
        sourceBytes = encodePng(img)
        if (imageName != null && projectFile == null) {
            val base = File(imageName).name
            val dot = base.lastIndexOf('.')
            name = if (dot > 0) base.substring(0, dot) else base
        }
        result = null
        mcColorImage = null
        mcTextureImage = null
        dirty = true
    }

    fun setSourceBytes(bytes: ByteArray, imageName: String?) {
        sourceBytes = bytes
        sourceImageName = imageName
        sourceImage = decodePng(bytes)
    }

    fun hasImage(): Boolean = sourceImage != null

    fun setRoi(enabled: Boolean, x: Float, y: Float, w: Float, h: Float) {
        roiEnabled = enabled
        roiX = x; roiY = y; roiW = w; roiH = h
        dirty = true
    }

    fun clearRoi() {
        roiEnabled = false
        roiX = 0f; roiY = 0f; roiW = 1f; roiH = 1f
        dirty = true
    }

    fun roi(): Rectangle2D.Float? {
        if (!roiEnabled) return null
        return Rectangle2D.Float(roiX, roiY, roiW, roiH)
    }

    /** 目标方块尺寸 */
    fun targetWidth(): Int = mapsW * 128
    fun targetHeight(): Int = mapsH * 128

    // ================= 撤销 / 重做 =================

    fun snapshot(): ProjectState = ProjectState(
        mode, dither, ditherStrength, mapsW, mapsH, supportBlockId,
        exportFormat, previewMode, selectedBlockIds, preprocess,
        roiEnabled, roiX, roiY, roiW, roiH,
    )

    /** 在修改参数前调用：把当前状态压入撤销栈（与栈顶相同则忽略） */
    fun pushUndo() {
        val cur = snapshot()
        val top = undoStack.firstOrNull()
        if (top != null && top.sameParams(cur)) return
        undoStack.addFirst(cur)
        while (undoStack.size > MAX_UNDO) undoStack.removeLast()
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addFirst(snapshot())
        restore(undoStack.removeFirst())
        dirty = true
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addFirst(snapshot())
        restore(redoStack.removeFirst())
        dirty = true
    }

    fun restore(s: ProjectState) {
        mode = s.mode
        dither = s.dither
        ditherStrength = s.ditherStrength
        mapsW = s.mapsW
        mapsH = s.mapsH
        supportBlockId = s.supportBlockId
        exportFormat = s.exportFormat
        previewMode = s.previewMode
        selectedBlockIds = LinkedHashSet(s.blockIds)
        preprocess = s.preprocess
        roiEnabled = s.roiEnabled
        roiX = s.roiX; roiY = s.roiY; roiW = s.roiW; roiH = s.roiH
        result = null
        mcColorImage = null
        mcTextureImage = null
    }

    // ================= 命名快照 =================

    fun addSnapshot(name: String) {
        snapshots.add(NamedSnapshot(name, snapshot()))
        dirty = true
    }

    // ================= 序列化 =================

    fun toJson(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "version" to 1,
        "name" to name,
        "sourceImageName" to sourceImageName,
        "mode" to mode.name,
        "dither" to dither.name,
        "ditherStrength" to ditherStrength,
        "mapsW" to mapsW,
        "mapsH" to mapsH,
        "supportBlockId" to supportBlockId,
        "exportFormat" to exportFormat,
        "previewMode" to previewMode,
        "blocks" to selectedBlockIds.toList(),
        "preprocess" to linkedMapOf(
            "enabled" to preprocess.enabled,
            "brightness" to preprocess.brightness,
            "contrast" to preprocess.contrast,
            "saturation" to preprocess.saturation,
            "gamma" to preprocess.gamma,
            "redGain" to preprocess.redGain,
            "greenGain" to preprocess.greenGain,
            "blueGain" to preprocess.blueGain,
            "posterize" to preprocess.posterize,
            "grayscale" to preprocess.grayscale,
            "backgroundMode" to preprocess.backgroundMode.name,
            "backgroundRgb" to preprocess.backgroundRgb,
        ),
        "roi" to linkedMapOf(
            "enabled" to roiEnabled,
            "x" to roiX, "y" to roiY, "w" to roiW, "h" to roiH,
        ),
        "snapshots" to snapshots.map { sn ->
            linkedMapOf("name" to sn.name, "state" to stateToJson(sn.state))
        },
    )

    private fun stateToJson(s: ProjectState): Map<String, Any?> = linkedMapOf(
        "mode" to s.mode.name,
        "dither" to s.dither.name,
        "ditherStrength" to s.ditherStrength,
        "mapsW" to s.mapsW,
        "mapsH" to s.mapsH,
        "supportBlockId" to s.supportBlockId,
        "exportFormat" to s.exportFormat,
        "previewMode" to s.previewMode,
        "blocks" to s.blockIds.toList(),
    )

    companion object {
        private const val MAX_UNDO: Int = 50

        @JvmStatic
        fun fromJson(m: Map<String, Any?>, imageBytes: ByteArray?): Project {
            val p = Project()
            p.name = MiniJson.str(m, "name", "未命名")!!
            val imgName = MiniJson.str(m, "sourceImageName", null)
            if (imageBytes != null) {
                p.setSourceBytes(imageBytes, imgName)
            }
            p.mode = parseEnum<MapMode>(MiniJson.str(m, "mode", "FLAT")!!, MapMode.FLAT)
            p.dither = parseEnum<DitherMethod>(
                MiniJson.str(m, "dither", "FLOYD_STEINBERG")!!, DitherMethod.FLOYD_STEINBERG,
            )
            p.ditherStrength = MiniJson.integer(m, "ditherStrength", 100)
            p.mapsW = MiniJson.integer(m, "mapsW", 2)
            p.mapsH = MiniJson.integer(m, "mapsH", 2)
            p.supportBlockId = MiniJson.str(m, "supportBlockId", "minecraft:stone")!!
            p.exportFormat = MiniJson.integer(m, "exportFormat", 0)
            p.previewMode = MiniJson.integer(m, "previewMode", 0)

            p.selectedBlockIds = LinkedHashSet(
                MiniJson.arr(m, "blocks").map { it.toString() }
            )
            if (p.selectedBlockIds.isEmpty()) {
                p.selectedBlockIds = LinkedHashSet(PaletteService.defaultSelection())
            }

            val pp = MiniJson.obj(m, "preprocess")
            if (pp.isNotEmpty()) {
                p.preprocess = PreprocessSettings(
                    MiniJson.bool(pp, "enabled", false),
                    MiniJson.integer(pp, "brightness", 100),
                    MiniJson.integer(pp, "contrast", 100),
                    MiniJson.integer(pp, "saturation", 100),
                    MiniJson.integer(pp, "gamma", 100),
                    MiniJson.integer(pp, "redGain", 100),
                    MiniJson.integer(pp, "greenGain", 100),
                    MiniJson.integer(pp, "blueGain", 100),
                    MiniJson.integer(pp, "posterize", 0),
                    MiniJson.bool(pp, "grayscale", false),
                    parseEnum<PreprocessSettings.BackgroundMode>(
                        MiniJson.str(pp, "backgroundMode", "OFF")!!,
                        PreprocessSettings.BackgroundMode.OFF,
                    ),
                    MiniJson.integer(pp, "backgroundRgb", PreprocessSettings.DEFAULT_BACKGROUND_RGB),
                )
            }
            val roi = MiniJson.obj(m, "roi")
            if (roi.isNotEmpty()) {
                p.roiEnabled = MiniJson.bool(roi, "enabled", false)
                p.roiX = MiniJson.dbl(roi, "x", 0.0).toFloat()
                p.roiY = MiniJson.dbl(roi, "y", 0.0).toFloat()
                p.roiW = MiniJson.dbl(roi, "w", 1.0).toFloat()
                p.roiH = MiniJson.dbl(roi, "h", 1.0).toFloat()
            }
            p.dirty = false
            return p
        }

        private inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T =
            try {
                enumValueOf<T>(name)
            } catch (e: IllegalArgumentException) {
                default
            }

        private fun encodePng(img: BufferedImage?): ByteArray? {
            if (img == null) return null
            try {
                var rgb = img
                if (img.type != BufferedImage.TYPE_INT_RGB && img.type != BufferedImage.TYPE_INT_ARGB) {
                    rgb = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
                    rgb.createGraphics().drawImage(img, 0, 0, null)
                }
                return rgb.let {
                    java.io.ByteArrayOutputStream().use { baos ->
                        ImageIO.write(it, "png", baos)
                        baos.toByteArray()
                    }
                }
            } catch (e: Exception) {
                return null
            }
        }

        private fun decodePng(bytes: ByteArray?): BufferedImage? {
            if (bytes == null) return null
            return try {
                ImageIO.read(java.io.ByteArrayInputStream(bytes))
            } catch (e: Exception) {
                null
            }
        }
    }
}
