package com.axolotl.mcart.model

/**
 * 调色板服务：合并内置方块与自定义 / 插件方块，提供默认选择与内置预设。
 */
object PaletteService {

    private var cache: List<BlockColor>? = null

    /** 全部可用方块（内置 + 自定义 + 插件），按 blockId 去重 */
    @JvmStatic
    @Synchronized
    fun allBlocks(): List<BlockColor> {
        cache?.let { return it }
        // toMap 保留首次出现的条目（内置优先于自定义）
        val byId = LinkedHashMap<String, BlockColor>()
        BlockPalette.fullPalette().associateTo(byId) { it.blockId to it }
        for (bc in CustomBlockStore.all()) {
            byId.putIfAbsent(bc.blockId, bc)
        }
        return byId.values.toList().also { cache = it }
    }

    /** 自定义方块增删后调用 */
    @JvmStatic
    @Synchronized
    fun invalidate() {
        cache = null
        CustomBlockStore.reload()
    }

    @JvmStatic
    fun byId(blockId: String): BlockColor? =
        allBlocks().firstOrNull { it.blockId == blockId }

    /** 默认勾选：羊毛 + 混凝土（32 种） */
    @JvmStatic
    fun defaultSelection(): List<String> = allBlocks()
        .filter { it.category == BlockColor.Category.WOOL || it.category == BlockColor.Category.CONCRETE }
        .map { it.blockId }

    /** 生存易得：排除创造专属方块（保留水以外的绝大多数） */
    @JvmStatic
    fun survivalSelection(): List<String> = allBlocks()
        .filterNot { BlockTag.tagsOf(it).contains(BlockTag.CREATIVE_ONLY) }
        .map { it.blockId }

    /** 建筑常用：羊毛 / 混凝土 / 陶瓦 + 常见自然建材 */
    @JvmStatic
    fun buildingSelection(): List<String> = allBlocks().mapNotNull { bc ->
        val c = bc.category
        if (c == BlockColor.Category.WOOL || c == BlockColor.Category.CONCRETE
            || c == BlockColor.Category.TERRACOTTA || c == BlockColor.Category.GLAZED_TERRACOTTA
        ) {
            bc.blockId
        } else {
            val id = bc.blockId
            val naturalWords = listOf(
                "log", "planks", "stone", "deepslate", "sand", "dirt",
                "clay", "quartz", "snow", "mud", "moss",
            )
            id.takeIf { naturalWords.any(id::contains) }
        }
    }

    /** 仅自然方块 */
    @JvmStatic
    fun naturalSelection(): List<String> = allBlocks()
        .filter { it.category == BlockColor.Category.NATURAL }
        .map { it.blockId }
}
