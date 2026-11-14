package com.axolotl.mcart.model

/**
 * Minecraft 地图颜色调色板
 *
 * 基色 RGB 与 colorId 均采用 Java 版官方 MapColor 表（地图物品实际显示颜色，
 * 非方块贴图平均色），并与主流地图画工具 mapartcraft 的现代配色对齐。
 * 同一官方基色可对应多个方块（如白羊毛/白混凝土/雪均为 SNOW 白），
 * 方块不同但地图颜色相同，建造时可按材料易得程度在方块库中取舍。
 * 每个基色另有 4 个亮度变体（dark/normal/bright/unobtainable，见 BlockColor）。
 */
object BlockPalette {

    /**
     * 完整的 MC 地图颜色基色表（官方 MapColor；RGB 为 shade=2/bright 基色）
     * 数据来源: Minecraft Wiki「Map item format」颜色表，对照 mapartcraft coloursJSON
     */
    @JvmStatic
    fun baseColors(): List<BlockColor> = buildList {
        // ID 0: Air (透明，跳过)

        // 1: Grass
        add(BlockColor(1, "Grass", 127, 178, 56, "minecraft:grass_block", BlockColor.Category.NATURAL))
        // 2: Sand
        add(BlockColor(2, "Sand", 247, 233, 163, "minecraft:sand", BlockColor.Category.NATURAL))
        // 3: Wool (Cloth)
        add(BlockColor(8, "White Wool", 255, 255, 255, "minecraft:white_wool", BlockColor.Category.WOOL))
        // 4: Fire / TNT
        add(BlockColor(4, "TNT", 255, 0, 0, "minecraft:tnt", BlockColor.Category.SPECIAL))
        // 5: Ice
        add(BlockColor(5, "Ice", 160, 160, 255, "minecraft:ice", BlockColor.Category.NATURAL))
        // 6: Metal / Iron
        add(BlockColor(6, "Iron Block", 167, 167, 167, "minecraft:iron_block", BlockColor.Category.MINERAL))
        // 7: Plant / Foliage
        add(BlockColor(7, "Leaves", 0, 124, 0, "minecraft:oak_leaves", BlockColor.Category.NATURAL))
        // 8: Snow
        add(BlockColor(8, "Snow", 255, 255, 255, "minecraft:snow_block", BlockColor.Category.NATURAL))
        // 9: Clay
        add(BlockColor(9, "Clay", 164, 168, 184, "minecraft:clay", BlockColor.Category.NATURAL))
        // 10: Dirt
        add(BlockColor(10, "Dirt", 151, 109, 77, "minecraft:dirt", BlockColor.Category.NATURAL))
        // 11: Stone
        add(BlockColor(11, "Stone", 112, 112, 112, "minecraft:stone", BlockColor.Category.NATURAL))
        // 12: Water
        add(BlockColor(12, "Water", 64, 64, 255, "minecraft:water", BlockColor.Category.NATURAL))
        // 13: Oak Wood
        add(BlockColor(13, "Oak Wood", 143, 119, 72, "minecraft:oak_log", BlockColor.Category.NATURAL))
        // 14: Quartz
        add(BlockColor(14, "Quartz", 255, 252, 245, "minecraft:quartz_block", BlockColor.Category.MINERAL))
        // 15: Orange
        add(BlockColor(15, "Orange", 216, 127, 51, "minecraft:orange_wool", BlockColor.Category.WOOL))
        // 16: Magenta
        add(BlockColor(16, "Magenta", 178, 76, 216, "minecraft:magenta_wool", BlockColor.Category.WOOL))
        // 17: Light Blue
        add(BlockColor(17, "Light Blue", 102, 153, 216, "minecraft:light_blue_wool", BlockColor.Category.WOOL))
        // 18: Yellow
        add(BlockColor(18, "Yellow", 229, 229, 51, "minecraft:yellow_wool", BlockColor.Category.WOOL))
        // 19: Lime
        add(BlockColor(19, "Lime", 127, 204, 25, "minecraft:lime_wool", BlockColor.Category.WOOL))
        // 20: Pink
        add(BlockColor(20, "Pink", 242, 127, 165, "minecraft:pink_wool", BlockColor.Category.WOOL))
        // 21: Gray
        add(BlockColor(21, "Gray", 76, 76, 76, "minecraft:gray_wool", BlockColor.Category.WOOL))
        // 22: Light Gray
        add(BlockColor(22, "Light Gray", 153, 153, 153, "minecraft:light_gray_wool", BlockColor.Category.WOOL))
        // 23: Cyan
        add(BlockColor(23, "Cyan", 76, 127, 153, "minecraft:cyan_wool", BlockColor.Category.WOOL))
        // 24: Purple
        add(BlockColor(24, "Purple", 127, 63, 178, "minecraft:purple_wool", BlockColor.Category.WOOL))
        // 25: Blue
        add(BlockColor(25, "Blue", 51, 76, 178, "minecraft:blue_wool", BlockColor.Category.WOOL))
        // 26: Brown
        add(BlockColor(26, "Brown", 102, 76, 51, "minecraft:brown_wool", BlockColor.Category.WOOL))
        // 27: Green
        add(BlockColor(27, "Green", 102, 127, 51, "minecraft:green_wool", BlockColor.Category.WOOL))
        // 28: Red
        add(BlockColor(28, "Red", 153, 51, 51, "minecraft:red_wool", BlockColor.Category.WOOL))
        // 29: Black
        add(BlockColor(29, "Black", 25, 25, 25, "minecraft:black_wool", BlockColor.Category.WOOL))
        // 30: Gold
        add(BlockColor(30, "Gold Block", 250, 238, 77, "minecraft:gold_block", BlockColor.Category.MINERAL))
        // 31: Diamond
        add(BlockColor(31, "Diamond Block", 92, 219, 213, "minecraft:diamond_block", BlockColor.Category.MINERAL))
        // 32: Lapis
        add(BlockColor(32, "Lapis Block", 74, 128, 255, "minecraft:lapis_block", BlockColor.Category.MINERAL))
        // 33: Emerald
        add(BlockColor(33, "Emerald Block", 0, 217, 58, "minecraft:emerald_block", BlockColor.Category.MINERAL))
        // 34: Podzol
        add(BlockColor(34, "Podzol", 129, 86, 49, "minecraft:podzol", BlockColor.Category.NATURAL))
        // 35: Nether
        add(BlockColor(28, "Nether Wart", 153, 51, 51, "minecraft:nether_wart_block", BlockColor.Category.SPECIAL))
        // 36: White Terracotta
        add(BlockColor(36, "White Terracotta", 209, 177, 161, "minecraft:white_terracotta", BlockColor.Category.TERRACOTTA))
        // 37: Orange Terracotta
        add(BlockColor(37, "Orange Terracotta", 159, 82, 36, "minecraft:orange_terracotta", BlockColor.Category.TERRACOTTA))
        // 38: Magenta Terracotta
        add(BlockColor(38, "Magenta Terracotta", 149, 87, 108, "minecraft:magenta_terracotta", BlockColor.Category.TERRACOTTA))
        // 39: Light Blue Terracotta
        add(BlockColor(39, "Light Blue Terracotta", 112, 108, 138, "minecraft:light_blue_terracotta", BlockColor.Category.TERRACOTTA))
        // 40: Yellow Terracotta
        add(BlockColor(40, "Yellow Terracotta", 186, 133, 36, "minecraft:yellow_terracotta", BlockColor.Category.TERRACOTTA))
        // 41: Lime Terracotta
        add(BlockColor(41, "Lime Terracotta", 103, 117, 53, "minecraft:lime_terracotta", BlockColor.Category.TERRACOTTA))
        // 42: Pink Terracotta
        add(BlockColor(42, "Pink Terracotta", 160, 77, 78, "minecraft:pink_terracotta", BlockColor.Category.TERRACOTTA))
        // 43: Gray Terracotta
        add(BlockColor(43, "Gray Terracotta", 57, 41, 35, "minecraft:gray_terracotta", BlockColor.Category.TERRACOTTA))
        // 44: Light Gray Terracotta
        add(BlockColor(44, "Light Gray Terracotta", 135, 107, 98, "minecraft:light_gray_terracotta", BlockColor.Category.TERRACOTTA))
        // 45: Cyan Terracotta
        add(BlockColor(45, "Cyan Terracotta", 87, 92, 92, "minecraft:cyan_terracotta", BlockColor.Category.TERRACOTTA))
        // 46: Purple Terracotta
        add(BlockColor(46, "Purple Terracotta", 122, 73, 88, "minecraft:purple_terracotta", BlockColor.Category.TERRACOTTA))
        // 47: Blue Terracotta
        add(BlockColor(47, "Blue Terracotta", 76, 62, 92, "minecraft:blue_terracotta", BlockColor.Category.TERRACOTTA))
        // 48: Brown Terracotta
        add(BlockColor(48, "Brown Terracotta", 76, 50, 35, "minecraft:brown_terracotta", BlockColor.Category.TERRACOTTA))
        // 49: Green Terracotta
        add(BlockColor(49, "Green Terracotta", 76, 82, 42, "minecraft:green_terracotta", BlockColor.Category.TERRACOTTA))
        // 50: Red Terracotta
        add(BlockColor(50, "Red Terracotta", 142, 60, 46, "minecraft:red_terracotta", BlockColor.Category.TERRACOTTA))
        // 51: Black Terracotta
        add(BlockColor(51, "Black Terracotta", 37, 22, 16, "minecraft:black_terracotta", BlockColor.Category.TERRACOTTA))
        // 52: Crimson Nylium
        add(BlockColor(52, "Crimson Nylium", 189, 48, 49, "minecraft:crimson_nylium", BlockColor.Category.NATURAL))
        // 53: Crimson Stem
        add(BlockColor(53, "Crimson Stem", 148, 63, 97, "minecraft:crimson_stem", BlockColor.Category.NATURAL))
        // 54: Crimson Planks
        add(BlockColor(53, "Crimson Planks", 148, 63, 97, "minecraft:crimson_planks", BlockColor.Category.NATURAL))
        // 55: Warped Nylium
        add(BlockColor(55, "Warped Nylium", 22, 126, 134, "minecraft:warped_nylium", BlockColor.Category.NATURAL))
        // 56: Warped Stem
        add(BlockColor(56, "Warped Stem", 58, 142, 140, "minecraft:warped_stem", BlockColor.Category.NATURAL))
        // 57: Warped Planks
        add(BlockColor(56, "Warped Planks", 58, 142, 140, "minecraft:warped_planks", BlockColor.Category.NATURAL))
        // 58: Deepslate
        add(BlockColor(59, "Deepslate", 100, 100, 100, "minecraft:deepslate", BlockColor.Category.NATURAL))
        // 59: Raw Iron
        add(BlockColor(60, "Raw Iron Block", 216, 175, 147, "minecraft:raw_iron_block", BlockColor.Category.MINERAL))
        // 60: Moss
        add(BlockColor(27, "Moss Block", 102, 127, 51, "minecraft:moss_block", BlockColor.Category.NATURAL))
        // 61: Mud
        add(BlockColor(45, "Mud", 87, 92, 92, "minecraft:mud", BlockColor.Category.NATURAL))
        // 62: Mangrove Roots
        add(BlockColor(34, "Mangrove Roots", 129, 86, 49, "minecraft:mangrove_roots", BlockColor.Category.NATURAL))
        // 63: Muddy Mangrove Roots
        add(BlockColor(34, "Muddy Mangrove Roots", 129, 86, 49, "minecraft:mangrove_roots", BlockColor.Category.NATURAL))
        // 64: Cherry Leaves
        add(BlockColor(20, "Cherry Leaves", 242, 127, 165, "minecraft:cherry_leaves", BlockColor.Category.NATURAL))
        // 65: Cherry Wood
        add(BlockColor(36, "Cherry Wood", 209, 177, 161, "minecraft:cherry_log", BlockColor.Category.NATURAL))
        // 66: Pale Moss
        add(BlockColor(22, "Pale Moss Block", 153, 153, 153, "minecraft:pale_moss_block", BlockColor.Category.NATURAL))
        // 67: Pale Oak Wood
        add(BlockColor(14, "Pale Oak Wood", 255, 252, 245, "minecraft:pale_oak_log", BlockColor.Category.NATURAL))
    }

