package com.axolotl.mcart.export;

import com.axolotl.mcart.core.PixelArtGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sponge Schematic v2 (.schem) 导出器
 * 兼容 WorldEdit 7+ / FAWE
 *
 * 结构:
 * └─ Root Compound
 *    ├─ Version: Int (2)
 *    ├─ Width: Short
 *    ├─ Height: Short
 *    ├─ Length: Short
 *    ├─ PaletteMax: Int
 *    ├─ Palette: Compound { blockstate: Int }
 *    ├─ BlockData: Byte_Array (位打包)
 *    ├─ BlockEntities: List
 *    ├─ Entities: List
 *    └─ Metadata: Compound
 */
public class SchemExporter {

    public static void export(PixelArtGenerator.Result result, Path outputPath) throws IOException {
        NBTWriter writer = new NBTWriter();

        writer.writeRootCompound("");

        writer.writeInt("Version", 2);
        writer.writeShort("Width", (short) result.width);
        writer.writeShort("Height", (short) result.height);
        writer.writeShort("Length", (short) result.length);

        // 构建Palette（air=0）
        Map<String, Integer> paletteMap = new LinkedHashMap<>();
        paletteMap.put("minecraft:air", 0);
        for (var entry : result.palette) {
            String blockState = entry.baseColor.getBlockStateId(entry.shade);
            if (!paletteMap.containsKey(blockState)) {
                paletteMap.put(blockState, paletteMap.size());
            }
        }
        // 立体结构的支撑方块（“与顶层同色”时各列状态已在显示调色板中，无需额外加入）
        if (result.height > 1 && !"#same".equals(result.supportBlockId)
                && !paletteMap.containsKey(result.supportBlockId)) {
            paletteMap.put(result.supportBlockId, paletteMap.size());
        }

        writer.writeInt("PaletteMax", paletteMap.size());
        writer.writeCompoundMap("Palette", paletteMap);

        // 位打包BlockData
        byte[] blockData = packBlockData(result, paletteMap);
        writer.writeByteArray("BlockData", blockData);

        writer.writeEmptyList("BlockEntities");
        writer.writeEmptyList("Entities");

        writer.beginCompound("Metadata");
        writer.writeString("Name", "MC Pixel Art");
        writer.writeString("Author", "mc-pixel-art-converter");
        writer.endCompound();

        writer.endCompound(); // root

        Files.write(outputPath, writer.toGzippedByteArray());
    }

    /**
     * BlockData（Sponge v2 标准：每个调色板索引按 LEB128 VarInt 连续写入）。
     * 索引顺序: index = y*Width*Length + z*Width + x
     */
    private static byte[] packBlockData(PixelArtGenerator.Result result,
                                         Map<String, Integer> paletteMap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int y = 0; y < result.height; y++) {
            for (int z = 0; z < result.length; z++) {
                for (int x = 0; x < result.width; x++) {
                    int blockIdx = result.blockIds[y][z][x];

                    int paletteIdx;
                    if (blockIdx == PixelArtGenerator.SUPPORT) {
                        String state = "#same".equals(result.supportBlockId)
                                ? topState(result, x, z) : result.supportBlockId;
                        paletteIdx = paletteMap.get(state);
                    } else if (blockIdx >= 0) {
                        var entry = result.palette.get(blockIdx);
                        String blockState = entry.baseColor.getBlockStateId(entry.shade);
                        paletteIdx = paletteMap.get(blockState);
                    } else {
                        paletteIdx = 0; // air
                    }
                    writeVarInt(baos, paletteIdx);
                }
            }
        }
        return baos.toByteArray();
    }

    /** LEB128 无符号 VarInt */
    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    /** 取某列顶层显示方块的 block state（“与顶层同色”支撑用） */
    static String topState(PixelArtGenerator.Result result, int x, int z) {
        for (int y = result.height - 1; y >= 0; y--) {
            int idx = result.blockIds[y][z][x];
            if (idx >= 0) {
                var e = result.palette.get(idx);
                return e.baseColor.getBlockStateId(e.shade);
            }
        }
        return "minecraft:stone";
    }
}
