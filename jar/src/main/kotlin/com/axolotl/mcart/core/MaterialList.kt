package com.axolotl.mcart.core

import com.axolotl.mcart.i18n.I18n
import com.axolotl.mcart.model.BlockColor
import com.axolotl.mcart.model.BlockTag
import com.axolotl.mcart.model.PaletteService
import com.axolotl.mcart.util.BlockNames

/**
 * 物料清单（BOM）：统计顶层方块与支撑方块数量，导出 CSV。
 */
object MaterialList {

    @JvmStatic
    fun of(result: PixelArtGenerator.Result): List<Row> {
        val rows = LinkedHashMap<String, Row>()

        for (z in 0 until result.length) {
            for (x in 0 until result.width) {
                var topId: String? = null

                for (y in result.height - 1 downTo 0) {
                    val idx = result.blockIds[y][z][x]
                    if (idx >= 0) {
                        val e = result.palette[idx]
                        topId = e.baseColor.getBlockStateId(e.shade)
                        break
                    }
                }

                if (topId != null) {
                    rows.getOrPut(topId) { Row(topId) }.count++
                }

                val layers = result.heightMap[z][x] - 1
                if (layers > 0) {
                    val support = if (result.supportBlockId == "#same") {
                        topId ?: "minecraft:stone"
                    } else {
                        result.supportBlockId
                    }
                    rows.getOrPut(support) { Row(support) }.count += layers.toLong()
                }
            }
        }

        return rows.values.sortedByDescending { it.count }
    }

    @JvmStatic
    fun total(rows: List<Row>): Long = rows.sumOf { it.count }

    @JvmStatic
    fun toCsv(rows: List<Row>): String = buildString {
        append('﻿')
        append("方块名称,方块ID,类别,数量,标签\n")
        for (r in rows) {
            append(r.displayName.csv())
            append(',')
            append(r.blockId.csv())
            append(',')
            append(r.category.csv())
            append(',')
            append(r.count)
            append(',')
            append(r.tags.joinToString(";").csv())
            append('\n')
        }
        append("合计,,")
        append(total(rows))
        append('\n')
    }

    private fun String?.csv(): String {
        if (this == null) return ""
        if (!contains(",") && !contains("\"") && !contains("\n")) return this
        return "\"" + replace("\"", "\"\"") + "\""
    }

    /**
     * 物料行。字段 @JvmField，Java 侧可直接访问；count 可变。
     */
    class Row internal constructor(@JvmField val blockId: String) {
        @JvmField val displayName: String
        @JvmField val category: String
        @JvmField var count: Long = 0L
        @JvmField val tags: MutableList<String> = mutableListOf()

        init {
            val bc: BlockColor? = PaletteService.byId(blockId)
            displayName = BlockNames.of(blockId, bc?.name ?: blockId)
            category = bc?.category?.displayName() ?: I18n.t("bom.custom")
            if (bc != null) {
                for (t in BlockTag.tagsOf(bc)) tags.add(t.label(I18n.language()))
            }
        }
    }
}
