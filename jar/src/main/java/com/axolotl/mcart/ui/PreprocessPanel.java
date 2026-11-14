package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.PreprocessSettings;
import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.IntFunction;

/**
 * 内嵌式图像处理面板（圆角卡片风格）：分区标题 + 启用开关、亮度/对比度/伽马/RGB/
 * 饱和度/色调分离/去色/背景（每个滑块右侧带实时数值徽标），底部为裁剪 / 像素编辑工具条。
 * 与 {@link PreprocessDialog} 共用同一套参数语义；所有变化实时回调。
 */
public class PreprocessPanel extends JPanel {

    private static final int LABEL_W = 56;
    private static final int VALUE_W = 52;

    private JCheckBox enableCheck;
    private JSlider brightnessSlider, contrastSlider, saturationSlider,
            gammaSlider, redSlider, greenSlider, blueSlider, posterizeSlider;
    private JCheckBox grayscaleCheck;
    private JComboBox<PreprocessSettings.BackgroundMode> bgCombo;
    private JButton colorButton;
    private int bgRgb = PreprocessSettings.DEFAULT_BACKGROUND_RGB;
    private boolean loading = false;
    private Runnable onChange;

    public PreprocessPanel() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        build();
        load(PreprocessSettings.disabled());
    }

    public void setOnChange(Runnable r) { this.onChange = r; }

    private void fireChange() {
        if (loading || onChange == null) return;
        onChange.run();
    }

    private void build() {
        AppDialog.RoundedPanel card = new AppDialog.RoundedPanel(new BorderLayout(0, 8));
        card.setBackground(UiTheme.CARD_BG);
        card.setBorderColor(UiTheme.BORDER);
        card.setArc(10);
        card.setBorderWidth(1);
        card.setInsets(new Insets(10, 12, 10, 12));

        // —— 标题 + 启用开关 ——
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        JLabel title = new JLabel(I18n.t("panel.image"));
        title.setForeground(UiTheme.SECTION_TITLE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        header.add(title, BorderLayout.WEST);

        enableCheck = new JCheckBox(I18n.t("pp.enable"));
        enableCheck.setOpaque(false);
        enableCheck.setHorizontalTextPosition(SwingConstants.LEFT);
        enableCheck.setToolTipText(I18n.t("pp.enableTip"));
        enableCheck.addActionListener(e -> { syncEnabled(); fireChange(); });
        header.add(enableCheck, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        // —— 滑块区（可滚动，宽度始终跟随视口，避免右侧数值徽标被裁） ——
        AppDialog.VScrollList body = new AppDialog.VScrollList();
        body.setOpaque(false);

        brightnessSlider = slider(0, 200, 100); contrastSlider = slider(0, 200, 100);
        saturationSlider = slider(0, 200, 100); gammaSlider = slider(20, 300, 100);
        redSlider = slider(0, 200, 100); greenSlider = slider(0, 200, 100); blueSlider = slider(0, 200, 100);
        posterizeSlider = slider(0, 32, 0);

        body.add(sliderRow(I18n.t("pp.brightness"), brightnessSlider,
                I18n.t("pp.tip.brightness"), PreprocessPanel::percentText));
        body.add(sliderRow(I18n.t("pp.contrast"), contrastSlider,
                I18n.t("pp.tip.contrast"), PreprocessPanel::percentText));
        body.add(sliderRow(I18n.t("pp.gamma"), gammaSlider,
                I18n.t("pp.tip.gamma"), v -> String.format("%.2f", v / 100.0)));
        body.add(sliderRow(I18n.t("pp.r"), redSlider,
                I18n.t("pp.tip.r"), PreprocessPanel::percentText));
        body.add(sliderRow(I18n.t("pp.g"), greenSlider,
                I18n.t("pp.tip.g"), PreprocessPanel::percentText));
        body.add(sliderRow(I18n.t("pp.b"), blueSlider,
                I18n.t("pp.tip.b"), PreprocessPanel::percentText));
        body.add(sliderRow(I18n.t("pp.saturation"), saturationSlider,
                I18n.t("pp.tip.saturation"), PreprocessPanel::percentText));
        body.add(sliderRow(I18n.t("pp.posterize"), posterizeSlider,
                I18n.t("pp.tip.posterize"),
                v -> v == 0 ? I18n.t("bg.OFF") : String.valueOf(v)));

        grayscaleCheck = new JCheckBox(I18n.t("pp.grayscale"));
        grayscaleCheck.setOpaque(false);
        grayscaleCheck.setAlignmentX(LEFT_ALIGNMENT);
        grayscaleCheck.addActionListener(e -> fireChange());
        JPanel grayWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        grayWrap.setOpaque(false);
        grayWrap.setAlignmentX(LEFT_ALIGNMENT);
        grayWrap.setMaximumSize(new Dimension(Short.MAX_VALUE, 26));
        grayWrap.add(grayscaleCheck);
        body.add(grayWrap);

        // 背景行
        JPanel bgRow = new JPanel(new BorderLayout(8, 0));
        bgRow.setOpaque(false);
        bgRow.setAlignmentX(LEFT_ALIGNMENT);
        bgRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
        bgRow.setBorder(new EmptyBorder(4, 0, 0, 0));
        JLabel bgLabel = new JLabel(I18n.t("pp.background"));
        bgLabel.setPreferredSize(new Dimension(I18n.isEn() ? 80 : LABEL_W, 22));
        bgRow.add(bgLabel, BorderLayout.WEST);

        JPanel bgEditors = new JPanel(new BorderLayout(6, 0));
        bgEditors.setOpaque(false);
        bgCombo = new JComboBox<>(PreprocessSettings.BackgroundMode.values());
        bgCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PreprocessSettings.BackgroundMode bg)
                    setText(I18n.t("bg." + bg.name()));
                return this;
            }
        });
        bgCombo.setToolTipText(I18n.t("pp.bgComboTip"));
        bgCombo.addActionListener(e -> { syncEnabled(); fireChange(); });
        bgEditors.add(bgCombo, BorderLayout.CENTER);
        colorButton = new JButton(" ");
        colorButton.setMargin(new Insets(2, 4, 2, 4));
        colorButton.setFocusPainted(false);
        colorButton.setPreferredSize(new Dimension(64, 26));
        colorButton.addActionListener(e -> pickColor());
        bgEditors.add(colorButton, BorderLayout.EAST);
        bgRow.add(bgEditors, BorderLayout.CENTER);
        body.add(bgRow);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBackground(UiTheme.CARD_BG);
        scroll.getViewport().setBackground(UiTheme.CARD_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setBorder(null);
        card.add(scroll, BorderLayout.CENTER);

        add(card, BorderLayout.CENTER);
    }

    private JSlider slider(int min, int max, int val) {
        JSlider s = new JSlider(min, max, val);
        s.setOpaque(false);
        return s;
    }

    /** 一行：标签（固定宽）+ 滑块 + 数值徽标（固定宽，右对齐）。 */
    private JComponent sliderRow(String label, JSlider s, String tip, IntFunction<String> fmt) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Short.MAX_VALUE, 26));
        row.setBorder(new EmptyBorder(1, 0, 1, 0));

        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(I18n.isEn() ? 80 : LABEL_W, 22));
        l.setToolTipText(tip);
        s.setToolTipText(tip);
        row.add(l, BorderLayout.WEST);

        JLabel value = new JLabel(fmt.apply(s.getValue()));
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        value.setPreferredSize(new Dimension(I18n.isEn() ? 60 : VALUE_W, 22));
        value.setForeground(UiTheme.MUTED_TEXT);
        value.setFont(value.getFont().deriveFont(11f));
        value.setToolTipText(tip);
        row.add(value, BorderLayout.EAST);

        row.add(s, BorderLayout.CENTER);
        s.addChangeListener(e -> {
            value.setText(fmt.apply(s.getValue()));
            fireChange();
        });
        return row;
    }

    private static String percentText(int v) {
        int d = v - 100;
        return (d > 0 ? "+" : "") + d + "%";
    }

    private void pickColor() {
        Color c = JColorChooser.showDialog(this, I18n.t("ppd.pickBg"), new Color(bgRgb));
        if (c != null) {
            bgRgb = c.getRGB() & 0xFFFFFF;
            updateColorButton();
            fireChange();
        }
    }

    private void updateColorButton() {
        colorButton.setBackground(new Color(bgRgb));
        colorButton.setText(String.format("#%06X", bgRgb));
        int r = (bgRgb >> 16) & 0xFF, g = (bgRgb >> 8) & 0xFF, b = bgRgb & 0xFF;
        double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        colorButton.setForeground(luma > 140 ? Color.BLACK : Color.WHITE);
    }

    private void syncEnabled() {
        boolean on = enableCheck.isSelected();
        for (JComponent c : new JComponent[]{
                brightnessSlider, contrastSlider, saturationSlider, gammaSlider,
                redSlider, greenSlider, blueSlider, posterizeSlider,
                grayscaleCheck, bgCombo}) {
            c.setEnabled(on);
        }
        colorButton.setEnabled(on && bgCombo.getSelectedItem() != PreprocessSettings.BackgroundMode.OFF);
    }

    public void load(PreprocessSettings s) {
        if (s == null) s = PreprocessSettings.disabled();
        loading = true;
        enableCheck.setSelected(s.enabled);
        brightnessSlider.setValue(s.brightness);
        contrastSlider.setValue(s.contrast);
        saturationSlider.setValue(s.saturation);
        gammaSlider.setValue(s.gamma);
        redSlider.setValue(s.redGain);
        greenSlider.setValue(s.greenGain);
        blueSlider.setValue(s.blueGain);
        posterizeSlider.setValue(s.posterize);
        grayscaleCheck.setSelected(s.grayscale);
        bgCombo.setSelectedItem(s.backgroundMode);
        bgRgb = s.backgroundRgb;
        updateColorButton();
        syncEnabled();
        loading = false;
    }

    public PreprocessSettings getSettings() {
        return new PreprocessSettings(
                enableCheck.isSelected(),
                brightnessSlider.getValue(), contrastSlider.getValue(), saturationSlider.getValue(),
                gammaSlider.getValue(), redSlider.getValue(), greenSlider.getValue(), blueSlider.getValue(),
                posterizeSlider.getValue(), grayscaleCheck.isSelected(),
                (PreprocessSettings.BackgroundMode) bgCombo.getSelectedItem(),
                bgRgb);
    }
}
