package com.axolotl.mcart.core;

import com.axolotl.mcart.util.TextureManager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 立体地图画的软件 3D 渲染器（正交投影，画家算法）。
 * 相机可任意旋转/俯仰/缩放/平移；pitch=90° 时为正俯视图。
 * 顶面显示方块真实纹理（模型外观），露出的侧面按 MC 面光照压暗
 * （南北面 ×0.8、东西面 ×0.6）。
 */
public final class Model3DRenderer {

    private Model3DRenderer() {}

    /** 相机参数 */
    public static class Camera {
        public double yaw = 0;            // 绕Y轴旋转（弧度）
        public double pitch = Math.PI / 2; // 俯仰角，π/2=正俯视，0=平视
        public double zoom = 4;            // 每方块像素
        public double panX = 0, panY = 0;  // 屏幕平移（像素）

        public void resetTopDown() {
            yaw = 0;
            pitch = Math.PI / 2;
            panX = 0;
            panY = 0;
        }

        public Camera copy() {
            Camera c = new Camera();
            c.yaw = yaw; c.pitch = pitch; c.zoom = zoom; c.panX = panX; c.panY = panY;
            return c;
        }
    }

    /** 计算俯视复位时适配视口的 zoom */
    public static double fitZoom(PixelArtGenerator.Result r, int viewW, int viewH) {
        double zx = (viewW * 0.92) / Math.max(1, r.width);
        double zy = (viewH * 0.92) / Math.max(1, r.length);
        return Math.max(0.5, Math.min(zx, zy));
    }

