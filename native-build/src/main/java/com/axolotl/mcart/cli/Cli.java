package com.axolotl.mcart.cli;

import com.axolotl.mcart.core.ColorMatcher;
import com.axolotl.mcart.core.DitherMethod;
import com.axolotl.mcart.core.MaterialList;
import com.axolotl.mcart.core.PixelArtGenerator;
import com.axolotl.mcart.core.PreprocessSettings;
import com.axolotl.mcart.core.TexturePreviewRenderer;
import com.axolotl.mcart.export.ExportFormat;
import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.model.BlockPresetStore;
import com.axolotl.mcart.model.MapMode;
import com.axolotl.mcart.model.PaletteService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 命令行无头模式：不启动 GUI，直接把图片转换为结构文件 / 物料清单 / 预览图，
 * 可用于服务器流水线与脚本批处理。
 *
 * <pre>
 * java -jar main.jar --cli \
 *   --input cat.png --output out.litematic \
 *   --mode flat --dither BLUE_NOISE --strength 80 \
 *   --maps 2x2 --blocks survival --support stone
 *
 * --blocks 可选：default / survival / building / natural / all /
 *              逗号分隔的 blockId 列表 / 预设 JSON 文件路径
 * --format 可选：litematic schem schematic nbt mcfunction datapack csv png
 *              （缺省时按 --output 扩展名推断）
 * </pre>
 */
public final class Cli {

    private Cli() {}

