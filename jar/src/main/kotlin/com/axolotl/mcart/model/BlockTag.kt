package com.axolotl.mcart.model

import java.util.EnumSet

/**
 * 方块业务标签：用于方块库过滤、生存预设、质量警告。
 * 标签依据 Minecraft 常识按 blockId / 类别推断；自定义方块可显式声明标签。
 */
enum class BlockTag(val zh: String, val en: String) {
    TRANSPARENT("透明", "Transparent"),
    GLOWING("发光", "Glowing"),
    CRAFTED("需合成", "Crafted"),
    MINERAL_RESOURCE("矿物资源", "Mineral"),
    NETHER("下界", "Nether"),
    END("末地", "End"),
    CREATIVE_ONLY("创造专属", "Creative only"),
    WATER("液体", "Liquid"),
    ;

    fun label(lang: String): String = if (lang == "en") en else zh

    companion object {
        private val NO_TAGS: EnumSet<BlockTag> = EnumSet.noneOf(BlockTag::class.java)

        /** 推断内置方块的标签集合 */
        @JvmStatic
        fun tagsOf(bc: BlockColor): Set<BlockTag> {
            val id = bc.blockId
            val set = EnumSet.noneOf(BlockTag::class.java)

            if (bc.isWater()) set.add(WATER)

            val cat = bc.category
            if (cat == BlockColor.Category.STAINED_GLASS
                || id.contains("glass") || id.contains("ice")
            ) {
                set.add(TRANSPARENT)
            }

            val glowingWords = listOf(
                "glowstone", "sea_lantern", "shroomlight", "froglight",
                "lantern", "torch", "lava", "magma", "jack_o_lantern",
                "glow_lichen", "light",
            )
            if (glowingWords.any(id::contains)) set.add(GLOWING)

            if (cat == BlockColor.Category.MINERAL) set.add(MINERAL_RESOURCE)

            val craftedCategories = setOf(
                BlockColor.Category.WOOL, BlockColor.Category.CONCRETE,
                BlockColor.Category.TERRACOTTA, BlockColor.Category.GLAZED_TERRACOTTA,
                BlockColor.Category.MINERAL,
            )
            if (cat in craftedCategories || id.contains("tnt") || id.contains("snow_block")) {
                set.add(CRAFTED)
            }

            val netherWords = listOf(
                "nether", "crimson", "warped", "basalt",
                "blackstone", "soul_", "netherrack",
            )
            if (netherWords.any(id::contains)) set.add(NETHER)

            val endWords = listOf("end_stone", "purpur", "end_rod", "chorus")
            if (endWords.any(id::contains)) set.add(END)

            val creativeWords = listOf(
                "spawner", "barrier", "command_block", "structure_block",
                "bedrock", "infested", "petrified",
            )
            if (creativeWords.any(id::contains)) set.add(CREATIVE_ONLY)

            return if (set.isEmpty()) NO_TAGS else set
        }

        @JvmStatic
        fun has(bc: BlockColor, tag: BlockTag): Boolean = tagsOf(bc).contains(tag)
    }
}
