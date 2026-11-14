package com.axolotl.mcart.util;

import com.axolotl.mcart.i18n.I18n;

import java.util.HashMap;
import java.util.Map;

/**
 * 方块中文名映射（按 blockId）
 */
public final class BlockNames {

    private BlockNames() {}

    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        String[][] colors = {
                {"white", "白色"}, {"orange", "橙色"}, {"magenta", "品红色"},
                {"light_blue", "淡蓝色"}, {"yellow", "黄色"}, {"lime", "黄绿色"},
                {"pink", "粉红色"}, {"gray", "灰色"}, {"light_gray", "淡灰色"},
                {"cyan", "青色"}, {"purple", "紫色"}, {"blue", "蓝色"},
                {"brown", "棕色"}, {"green", "绿色"}, {"red", "红色"}, {"black", "黑色"}
        };
        for (String[] c : colors) {
            NAMES.put("minecraft:" + c[0] + "_wool", c[1] + "羊毛");
            NAMES.put("minecraft:" + c[0] + "_concrete", c[1] + "混凝土");
            NAMES.put("minecraft:" + c[0] + "_terracotta", c[1] + "陶瓦");
        }

        NAMES.put("minecraft:grass_block", "草方块");
        NAMES.put("minecraft:sand", "沙子");
        NAMES.put("minecraft:tnt", "TNT");
        NAMES.put("minecraft:ice", "冰");
        NAMES.put("minecraft:iron_block", "铁块");
        NAMES.put("minecraft:oak_leaves", "橡树树叶");
        NAMES.put("minecraft:snow_block", "雪块");
        NAMES.put("minecraft:clay", "黏土块");
        NAMES.put("minecraft:dirt", "泥土");
        NAMES.put("minecraft:stone", "石头");
        NAMES.put("minecraft:water", "水");
        NAMES.put("minecraft:oak_log", "橡木原木");
        NAMES.put("minecraft:quartz_block", "石英块");
        NAMES.put("minecraft:gold_block", "金块");
        NAMES.put("minecraft:diamond_block", "钻石块");
        NAMES.put("minecraft:lapis_block", "青金石块");
        NAMES.put("minecraft:emerald_block", "绿宝石块");
        NAMES.put("minecraft:podzol", "灰化土");
        NAMES.put("minecraft:nether_wart_block", "下界疣块");
        NAMES.put("minecraft:crimson_nylium", "绯红菌岩");
        NAMES.put("minecraft:crimson_stem", "绯红菌柄");
        NAMES.put("minecraft:crimson_planks", "绯红木板");
        NAMES.put("minecraft:warped_nylium", "诡异菌岩");
        NAMES.put("minecraft:warped_stem", "诡异菌柄");
        NAMES.put("minecraft:warped_planks", "诡异木板");
        NAMES.put("minecraft:deepslate", "深板岩");
        NAMES.put("minecraft:raw_iron_block", "粗铁块");
        NAMES.put("minecraft:moss_block", "苔藓块");
        NAMES.put("minecraft:mud", "泥巴");
        NAMES.put("minecraft:mangrove_roots", "红树根");
        NAMES.put("minecraft:muddy_mangrove_roots", "沾泥的红树根");
        NAMES.put("minecraft:cherry_leaves", "樱花树叶");
        NAMES.put("minecraft:cherry_log", "樱花原木");
        NAMES.put("minecraft:pale_moss_block", "苍白苔藓块");
        NAMES.put("minecraft:pale_oak_log", "苍白橡木原木");
        NAMES.put("minecraft:terracotta", "陶瓦");
    }

    /** 返回当前界面语言下的方块名；没有映射时退回英文原名 */
    public static String of(String blockId, String fallback) {
        if (I18n.isEn()) return englishName(blockId, fallback);
        return NAMES.getOrDefault(blockId, fallback);
    }

    /** 英文界面：按 MC 命名习惯从 blockId 派生英文名 */
    private static String englishName(String blockId, String fallback) {
        if (blockId == null || blockId.isBlank()) return fallback;
        String path = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;

        String[][] colors = {
                {"white", "White"}, {"orange", "Orange"}, {"magenta", "Magenta"},
                {"light_blue", "Light Blue"}, {"yellow", "Yellow"}, {"lime", "Lime"},
                {"pink", "Pink"}, {"gray", "Gray"}, {"light_gray", "Light Gray"},
                {"cyan", "Cyan"}, {"purple", "Purple"}, {"blue", "Blue"},
                {"brown", "Brown"}, {"green", "Green"}, {"red", "Red"}, {"black", "Black"}
        };
        for (String[] c : colors) {
            if (path.equals(c[0] + "_wool")) return c[1] + " Wool";
            if (path.equals(c[0] + "_concrete")) return c[1] + " Concrete";
            if (path.equals(c[0] + "_terracotta")) return c[1] + " Terracotta";
            if (path.equals(c[0] + "_glazed_terracotta")) return c[1] + " Glazed Terracotta";
            if (path.equals(c[0] + "_stained_glass")) return c[1] + " Stained Glass";
            if (path.equals(c[0] + "_stained_glass_pane")) return c[1] + " Stained Glass Pane";
        }
        String override = EN_OVERRIDES.get(path);
        if (override != null) return override;
        return humanize(path);
    }

    private static final Map<String, String> EN_OVERRIDES = new HashMap<>();
    static {
        EN_OVERRIDES.put("tnt", "TNT");
        EN_OVERRIDES.put("terracotta", "Terracotta");
        EN_OVERRIDES.put("grass_block", "Grass Block");
        EN_OVERRIDES.put("oak_leaves", "Oak Leaves");
        EN_OVERRIDES.put("cherry_leaves", "Cherry Leaves");
        EN_OVERRIDES.put("oak_log", "Oak Log");
        EN_OVERRIDES.put("cherry_log", "Cherry Log");
        EN_OVERRIDES.put("pale_oak_log", "Pale Oak Log");
        EN_OVERRIDES.put("iron_block", "Block of Iron");
        EN_OVERRIDES.put("gold_block", "Block of Gold");
        EN_OVERRIDES.put("diamond_block", "Block of Diamond");
        EN_OVERRIDES.put("lapis_block", "Lapis Lazuli Block");
        EN_OVERRIDES.put("emerald_block", "Block of Emerald");
        EN_OVERRIDES.put("quartz_block", "Block of Quartz");
        EN_OVERRIDES.put("raw_iron_block", "Block of Raw Iron");
        EN_OVERRIDES.put("snow_block", "Snow Block");
        EN_OVERRIDES.put("clay", "Clay");
        EN_OVERRIDES.put("nether_wart_block", "Nether Wart Block");
        EN_OVERRIDES.put("crimson_nylium", "Crimson Nylium");
        EN_OVERRIDES.put("crimson_stem", "Crimson Stem");
        EN_OVERRIDES.put("crimson_planks", "Crimson Planks");
        EN_OVERRIDES.put("warped_nylium", "Warped Nylium");
        EN_OVERRIDES.put("warped_stem", "Warped Stem");
        EN_OVERRIDES.put("warped_planks", "Warped Planks");
        EN_OVERRIDES.put("moss_block", "Moss Block");
        EN_OVERRIDES.put("pale_moss_block", "Pale Moss Block");
        EN_OVERRIDES.put("mangrove_roots", "Mangrove Roots");
        EN_OVERRIDES.put("muddy_mangrove_roots", "Muddy Mangrove Roots");
    }

    private static String humanize(String path) {
        String[] words = path.replace('/', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.length() == 0 ? path : sb.toString();
    }
}
