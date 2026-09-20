package com.axolotl.mcart.util;

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

    /** 返回中文名；没有映射时退回英文原名 */
    public static String of(String blockId, String fallback) {
        return NAMES.getOrDefault(blockId, fallback);
    }
}
