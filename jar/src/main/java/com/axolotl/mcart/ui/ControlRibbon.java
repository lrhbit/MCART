package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.DitherMethod;
import com.axolotl.mcart.core.PreprocessSettings;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.MapMode;
import com.axolotl.mcart.model.Project;
import com.axolotl.mcart.ui.titlebar.TitleIcons;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * 中央区顶部的「参数工具条」（PS 式 options bar）：横向单行排布高频参数，
 * 图像处理的 8 个滑块收进「调整」弹层；整条可一键折叠以最大化预览区。
 *
 * 控件值通过 {@link #captureTo(Project)} / {@link #loadFrom(Project)} 与工程对象互相同步；
 * 任意参数变化触发 {@link Listener#changed()}。
 */
public class ControlRibbon extends JPanel {

    public static final int BLOCKS_PER_MAP = 128;

    public interface Listener {
        /** 任一参数被用户改动（主窗口负责压栈撤销、实时重生成、刷新标签）。 */
        void changed();
    }

    private final Listener listener;
    private final PreprocessPanel adjustPanel;
    private Runnable onAdjustChange;

    private final JComboBox<MapMode> modeCombo = new JComboBox<>(MapMode.values());
    private final JComboBox<DitherMethod> ditherCombo = new JComboBox<>(DitherMethod.values());
    private final JSpinner strengthSpinner = new JSpinner(new SpinnerNumberModel(100, 0, 100, 5));

    private JComboBox<String> supportCombo;
    private JLabel fillerLabel;

    private final JSpinner mapWidthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    private final JSpinner mapHeightSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    private final JLabel sizeInfoLabel = new JLabel();
    private final JLabel selectedCountLabel = new JLabel();
    private final JLabel preprocessStatusLabel = new JLabel();

    private final JButton adjustBtn;
    private boolean adjustActive;

    private JPopupMenu adjustPopup;
    private boolean loading;

    private static final String[] SUPPORT_KEYS = {"support.STONE", "support.COBBLE", "support.DIRT",
            "support.DEEPSLATE", "support.ANDESITE", "support.SAME"};

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost;
    private final JPanel fullRow;
    private final JPanel collapsedRow;
    private JButton expandBtn;
    private boolean collapsed;

    public ControlRibbon(Listener listener) {
        super(new BorderLayout());
        this.listener = listener;
        this.adjustPanel = new PreprocessPanel();
        this.adjustPanel.setOnChange(this::onAdjustChanged);
        adjustBtn = new AdjustButton();

        initCombos();

        cardHost = new JPanel(cards);
        cardHost.setOpaque(false);
        fullRow = buildFullRow();
        collapsedRow = buildCollapsedRow();
        cardHost.add(fullRow, "full");
        cardHost.add(collapsedRow, "collapsed");

        setOpaque(true);
        setBackground(UiTheme.chrome());
        setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER));
        add(cardHost, BorderLayout.CENTER);
        showCollapsed(false);
        updateSupportComboState();
    }

    // -------------------------------------------------------------- 折叠状态

    private void showCollapsed(boolean c) {
        this.collapsed = c;
        cards.show(cardHost, c ? "collapsed" : "full");
        revalidate();
        repaint();
    }

    private JPanel buildCollapsedRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(2, 10, 2, 8));
        expandBtn = new JButton(I18n.t("ribbon.params"), TitleIcons.collapseBar);
        expandBtn.setIconTextGap(7);
        expandBtn.setHorizontalTextPosition(SwingConstants.RIGHT);
        expandBtn.setFocusPainted(false);
        expandBtn.setContentAreaFilled(false);
        expandBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        expandBtn.setForeground(UiTheme.ICON_TEXT);
        expandBtn.addActionListener(e -> showCollapsed(false));
        rotateIcon(expandBtn, 180);
        row.add(expandBtn, BorderLayout.WEST);
        return row;
    }

    private static void rotateIcon(AbstractButton b, int deg) {
        if (b.getIcon() == null) return;
        Icon src = b.getIcon();
        int w = src.getIconWidth(), h = src.getIconHeight();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                Math.max(1, w), Math.max(1, h), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.rotate(Math.toRadians(deg), w / 2.0, h / 2.0);
        src.paintIcon(b, g, 0, 0);
        g.dispose();
        b.setIcon(new ImageIcon(img));
    }

    // -------------------------------------------------------------- 完整一行

    private JPanel buildFullRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(5, 10, 5, 8));

        adjustBtn.setIcon(TitleIcons.sliders);
        adjustBtn.setHorizontalTextPosition(SwingConstants.RIGHT);
        adjustBtn.addActionListener(e -> showAdjustPopup(adjustBtn));
        row.add(adjustBtn);

        row.add(divider());

        row.add(fieldLabel(I18n.t("ribbon.type")));
        row.add(modeCombo);
        row.add(hGap(8));
        row.add(fieldLabel(I18n.t("ribbon.dither")));
        row.add(ditherCombo);
        row.add(hGap(8));
        row.add(fieldLabel(I18n.t("ribbon.strength")));
        row.add(strengthSpinner);

        row.add(divider());

        row.add(fieldLabel(I18n.t("label.maps")));
        row.add(hGap(2));
        Dimension spinSize = new Dimension(54, 26);
        mapWidthSpinner.setPreferredSize(spinSize);
        mapHeightSpinner.setPreferredSize(spinSize);
        row.add(mapWidthSpinner);
        JLabel x = new JLabel("×");
        x.setForeground(UiTheme.MUTED_TEXT);
        row.add(x);
        row.add(mapHeightSpinner);
        row.add(new JLabel(I18n.t("form.mapsUnit")));
        row.add(hGap(8));
        sizeInfoLabel.setForeground(UiTheme.MUTED_TEXT);
        sizeInfoLabel.setFont(sizeInfoLabel.getFont().deriveFont(11f));
        row.add(sizeInfoLabel);

        row.add(divider());

        fillerLabel = fieldLabel(I18n.t("label.filler"));
        row.add(fillerLabel);
        row.add(hGap(4));
        row.add(supportCombo);

        row.add(Box.createHorizontalGlue());

        selectedCountLabel.setForeground(UiTheme.MUTED_TEXT);
        selectedCountLabel.setFont(selectedCountLabel.getFont().deriveFont(11f));
        row.add(selectedCountLabel);
        row.add(hGap(12));
        preprocessStatusLabel.setForeground(UiTheme.MUTED_TEXT);
        preprocessStatusLabel.setFont(preprocessStatusLabel.getFont().deriveFont(11f));
        preprocessStatusLabel.setMaximumSize(new Dimension(260, 22));
        row.add(preprocessStatusLabel);
        row.add(hGap(10));
        JButton collapseBtn = new JButton(TitleIcons.collapseBar);
        collapseBtn.setFocusPainted(false);
        collapseBtn.setContentAreaFilled(false);
        collapseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        collapseBtn.setToolTipText(I18n.t("ribbon.collapse"));
        collapseBtn.setMargin(new Insets(2, 4, 2, 4));
        collapseBtn.addActionListener(e -> showCollapsed(true));
        row.add(collapseBtn);

        return row;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(UiTheme.MUTED_TEXT);
        l.setFont(l.getFont().deriveFont(11.5f));
        l.setBorder(new EmptyBorder(0, 0, 0, 5));
        return l;
    }

    private static Component hGap(int px) {
        return Box.createHorizontalStrut(UIScale.scale(px));
    }

    private static JComponent divider() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 22));
        s.setMaximumSize(new Dimension(1, 22));
        s.setForeground(UiTheme.BORDER);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(0, 10, 0, 10));
        wrap.add(s, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(21, 24));
        return wrap;
    }

    // -------------------------------------------------------------- 调整弹层

    public void setOnAdjust(Runnable r) { this.onAdjustChange = r; }

    void showAdjustPopup(Component invoker) {
        if (adjustPopup == null) {
            adjustPopup = new JPopupMenu();
            adjustPopup.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER, 1));
            adjustPopup.setBackground(UiTheme.CARD_BG);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setBorder(new EmptyBorder(6, 6, 6, 6));
            wrap.setBackground(UiTheme.CARD_BG);
            adjustPanel.setPreferredSize(new Dimension(340, 460));
            wrap.add(adjustPanel, BorderLayout.CENTER);
            adjustPopup.add(wrap);
        }
        adjustPopup.setPopupSize(356, 480);
        adjustPopup.show(invoker, 0, invoker.getHeight() + 4);
    }

    public void showAdjustPopup() {
        showAdjustPopup(adjustBtn);
    }

    private void onAdjustChanged() {
        adjustActive = adjustPanel.getSettings().enabled;
        adjustBtn.repaint();
        if (onAdjustChange != null) onAdjustChange.run();
    }

    // -------------------------------------------------------------- 控件初始化

    private void initCombos() {
        modeCombo.setPrototypeDisplayValue(MapMode.RELIEF);
        modeCombo.setRenderer(render((v) -> v instanceof MapMode m ? I18n.t("mode." + m.name()) : null));
        modeCombo.addActionListener(e -> {
            updateSupportComboState();
            fireChanged();
        });

        ditherCombo.setPrototypeDisplayValue(DitherMethod.FLOYD_STEINBERG);
        ditherCombo.setRenderer(render((v) -> v instanceof DitherMethod dm ? I18n.t("dither." + dm.name()) : null));
        ditherCombo.setToolTipText(I18n.t("dither.tip"));
        ditherCombo.addActionListener(e -> fireChanged());

        strengthSpinner.setToolTipText(I18n.t("strength.tip"));
        strengthSpinner.setPreferredSize(new Dimension(64, 26));
        strengthSpinner.addChangeListener(e -> fireChanged());

        mapWidthSpinner.addChangeListener(e -> { updateSizeLabel(); fireChanged(); });
        mapHeightSpinner.addChangeListener(e -> { updateSizeLabel(); fireChanged(); });

        supportCombo = new JComboBox<>(SUPPORT_KEYS);
        supportCombo.setRenderer(render((v) -> v instanceof String key ? I18n.t(key) : null));
        supportCombo.setPrototypeDisplayValue("support.SAME");
        supportCombo.setToolTipText(I18n.t("filler.tip"));
        supportCombo.addActionListener(e -> fireChanged());

        // 统一所有输入框的视觉样式
        for (JComponent c : new JComponent[]{modeCombo, ditherCombo, strengthSpinner,
                supportCombo, mapWidthSpinner, mapHeightSpinner}) {
            styleAsAdjustButton(c);
        }

        updateSizeLabel();
    }

    /**
     * 将组件样式调整为与「调整」按钮一致：圆角 8px、无外发光，并完全透明化内部箭头按钮。
     * 背景常态 FIELD_BG，悬停 HOVER，禁用 TAB_WELL。
     */
    private void styleAsAdjustButton(JComponent comp) {
        String borderColorHex = String.format("#%06X", UiTheme.BORDER.getRGB() & 0xFFFFFF);

        // 核心修改：使用 `buttonStyle: none` 彻底去除内部按钮背景和分隔线，
        // 这样下拉箭头和微调箭头周围就会完全透明，不留痕迹。
        comp.putClientProperty("FlatLaf.style",
                "arc: 8; focusWidth: 0; innerFocusWidth: 0; borderColor: " + borderColorHex +
                        "; buttonBackground: null; buttonSeparatorColor: null; buttonDisabledSeparatorColor: null");
        comp.setForeground(UiTheme.ICON_TEXT);

        // 处理 Spinner 内部编辑器文本的透明与配色
        if (comp instanceof JSpinner sp) {
            JComponent editor = sp.getEditor();
            if (editor instanceof JSpinner.DefaultEditor de) {
                JTextField tf = de.getTextField();
                tf.setOpaque(false);
                tf.setForeground(UiTheme.ICON_TEXT);
                tf.setCaretColor(UiTheme.ICON_TEXT);
            }
        }

        setBg(comp, false);

        comp.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (comp.isEnabled()) setBg(comp, true); }
            @Override public void mouseExited(MouseEvent e)  { if (comp.isEnabled()) setBg(comp, false); }
        });

        comp.addPropertyChangeListener("enabled", evt -> setBg(comp, false));
    }

    /** 同步更新控件及其内部 Spinner 编辑器的背景色。 */
    private static void setBg(JComponent comp, boolean hover) {
        Color bg = !comp.isEnabled() ? UiTheme.TAB_WELL : (hover ? UiTheme.HOVER : UiTheme.FIELD_BG);
        comp.setBackground(bg);
        if (comp instanceof JSpinner sp) {
            sp.getEditor().setBackground(bg);
        }
    }

    private void updateSupportComboState() {
        if (supportCombo == null) return;
        boolean relief = modeCombo.getSelectedItem() == MapMode.RELIEF;
        supportCombo.setEnabled(relief);
        if (fillerLabel != null) fillerLabel.setEnabled(relief);
    }

    private void fireChanged() {
        if (loading || listener == null) return;
        listener.changed();
    }

    private interface TextOf { String text(Object value); }

    private static DefaultListCellRenderer render(TextOf textOf) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String t = textOf.text(value);
                if (t != null) setText(t);
                return this;
            }
        };
    }

    private void updateSizeLabel() {
        int w = (int) mapWidthSpinner.getValue(), h = (int) mapHeightSpinner.getValue();
        sizeInfoLabel.setText(w * BLOCKS_PER_MAP + "×" + h * BLOCKS_PER_MAP + " " + I18n.t("unit.cells"));
    }

    // ============================================================ 工程 ↔ 控件

    public void captureTo(Project p) {
        p.setMode((MapMode) modeCombo.getSelectedItem());
        p.setDither((DitherMethod) ditherCombo.getSelectedItem());
        p.setDitherStrength((Integer) strengthSpinner.getValue());
        p.setMapsW((Integer) mapWidthSpinner.getValue());
        p.setMapsH((Integer) mapHeightSpinner.getValue());
        p.setSupportBlockId(selectedSupportBlock());
        p.setPreprocess(adjustPanel.getSettings());
    }

    public void loadFrom(Project p) {
        loading = true;
        try {
            modeCombo.setSelectedItem(p.getMode());
            ditherCombo.setSelectedItem(p.getDither());
            strengthSpinner.setValue(p.getDitherStrength());
            mapWidthSpinner.setValue(p.getMapsW());
            mapHeightSpinner.setValue(p.getMapsH());
            supportCombo.setSelectedIndex(supportIndexOf(p.getSupportBlockId()));
            adjustPanel.load(p.getPreprocess());
            adjustActive = p.getPreprocess() != null && p.getPreprocess().enabled;
            updateSizeLabel();
            updateSupportComboState();
        } finally {
            loading = false;
        }
    }

    public PreprocessSettings getPreprocessSettings() { return adjustPanel.getSettings(); }

    public void loadPreprocess(PreprocessSettings s) {
        adjustPanel.load(s);
        adjustActive = s != null && s.enabled;
        adjustBtn.repaint();
    }

    public void setSelectedCount(int n) {
        selectedCountLabel.setText(I18n.t("label.selectedKinds", n));
    }

    public void setPreprocessStatus(String text) {
        preprocessStatusLabel.setText(text);
    }

    public boolean isCollapsed() { return collapsed; }

    private String selectedSupportBlock() {
        return switch (supportCombo == null ? 0 : supportCombo.getSelectedIndex()) {
            case 1 -> "minecraft:cobblestone";
            case 2 -> "minecraft:dirt";
            case 3 -> "minecraft:deepslate";
            case 4 -> "minecraft:andesite";
            case 5 -> "#same";
            default -> "minecraft:stone";
        };
    }

    private static int supportIndexOf(String id) {
        return switch (id == null ? "minecraft:stone" : id) {
            case "minecraft:cobblestone" -> 1;
            case "minecraft:dirt" -> 2;
            case "minecraft:deepslate" -> 3;
            case "minecraft:andesite" -> 4;
            case "#same" -> 5;
            default -> 0;
        };
    }

    // -------------------------------------------------------------- 调整按钮

    /** 「调整」按钮：圆角；预处理开启时描边 + 强调色文字，关闭时为普通次要按钮。 */
    private class AdjustButton extends JButton {
        AdjustButton() {
            super(I18n.t("ribbon.adjust"));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIconTextGap(6);
            setMargin(new Insets(4, 10, 4, 10));
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setForeground(UiTheme.ICON_TEXT);
            setBorder(new EmptyBorder(4, 10, 4, 10));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { repaint(); }
                @Override public void mouseExited(MouseEvent e) { repaint(); }
                @Override public void mousePressed(MouseEvent e) { repaint(); }
                @Override public void mouseReleased(MouseEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = UIScale.scale(8);
            boolean pressed = getModel().isPressed();
            Color bg;
            if (adjustActive) {
                bg = UiTheme.alpha(UiTheme.ACCENT, pressed ? 60 : 40);
            } else {
                bg = pressed ? UiTheme.HOVER_STRONG : (getMousePosition() != null ? UiTheme.HOVER : UiTheme.FIELD_BG);
            }
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1f, getHeight() - 1f, arc, arc));
            g2.setColor(adjustActive ? UiTheme.ACCENT : UiTheme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2f, getHeight() - 2f, arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Color getForeground() {
            return adjustActive ? UiTheme.ACCENT
                    : (super.getForeground() == null ? UiTheme.ICON_TEXT : super.getForeground());
        }
    }
}