package com.axolotl.mcart.export;

import com.axolotl.mcart.core.PixelArtGenerator;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Legacy MCEdit .schematic 导出器（1.12及以前格式）
 * 使用数字方块ID + data值
 *
 * 结构:
 * └─ Root Compound "Schematic"
 *    ├─ Width: Short
 *    ├─ Height: Short
 *    ├─ Length: Short
 *    ├─ Materials: String "Alpha"
 *    ├─ Blocks: Byte_Array
 *    ├─ Data: Byte_Array
 *    ├─ Entities: List
 *    └─ TileEntities: List
 */
public class SchematicExporter {

    /** 方块ID映射（简化版，只包含我们调色板中用到的方块） */
    private static final Map<String, int[]> BLOCK_ID_MAP = new HashMap<>();

    static {
        // wool: id=35, data=0-15
        for (int i = 0; i <= 15; i++) {
            String colorName = switch (i) {
                case 0 -> "white"; case 1 -> "orange"; case 2 -> "magenta";
                case 3 -> "light_blue"; case 4 -> "yellow"; case 5 -> "lime";
                case 6 -> "pink"; case 7 -> "gray"; case 8 -> "light_gray";
                case 9 -> "cyan"; case 10 -> "purple"; case 11 -> "blue";
                case 12 -> "brown"; case 13 -> "green"; case 14 -> "red";
                default -> "black";
            };
            BLOCK_ID_MAP.put("minecraft:" + colorName + "_wool", new int[]{35, i});
        }
        // concrete: id=251, data=0-15
        for (int i = 0; i <= 15; i++) {
            String colorName = switch (i) {
                case 0 -> "white"; case 1 -> "orange"; case 2 -> "magenta";
                case 3 -> "light_blue"; case 4 -> "yellow"; case 5 -> "lime";
                case 6 -> "pink"; case 7 -> "gray"; case 8 -> "light_gray";
                case 9 -> "cyan"; case 10 -> "purple"; case 11 -> "blue";
                case 12 -> "brown"; case 13 -> "green"; case 14 -> "red";
                default -> "black";
            };
            BLOCK_ID_MAP.put("minecraft:" + colorName + "_concrete", new int[]{251, i});
        }
        // terracotta: id=159, data=0-15
        for (int i = 0; i <= 15; i++) {
            String colorName = switch (i) {
                case 0 -> "white"; case 1 -> "orange"; case 2 -> "magenta";
                case 3 -> "light_blue"; case 4 -> "yellow"; case 5 -> "lime";
                case 6 -> "pink"; case 7 -> "gray"; case 8 -> "light_gray";
                case 9 -> "cyan"; case 10 -> "purple"; case 11 -> "blue";
                case 12 -> "brown"; case 13 -> "green"; case 14 -> "red";
                default -> "black";
            };
            BLOCK_ID_MAP.put("minecraft:" + colorName + "_terracotta", new int[]{159, i});
        }
        // 常见方块
        BLOCK_ID_MAP.put("minecraft:air", new int[]{0, 0});
        BLOCK_ID_MAP.put("minecraft:stone", new int[]{1, 0});
        BLOCK_ID_MAP.put("minecraft:cobblestone", new int[]{4, 0});
        BLOCK_ID_MAP.put("minecraft:deepslate", new int[]{1, 0}); // 1.12 无深板岩，回退石头
        BLOCK_ID_MAP.put("minecraft:andesite", new int[]{1, 5});
        BLOCK_ID_MAP.put("minecraft:grass_block", new int[]{2, 0});
        BLOCK_ID_MAP.put("minecraft:dirt", new int[]{3, 0});
        BLOCK_ID_MAP.put("minecraft:sand", new int[]{12, 0});
        BLOCK_ID_MAP.put("minecraft:gold_block", new int[]{41, 0});
        BLOCK_ID_MAP.put("minecraft:iron_block", new int[]{42, 0});
        BLOCK_ID_MAP.put("minecraft:diamond_block", new int[]{57, 0});
        BLOCK_ID_MAP.put("minecraft:emerald_block", new int[]{133, 0});
        BLOCK_ID_MAP.put("minecraft:lapis_block", new int[]{22, 0});
        BLOCK_ID_MAP.put("minecraft:quartz_block", new int[]{155, 0});
        BLOCK_ID_MAP.put("minecraft:redstone_block", new int[]{152, 0});
        BLOCK_ID_MAP.put("minecraft:clay", new int[]{82, 0});
        BLOCK_ID_MAP.put("minecraft:tnt", new int[]{46, 0});
        BLOCK_ID_MAP.put("minecraft:snow_block", new int[]{80, 0});
    }

    public static void export(PixelArtGenerator.Result result, Path outputPath) throws IOException {
        NBTWriter writer = new NBTWriter();

        writer.writeRootCompound("Schematic");

        writer.writeShort("Width", (short) result.width);
        writer.writeShort("Height", (short) result.height);
        writer.writeShort("Length", (short) result.length);
        writer.writeString("Materials", "Alpha");

        int totalBlocks = result.width * result.height * result.length;
        byte[] blocks = new byte[totalBlocks];
        byte[] data = new byte[totalBlocks];

        int index = 0;
        // Legacy格式: 索引顺序 y*Width*Length + z*Width + x
        for (int y = 0; y < result.height; y++) {
            for (int z = 0; z < result.length; z++) {
                for (int x = 0; x < result.width; x++) {
                    int blockIdx = result.blockIds[y][z][x];

                    int blockId = 0; // air
                    int blockData = 0;

                    if (blockIdx == PixelArtGenerator.SUPPORT) {
                        String state = "#same".equals(result.supportBlockId)
                                ? SchemExporter.topState(result, x, z) : result.supportBlockId;
                        int[] idData = BLOCK_ID_MAP.getOrDefault(state, new int[]{1, 0});
                        blockId = idData[0];
                        blockData = idData[1];
                    } else if (blockIdx >= 0) {
                        var entry = result.palette.get(blockIdx);
                        String blockState = entry.baseColor.getBlockStateId(entry.shade);
                        int[] idData = BLOCK_ID_MAP.getOrDefault(blockState, new int[]{1, 0});
                        blockId = idData[0];
                        blockData = idData[1];
                    }

                    blocks[index] = (byte) (blockId & 0xFF);
                    data[index] = (byte) (blockData & 0xFF);
                    index++;
                }
            }
        }

        writer.writeByteArray("Blocks", blocks);
        writer.writeByteArray("Data", data);
        writer.writeEmptyList("Entities");
        writer.writeEmptyList("TileEntities");

        writer.endCompound(); // Schematic

        Files.write(outputPath, writer.toGzippedByteArray());
    }
}
