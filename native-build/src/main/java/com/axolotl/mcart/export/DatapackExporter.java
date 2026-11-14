package com.axolotl.mcart.export;

import com.axolotl.mcart.core.PixelArtGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 数据包导出（.zip，可直接解压到存档 datapacks/ 目录）。
 * 载入存档后自动执行 data/mcpixel/function/build.mcfunction 完成建造。
 */
public class DatapackExporter {

    public static void export(PixelArtGenerator.Result result, Path outputPath) throws IOException {
        StringBuilder fn = new StringBuilder();
        for (int y = 0; y < result.height; y++) {
            for (int z = 0; z < result.length; z++) {
                for (int x = 0; x < result.width; x++) {
                    int idx = result.blockIds[y][z][x];
                    if (idx == PixelArtGenerator.AIR) continue;
                    String state;
                    if (idx == PixelArtGenerator.SUPPORT) {
                        state = "#same".equals(result.supportBlockId)
                                ? SchemExporter.topState(result, x, z) : result.supportBlockId;
                    } else {
                        var e = result.palette.get(idx);
                        state = e.baseColor.getBlockStateId(e.shade);
                    }
                    fn.append("setblock ~").append(x).append(" ~").append(y)
                      .append(" ~").append(z).append(' ').append(state).append('\n');
                }
            }
        }

        String packMcmeta = """
                {"pack":{"pack_format":15,"description":"MC Pixel Art - auto build datapack"}}
                """;
        String loadTag = """
                {"values":["mcpixel:build"]}
                """;

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
            putEntry(zip, "pack.mcmeta", packMcmeta.getBytes(StandardCharsets.UTF_8));
            putEntry(zip, "data/mcpixel/function/build.mcfunction",
                    fn.toString().getBytes(StandardCharsets.UTF_8));
            putEntry(zip, "data/minecraft/tags/function/load.json",
                    loadTag.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }
}