    public static int run(String[] args) {
        String input = null, output = null;
        String mode = "flat", dither = "BLUE_NOISE", blocks = "default", support = "stone";
        int strength = 100, mapsW = 2, mapsH = 2;
        String format = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--cli" -> { }
                case "--input", "-i" -> input = args[++i];
                case "--output", "-o" -> output = args[++i];
                case "--mode" -> mode = args[++i];
                case "--dither" -> dither = args[++i];
                case "--strength" -> strength = Integer.parseInt(args[++i]);
                case "--maps" -> {
                    String[] wh = args[++i].toLowerCase(Locale.ROOT).split("[x×]");
                    mapsW = Integer.parseInt(wh[0].trim());
                    mapsH = wh.length > 1 ? Integer.parseInt(wh[1].trim()) : mapsW;
                }
                case "--maps-w" -> mapsW = Integer.parseInt(args[++i]);
                case "--maps-h" -> mapsH = Integer.parseInt(args[++i]);
                case "--blocks" -> blocks = args[++i];
                case "--support" -> support = args[++i];
                case "--format" -> format = args[++i];
                case "--help", "-h" -> { printHelp(); return 0; }
                default -> System.err.println("未知参数：" + a);
            }
        }

        if (input == null || output == null) {
            System.err.println("错误：必须指定 --input 与 --output");
            printHelp();
            return 2;
        }

        try {
            BufferedImage src = ImageIO.read(new File(input));
            if (src == null) {
                System.err.println("错误：无法读取图片 " + input);
                return 1;
            }

            MapMode mapMode = mode.toLowerCase(Locale.ROOT).contains("relief")
                    ? MapMode.RELIEF : MapMode.FLAT;
            DitherMethod method;
            try {
                method = DitherMethod.valueOf(dither.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                method = DitherMethod.BLUE_NOISE;
            }

            List<BlockColor> chosen = resolveBlocks(blocks);
            if (chosen.isEmpty()) {
                System.err.println("错误：方块集合为空");
                return 1;
            }
            String supportId = "same".equalsIgnoreCase(support) || "#same".equals(support)
                    ? "#same" : support.contains(":") ? support : "minecraft:" + support;

            ColorMatcher matcher = new ColorMatcher(chosen, mapMode);
            PreprocessSettings pp = PreprocessSettings.disabled();
            PixelArtGenerator gen = new PixelArtGenerator(matcher, method, mapMode,
                    supportId, pp, strength / 100.0, null);
            PixelArtGenerator.Result result = gen.generate(src, mapsW * 128, mapsH * 128);

            ExportFormat fmt = resolveFormat(format, output);
            Path out = Path.of(output);
            BufferedImage preview = null;
            if (fmt == ExportFormat.PNG) {
                preview = TexturePreviewRenderer.render(result);
            }
            fmt.export(result, preview, out);

            List<MaterialList.Row> bom = MaterialList.of(result);
            System.out.println("转换完成：" + output);
            System.out.println("  尺寸: " + result.width + "x" + result.length
                    + (result.height > 1 ? "（立体，高 " + result.height + "）" : "（平面）"));
            System.out.println("  方块总数: " + MaterialList.total(bom)
                    + "，种类: " + bom.size());
            return 0;
        } catch (Exception e) {
            System.err.println("转换失败：" + e);
            e.printStackTrace();
            return 1;
        }
    }

    private static List<BlockColor> resolveBlocks(String spec) {
        List<String> ids = switch (spec.toLowerCase(Locale.ROOT)) {
            case "default" -> PaletteService.defaultSelection();
            case "survival" -> PaletteService.survivalSelection();
            case "building" -> PaletteService.buildingSelection();
            case "natural" -> PaletteService.naturalSelection();
            case "all" -> {
                List<String> l = new ArrayList<>();
                for (BlockColor bc : PaletteService.allBlocks()) l.add(bc.blockId());
                yield l;
            }
            default -> {
                if (spec.contains(",")) {
                    List<String> l = new ArrayList<>();
                    for (String s : spec.split(",")) {
                        String id = s.trim();
                        if (id.contains(":")) l.add(id);
                        else if (!id.isEmpty()) l.add("minecraft:" + id);
                    }
                    yield l;
                }
                // 预设名或预设文件
                for (BlockPresetStore.Preset p : BlockPresetStore.all()) {
                    if (p.name().equals(spec)) yield p.blockIds();
                }
                File f = new File(spec);
                if (f.isFile()) {
                    try {
                        var m = com.axolotl.mcart.util.MiniJson.parseObject(
                                java.nio.file.Files.readString(f.toPath()));
                        List<String> l = new ArrayList<>();
                        for (Object o : com.axolotl.mcart.util.MiniJson.arr(m, "blocks")) {
                            l.add(String.valueOf(o));
                        }
                        yield l;
                    } catch (Exception ignored) {
                    }
                }
                yield PaletteService.defaultSelection();
            }
        };
        List<BlockColor> list = new ArrayList<>();
        for (String id : ids) {
            BlockColor bc = PaletteService.byId(id);
            if (bc != null) list.add(bc);
        }
        return list;
    }

    private static ExportFormat resolveFormat(String format, String output) {
        if (format != null) {
            for (ExportFormat f : ExportFormat.values()) {
                if (f.name().equalsIgnoreCase(format)
                        || f.extension.equalsIgnoreCase("." + format.replace(".", ""))) {
                    return f;
                }
            }
        }
        String lower = output.toLowerCase(Locale.ROOT);
        for (ExportFormat f : ExportFormat.values()) {
            if (lower.endsWith(f.extension)) return f;
        }
        return ExportFormat.LITEMATIC;
    }

    private static void printHelp() {
        System.out.println("""
                MC 地图画生成器 - 命令行模式
                用法:
                  java -jar main.jar --cli --input <图片> --output <输出文件> [选项]
                选项:
                  --mode flat|relief        平面 / 立体（默认 flat）
                  --dither <方法>           NONE/BLUE_NOISE/FLOYD_STEINBERG/BURKES/
                                            ATKINSON/STUCKI/JARVIS/SIERRA/ORDERED
                  --strength 0-100          抖动强度（默认 100）
                  --maps WxH                地图张数（默认 2x2）
                  --blocks <集合>           default/survival/building/natural/all
                                            或逗号分隔 blockId，或预设 JSON 文件
                  --support <方块|same>     立体支撑方块（默认 stone）
                  --format <格式>           litematic/schem/schematic/nbt/
                                            mcfunction/datapack/csv/png
                  -h, --help                显示帮助
                """);
    }
}
