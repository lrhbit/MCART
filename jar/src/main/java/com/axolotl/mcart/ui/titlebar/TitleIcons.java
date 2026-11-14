package com.axolotl.mcart.ui.titlebar;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 手绘矢量图标集（无图片资源）+ 扁平图标按钮。
 * 完整移植自 Pixel 的 ui.Icons，供标题栏各组件复用。
 */
public final class TitleIcons {

    public interface Painter {
        void paint(Graphics2D g, int w, int h);
    }

    public static Icon of(int w, int h, Painter painter) {
        return new VectorIcon(w, h, painter);
    }

    private static class VectorIcon implements Icon {
        private final int w, h;
        private final Painter painter;

        VectorIcon(int w, int h, Painter painter) {
            this.w = w;
            this.h = h;
            this.painter = painter;
        }

        @Override public int getIconWidth() { return UIScale.scale(w); }
        @Override public int getIconHeight() { return UIScale.scale(h); }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.setColor(c != null ? c.getForeground() : Color.LIGHT_GRAY);
                painter.paint(g2, getIconWidth(), getIconHeight());
            } finally {
                g2.dispose();
            }
        }
    }

    // ---------------------------------------------------------------- shapes

    private static void stroke(Graphics2D g, Shape s, float width) {
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    private static double s(double v) {
        return UIScale.scale((float) v);
    }

    // ---------------------------------------------------------------- icons

    /** git branch：两个节点 + 汇合线 */
    public static final Icon branch = of(14, 14, (g, w, h) -> {
        float sw = 1.3f;
        int r = UIScale.scale(2);
        int cx1 = UIScale.scale(3), cx2 = w - UIScale.scale(3);
        int cyTop = UIScale.scale(3), cyBot = h - UIScale.scale(3);
        int cyMid = h / 2;
        g.fill(new Ellipse2D.Double(cx1 - r, cyMid - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(cx1 - r, cyBot - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(cx2 - r, cyTop - r, r * 2, r * 2));
        stroke(g, new Line2D.Double(cx1, cyMid + r, cx1, cyBot - r), sw);
        Path2D p = new Path2D.Double();
        p.moveTo(cx1, cyMid);
        p.curveTo(cx1 + (cx2 - cx1) * 0.6, cyMid, cx2 - (cx2 - cx1) * 0.2, cyMid, cx2, cyTop + r);
        stroke(g, p, sw);
    });

    /** 小的下箭头 */
    public static final Icon chevronDown = of(8, 8, (g, w, h) -> {
        Path2D p = new Path2D.Double();
        p.moveTo(1, h * 0.35);
        p.lineTo(w / 2.0, h * 0.68);
        p.lineTo(w - 1, h * 0.35);
        stroke(g, p, 1.4f);
    });

    /** 运行：实心三角 */
    public static final Icon play = of(12, 12, (g, w, h) -> {
        Path2D p = new Path2D.Double();
        p.moveTo(w * 0.25, h * 0.18);
        p.lineTo(w * 0.85, h * 0.5);
        p.lineTo(w * 0.25, h * 0.82);
        p.closePath();
        g.fill(p);
    });

    /** 调试：简化的虫子 */
    public static final Icon bug = of(14, 14, (g, w, h) -> {
        float sw = 1.2f;
        double cx = w / 2.0, cy = h / 2.0 + 1;
        g.fill(new Ellipse2D.Double(cx - 3, cy - 3.5, 6, 8)); // body
        g.fill(new Ellipse2D.Double(cx - 1.6, cy - 6.5, 3.2, 3.2)); // head
        stroke(g, new Line2D.Double(cx - 3, cy - 1, cx - 6, cy - 3), sw);
        stroke(g, new Line2D.Double(cx + 3, cy - 1, cx + 6, cy - 3), sw);
        stroke(g, new Line2D.Double(cx - 3, cy + 1.5, cx - 6, cy + 3.5), sw);
        stroke(g, new Line2D.Double(cx + 3, cy + 1.5, cx + 6, cy + 3.5), sw);
        stroke(g, new Line2D.Double(cx - 3, cy + 4, cx - 5, cy + 6.5), sw);
        stroke(g, new Line2D.Double(cx + 3, cy + 4, cx + 5, cy + 6.5), sw);
    });

    /** 竖向省略号（更多） */
    public static final Icon dots = of(12, 12, (g, w, h) -> {
        double cx = w / 2.0;
        double r = s(1.1);
        for (int i = -1; i <= 1; i++)
            g.fill(new Ellipse2D.Double(cx - r, h / 2.0 + i * UIScale.scale(3) - r, r * 2, r * 2));
    });

    /** 放大镜 */
    public static final Icon search = of(14, 14, (g, w, h) -> {
        double cx = w * 0.42, cy = h * 0.42, r = s(3.4);
        stroke(g, new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2), 1.3f);
        stroke(g, new Line2D.Double(cx + r * 0.72, cy + r * 0.72, w - 1.5, h - 1.5), 1.5f);
    });

    /** 齿轮 */
    public static final Icon gear = of(14, 14, (g, w, h) -> {
        double cx = w / 2.0, cy = h / 2.0;
        double rOuter = s(5.2), rInner = s(2.2);
        stroke(g, new Ellipse2D.Double(cx - rInner, cy - rInner, rInner * 2, rInner * 2), 1.2f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            stroke(g, new Line2D.Double(
                    cx + Math.cos(a) * rInner * 0.9, cy + Math.sin(a) * rInner * 0.9,
                    cx + Math.cos(a) * rOuter, cy + Math.sin(a) * rOuter), 1.4f);
        }
    });

    /** 文件夹 */
    public static final Icon folder = of(14, 14, (g, w, h) -> {
        int x = 1, y = UIScale.scale(3);
        int ww = w - 2, hh = h - UIScale.scale(5);
        int tabW = UIScale.scale(6), tabH = UIScale.scale(2);
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + tabH);
        p.lineTo(x + tabW, y + tabH);
        p.lineTo(x + tabW + s(1.5), y);
        p.lineTo(x + ww - UIScale.scale(1), y);
        p.lineTo(x + ww, y + tabH);
        p.lineTo(x + ww, y + hh);
        p.lineTo(x, y + hh);
        p.closePath();
        g.draw(p);
        stroke(g, p, 1.1f);
    });

    /** 终端提示符 "> _" */
    public static final Icon terminal = of(14, 14, (g, w, h) -> {
        Path2D p = new Path2D.Double();
        p.moveTo(UIScale.scale(3), UIScale.scale(4));
        p.lineTo(UIScale.scale(7), h / 2.0);
        p.lineTo(UIScale.scale(3), h - UIScale.scale(4));
        stroke(g, p, 1.3f);
        stroke(g, new Line2D.Double(UIScale.scale(8), h - UIScale.scale(4), w - UIScale.scale(2), h - UIScale.scale(4)), 1.3f);
    });

    /** 加号 */
    public static final Icon plus = of(12, 12, (g, w, h) -> {
        stroke(g, new Line2D.Double(w / 2.0, 2, w / 2.0, h - 2), 1.3f);
        stroke(g, new Line2D.Double(2, h / 2.0, w - 2, h / 2.0), 1.3f);
    });

    /** 小关闭叉 */
    public static final Icon cross = of(10, 10, (g, w, h) -> {
        stroke(g, new Line2D.Double(2, 2, w - 2, h - 2), 1.3f);
        stroke(g, new Line2D.Double(w - 2, 2, 2, h - 2), 1.3f);
    });

    /** 十字准星 */
    public static final Icon locate = of(14, 14, (g, w, h) -> {
        double cx = w / 2.0, cy = h / 2.0, r = s(3.4);
        stroke(g, new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2), 1.2f);
        stroke(g, new Line2D.Double(cx, 1, cx, cy - r), 1.2f);
        stroke(g, new Line2D.Double(cx, cy + r, cx, h - 1), 1.2f);
        stroke(g, new Line2D.Double(1, cy, cx - r, cy), 1.2f);
        stroke(g, new Line2D.Double(cx + r, cy, w - 1, cy), 1.2f);
    });

    /** 铃铛 */
    public static final Icon bell = of(14, 14, (g, w, h) -> {
        double cx = w / 2.0;
        Path2D p = new Path2D.Double();
        p.moveTo(cx - UIScale.scale(4), h - UIScale.scale(4));
        p.curveTo(cx - UIScale.scale(4), h * 0.55, cx - s(3.4), h * 0.28, cx, h * 0.24);
        p.curveTo(cx + s(3.4), h * 0.28, cx + UIScale.scale(4), h * 0.55, cx + UIScale.scale(4), h - UIScale.scale(4));
        p.closePath();
        stroke(g, p, 1.2f);
        stroke(g, new Line2D.Double(cx - UIScale.scale(5), h - UIScale.scale(4), cx + UIScale.scale(5), h - UIScale.scale(4)), 1.2f);
    });

    /** 上箭头 */
    public static final Icon chevronUp = of(8, 8, (g, w, h) -> {
        Path2D p = new Path2D.Double();
        p.moveTo(1, h * 0.65);
        p.lineTo(w / 2.0, h * 0.32);
        p.lineTo(w - 1, h * 0.65);
        stroke(g, p, 1.4f);
    });

    // ------------------------------------------------- 标题栏（New UI）

    /** 主菜单汉堡按钮：四条圆角横线 */
    public static final Icon hamburger = of(14, 14, (g, w, h) -> {
        double barW = w;
        double x = (w - barW) / 2;
        double barH = 1.5;
        double[] ys = {1.2, 4.8, 8.4, 12.0};
        for (double yy : ys)
            g.fill(new RoundRectangle2D.Double(x, yy, barW, barH, barH, barH));
    });

    /** 放大镜（Search Everywhere），描边风格 */
    public static final Icon searchOutline = of(16, 16, (g, w, h) -> {
        double cx = w * 0.42, cy = h * 0.42, r = s(4.7);
        stroke(g, new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2), 1.6f);
        stroke(g, new Line2D.Double(cx + r * 0.72, cy + r * 0.72, w - 1, h - 1), 1.8f);
    });

    /** “Run Anything” 旋涡 */
    public static final Icon swirl = of(16, 16, (g, w, h) -> {
        double cx = w / 2.0, cy = h / 2.0;
        double r0 = 1.1, r1 = s(6.8);
        double turns = 2.35;
        Path2D p = new Path2D.Double();
        int steps = 96;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double theta = t * turns * 2 * Math.PI;
            double r = r1 - (r1 - r0) * t;
            double x = cx + r * Math.cos(theta);
            double y = cy + r * Math.sin(theta);
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        stroke(g, p, 1.6f);
    });

    /** 设置齿轮：八齿描边 + 中心孔 */
    public static final Icon gearOutline = of(16, 16, (g, w, h) -> {
        double cx = w / 2.0, cy = h / 2.0;
        double rTip = s(7.1), rRoot = s(5.4);
        int n = 8;
        double tooth = Math.toRadians(11), gap = Math.toRadians(21);
        Path2D p = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / n;
            double[][] pts = {
                    {a - gap, rRoot}, {a - tooth, rTip},
                    {a + tooth, rTip}, {a + gap, rRoot}
            };
            for (double[] pt : pts) {
                double x = cx + pt[1] * Math.cos(pt[0]);
                double y = cy + pt[1] * Math.sin(pt[0]);
                if (i == 0 && pt == pts[0]) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
        }
        p.closePath();
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
        stroke(g, new Ellipse2D.Double(cx - s(2.3), cy - s(2.3), s(4.6), s(4.6)), 1.5f);
    });

    /** 标题栏小部件用的下箭头 */
    public static final Icon titleChevron = of(9, 9, (g, w, h) -> {
        Path2D p = new Path2D.Double();
        p.moveTo(1.5, h * 0.32);
        p.lineTo(w / 2.0, h * 0.7);
        p.lineTo(w - 1.5, h * 0.32);
        stroke(g, p, 1.5f);
    });

    // --------------------------------------------- 左右竖向工具栏（16x16）

    /** 方块库：2×2 方块网格 */
    public static final Icon blocksGrid = of(16, 16, (g, w, h) -> {
        float sw = 1.4f;
        double c = s(5.6), gap = s(1.4);
        double x0 = (w - (c * 2 + gap)) / 2, y0 = (h - (c * 2 + gap)) / 2;
        for (int ix = 0; ix < 2; ix++) for (int iy = 0; iy < 2; iy++) {
            RoundRectangle2D rr = new RoundRectangle2D.Double(
                    x0 + ix * (c + gap), y0 + iy * (c + gap), c, c, s(1.2), s(1.2));
            stroke(g, rr, sw);
        }
    });

    /** 物料清单：文档 + 列表行 */
    public static final Icon bomList = of(16, 16, (g, w, h) -> {
        float sw = 1.4f;
        RoundRectangle2D doc = new RoundRectangle2D.Double(2.5, 1.5, w - 5, h - 3, s(1.5), s(1.5));
        stroke(g, doc, sw);
        double[] ys = {6.2, 9.4, 12.6};
        for (double yy : ys) {
            g.fill(new Ellipse2D.Double(5 - s(1), yy - s(1), s(2), s(2)));
            stroke(g, new Line2D.Double(8, yy, w - 5, yy), 1.2f);
        }
    });

    /** 批量：三层菱形堆叠 */
    public static final Icon stackLayers = of(16, 16, (g, w, h) -> {
        float sw = 1.4f;
        double cx = w / 2.0;
        double[][] rows = {{4.2}, {8.0}, {11.8}};
        double hw = 5.4, hh = 2.4;
        for (double[] r : rows) {
            double cy = r[0];
            Path2D p = new Path2D.Double();
            p.moveTo(cx, cy - hh);
            p.lineTo(cx + hw, cy);
            p.lineTo(cx, cy + hh);
            p.lineTo(cx - hw, cy);
            p.closePath();
            stroke(g, p, sw);
        }
    });

    /** 导出：托盘 + 向上箭头 */
    public static final Icon exportTray = of(16, 16, (g, w, h) -> {
        float sw = 1.5f;
        double cx = w / 2.0;
        stroke(g, new Line2D.Double(cx, 2.5, cx, 9.5), sw);
        Path2D head = new Path2D.Double();
        head.moveTo(cx - 3, 5.6);
        head.lineTo(cx, 2.4);
        head.lineTo(cx + 3, 5.6);
        stroke(g, head, sw);
        Path2D tray = new Path2D.Double();
        tray.moveTo(2.5, 11.5);
        tray.lineTo(2.5, h - 2.5);
        tray.lineTo(w - 2.5, h - 2.5);
        tray.lineTo(w - 2.5, 11.5);
        stroke(g, tray, sw);
    });

    /** 保存：软盘 */
    public static final Icon saveFloppy = of(16, 16, (g, w, h) -> {
        float sw = 1.3f;
        RoundRectangle2D body = new RoundRectangle2D.Double(2, 1.5, w - 4, h - 3, s(1.4), s(1.4));
        stroke(g, body, sw);
        // 顶部金属滑套
        g.fill(new Rectangle2D.Double(9.5, 2.6, s(3.2), s(3.4)));
        // 标签区
        Rectangle2D label = new Rectangle2D.Double(4.2, 8.6, w - 8.4, 4.4);
        stroke(g, label, sw);
    });

    /** 裁剪：两个直角裁切符 */
    public static final Icon cropMarks = of(16, 16, (g, w, h) -> {
        float sw = 1.5f;
        Path2D a = new Path2D.Double();
        a.moveTo(2.5, 6.5);
        a.lineTo(2.5, 13.5);
        a.lineTo(9.5, 13.5);
        stroke(g, a, sw);
        Path2D b = new Path2D.Double();
        b.moveTo(6.5, 2.5);
        b.lineTo(13.5, 2.5);
        b.lineTo(13.5, 9.5);
        stroke(g, b, sw);
    });

    /** 像素编辑：斜置铅笔 */
    public static final Icon pencil = of(16, 16, (g, w, h) -> {
        float sw = 1.4f;
        Path2D body = new Path2D.Double();
        body.moveTo(9.6, 3.0);
        body.lineTo(13.0, 6.4);
        body.lineTo(6.0, 13.4);
        body.lineTo(3.2, 12.8);
        body.lineTo(3.8, 10.0);
        body.closePath();
        stroke(g, body, sw);
        stroke(g, new Line2D.Double(8.6, 4.0, 12.0, 7.4), sw);
        g.fill(new Ellipse2D.Double(2.4, 12.2, s(1.8), s(1.8)));
    });

    /** 适应窗口 / 复位：四角外扩箭头 */
    public static final Icon fitCorners = of(16, 16, (g, w, h) -> {
        float sw = 1.5f;
        Path2D tl = new Path2D.Double();
        tl.moveTo(2.5, 6.0); tl.lineTo(2.5, 2.5); tl.lineTo(6.0, 2.5);
        Path2D tr = new Path2D.Double();
        tr.moveTo(10.0, 2.5); tr.lineTo(13.5, 2.5); tr.lineTo(13.5, 6.0);
        Path2D bl = new Path2D.Double();
        bl.moveTo(2.5, 10.0); bl.lineTo(2.5, 13.5); bl.lineTo(6.0, 13.5);
        Path2D br = new Path2D.Double();
        br.moveTo(10.0, 13.5); br.lineTo(13.5, 13.5); br.lineTo(13.5, 10.0);
        stroke(g, tl, sw); stroke(g, tr, sw); stroke(g, bl, sw); stroke(g, br, sw);
    });

    // ------------------------------------------------- 质量级别图标（16x16，颜色固定）

    /** 图像调整（滑块）：三条横线 + 各自旋钮，象征亮度 / 对比度 / 伽马等调节。 */
    public static final Icon sliders = of(16, 16, (g, w, h) -> {
        float sw = 1.4f;
        double[] ys = {4.2, 8.0, 11.8};
        double[] kx = {5.2, 10.2, 7.0};
        for (int i = 0; i < 3; i++) {
            stroke(g, new Line2D.Double(2.0, ys[i], 14.0, ys[i]), sw);
            double r = s(1.7);
            g.fill(new Ellipse2D.Double(s(kx[i]) - r, s(ys[i]) - r, r * 2, r * 2));
        }
    });

    /** 折叠面板：向上双细线（展开时为向下）。 */
    public static final Icon collapseBar = of(14, 14, (g, w, h) -> {
        Path2D p = new Path2D.Double();
        p.moveTo(3.0, 9.0); p.lineTo(7.0, 5.0); p.lineTo(11.0, 9.0);
        stroke(g, p, 1.6f);
    });


    private static final Color SEV_ERROR = new Color(0xE55765);
    private static final Color SEV_WARN  = new Color(0xE8A33D);
    private static final Color SEV_INFO  = new Color(0x6FA8DC);

    /** 错误：红圆白叹号 */
    public static final Icon sevError = of(16, 16, (g, w, h) -> {
        g.setColor(SEV_ERROR);
        g.fill(new Ellipse2D.Double(1.5, 1.5, 13, 13));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(8, 4.6, 8, 9.2));
        g.fill(new Ellipse2D.Double(7.2, 10.8, 1.6, 1.6));
    });

    /** 警告：琥珀三角叹号 */
    public static final Icon sevWarn = of(16, 16, (g, w, h) -> {
        Path2D tri = new Path2D.Double();
        tri.moveTo(8, 2.1);
        tri.lineTo(14.2, 13.2);
        tri.lineTo(1.8, 13.2);
        tri.closePath();
        g.setColor(SEV_WARN);
        g.fill(tri);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(8, 5.6, 8, 9.6));
        g.fill(new Ellipse2D.Double(7.2, 11.0, 1.6, 1.6));
    });

    /** 信息：蓝圆 i */
    public static final Icon sevInfo = of(16, 16, (g, w, h) -> {
        g.setColor(SEV_INFO);
        g.fill(new Ellipse2D.Double(1.5, 1.5, 13, 13));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(8, 7.4, 8, 11.6));
        g.fill(new Ellipse2D.Double(7.2, 4.7, 1.6, 1.6));
    });

    /** 全部通过：绿色圆勾 */
    public static final Icon sevOk = of(16, 16, (g, w, h) -> {
        g.setColor(new Color(0x59A869));
        g.fill(new Ellipse2D.Double(1.5, 1.5, 13, 13));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D ck = new Path2D.Double();
        ck.moveTo(4.6, 8.2); ck.lineTo(7.0, 10.6); ck.lineTo(11.6, 5.6);
        g.draw(ck);
    });

    // ---------------------------------------------------------------- button

    /** 扁平图标按钮：无边框，圆角悬停 / 按下底色。 */
    public static class Button extends JButton {
        private final int arc;
        private boolean primary;

        public Button(Icon icon) {
            this(icon, 22, 22, 6);
        }

        public Button(Icon icon, int size, int arc) {
            this(icon, size, size, arc);
        }

        public Button(Icon icon, int w, int h, int arc) {
            super(icon);
            this.arc = arc;
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            Dimension d = new Dimension(UIScale.scale(w), UIScale.scale(h));
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setForeground(TitleTheme.TITLE_ICON);
        }

        /** 主操作按钮：常驻蓝色底 + 白色图标。 */
        public Button primary() {
            this.primary = true;
            setForeground(com.axolotl.mcart.ui.UiTheme.ON_ACCENT);
            return this;
        }

        public Button onClick(Runnable r) {
            addActionListener((ActionListener) e -> r.run());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            if (primary) {
                TitleTheme.paintRoundedBg(g2, 0, 0, getWidth(), getHeight(), UIScale.scale(arc),
                        com.axolotl.mcart.ui.UiTheme.alpha(com.axolotl.mcart.ui.UiTheme.ACCENT, model.isRollover() ? 235 : 215));
            } else if (model.isRollover() || model.isSelected())
                TitleTheme.paintRoundedBg(g2, 0, 0, getWidth(), getHeight(), UIScale.scale(arc),
                        TitleTheme.HOVER_OVERLAY);
            else if (model.isPressed())
                TitleTheme.paintRoundedBg(g2, 0, 0, getWidth(), getHeight(), UIScale.scale(arc),
                        TitleTheme.PRESSED_OVERLAY);
            super.paintComponent(g);
        }
    }
}