    /**
     * 渲染模型
     * @param textured true=贴方块纹理（精细），false=纯色平涂（拖动时快速预览）
     */
    public static BufferedImage render(PixelArtGenerator.Result result, Camera cam,
                                       int viewW, int viewH, boolean textured) {
        BufferedImage img = new BufferedImage(Math.max(8, viewW), Math.max(8, viewH),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setColor(new Color(0xF2, 0xF2, 0xF5));
        g.fillRect(0, 0, viewW, viewH);

        int W = result.width, L = result.length, H = result.height;
        double cx0 = W / 2.0, cy0 = H / 2.0, cz0 = L / 2.0;
        double cx = viewW / 2.0 + cam.panX;
        double cy = viewH / 2.0 + cam.panY;

        double cosY = Math.cos(cam.yaw), sinY = Math.sin(cam.yaw);
        double cosP = Math.cos(cam.pitch), sinP = Math.sin(cam.pitch);

        // 正俯视时侧面退化为不可见，直接跳过，大幅减少面数
        boolean topDown = cam.pitch >= Math.PI / 2 - 0.02;

        // 收集所有可见面，按深度排序
        List<Face> faces = new ArrayList<>(W * L * 2);
        for (int z = 0; z < L; z++) {
            for (int x = 0; x < W; x++) {
                int top = result.heightMap[z][x];
                ColorMatcher.PaletteEntry entry = result.topEntries[z][x];
                if (entry == null) continue;

                // 顶面
                double[] c00 = project(x,     top, z,     cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
                double[] c10 = project(x + 1, top, z,     cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
                double[] c11 = project(x + 1, top, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
                double[] c01 = project(x,     top, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
                faces.add(new Face(new double[][]{c00, c10, c11, c01}, entry, 1.0, false));

                if (topDown) continue;

                // 背面剔除：每个轴向只有朝相机的一侧可见
                int hN = z > 0     ? result.heightMap[z - 1][x] : 0;
                int hS = z < L - 1 ? result.heightMap[z + 1][x] : 0;
                int hW = x > 0     ? result.heightMap[z][x - 1] : 0;
                int hE = x < W - 1 ? result.heightMap[z][x + 1] : 0;
                if (cosY > 0) addSides(faces, x, z, top, hN, 0, entry, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy, textured);
                else         addSides(faces, x, z, top, hS, 1, entry, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy, textured);
                if (sinY > 0) addSides(faces, x, z, top, hE, 3, entry, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy, textured);
                else          addSides(faces, x, z, top, hW, 2, entry, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy, textured);
            }
        }

        faces.sort((a, b) -> Double.compare(a.depth, b.depth));
        boolean showTopGrid = cam.zoom >= 8;
        for (Face f : faces) {
            drawFace(g, f, textured, f.isSide || showTopGrid);
        }
        g.dispose();
        return img;
    }

    /**
     * dir: 0=N(z-面) 1=S(z+面) 2=W(x-面) 3=E(x+面)。
     * 纹理模式逐格分段（每格一块完整纹理）；快速模式合并为一整个立面。
     */
    private static void addSides(List<Face> faces, int x, int z, int top, int neighborTop,
                                 int dir, ColorMatcher.PaletteEntry entry,
                                 double cx0, double cy0, double cz0,
                                 double cosY, double sinY, double cosP, double sinP,
                                 Camera cam, double cx, double cy, boolean perBlock) {
        if (top <= neighborTop) return;
        double brightness = (dir <= 1) ? 0.8 : 0.6; // 南北面0.8，东西面0.6
        int from = perBlock ? neighborTop : top - 1;
        for (int y0 = from; y0 < top; y0++) {
            int y1 = y0 + 1;
            int yb = perBlock ? y0 : neighborTop; // 合并模式底部为邻居高度
            double[][] p;
            switch (dir) {
                case 0 -> p = new double[][]{
                    proj(x,     yb, z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x + 1, yb, z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x + 1, y1, z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x,     y1, z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy)};
                case 1 -> p = new double[][]{
                    proj(x + 1, yb, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x,     yb, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x,     y1, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x + 1, y1, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy)};
                case 2 -> p = new double[][]{
                    proj(x, yb, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x, yb, z,     cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x, y1, z,     cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x, y1, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy)};
                default -> p = new double[][]{
                    proj(x + 1, yb, z,     cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x + 1, yb, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x + 1, y1, z + 1, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                    proj(x + 1, y1, z,     cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy)};
            }
            faces.add(new Face(p, entry, brightness, true));
            if (!perBlock) break; // 合并模式只画一个面
        }
    }

    private static double[] proj(double x, double y, double z, double cx0, double cy0, double cz0,
                                 double cosY, double sinY, double cosP, double sinP,
                                 Camera cam, double cx, double cy) {
        return project(x, y, z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
    }

    /** 世界坐标 → 屏幕坐标，返回 [sx, sy, depth] */
    private static double[] project(double x, double y, double z,
                                    double cx0, double cy0, double cz0,
                                    double cosY, double sinY, double cosP, double sinP,
                                    double zoom, double cx, double cy) {
        double dx = x - cx0, dy = y - cy0, dz = z - cz0;
        // 绕 Y 轴
        double x1 = dx * cosY + dz * sinY;
        double z1 = -dx * sinY + dz * cosY;
        double y1 = dy;
        // 绕 X 轴俯仰
        double y2 = y1 * cosP - z1 * sinP;
        double z2 = y1 * sinP + z1 * cosP;
        return new double[]{cx + x1 * zoom, cy - y2 * zoom, z2};
    }

    private static void drawFace(Graphics2D g, Face f, boolean textured, boolean drawBorder) {
        Polygon poly = new Polygon();
        for (double[] p : f.pts) poly.addPoint((int) Math.round(p[0]), (int) Math.round(p[1]));

        Color base = new Color(f.entry.r, f.entry.g, f.entry.b);
        Color draw = f.brightness >= 1.0 ? base
                : new Color((int) (f.entry.r * f.brightness),
                            (int) (f.entry.g * f.brightness),
                            (int) (f.entry.b * f.brightness));

        BufferedImage tex = null;
        if (textured) {
            BufferedImage raw = TextureManager.get(f.entry.baseColor);
            if (raw != null) tex = shade(raw, f.brightness);
        }

        if (tex != null && quadArea(f.pts) > 0.5) {
            // 正交投影下矩形面 → 平行四边形，用三个角点构造仿射变换：
            // 单位正方形 (0,0)(1,0)(0,1) → p0 p1 p3；纹理先从 16px 缩到单位长度
            try {
                double[] p0 = f.pts[0], p1 = f.pts[1], p3 = f.pts[3];
                AffineTransform at = new AffineTransform(
                        p1[0] - p0[0], p1[1] - p0[1],
                        p3[0] - p0[0], p3[1] - p0[1],
                        p0[0], p0[1]);
                at.concatenate(AffineTransform.getScaleInstance(1.0 / 16.0, 1.0 / 16.0));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(tex, at, null);
            } catch (Exception ex) {
                g.setColor(draw);
                g.fillPolygon(poly);
            }
        } else {
            g.setColor(draw);
            g.fillPolygon(poly);
        }
        if (drawBorder) {
            g.setColor(new Color(0, 0, 0, 26));
            g.drawPolygon(poly);
        }
    }

    /** 四边形（平行四边形）面积，退化面返回 ~0 */
    private static double quadArea(double[][] p) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1];
        double bx = p[3][0] - p[0][0], by = p[3][1] - p[0][1];
        return Math.abs(ax * by - ay * bx);
    }

    /** 生成压暗版本的纹理 */
    private static BufferedImage shade(BufferedImage src, double brightness) {
        if (brightness >= 0.999) return src;
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int rgb = src.getRGB(x, y);
                int a = (rgb >>> 24);
                int r = (int) (((rgb >> 16) & 0xFF) * brightness);
                int gg = (int) (((rgb >> 8) & 0xFF) * brightness);
                int b = (int) ((rgb & 0xFF) * brightness);
                out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        return out;
    }

    /** 一个待绘制的面（四边形） */
    private static class Face {
        final double[][] pts;
        final ColorMatcher.PaletteEntry entry;
        final double brightness;
        final boolean isSide;
        final double depth;

        Face(double[][] pts, ColorMatcher.PaletteEntry entry, double brightness, boolean isSide) {
            this.pts = pts;
            this.entry = entry;
            this.brightness = brightness;
            this.isSide = isSide;
            double d = 0;
            for (double[] p : pts) d += p[2];
            this.depth = d / pts.length;
        }
    }
}
