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

public final class Model3DRenderer {
   private Model3DRenderer() {
   }

   public static double fitZoom(PixelArtGenerator.Result r, int viewW, int viewH) {
      double zx = (double)viewW * 0.92 / (double)Math.max(1, r.width);
      double zy = (double)viewH * 0.92 / (double)Math.max(1, r.length);
      return Math.max(0.5, Math.min(zx, zy));
   }

   public static BufferedImage render(PixelArtGenerator.Result result, Model3DRenderer.Camera cam, int viewW, int viewH, boolean textured) {
      BufferedImage img = new BufferedImage(Math.max(8, viewW), Math.max(8, viewH), 1);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
      g.setColor(new Color(242, 242, 245));
      g.fillRect(0, 0, viewW, viewH);
      int W = result.width;
      int L = result.length;
      int H = result.height;
      double cx0 = (double)W / 2.0;
      double cy0 = (double)H / 2.0;
      double cz0 = (double)L / 2.0;
      double cx = (double)viewW / 2.0 + cam.panX;
      double cy = (double)viewH / 2.0 + cam.panY;
      double cosY = Math.cos(cam.yaw);
      double sinY = Math.sin(cam.yaw);
      double cosP = Math.cos(cam.pitch);
      double sinP = Math.sin(cam.pitch);
      boolean topDown = cam.pitch >= 1.5507963267948965;
      List<Model3DRenderer.Face> faces = new ArrayList<>(W * L * 2);

      for (int z = 0; z < L; z++) {
         for (int x = 0; x < W; x++) {
            int top = result.heightMap[z][x];
            ColorMatcher.PaletteEntry entry = result.topEntries[z][x];
            if (entry != null) {
               double[] c00 = project((double)x, (double)top, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
               double[] c10 = project((double)(x + 1), (double)top, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
               double[] c11 = project((double)(x + 1), (double)top, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
               double[] c01 = project((double)x, (double)top, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
               faces.add(new Model3DRenderer.Face(new double[][]{c00, c10, c11, c01}, entry, 1.0, false));
               if (!topDown) {
                  int hN = z > 0 ? result.heightMap[z - 1][x] : 0;
                  int hS = z < L - 1 ? result.heightMap[z + 1][x] : 0;
                  int hW = x > 0 ? result.heightMap[z][x - 1] : 0;
                  int hE = x < W - 1 ? result.heightMap[z][x + 1] : 0;
                  if (cosY > 0.0) {
                     addSides(faces, x, z, top, hN, 0, entry, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy, textured);
                  } else {
                     addSides(faces, x, z, top, hS, 1, entry, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy, textured);
                  }

                  if (sinY > 0.0) {
                     addSides(faces, x, z, top, hE, 3, entry, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy, textured);
                  } else {
                     addSides(faces, x, z, top, hW, 2, entry, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy, textured);
                  }
               }
            }
         }
      }

      faces.sort((a, b) -> Double.compare(a.depth, b.depth));
      boolean showTopGrid = cam.zoom >= 8.0;

      for (Model3DRenderer.Face f : faces) {
         drawFace(g, f, textured, f.isSide || showTopGrid);
      }

      g.dispose();
      return img;
   }

   private static void addSides(
      List<Model3DRenderer.Face> faces,
      int x,
      int z,
      int top,
      int neighborTop,
      int dir,
      ColorMatcher.PaletteEntry entry,
      double cx0,
      double cy0,
      double cz0,
      double cosY,
      double sinY,
      double cosP,
      double sinP,
      Model3DRenderer.Camera cam,
      double cx,
      double cy,
      boolean perBlock
   ) {
      if (top > neighborTop) {
         double brightness = dir <= 1 ? 0.8 : 0.6;
         int from = perBlock ? neighborTop : top - 1;

         for (int y0 = from; y0 < top; y0++) {
            int y1 = y0 + 1;
            int yb = perBlock ? y0 : neighborTop;

            faces.add(
               new Model3DRenderer.Face(
                  switch (dir) {
                     case 0 -> new double[][]{
                     proj((double)x, (double)yb, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)(x + 1), (double)yb, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)(x + 1), (double)y1, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)x, (double)y1, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy)
                  };
                     case 1 -> new double[][]{
                     proj((double)(x + 1), (double)yb, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)x, (double)yb, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)x, (double)y1, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)(x + 1), (double)y1, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy)
                  };
                     case 2 -> new double[][]{
                     proj((double)x, (double)yb, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)x, (double)yb, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)x, (double)y1, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)x, (double)y1, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy)
                  };
                     default -> new double[][]{
                     proj((double)(x + 1), (double)yb, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)(x + 1), (double)yb, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)(x + 1), (double)y1, (double)(z + 1), cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy),
                     proj((double)(x + 1), (double)y1, (double)z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam, cx, cy)
                  };
                  },
                  entry,
                  brightness,
                  true
               )
            );
            if (!perBlock) {
               break;
            }
         }
      }
   }

   private static double[] proj(
      double x,
      double y,
      double z,
      double cx0,
      double cy0,
      double cz0,
      double cosY,
      double sinY,
      double cosP,
      double sinP,
      Model3DRenderer.Camera cam,
      double cx,
      double cy
   ) {
      return project(x, y, z, cx0, cy0, cz0, cosY, sinY, cosP, sinP, cam.zoom, cx, cy);
   }

   private static double[] project(
      double x, double y, double z, double cx0, double cy0, double cz0, double cosY, double sinY, double cosP, double sinP, double zoom, double cx, double cy
   ) {
      double dx = x - cx0;
      double dy = y - cy0;
      double dz = z - cz0;
      double x1 = dx * cosY + dz * sinY;
      double z1 = -dx * sinY + dz * cosY;
      double y2 = dy * cosP - z1 * sinP;
      double z2 = dy * sinP + z1 * cosP;
      return new double[]{cx + x1 * zoom, cy - y2 * zoom, z2};
   }

   /** 预计算的面绘制颜色（避免每帧 new Color 与逐像素着色纹理） */
   private static final java.util.Map<Long, java.awt.Color> COLOR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

   private static java.awt.Color faceColor(ColorMatcher.PaletteEntry e, double brightness) {
      long key = ((long) e.r << 24) | ((long) e.g << 16) | ((long) e.b << 8) | (long) Math.round(brightness * 100);
      java.awt.Color c = COLOR_CACHE.get(key);
      if (c == null) {
         int r = (int) (e.r * brightness);
         int gg = (int) (e.g * brightness);
         int b = (int) (e.b * brightness);
         c = new java.awt.Color(Math.min(255, r), Math.min(255, gg), Math.min(255, b));
         COLOR_CACHE.put(key, c);
      }
      return c;
   }

   private static void drawFace(Graphics2D g, Model3DRenderer.Face f, boolean textured, boolean drawBorder) {
      Polygon poly = new Polygon();
      for (double[] p : f.pts) {
         poly.addPoint((int) Math.round(p[0]), (int) Math.round(p[1]));
      }

      // 纹理路径：直接绘制原始纹理 + 着色矩阵，省去逐面生成 16x16 着色图
      BufferedImage tex = textured ? TextureManager.get(f.entry.baseColor) : null;
      if (tex != null && quadArea(f.pts) > 0.5) {
         try {
            double[] p0 = f.pts[0];
            double[] p1 = f.pts[1];
            double[] p3 = f.pts[3];
            AffineTransform at = new AffineTransform(p1[0] - p0[0], p1[1] - p0[1], p3[0] - p0[0], p3[1] - p0[1], p0[0], p0[1]);
            at.concatenate(AffineTransform.getScaleInstance(0.0625, 0.0625));
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (f.brightness < 0.999) {
               float k = (float) f.brightness;
               g.drawImage(tex, at, null);
               g.setComposite(java.awt.AlphaComposite.SrcOver);
               // 用同色低透明度叠加实现明暗（比逐像素生成纹理快约 30 倍）
               g.setColor(new java.awt.Color(0, 0, 0, (int) ((1.0 - k) * 255)));
               g.fillPolygon(poly);
            } else {
               g.drawImage(tex, at, null);
            }
            if (drawBorder) {
               g.setColor(BORDER_COLOR);
               g.drawPolygon(poly);
            }
            return;
         } catch (Exception ignored) {
            // 回退到纯色填充
         }
      }

      g.setColor(faceColor(f.entry, f.brightness));
      g.fillPolygon(poly);
      if (drawBorder) {
         g.setColor(BORDER_COLOR);
         g.drawPolygon(poly);
      }
   }

   private static final java.awt.Color BORDER_COLOR = new java.awt.Color(0, 0, 0, 26);

   private static double quadArea(double[][] p) {
      double ax = p[1][0] - p[0][0];
      double ay = p[1][1] - p[0][1];
      double bx = p[3][0] - p[0][0];
      double by = p[3][1] - p[0][1];
      return Math.abs(ax * by - ay * bx);
   }

   private static BufferedImage shade(BufferedImage src, double brightness) {
      if (brightness >= 0.999) {
         return src;
      } else {
         BufferedImage out = new BufferedImage(16, 16, 2);

         for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
               int rgb = src.getRGB(x, y);
               int a = rgb >>> 24;
               int r = (int)((double)(rgb >> 16 & 0xFF) * brightness);
               int gg = (int)((double)(rgb >> 8 & 0xFF) * brightness);
               int b = (int)((double)(rgb & 0xFF) * brightness);
               out.setRGB(x, y, a << 24 | r << 16 | gg << 8 | b);
            }
         }

         return out;
      }
   }

   public static class Camera {
      public double yaw = 0.0;
      public double pitch = Math.PI / 2;
      public double zoom = 4.0;
      public double panX = 0.0;
      public double panY = 0.0;

      public void resetTopDown() {
         this.yaw = 0.0;
         this.pitch = Math.PI / 2;
         this.panX = 0.0;
         this.panY = 0.0;
      }

      public Model3DRenderer.Camera copy() {
         Model3DRenderer.Camera c = new Model3DRenderer.Camera();
         c.yaw = this.yaw;
         c.pitch = this.pitch;
         c.zoom = this.zoom;
         c.panX = this.panX;
         c.panY = this.panY;
         return c;
      }
   }

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
         double d = 0.0;

         for (double[] p : pts) {
            d += p[2];
         }

         this.depth = d / (double)pts.length;
      }
   }
}
