package com.axolotl.mcart.ui.titlebar;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * 标题栏上的小圆角方块（移植自 Pixel 的 GradientMonogram）：
 * 柔和对角渐变 + 白色首字母，色相见 {@link ProjectPalette}。
 *
 * 对应的彩色光晕由 {@link AppTitleBar} 绘制。
 * 首字母可随当前文档切换（{@link #setInitials(String)}），颜色保持应用标识不变。
 */
public class GradientMonogram extends JComponent {

    private ProjectPalette palette;
    private String initials;
    private final String paletteKey;
    private Runnable paletteChanged;

    public GradientMonogram(ProjectPalette palette, String paletteKey, String initials) {
        this.palette = palette;
        this.paletteKey = paletteKey;
        this.initials = initials;
        setOpaque(false);
        int s = UIScale.scale(20);
        Dimension d = new Dimension(s, s);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        setAlignmentY(0.5f);
        setToolTipText("主题色 — 右键重新随机");

        JPopupMenu popup = new JPopupMenu();
        JMenuItem reroll = new JMenuItem("重新随机主题色");
        reroll.addActionListener(e -> {
            this.palette = ProjectPalette.regenerate(paletteKey);
            if (paletteChanged != null)
                paletteChanged.run();
            repaint();
        });
        popup.add(reroll);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { maybePopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { maybePopup(e); }
            private void maybePopup(MouseEvent e) {
                if (e.isPopupTrigger())
                    popup.show(GradientMonogram.this, e.getX(), e.getY());
            }
        });
    }

    public ProjectPalette getPalette() {
        return palette;
    }

    /** 切换显示的首字母（打开 / 切换图片时调用）。 */
    public void setInitials(String initials) {
        String next = initials == null || initials.isBlank() ? "?" : initials;
        if (!next.equals(this.initials)) {
            this.initials = next;
            repaint();
        }
    }

    public void setPaletteChangedCallback(Runnable r) {
        this.paletteChanged = r;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int s = Math.min(getWidth(), getHeight());
            paintBlock(g2, 0, 0, s);
        } finally {
            g2.dispose();
        }
    }

    private void paintBlock(Graphics2D g2, int x, int y, int size) {
        int arc = UIScale.scale(5);
        LinearGradientPaint gradient = new LinearGradientPaint(
                new Point2D.Double(x, y), new Point2D.Double(x + size, y + size),
                new float[]{0f, 1f}, new Color[]{palette.blockTop, palette.blockBottom});
        g2.setPaint(gradient);
        g2.fill(new RoundRectangle2D.Double(x, y, size, size, arc, arc));

        g2.setColor(Color.WHITE);
        if (initials.length() == 1 && initials.charAt(0) == 'I')
            paintSlabI(g2, x, y, size);
        else
            paintLetter(g2, x, y, size);
    }

    /** 衬线风格的大写 "I"（上下带短横），与参考图一致。 */
    private void paintSlabI(Graphics2D g2, int x, int y, int size) {
        double stemW = size * 0.13;
        double slabH = size * 0.10;
        double slabW = size * 0.40;
        g2.fill(new RoundRectangle2D.Double(
                x + (size - stemW) / 2, y + size * 0.27, stemW, size * 0.42,
                stemW / 2, stemW / 2));
        g2.fill(new RoundRectangle2D.Double(
                x + (size - slabW) / 2, y + size * 0.24, slabW, slabH,
                slabH / 2, slabH / 2));
        g2.fill(new RoundRectangle2D.Double(
                x + (size - slabW) / 2, y + size * 0.66, slabW, slabH,
                slabH / 2, slabH / 2));
    }

    private void paintLetter(Graphics2D g2, int x, int y, int size) {
        // 双字母用 0.40，单字母（含汉字）用 0.58
        boolean single = initials.length() == 1;
        float fs = single ? size * 0.58f : size * 0.40f;
        g2.setFont(TitleTheme.textFont(fs, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(initials);
        g2.drawString(initials,
                x + (size - tw) / 2,
                y + (size + fm.getAscent() - fm.getDescent()) / 2);
    }

    /** 把色块渲染成图片。 */
    public BufferedImage toImage(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            paintBlock(g2, 0, 0, size);
        } finally {
            g2.dispose();
        }
        return img;
    }

    /** 取名字的前一 / 两个首字母（"cat.png" -> "C"，"my photo" -> "MP"）。 */
    public static String initialsOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("[-_.\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty())
                sb.append(Character.toUpperCase(p.charAt(0)));
        }
        if (sb.length() == 0)
            sb.append(Character.toUpperCase(name.trim().charAt(0)));
        return sb.substring(0, Math.min(2, sb.length()));
    }
}
