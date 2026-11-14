package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.PreprocessSettings;
import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * 「图像预处理」对话框（非模态）：亮度 / 对比度 / 伽马 / RGB 通道增益 /
 * 饱和度 / 去色 / 色调分离 / 背景模式与底色。任何变化实时回调，
 * 「完成」保留、「取消」/ 叉掉还原打开前设置。
 */
public class PreprocessDialog extends AppDialog {

    private JCheckBox enableCheck;
    private JSlider brightnessSlider, contrastSlider, saturationSlider,
            gammaSlider, redSlider, greenSlider, blueSlider, posterizeSlider;
    private JSpinner brightnessSpinner, contrastSpinner, saturationSpinner,
            gammaSpinner, redSpinner, greenSpinner, blueSpinner, posterizeSpinner;
    private JCheckBox grayscaleCheck;
    private JComboBox<PreprocessSettings.BackgroundMode> bgCombo;
    private JButton colorButton;

    private int bgRgb = PreprocessSettings.DEFAULT_BACKGROUND_RGB;
    private boolean applied = false;
    private boolean loading = false;
    private Consumer<PreprocessSettings> onChange;
    private Consumer<Boolean> onClose;

    public PreprocessDialog(JFrame owner) {
        super(owner, I18n.t("ppd.title"), false);
        initUI();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { notifyClosed(); }
        });
        setupSize(540, 660);
    }

    public void setOnChange(Consumer<PreprocessSettings> onChange) { this.onChange = onChange; }
    public void setOnClose(Consumer<Boolean> onClose) { this.onClose = onClose; }

    private void fireChange() {
        if (loading || onChange == null) return;
        onChange.accept(getSettings());
    }

    @Override public void close() {
        notifyClosed();
        super.close();
    }

    private void notifyClosed() {
        if (onClose != null) onClose.accept(applied);
    }

    private void initUI() {
        enableCheck = new JCheckBox(I18n.t("ppd.enable"));
        enableCheck.setFont(enableCheck.getFont().deriveFont(Font.BOLD, 13f));
        enableCheck.setOpaque(false);
        enableCheck.addActionListener(e -> { syncEnabledState(); fireChange(); });
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 2, 2, 2));
        header.add(enableCheck, BorderLayout.WEST);
        setHeader(header);

        RoundedPanel card = card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        brightnessSlider = slider(0, 200, 100); contrastSlider = slider(0, 200, 100);
        saturationSlider = slider(0, 200, 100); gammaSlider = slider(20, 300, 100);
        redSlider = slider(0, 200, 100); greenSlider = slider(0, 200, 100); blueSlider = slider(0, 200, 100);
        posterizeSlider = slider(0, 32, 0);
        brightnessSpinner = numberSpinner(0, 200, 100); contrastSpinner = numberSpinner(0, 200, 100);
        saturationSpinner = numberSpinner(0, 200, 100); gammaSpinner = numberSpinner(20, 300, 100);
        redSpinner = numberSpinner(0, 200, 100); greenSpinner = numberSpinner(0, 200, 100);
        blueSpinner = numberSpinner(0, 200, 100); posterizeSpinner = numberSpinner(0, 32, 0);

        link(brightnessSlider, brightnessSpinner);
        link(contrastSlider, contrastSpinner);
        link(saturationSlider, saturationSpinner);
        link(gammaSlider, gammaSpinner);
        link(redSlider, redSpinner);
        link(greenSlider, greenSpinner);
        link(blueSlider, blueSpinner);
        link(posterizeSlider, posterizeSpinner);

        card.add(sliderRow(I18n.t("pp.brightness"), brightnessSlider, brightnessSpinner,
                I18n.t("pp.tip.brightness")));
        card.add(sliderRow(I18n.t("pp.contrast"), contrastSlider, contrastSpinner,
                I18n.t("pp.tip.contrast")));
        card.add(sliderRow(I18n.t("pp.gamma"), gammaSlider, gammaSpinner,
                I18n.t("pp.tip.gamma")));
        card.add(sliderRow(I18n.t("pp.r"), redSlider, redSpinner, I18n.t("pp.tip.r")));
        card.add(sliderRow(I18n.t("pp.g"), greenSlider, greenSpinner, I18n.t("pp.tip.g")));
        card.add(sliderRow(I18n.t("pp.b"), blueSlider, blueSpinner, I18n.t("pp.tip.b")));
        card.add(sliderRow(I18n.t("pp.saturation"), saturationSlider, saturationSpinner,
                I18n.t("pp.tip.saturation")));
        card.add(sliderRow(I18n.t("pp.posterize"), posterizeSlider, posterizeSpinner,
                I18n.t("pp.tip.posterize")));

        grayscaleCheck = new JCheckBox(I18n.t("ppd.grayscale"));
        grayscaleCheck.setOpaque(false);
        grayscaleCheck.setAlignmentX(LEFT_ALIGNMENT);
        grayscaleCheck.addActionListener(e -> fireChange());
        card.add(grayscaleCheck);

        JPanel bgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bgRow.setOpaque(false);
        bgRow.setAlignmentX(LEFT_ALIGNMENT);
        bgRow.add(new JLabel(I18n.t("pp.background")));
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
        bgCombo.addActionListener(e -> { syncEnabledState(); fireChange(); });
        bgRow.add(bgCombo);
        colorButton = new JButton(" ") {
            @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        };
        colorButton.setPreferredSize(new Dimension(ui(96), ui(26)));
        colorButton.setMargin(new Insets(2, 6, 2, 6));
        colorButton.setFocusPainted(false);
        colorButton.addActionListener(e -> pickColor());
        bgRow.add(colorButton);
        JLabel bgHint = new JLabel(I18n.t("ppd.bgHint"));
        bgHint.setForeground(UiTheme.MUTED_TEXT);
        bgRow.add(bgHint);
        card.add(bgRow);

        JPanel scrollView = new JPanel(new BorderLayout());
        scrollView.setOpaque(false);
        scrollView.add(card, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(scrollView);
        blendScroll(scroll, UiTheme.CENTER_BG, false);
        setBody(scroll);

        Footer footer = new Footer();
        JButton resetBtn = actionButton(I18n.t("common.reset"));
        resetBtn.addActionListener(e -> resetDefaults());
        footer.addLeft(resetBtn);
        JButton cancelBtn = actionButton(I18n.t("common.cancel"));
        cancelBtn.addActionListener(e -> { applied = false; close(); });
        JButton okBtn = primaryActionButton(I18n.t("ppd.ok"));
        okBtn.addActionListener(e -> { applied = true; close(); });
        footer.addButton(cancelBtn);
        footer.addButton(okBtn, true);
        setFooter(footer);
    }

    private JSlider slider(int min, int max, int val) {
        return new JSlider(min, max, val);
    }

    private JSpinner numberSpinner(int min, int max, int val) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
        sp.setPreferredSize(new Dimension(ui(62), ui(28)));
        com.axolotl.mcart.util.InputMethodHints.disableCompositionWhileFocused(sp);
        return sp;
    }

    private JPanel sliderRow(String label, JSlider slider, JSpinner spinner, String tip) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(2, 0, 2, 0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(I18n.isEn() ? ui(92) : ui(62), ui(26)));
        l.setToolTipText(tip);
        slider.setToolTipText(tip);
        row.add(l, BorderLayout.WEST);
        JPanel mid = new JPanel(new BorderLayout(6, 0));
        mid.setOpaque(false);
        mid.add(slider, BorderLayout.CENTER);
        mid.add(spinner, BorderLayout.EAST);
        row.add(mid, BorderLayout.CENTER);
        return row;
    }

    private void link(JSlider slider, JSpinner spinner) {
        slider.addChangeListener(e -> {
            int v = slider.getValue();
            if (((Number) spinner.getValue()).intValue() != v) spinner.setValue(v);
            fireChange();
        });
        spinner.addChangeListener(e -> {
            int v = ((Number) spinner.getValue()).intValue();
            if (slider.getValue() != v) slider.setValue(v);
        });
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

    private void syncEnabledState() {
        boolean on = enableCheck.isSelected();
        for (JComponent c : new JComponent[]{
                brightnessSlider, contrastSlider, saturationSlider, gammaSlider,
                redSlider, greenSlider, blueSlider, posterizeSlider,
                brightnessSpinner, contrastSpinner, saturationSpinner, gammaSpinner,
                redSpinner, greenSpinner, blueSpinner, posterizeSpinner,
                grayscaleCheck, bgCombo}) {
            c.setEnabled(on);
        }
        boolean bgOn = on && bgCombo.getSelectedItem() != PreprocessSettings.BackgroundMode.OFF;
        colorButton.setEnabled(bgOn);
    }

    void resetDefaults() {
        loading = true;
        enableCheck.setSelected(false);
        brightnessSlider.setValue(100); contrastSlider.setValue(100); saturationSlider.setValue(100);
        gammaSlider.setValue(100); redSlider.setValue(100); greenSlider.setValue(100); blueSlider.setValue(100);
        posterizeSlider.setValue(0); grayscaleCheck.setSelected(false);
        bgCombo.setSelectedItem(PreprocessSettings.BackgroundMode.OFF);
        bgRgb = PreprocessSettings.DEFAULT_BACKGROUND_RGB;
        updateColorButton();
        syncEnabledState();
        loading = false;
        fireChange();
    }

    public void load(PreprocessSettings s) {
        loading = true;
        applied = false;
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
        syncEnabledState();
        loading = false;
    }

    public boolean isApplied() { return applied; }

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
