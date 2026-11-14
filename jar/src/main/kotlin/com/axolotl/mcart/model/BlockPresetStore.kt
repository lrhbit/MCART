package com.axolotl.mcart.model

import com.axolotl.mcart.i18n.I18n
import com.axolotl.mcart.util.AppDirs
import com.axolotl.mcart.util.MiniJson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 方块选择预设：内置 4 套 + 用户保存（mc-pixel-art-data 的 presets 目录下的 .json）
 * + 插件目录（plugins 的 presets 目录下的 .json）。可导入 / 导出 / 团队共享。
 *
 * JSON 格式：{ "name": "...", "blocks": ["minecraft:white_wool", ...] }
 */
object BlockPresetStore {

    /**
     * 一套预设。@get:JvmName 保留原 record 风格访问器，Java 侧无需改动。
     */
    data class Preset(
        @get:JvmName("name") val name: String,
        @get:JvmName("blockIds") val blockIds: List<String>,
        @get:JvmName("builtin") val builtin: Boolean,
        @get:JvmName("file") val file: Path?,
    ) {
        fun isEditable(): Boolean = !builtin
    }

    @JvmStatic
    fun all(): List<Preset> = buildList {
        add(Preset(I18n.t("preset.default"), PaletteService.defaultSelection(), true, null))
        add(Preset(I18n.t("preset.survival"), PaletteService.survivalSelection(), true, null))
        add(Preset(I18n.t("preset.building"), PaletteService.buildingSelection(), true, null))
        add(Preset(I18n.t("preset.natural"), PaletteService.naturalSelection(), true, null))
        AppDirs.ensure()
        loadDir(AppDirs.presetsDir())
        loadDir(AppDirs.pluginPresetsDir())
    }

    private fun MutableList<Preset>.loadDir(dir: Path) {
        if (!Files.isDirectory(dir)) return
        try {
            Files.list(dir).use { files ->
                files.filter { it.toString().endsWith(".json") }.forEach { f ->
                    try {
                        val m = MiniJson.parseObject(Files.readString(f, StandardCharsets.UTF_8))
                        val name = MiniJson.str(m, "name", f.fileName.toString())!!
                        val ids = MiniJson.arr(m, "blocks").map { it.toString() }
                        add(Preset(name, ids, false, f))
                    } catch (ignored: Exception) {
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    /** 保存用户预设（同名覆盖） */
    @JvmStatic
    fun save(name: String, blockIds: List<String>) {
        AppDirs.ensure()
        val m: Map<String, Any?> = linkedMapOf("name" to name, "blocks" to blockIds)
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val f = AppDirs.presetsDir().resolve("$safe.json")
        try {
            Files.writeString(f, MiniJson.write(m), StandardCharsets.UTF_8)
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun delete(p: Preset?) {
        if (p == null || p.builtin || p.file == null) return
        try {
            Files.deleteIfExists(p.file)
        } catch (ignored: Exception) {
        }
    }

    /** 导出为单个 JSON 文件 */
    @JvmStatic
    fun exportPreset(p: Preset, target: Path) {
        val m: Map<String, Any?> = linkedMapOf("name" to p.name, "blocks" to p.blockIds)
        Files.writeString(target, MiniJson.write(m), StandardCharsets.UTF_8)
    }

    /** 从 JSON 文件导入（复制进用户预设目录） */
    @JvmStatic
    fun importPreset(source: Path): Preset {
        val m = MiniJson.parseObject(Files.readString(source, StandardCharsets.UTF_8))
        val name = MiniJson.str(m, "name", source.fileName.toString())!!
        val ids = MiniJson.arr(m, "blocks").map { it.toString() }
        save(name, ids)
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return Preset(name, ids, false, AppDirs.presetsDir().resolve("$safe.json"))
    }
}
