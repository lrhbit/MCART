package com.axolotl.mcart.export

import com.axolotl.mcart.core.MaterialList
import com.axolotl.mcart.core.PixelArtGenerator
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * 统一导出格式与分发：结构文件、命令 / 数据包、物料清单、预览图。
 */
enum class ExportFormat(
    @JvmField val extension: String,
    @JvmField val description: String,
) {
    LITEMATIC(".litematic", "Litematica 投影（.litematic）"),
    SCHEM(".schem", "Sponge schematic（.schem，WorldEdit/FAWE）"),
    SCHEMATIC(".schematic", "经典 schematic（.schematic）"),
    NBT(".nbt", "原版结构文件（.nbt，结构方块）"),
    MCFUNCTION(".mcfunction", "setblock 命令序列（.mcfunction）"),
    DATAPACK(".zip", "数据包（.zip，载入自动建造）"),
    CSV(".csv", "物料清单（.csv，Excel 可开）"),
    PNG(".png", "预览图（.png）"),
    ;

    /** 工具栏下拉使用的简短标签 */
    fun shortLabel(): String = when (this) {
        LITEMATIC -> ".litematic"
        SCHEM -> ".schem"
        SCHEMATIC -> ".schematic"
        NBT -> ".nbt（结构方块）"
        MCFUNCTION -> ".mcfunction（命令）"
        DATAPACK -> "数据包 .zip"
        CSV -> "物料清单 .csv"
        PNG -> "预览图 .png"
    }

    fun needsImage(): Boolean = this == PNG

    /** 执行导出。PNG 格式需提供预览图，其余格式可传 null。 */
    fun export(
        result: PixelArtGenerator.Result,
        previewImage: BufferedImage?,
        path: Path,
    ) {
        when (this) {
            LITEMATIC -> LitematicExporter.export(result, path)
            SCHEM -> SchemExporter.export(result, path)
            SCHEMATIC -> SchematicExporter.export(result, path)
            NBT -> StructureNbtExporter.export(result, path)
            MCFUNCTION -> McFunctionExporter.export(result, path)
            DATAPACK -> DatapackExporter.export(result, path)
            CSV -> Files.writeString(
                path,
                MaterialList.toCsv(MaterialList.of(result)),
                StandardCharsets.UTF_8,
            )
            PNG -> {
                val img = previewImage ?: throw java.io.IOException("PNG 导出需要预览图")
                ImageIO.write(img, "png", path.toFile())
            }
        }
    }

    companion object {
        @JvmStatic
        fun fromIndex(i: Int): ExportFormat {
            val v = entries.toTypedArray()
            return if (i in v.indices) v[i] else LITEMATIC
        }
    }
}
