package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.util.SvgMiniRenderer;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * 全软件统一的对话框基类：与主窗口同一套语义配色与组件语言。
 *
 * <ul>
 *   <li>窗口底色 {@link UiTheme#CENTER_BG}，内容分区使用圆角卡片 {@link #card(LayoutManager)}；</li>
 *   <li>统一 16px 外边距、底部按钮栏（顶部分隔线、右对齐、主按钮强调色）；</li>
 *   <li>Esc 关闭、Enter 触发主按钮、窗口图标与主程序一致、尺寸随 UI 缩放；</li>
 *   <li>非模态复用对话框默认隐藏而非销毁。</li>
 * </ul>
 */
public class AppDialog extends JDialog {

    protected final JPanel root;
    private Footer footer;

    public AppDialog(Window owner, String title, ModalityType modality) {
        super(owner, title, modality);
        try {
            setIconImages(SvgMiniRenderer.loadMultiSizeIconSet("/Logo/logo.svg"));
        } catch (Exception ignored) {
        }
        root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(UiTheme.CENTER_BG);
        root.setBorder(new EmptyBorder(ui(16), ui(18), ui(14), ui(18)));
        setContentPane(root);
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "appdialog.close");
        rp.getActionMap().put("appdialog.close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { close(); }
        });
    }

    /** 便捷构造：模态 / 非模态。 */
    public AppDialog(JFrame owner, String title, boolean modal) {
        this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
    }

    /** 中央内容区。 */
    public void setBody(JComponent body) {
        root.add(body, BorderLayout.CENTER);
    }

    /** 顶部区（可选，位于内容区之上）。 */
    public void setHeader(JComponent header) {
        root.add(header, BorderLayout.NORTH);
    }

    /** 安装底部按钮栏，并把最后一个主按钮设为默认按钮（Enter 触发）。 */
    public void setFooter(Footer bar) {
        this.footer = bar;
        root.add(bar, BorderLayout.SOUTH);
        if (bar.primary != null) getRootPane().setDefaultButton(bar.primary);
    }

    /** Esc / 关闭按钮行为：默认隐藏；需要还原状态的对话框可覆盖。 */
    public void close() {
        setVisible(false);
    }

    /** 统一尺寸：按 UI 缩放设置首选大小并居中，设置最小尺寸。 */
    public void setupSize(int w, int h) {
        Dimension pref = new Dimension(ui(w), ui(h));
        setPreferredSize(pref);
        Dimension min = new Dimension(ui(Math.max(360, (int) (w * 0.62))),
                ui(Math.max(280, (int) (h * 0.62))));
        setMinimumSize(min);
        pack();
        setSize(pref);
        setLocationRelativeTo(getOwner());
    }

    public static int ui(int v) { return UIScale.scale(v); }

    // ---------------------------------------------------------------- 按钮

    /** 普通动作按钮（圆角、统一高度与最小宽度）。 */
    public static JButton actionButton(String text) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setMargin(new Insets(ui(5), ui(14), ui(5), ui(14)));
        b.setPreferredSize(new Dimension(Math.max(ui(84), b.getPreferredSize().width), ui(30)));
        return b;
    }

    /** 主动作按钮（强调色底）。 */
    public static JButton primaryActionButton(String text) {
        JButton b = actionButton(text);
        UiTheme.primaryButton(b);
        return b;
    }

    // ---------------------------------------------------------------- 卡片 / 文字

    /** 圆角卡片容器（CARD_BG 底 + 1px 描边 + 内边距）。 */
    public static RoundedPanel card(LayoutManager layout) {
        RoundedPanel p = new RoundedPanel(layout);
        p.setBackground(UiTheme.CARD_BG);
        p.setBorderColor(UiTheme.BORDER);
        p.setArc(10);
        p.setBorderWidth(1);
        p.setInsets(new Insets(ui(12), ui(14), ui(12), ui(14)));
        return p;
    }

    public static RoundedPanel card() {
        return card(new BorderLayout(8, 8));
    }

    /** 卡片 / 分区标题（加粗）。 */
    public static JLabel cardTitle(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(UiTheme.SECTION_TITLE);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        return l;
    }

    /** 弱化说明文字（自动换行，不依赖 Swing HTML 引擎，native 下可正常显示）。 */
    public static JTextArea hintLabel(String text, int widthPx) {
        JTextArea t = new JTextArea(text);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setOpaque(false);
        t.setFocusable(false);
        t.setForeground(UiTheme.MUTED_TEXT);
        t.setFont(t.getFont().deriveFont(12f));
        return t;
    }

    /** 让滚动面板与指定底色融为一体（无边框、视口同色、滚动速度适中）。 */
    public static void blendScroll(JScrollPane sp, Color viewportBg, boolean bordered) {
        sp.setBorder(bordered ? BorderFactory.createLineBorder(UiTheme.BORDER, 1) : null);
        sp.getViewport().setBackground(viewportBg);
        sp.setBackground(viewportBg);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
    }

    // ---------------------------------------------------------------- 底部栏

    /** 底部按钮栏：左侧可放次要操作，右侧放动作按钮（最后一个为主按钮）。 */
    public static class Footer extends JPanel {
        private final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        private JButton primary;

        public Footer() {
            super(new BorderLayout(8, 0));
            setBackground(UiTheme.CENTER_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(1, 0, 0, 0, UiTheme.alpha(UiTheme.BORDER, 150)),
                    new EmptyBorder(ui(12), 0, 0, 0)));
            left.setOpaque(false);
            add(left, BorderLayout.WEST);
        }

        public Footer addLeft(JComponent c) {
            left.add(c);
            return this;
        }

        /** 右侧追加按钮；primary=true 时作为默认主按钮（强调色）。 */
        public Footer addButton(JButton b, boolean primary) {
            right().add(b);
            if (primary) {
                this.primary = b;
                UiTheme.primaryButton(b);
                Dimension d = b.getPreferredSize();
                b.setPreferredSize(new Dimension(Math.max(ui(84), d.width), Math.max(ui(30), d.height)));
            }
            return this;
        }

        public Footer addButton(JButton b) {
            return addButton(b, false);
        }

        private JPanel right() {
            JPanel r = (JPanel) getClientProperty("rightPanel");
            if (r == null) {
                r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                r.setOpaque(false);
                putClientProperty("rightPanel", r);
                add(r, BorderLayout.EAST);
            }
            return r;
        }
    }

    /** 纵向列表容器：宽度始终跟随视口（子项可横向填满），高度按内容增长。 */
    public static class VScrollList extends JPanel implements Scrollable {
        public VScrollList() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ui(18);
        }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(ui(40), visibleRect.height - ui(20));
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** 圆角填充面板（可描边、可设内边距）。 */
    public static class RoundedPanel extends JPanel {        private int arc = 10;
        private int borderWidth = 1;
        private Color borderColor;
        private Insets insets = new Insets(0, 0, 0, 0);

        public RoundedPanel(LayoutManager lm) {
            super(lm);
            setOpaque(false);
        }

        public void setArc(int arc) { this.arc = arc; }
        public void setBorderWidth(int w) { this.borderWidth = w; }
        public void setBorderColor(Color c) { this.borderColor = c; }
        public void setInsets(Insets i) { this.insets = i; }

        @Override
        public Insets getInsets() { return insets; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            if (borderColor != null && borderWidth > 0) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(borderWidth));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
