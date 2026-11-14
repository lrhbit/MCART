package com.axolotl.mcart.export;

import com.axolotl.mcart.core.PixelArtGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版结构方块格式（.nbt，1.13+，gzip 压缩）导出器。
 * 可由结构方块 / {@code /place template} 加载。
 *
 * <pre>
 * root
 * ├─ size: Int_Array [W,H,L]
 * ├─ palette: List<Compound {Name, Properties?}>
 * ├─ blocks:  List<Compound {pos:[x,y,z], state}>
 * └─ entities: List<Compound>（空）
 * </pre>
 */
public class StructureNbtExporter {

    public static void export(PixelArtGenerator.Result result, Path outputPath) throws IOException {
        // 构建调色板（air 在 state 0）
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        for (var entry : result.palette) {
            String state = entry.baseColor.getBlockStateId(entry.shade);
            palette.putIfAbsent(state, palette.size());
        }
        if (result.height > 1 && !"#same".equals(result.supportBlockId)) {
            palette.putIfAbsent(result.supportBlockId, palette.size());
        }

        NBTWriter w = new NBTWriter();
        w.writeRootCompound("");

        w.writeIntArray("size", new int[]{result.width, result.height, result.length});

        // palette
        w.beginList("palette", NBTWriter.TAG_Compound, palette.size());
        for (String state : palette.keySet()) {
            w.beginListCompoundItem();
            w.writeString("Name", state);
            w.endCompound();
        }

        // blocks（仅写非空气）
        List<int[]> placed = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        for (int y = 0; y < result.height; y++) {
            for (int z = 0; z < result.length; z++) {
                for (int x = 0; x < result.width; x++) {
                    int idx = result.blockIds[y][z][x];
                    if (idx < 0) {
                        if (idx == PixelArtGenerator.AIR) continue;
                        // SUPPORT
                        String state = "#same".equals(result.supportBlockId)
                                ? SchemExporter.topState(result, x, z) : result.supportBlockId;
                        Integer si = palette.get(state);
                        if (si != null && si != 0) {
                            placed.add(new int[]{x, y, z});
                            states.add(si);
                        }
                        continue;
                    }
                    var e = result.palette.get(idx);
                    String state = e.baseColor.getBlockStateId(e.shade);
                    Integer si = palette.get(state);
                    if (si != null && si != 0) {
                        placed.add(new int[]{x, y, z});
                        states.add(si);
                    }
                }
            }
        }
        w.beginList("blocks", NBTWriter.TAG_Compound, placed.size());
        for (int i = 0; i < placed.size(); i++) {
            int[] pos = placed.get(i);
            w.beginListCompoundItem();
            w.writeIntArray("pos", pos);
            w.writeInt("state", states.get(i));
            w.endCompound();
        }

        w.writeEmptyList("entities", NBTWriter.TAG_Compound);
        w.endCompound(); // root

        Files.write(outputPath, w.toGzippedByteArray());
    }
}
