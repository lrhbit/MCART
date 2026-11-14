package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * 统一风格的消息 / 确认弹窗（替代原生 JOptionPane）：
 * 左侧彩色圆形图标 + 多行提示文本，底部「取消 / 确定」。
 */
public class MessageDialog extends AppDialog {

    public enum Kind { INFO, WARNING, ERROR }

    private boolean confirmed;

    private MessageDialog(JFrame owner, Kind kind, String title, String message, boolean ask) {
        super(owner, title, true);

        RoundedPanel body = card(new BorderLayout(14, 0));
        body.setBorder(new EmptyBorder(20, 20, 18, 20));

        // 左侧圆形图标
        IconGlyph glyph = new IconGlyph(kind);
        glyph.setPreferredSize(new Dimension(40, 40));
        body.add(glyph, BorderLayout.WEST);

        // 右侧文本（支持换行）
        JTextArea text = new JTextArea(message);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setOpaque(false);
        text.setFocusable(false);
        text.setForeground(UiTheme.SECTION_TITLE);
        text.setFont(text.getFont().deriveFont(13f));
        text.setBorder(new EmptyBorder(2, 0, 0, 0));
        body.add(text, BorderLayout.CENTER);

        setBody(body);

        Footer footer = new Footer();
        if (ask) {
            JButton cancel = actionButton(I18n.t("common.cancel"));
            cancel.addActionListener(e -> close());
            footer.addButton(cancel);
        }
        JButton ok = ask ? primaryActionButton(I18n.t("common.ok"))
                : primaryActionButton(I18n.t("common.ok"));
        ok.addActionListener(e -> { confirmed = true; close(); });
        footer.addButton(ok, true);
        setFooter(footer);

        // 按文本行数估算高度
        int lines = estimateLines(message, 320);
        int h = Math.max(180, 128 + lines * 22);
        setupSize(440, Math.min(380, h));
        setLocationRelativeTo(owner);
    }

    private static int estimateLines(String text, int width) {
        int total = 0;
        for (String raw : text.split("\n", -1)) {
            String line = raw.isEmpty() ? " " : raw;
            int len = line.length();
            // 粗略按 14px/字符估算，中文更宽
            total += Math.max(1, (int) Math.ceil(len * 16.0 / width));
        }
        return total;
    }

    /** 仅提示，返回 true 表示已确认。 */
    public static boolean show(JFrame owner, Kind kind, String title, String message) {
        MessageDialog d = new MessageDialog(owner, kind, title, message, false);
        d.setVisible(true);
        return d.confirmed;
    }

    /** 确认/取消；返回 true 表示用户点了确定。 */
    public static boolean confirm(JFrame owner, Kind kind, String title, String message) {
        MessageDialog d = new MessageDialog(owner, kind, title, message, true);
        d.setVisible(true);
        return d.confirmed;
    }

    @Override public void close() { setVisible(false); dispose(); }

    /** 左侧彩色圆形小图标：警告三角 / 错误叉 / 信息 i。 */
    private static class IconGlyph extends JComponent {
        private final Kind kind;
        IconGlyph(Kind kind) { this.kind = kind; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = switch (kind) {
                case WARNING -> new Color(0xE0A030);
                case ERROR -> new Color(0xD05A54);
                default -> UiTheme.ACCENT;
            };
            int d = Math.min(getWidth(), getHeight());
            // 圆形底
            g2.setColor(UiTheme.alpha(c, 34));
            g2.fill(new RoundRectangle2D.Float(0, 0, d, d, d, d));
            g2.setColor(c);
            g2.setStroke(new BasicStroke(2f));
            int cx = d / 2;
            if (kind == Kind.WARNING) {
                // 感叹号
                g2.setFont(getFont().deriveFont(Font.BOLD, 20f));
                FontMetrics fm = g2.getFontMetrics();
                String ex = "!";
                g2.drawString(ex, cx - fm.stringWidth(ex) / 2, (d + fm.getAscent()) / 2 - 2);
            } else if (kind == Kind.ERROR) {
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                FontMetrics fm = g2.getFontMetrics();
                String ex = "×";
                g2.drawString(ex, cx - fm.stringWidth(ex) / 2, (d + fm.getAscent()) / 2 - 2);
            } else {
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                FontMetrics fm = g2.getFontMetrics();
                String ex = "i";
                g2.drawString(ex, cx - fm.stringWidth(ex) / 2, (d + fm.getAscent()) / 2 - 2);
            }
            g2.dispose();
        }
    }
}
