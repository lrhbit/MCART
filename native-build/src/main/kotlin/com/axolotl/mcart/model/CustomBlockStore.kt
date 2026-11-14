package com.axolotl.mcart.model

import com.axolotl.mcart.util.AppDirs
import com.axolotl.mcart.util.MiniJson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 自定义方块仓库：用户在「添加自定义方块」中登记的方块，以及
 * plugins 的 blocks 目录下的 .json 插件方块定义，启动时合并进调色板。
 *
 * JSON 格式：
 * { "blocks": [
 *   { "id": "mymod:custom_block", "name": "自定义方块",
 *     "r": 200, "g": 100, "b": 50 }
 * ]}
 *
 * 自定义方块使用 1000+ 的虚拟地图颜色 ID，类别为特殊方块；
 * 游戏中需要同名方块（模组 / 资源包）真实存在。
 */
object CustomBlockStore {

    private val custom: MutableList<BlockColor> = mutableListOf()
    @Volatile
    private var loaded = false

    /** 全部自定义方块的防御性拷贝 */
    @JvmStatic
    @Synchronized
    fun all(): List<BlockColor> {
        if (!loaded) reload()
        return custom.toList()
    }

    @JvmStatic
    @Synchronized
    fun reload() {
        loaded = true
        custom.clear()
        // 1. 用户自定义
        loadFile(AppDirs.customBlocksFile())
        // 2. 插件目录
        AppDirs.ensure()
        val dir = AppDirs.pluginBlocksDir()
        if (Files.isDirectory(dir)) {
            Files.list(dir).use { files ->
                files.filter { it.toString().endsWith(".json") }.forEach(::loadFile)
            }
        }
    }

    private fun loadFile(f: Path?) {
        if (f == null || !Files.isRegularFile(f)) return
        try {
            val root = MiniJson.parseObject(Files.readString(f, StandardCharsets.UTF_8))
            for (o in MiniJson.arr(root, "blocks")) {
                @Suppress("UNCHECKED_CAST")
                val m = o as? Map<String, Any?> ?: continue
                fromMap(m)?.let(custom::add)
            }
        } catch (ignored: Exception) {
        }
    }

    /** 从 JSON 条目构造方块；id 为空返回 null */
    @JvmStatic
    fun fromMap(m: Map<String, Any?>): BlockColor? {
        var id = MiniJson.str(m, "id", "")!!
        if (id.isBlank()) return null
        if (!id.contains(":")) id = "minecraft:$id"
        val name = MiniJson.str(m, "name", id)!!
        val r = MiniJson.integer(m, "r", 128).coerceIn(0, 255)
        val g = MiniJson.integer(m, "g", 128).coerceIn(0, 255)
        val b = MiniJson.integer(m, "b", 128).coerceIn(0, 255)
        var colorId = MiniJson.integer(m, "colorId", -1)
        if (colorId < 0) colorId = 1000 + (Math.abs(id.hashCode()) % 9000)
        return BlockColor(colorId, name, r, g, b, id, BlockColor.Category.SPECIAL)
    }

    /** 添加并持久化一个自定义方块（返回 false 表示 ID 已存在） */
    @JvmStatic
    @Synchronized
    fun add(bc: BlockColor): Boolean {
        all()
        if (custom.any { it.blockId == bc.blockId }) return false
        custom.add(bc)
        persist()
        return true
    }

    @JvmStatic
    @Synchronized
    fun remove(blockId: String) {
        custom.removeAll { it.blockId == blockId }
        persist()
    }

    private fun persist() {
        AppDirs.ensure()
        // 仅持久化用户在界面添加的（colorId >= 1000 且非插件来源无法区分，
        // 重复写入无害：加载时插件文件仍会再次合并，调色板按 blockId 去重）
        val arr = custom.map { c ->
            linkedMapOf<String, Any?>(
                "id" to c.blockId,
                "name" to c.name,
                "r" to c.r,
                "g" to c.g,
                "b" to c.b,
            )
        }
        val root: Map<String, Any?> = linkedMapOf("blocks" to arr)
        try {
            Files.writeString(
                AppDirs.customBlocksFile(),
                MiniJson.write(root),
                StandardCharsets.UTF_8,
            )
        } catch (ignored: Exception) {
        }
    }
}
