package com.axolotl.mcart.ui;

import java.awt.*;
import java.awt.image.BufferedImage;

/** 内置示例：程序化生成一张苦力怕脸图片，供首次启动 / 帮助菜单演示。 */
public final class SampleImage {

    private SampleImage() {}

    public static BufferedImage creeper() {
        int n = 16, cell = 32, size = n * cell;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x6FB04E));
        g.fillRect(0, 0, size, size);
        java.util.Random rnd = new java.util.Random(7);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int d = rnd.nextInt(18) - 9;
            g.setColor(new Color(Math.max(0, Math.min(255, 0x6F + d)),
                    Math.max(0, Math.min(255, 0xB0 + d)), Math.max(0, Math.min(255, 0x4E + d))));
            g.fillRect(x * cell, y * cell, cell, cell);
        }
        g.setColor(new Color(0x1C1C1C));
        for (int[] e : new int[][]{{3, 7}, {4, 7}, {5, 7}, {3, 8}, {4, 8}, {5, 8}, {4, 9},
                {10, 7}, {11, 7}, {12, 7}, {10, 8}, {11, 8}, {12, 8}, {11, 9}})
            g.fillRect(e[0] * cell, e[1] * cell, cell, cell);
        for (int x = 5; x <= 10; x++) for (int y = 11; y <= 12; y++) g.fillRect(x * cell, y * cell, cell, cell);
        for (int x = 6; x <= 9; x++) g.fillRect(x * cell, 13 * cell, cell, cell);
        for (int x = 7; x <= 8; x++) for (int y = 14; y <= 15; y++) g.fillRect(x * cell, y * cell, cell, cell);
        g.dispose();
        return img;
    }
}
