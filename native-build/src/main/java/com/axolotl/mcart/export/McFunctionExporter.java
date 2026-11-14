package com.axolotl.mcart.export;

import com.axolotl.mcart.core.PixelArtGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * setblock 命令序列导出（.mcfunction，1.13+）。
 * 坐标为相对执行位置的 ~x ~y ~z；立体结构包含支撑方块。
 * 用法：在地图画起始位置执行 /function mcpixel:build（数据包），
 * 或把本文件内容逐行粘贴进命令方块 / 函数中。
 */
public class McFunctionExporter {

    public static void export(PixelArtGenerator.Result result, Path outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# MC Pixel Art Converter - setblock 命令序列\n");
        sb.append("# 尺寸: ").append(result.width).append('x').append(result.height)
          .append('x').append(result.length).append('\n');
        sb.append("# 在地图画起始位置（西北角底部）执行本函数\n\n");

        int lines = 0;
        for (int y = 0; y < result.height; y++) {
            for (int z = 0; z < result.length; z++) {
                for (int x = 0; x < result.width; x++) {
                    int idx = result.blockIds[y][z][x];
                    String state;
                    if (idx == PixelArtGenerator.AIR) continue;
                    if (idx == PixelArtGenerator.SUPPORT) {
                        state = "#same".equals(result.supportBlockId)
                                ? SchemExporter.topState(result, x, z) : result.supportBlockId;
                    } else {
                        var e = result.palette.get(idx);
                        state = e.baseColor.getBlockStateId(e.shade);
                    }
                    sb.append("setblock ~").append(x).append(" ~").append(y)
                      .append(" ~").append(z).append(' ').append(state).append('\n');
                    lines++;
                }
            }
        }
        sb.append("\n# 共 ").append(lines).append(" 个方块\n");
        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
    }
}
