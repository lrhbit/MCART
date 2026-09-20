package com.axolotl.mcart.util;

import com.axolotl.mcart.model.BlockColor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 方块纹理管理器
 * 从 classpath:/textures/block 或文件系统加载真实MC方块纹理（16×16）。
 * 对草、水、树叶等灰度贴图自动做生物群系染色；水的动画贴图只取第一帧。
 * 加载失败时用方块基色程序化生成兜底纹理，保证界面永不出现空白/崩溃。
 */
public final class TextureManager {

    private TextureManager() {}

    /** blockId → 纹理文件名 */
    private static final Map<String, String> TEXTURE_FILE = new HashMap<>();
    /** 纹理文件名 → 染色RGB（仅灰度贴图需要），0表示不染色 */
    private static final Map<String, Integer> TINT = new HashMap<>();

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    static {
        String[] colors = {
                "white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue",
                "brown", "green", "red", "black"
        };
        for (String c : colors) {
            TEXTURE_FILE.put("minecraft:" + c + "_wool", c + "_wool.png");
            TEXTURE_FILE.put("minecraft:" + c + "_concrete", c + "_concrete.png");
            TEXTURE_FILE.put("minecraft:" + c + "_terracotta", c + "_terracotta.png");
        }

        // 自然 / 矿物 / 特殊方块（顶面贴图）
        put("minecraft:grass_block", "grass_block_top.png");
        put("minecraft:sand", "sand.png");
        put("minecraft:tnt", "tnt_top.png");
        put("minecraft:ice", "ice.png");
        put("minecraft:iron_block", "iron_block.png");
        put("minecraft:oak_leaves", "oak_leaves.png");
        put("minecraft:snow_block", "snow.png");
        put("minecraft:clay", "clay.png");
        put("minecraft:dirt", "dirt.png");
        put("minecraft:stone", "stone.png");
        put("minecraft:water", "water_still.png");
        put("minecraft:oak_log", "oak_log_top.png");
        put("minecraft:quartz_block", "quartz_block_top.png");
        put("minecraft:gold_block", "gold_block.png");
        put("minecraft:diamond_block", "diamond_block.png");
        put("minecraft:lapis_block", "lapis_block.png");
        put("minecraft:emerald_block", "emerald_block.png");
        put("minecraft:podzol", "podzol_top.png");
        put("minecraft:nether_wart_block", "nether_wart_block.png");
        put("minecraft:crimson_nylium", "crimson_nylium.png");
        put("minecraft:crimson_stem", "crimson_stem_top.png");
        put("minecraft:crimson_planks", "crimson_planks.png");
        put("minecraft:warped_nylium", "warped_nylium.png");
        put("minecraft:warped_stem", "warped_stem_top.png");
        put("minecraft:warped_planks", "warped_planks.png");
        put("minecraft:deepslate", "deepslate.png");
        put("minecraft:raw_iron_block", "raw_iron_block.png");
        put("minecraft:moss_block", "moss_block.png");
        put("minecraft:mud", "mud.png");
        put("minecraft:mangrove_roots", "mangrove_roots_top.png");
        put("minecraft:muddy_mangrove_roots", "muddy_mangrove_roots_top.png");
        put("minecraft:cherry_leaves", "cherry_leaves.png");
        put("minecraft:cherry_log", "cherry_log_top.png");
        put("minecraft:pale_moss_block", "pale_moss_block.png");
        put("minecraft:pale_oak_log", "pale_oak_log_top.png");
        put("minecraft:terracotta", "terracotta.png");

        // 灰度贴图生物群系染色（经典平原配色）
        TINT.put("grass_block_top.png", 0x7CBD3B); // 草绿
        TINT.put("oak_leaves.png",    0x598C39);  // 橡树叶绿
        TINT.put("water_still.png",   0x3F76E4);  // 水蓝
    }

    private static void put(String blockId, String file) {
        TEXTURE_FILE.put(blockId, file);
    }

    /** 按方块定义获取纹理（带缓存） */
    public static BufferedImage get(BlockColor bc) {
        String file = TEXTURE_FILE.get(bc.blockId());
        if (file == null) {
            return fallback(bc.r(), bc.g(), bc.b());
        }
        BufferedImage img = CACHE.get(file);
        if (img == null) {
            img = load(file);
            if (img == null) {
                img = fallback(bc.r(), bc.g(), bc.b());
            } else {
                img = postProcess(file, img);
            }
            CACHE.put(file, img);
        }
        return img;
    }

    private static BufferedImage load(String file) {
        // 1. classpath（Maven/IDEA 打包后）
        try (InputStream in = TextureManager.class.getResourceAsStream("/textures/block/" + file)) {
            if (in != null) return ImageIO.read(in);
        } catch (Exception ignored) {}

        // 2. 文件系统多路径兜底（手动 javac / java -jar 场景）
        String[] candidates = {
                "textures/block/" + file,
                "src/main/resources/textures/block/" + file,
                "../textures/block/" + file,
                "../src/main/resources/textures/block/" + file
        };
        for (String path : candidates) {
            try {
                File f = new File(path);
                if (f.isFile()) return ImageIO.read(f);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** 后处理：动画贴图取首帧；灰度贴图染色 */
    private static BufferedImage postProcess(String file, BufferedImage raw) {
        // 水等动画贴图是 16×(16*N)，只取第一帧
        BufferedImage src = raw;
        if (raw.getHeight() > raw.getWidth() && raw.getWidth() == 16) {
            src = raw.getSubimage(0, 0, 16, 16);
        }

        Integer tint = TINT.get(file);
        if (tint == null) return toArgb(src);

        float tr = ((tint >> 16) & 0xFF) / 255f;
        float tg = ((tint >> 8) & 0xFF) / 255f;
        float tb = (tint & 0xFF) / 255f;

        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24);
                int r = (int) (((p >> 16) & 0xFF) * tr);
                int g = (int) (((p >> 8) & 0xFF) * tg);
                int b = (int) ((p & 0xFF) * tb);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB && src.getWidth() == 16 && src.getHeight() == 16) {
            return src;
        }
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, 16, 16, null);
        g.dispose();
        return out;
    }

    /** 找不到纹理时的兜底：基色 + 轻微噪点的程序化纹理 */
    private static BufferedImage fallback(int r, int g, int b) {
        String key = "fb_" + r + "_" + g + "_" + b;
        BufferedImage cached = CACHE.get(key);
        if (cached != null) return cached;

        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.util.Random rnd = new java.util.Random(r * 9173L + g * 37L + b);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int d = rnd.nextInt(16) - 8;
                int rr = Math.max(0, Math.min(255, r + d));
                int gg = Math.max(0, Math.min(255, g + d));
                int bb = Math.max(0, Math.min(255, b + d));
                img.setRGB(x, y, 0xFF000000 | (rr << 16) | (gg << 8) | bb);
            }
        }
        CACHE.put(key, img);
        return img;
    }
}
