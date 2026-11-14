package com.axolotl.mcart.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 SVG 渲染器【零外部依赖，无反射，GraalVM Native 友好】
 *
 * 支持：
 *   - &lt;path d="..."&gt; + style/fill 纯色 + transform="translate(dx,dy)"
 *   - 指令 M L H V C Z（含相对小写）
 *
 * 图标集特性：
 *   1. 探测内容包围盒 → 裁掉 SVG 透明留白
 *   2. 散图列表 + 多档尺寸 → 兼容所有 setIconImages 实现
 *   3. 大尺寸降超采样倍率，避免 512 档生成 2048² 中间位图
 *
 * 关键约束：
 *   viewBox 变换必须「先 scale 再 translate」，否则非零原点 viewBox 被推出画布。
 */
public final class SvgMiniRenderer {

    // ---------- 正则 ----------
    private static final Pattern PATH_TAG  = Pattern.compile("<path\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern D_ATTR    = Pattern.compile("\\bd\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILL_ATTR = Pattern.compile("fill\\s*[:=]\\s*[\"']?\\s*(#[0-9a-fA-F]{3,8})", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSLATE = Pattern.compile("translate\\s*\\(\\s*([-\\d.]+)[\\s,]+([-\\d.]+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIEWBOX   = Pattern.compile("viewBox\\s*=\\s*[\"']\\s*([-\\d.eE+]+)\\s+([-\\d.eE+]+)\\s+([-\\d.eE+]+)\\s+([-\\d.eE+]+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN     = Pattern.compile("([MmLlHhVvCcZz])|(-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)");

    // ---------- 常量 ----------
    /** 图标物理尺寸档位：覆盖 100%~400% DPI 常见缩放 */
    private static final int[]   ICON_SIZES      = {16, 20, 24, 32, 40, 48, 64, 80, 96, 128, 192, 256, 512};
    /** 探测内容盒时的探测分辨率 */
    private static final int     PROBE_SIZE      = 256;
    /** alpha 高于此值才算"内容"（避免抗锯齿边缘撑大包围盒） */
    private static final int     ALPHA_THRESHOLD = 4;
    /** 内容四周留白：1.06 = 每边 3% 内边距 */
    private static final float   CONTENT_PADDING = 1.06f;
    /** 回退 viewBox */
    private static final float[] DEFAULT_VB      = {0, 0, 100, 100};

    private SvgMiniRenderer() {}

    // ============================================================
    // 资源
    // ============================================================

    public static String readSvgResource(String path) throws IOException {
        try (InputStream is = SvgMiniRenderer.class.getResourceAsStream(path)) {
            if (is == null) throw new IOException("资源不存在: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ============================================================
    // 对外 API
    // ============================================================

    /** 渲染 SVG 到指定尺寸（保留原始 viewBox，不裁剪） */
    public static BufferedImage render(String svg, int w, int h) {
        return renderWithViewBox(svg, w, h, parseViewBox(svg));
    }

    /**
     * 一次性读取 SVG，生成用于 JFrame.setIconImages 的图标列表。
     * 流程：解析 viewBox → 探测内容盒裁剪留白 → 逐档渲染为散图列表。
     */
    public static List<Image> loadMultiSizeIconSet(String path) throws IOException {
        String svg = readSvgResource(path);
        float[] boxVb = probeContentBox(svg, parseViewBox(svg));
        List<Image> icons = new ArrayList<>(ICON_SIZES.length);
        for (int s : ICON_SIZES) icons.add(renderWithViewBox(svg, s, s, boxVb));
        return icons;
    }

    // ============================================================
    // viewBox / 内容盒
    // ============================================================

    private static float[] parseViewBox(String svg) {
        Matcher m = VIEWBOX.matcher(svg);
        if (!m.find()) return DEFAULT_VB;
        try {
            float x = Float.parseFloat(m.group(1));
            float y = Float.parseFloat(m.group(2));
            float w = Float.parseFloat(m.group(3));
            float h = Float.parseFloat(m.group(4));
            if (w > 0 && h > 0 && Float.isFinite(w) && Float.isFinite(h)) {
                return new float[]{x, y, w, h};
            }
        } catch (NumberFormatException ignored) {}
        return DEFAULT_VB;
    }

    /**
     * 探测实际内容包围盒，裁掉透明留白，取最大边扩成正方形（含 padding）。
     * 任何异常/失败都回退原 viewBox。
     */
    private static float[] probeContentBox(String svg, float[] vb) {
        if (vb[2] <= 0 || vb[3] <= 0) return vb;

        BufferedImage probe = renderWithViewBox(svg, PROBE_SIZE, PROBE_SIZE, vb);
        // 一次性拉取整幅像素，比逐行 getRGB 快得多
        int[] px = probe.getRGB(0, 0, PROBE_SIZE, PROBE_SIZE, null, 0, PROBE_SIZE);

        int minX = PROBE_SIZE, minY = PROBE_SIZE, maxX = -1, maxY = -1;
        for (int y = 0; y < PROBE_SIZE; y++) {
            int rowOff = y * PROBE_SIZE;
            for (int x = 0; x < PROBE_SIZE; x++) {
                if ((px[rowOff + x] >>> 24) > ALPHA_THRESHOLD) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return vb;

        float bw = (maxX - minX + 1) / (float) PROBE_SIZE * vb[2];
        float bh = (maxY - minY + 1) / (float) PROBE_SIZE * vb[3];
        float bx = vb[0] + minX / (float) PROBE_SIZE * vb[2];
        float by = vb[1] + minY / (float) PROBE_SIZE * vb[3];

        float side = Math.max(bw, bh) * CONTENT_PADDING;
        if (!Float.isFinite(side) || side <= 0.001f) return vb;

        float cx = bx + bw * 0.5f, cy = by + bh * 0.5f;
        return new float[]{cx - side * 0.5f, cy - side * 0.5f, side, side};
    }

    // ============================================================
    // 核心渲染
    // ============================================================

    private static BufferedImage renderWithViewBox(String svg, int w, int h, float[] vb) {
        if (w <= 0) w = 1;
        if (h <= 0) h = 1;
        if (vb[2] <= 0 || vb[3] <= 0) vb = DEFAULT_VB;

        // 大图 2× 超采样，小图 4×，平衡画质与内存
        double ss = (w >= 128 || h >= 128) ? 2.0 : 4.0;
        int superW = Math.max(1, (int) Math.round(w * ss));
        int superH = Math.max(1, (int) Math.round(h * ss));

        BufferedImage big = new BufferedImage(superW, superH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            // ★ 先 scale 再 translate：Graphics2D 左乘链 → (p - vb) * scale
            //   反过来写会让 translate 的量不被缩放，非零 viewBox 直接被推出画布
            g.scale(superW / (double) vb[2], superH / (double) vb[3]);
            g.translate(-vb[0], -vb[1]);

            Matcher p = PATH_TAG.matcher(svg);
            while (p.find()) {
                String attrs = p.group(1);
                String d       = findAttr(D_ATTR, attrs);
                String fillHex = findAttr(FILL_ATTR, attrs);
                if (d == null || fillHex == null) continue;

                GeneralPath path = parsePath(d);
                if (path == null) continue;

                Shape shape = path;
                Matcher tm = TRANSLATE.matcher(attrs);
                if (tm.find()) {
                    try {
                        shape = AffineTransform.getTranslateInstance(
                                Float.parseFloat(tm.group(1)),
                                Float.parseFloat(tm.group(2))).createTransformedShape(path);
                    } catch (NumberFormatException ignored) {}
                }
                g.setColor(parseColor(fillHex));
                g.fill(shape);
            }
        } finally {
            g.dispose();
        }

        if (superW == w && superH == h) return big;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(big, 0, 0, w, h, null);
        } finally {
            g2.dispose();
        }
        return out;
    }

    /** 匹配属性的通用工具：命中返回 group(1)，否则 null */
    private static String findAttr(Pattern p, String attrs) {
        Matcher m = p.matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    // ============================================================
    // Path d 解析
    // ============================================================

    private static GeneralPath parsePath(String d) {
        List<String> t = new ArrayList<>();
        Matcher m = TOKEN.matcher(d);
        while (m.find()) t.add(m.group());
        if (t.isEmpty()) return null;

        GeneralPath gp = new GeneralPath();
        float x = 0, y = 0;
        char cmd = 0;
        for (int i = 0; i < t.size(); ) {
            String tk = t.get(i);
            if (Character.isLetter(tk.charAt(0))) {
                cmd = tk.charAt(0);
                if (Character.toUpperCase(cmd) == 'Z') gp.closePath();
                i++;
                continue;
            }
            boolean rel = Character.isLowerCase(cmd);
            float bx = rel ? x : 0, by = rel ? y : 0;

            try {
                switch (Character.toUpperCase(cmd)) {
                    case 'M' -> { x = Float.parseFloat(t.get(i++)) + bx;
                        y = Float.parseFloat(t.get(i++)) + by;
                        gp.moveTo(x, y); }
                    case 'L' -> { x = Float.parseFloat(t.get(i++)) + bx;
                        y = Float.parseFloat(t.get(i++)) + by;
                        gp.lineTo(x, y); }
                    case 'H' -> { x = Float.parseFloat(t.get(i++)) + bx; gp.lineTo(x, y); }
                    case 'V' -> { y = Float.parseFloat(t.get(i++)) + by; gp.lineTo(x, y); }
                    case 'C' -> {
                        float c1x = Float.parseFloat(t.get(i++)) + bx, c1y = Float.parseFloat(t.get(i++)) + by;
                        float c2x = Float.parseFloat(t.get(i++)) + bx, c2y = Float.parseFloat(t.get(i++)) + by;
                        x = Float.parseFloat(t.get(i++)) + bx;
                        y = Float.parseFloat(t.get(i++)) + by;
                        gp.curveTo(c1x, c1y, c2x, c2y, x, y);
                    }
                    default -> i++;
                }
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                break;
            }
        }
        return gp;
    }

    // ============================================================
    // 颜色
    // ============================================================

    /** #RGB / #RRGGBB / #RRGGBBAA 颜色解析 */
    private static Color parseColor(String hex) {
        try {
            String s = hex.startsWith("#") ? hex.substring(1) : hex;
            if (s.length() == 3) {
                s = "" + s.charAt(0) + s.charAt(0)
                        + s.charAt(1) + s.charAt(1)
                        + s.charAt(2) + s.charAt(2);
            }
            if (s.length() == 8) return new Color((int) Long.parseLong(s, 16), true);
            if (s.length() == 6) return new Color(Integer.parseInt(s, 16));
        } catch (NumberFormatException ignored) {}
        return Color.BLACK;
    }
}