    /** 混凝土调色板（16 色标准染料） */
    @JvmStatic
    fun concreteColors(): List<BlockColor> = listOf(
        BlockColor(8, "White Concrete", 255, 255, 255, "minecraft:white_concrete", BlockColor.Category.CONCRETE),
        BlockColor(15, "Orange Concrete", 216, 127, 51, "minecraft:orange_concrete", BlockColor.Category.CONCRETE),
        BlockColor(16, "Magenta Concrete", 178, 76, 216, "minecraft:magenta_concrete", BlockColor.Category.CONCRETE),
        BlockColor(17, "Light Blue Concrete", 102, 153, 216, "minecraft:light_blue_concrete", BlockColor.Category.CONCRETE),
        BlockColor(18, "Yellow Concrete", 229, 229, 51, "minecraft:yellow_concrete", BlockColor.Category.CONCRETE),
        BlockColor(19, "Lime Concrete", 127, 204, 25, "minecraft:lime_concrete", BlockColor.Category.CONCRETE),
        BlockColor(20, "Pink Concrete", 242, 127, 165, "minecraft:pink_concrete", BlockColor.Category.CONCRETE),
        BlockColor(21, "Gray Concrete", 76, 76, 76, "minecraft:gray_concrete", BlockColor.Category.CONCRETE),
        BlockColor(22, "Light Gray Concrete", 153, 153, 153, "minecraft:light_gray_concrete", BlockColor.Category.CONCRETE),
        BlockColor(23, "Cyan Concrete", 76, 127, 153, "minecraft:cyan_concrete", BlockColor.Category.CONCRETE),
        BlockColor(24, "Purple Concrete", 127, 63, 178, "minecraft:purple_concrete", BlockColor.Category.CONCRETE),
        BlockColor(25, "Blue Concrete", 51, 76, 178, "minecraft:blue_concrete", BlockColor.Category.CONCRETE),
        BlockColor(26, "Brown Concrete", 102, 76, 51, "minecraft:brown_concrete", BlockColor.Category.CONCRETE),
        BlockColor(27, "Green Concrete", 102, 127, 51, "minecraft:green_concrete", BlockColor.Category.CONCRETE),
        BlockColor(28, "Red Concrete", 153, 51, 51, "minecraft:red_concrete", BlockColor.Category.CONCRETE),
        BlockColor(29, "Black Concrete", 25, 25, 25, "minecraft:black_concrete", BlockColor.Category.CONCRETE),
    )

    /** 完整调色板（基色 + 混凝土） */
    @JvmStatic
    fun fullPalette(): List<BlockColor> = baseColors() + concreteColors()
}
