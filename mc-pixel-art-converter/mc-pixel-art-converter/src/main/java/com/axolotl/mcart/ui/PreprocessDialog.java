package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.PreprocessSettings;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * 「图像预处理」面板（对齐 mapartcraft 的图像预处理）。
 *
 * 非模态窗口：打开后仍可看到并操作主窗口预览。开关、亮度/对比度/饱和度滑块
 * （0..200，默认 100，滑块与数字框双向联动）、背景模式（关 / 抖动 / 平滑）
 * 与背景颜色的任何变化都会通过 {@link #setOnChange} 实时回调，主窗口据此防抖刷新预览，
 * 无需点击确认。「完成」保留当前设置，「取消」/关闭按钮还原打开前的设置。
 * 默认关闭预处理，关闭时生成结果与不做预处理逐像素一致。
 */
public class PreprocessDialog extends JDialog {

    private static final Color ACCENT = new Color(74, 125, 199);
    private static final Color CARD_BORDER = new Color(64, 66, 72);
    private static final Color FIELD_BG = new Color(43, 44, 48);

    private JCheckBox enableCheck;
    private JSlider brightnessSlider, contrastSlider, saturationSlider;
    private JSpinner brightnessSpinner, contrastSpinner, saturationSpinner;
    private JComboBox<PreprocessSettings.BackgroundMode> bgCombo;
    private JButton colorButton;

    private int bgRgb = PreprocessSettings.DEFAULT_BACKGROUND_RGB;
    private boolean applied = false;
    private boolean loading = false;
    private Consumer<PreprocessSettings> onChange;
    private Consumer<Boolean> onClose;

    public PreprocessDialog(JFrame owner) {
        super(owner, "图像预处理", false); // 非模态，便于边调边看主窗口预览
        setSize(480, 330);
        setLocationRelativeTo(owner);
        setResizable(false);
        initUI();
        // 统一在窗口关闭时回调：完成(applied=true)保留，取消/叉掉(applied=false)还原
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { notifyClosed(); }
        });
    }

    /** 参数实时变化回调（拖动滑块、切换开关/背景、选色时高频触发，主窗口需防抖） */
    public void setOnChange(Consumer<PreprocessSettings> onChange) {
        this.onChange = onChange;
    }

    /** 窗口关闭回调：true=完成保留，false=取消/叉掉，需还原打开前设置 */
    public void setOnClose(Consumer<Boolean> onClose) {
        this.onClose = onClose;
    }

    private void fireChange() {
        if (loading || onChange == null) return;
        onChange.accept(getSettings());
    }

    private void notifyClosed() {
        if (onClose != null) onClose.accept(applied);
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        // —— 顶部开关 ——
        enableCheck = new JCheckBox("启用图像预处理（量化前调整图像）");
        enableCheck.setFont(enableCheck.getFont().deriveFont(Font.BOLD, 13f));
        enableCheck.addActionListener(e -> { syncEnabledState(); fireChange(); });
        root.add(enableCheck, BorderLayout.NORTH);

        // —— 中部设置卡片 ——
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new CompoundBorder(new LineBorder(CARD_BORDER, 1), new EmptyBorder(10, 12, 10, 12)));
        card.setBackground(FIELD_BG);

        brightnessSlider = new JSlider(0, 200, 100);
        contrastSlider = new JSlider(0, 200, 100);
        saturationSlider = new JSlider(0, 200, 100);
        brightnessSpinner = numberSpinner();
        contrastSpinner = numberSpinner();
        saturationSpinner = numberSpinner();
        link(brightnessSlider, brightnessSpinner);
        link(contrastSlider, contrastSpinner);
        link(saturationSlider, saturationSpinner);

        card.add(sliderRow("亮度", brightnessSlider, brightnessSpinner,
                "提亮/压暗整体画面（100=原始）"));
        card.add(sliderRow("对比度", contrastSlider, contrastSpinner,
                "拉开/收窄明暗差距（100=原始）"));
        card.add(sliderRow("饱和度", saturationSlider, saturationSpinner,
                "增强/减弱色彩浓度（100=原始，0=灰度）"));

        // 背景行
        JPanel bgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bgRow.setOpaque(false);
        bgRow.setAlignmentX(LEFT_ALIGNMENT);
        bgRow.add(new JLabel("背景"));
        bgCombo = new JComboBox<>(PreprocessSettings.BackgroundMode.values());
        bgCombo.setToolTipText("关：不铺底色；抖动：直接铺所选颜色再量化；平滑：铺最接近的可选方块纯色");
        bgCombo.addActionListener(e -> { syncEnabledState(); fireChange(); });
        bgRow.add(bgCombo);
        colorButton = new JButton(" ") {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        colorButton.setPreferredSize(new Dimension(96, 26));
        colorButton.setMargin(new Insets(2, 6, 2, 6));
        colorButton.setFocusPainted(false);
        colorButton.addActionListener(e -> pickColor());
        bgRow.add(colorButton);
        JLabel bgHint = new JLabel("仅对透明区域生效；不透明图片无需设置");
        bgHint.setForeground(new Color(150, 152, 158));
        bgRow.add(bgHint);
        card.add(bgRow);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(card, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        // —— 底部按钮 ——
        JPanel bottom = new JPanel(new BorderLayout());
        JButton resetBtn = new JButton("恢复默认");
        resetBtn.putClientProperty("JButton.buttonType", "roundRect");
        resetBtn.addActionListener(e -> resetDefaults());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancelBtn = new JButton("取消");
        cancelBtn.putClientProperty("JButton.buttonType", "roundRect");
        cancelBtn.addActionListener(e -> { applied = false; setVisible(false); });
        JButton okBtn = new JButton("完成");
        okBtn.setBackground(ACCENT);
        okBtn.setForeground(Color.WHITE);
        okBtn.putClientProperty("JButton.buttonType", "roundRect");
        okBtn.addActionListener(e -> { applied = true; setVisible(false); });
        right.add(cancelBtn);
        right.add(okBtn);
        bottom.add(resetBtn, BorderLayout.WEST);
        bottom.add(right, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JSpinner numberSpinner() {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(100, 0, 200, 1));
        sp.setPreferredSize(new Dimension(62, 28));
        return sp;
    }

    private JPanel sliderRow(String label, JSlider slider, JSpinner spinner, String tip) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(2, 0, 2, 0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(52, 26));
        l.setToolTipText(tip);
        slider.setToolTipText(tip);
        slider.setMajorTickSpacing(50);
        slider.setPaintTicks(false);
        row.add(l, BorderLayout.WEST);
        JPanel mid = new JPanel(new BorderLayout(6, 0));
        mid.setOpaque(false);
        mid.add(slider, BorderLayout.CENTER);
        mid.add(spinner, BorderLayout.EAST);
        row.add(mid, BorderLayout.CENTER);
        return row;
    }

    /** 滑块与数字框双向联动（值不同才回写，避免事件递归） */
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
        Color c = JColorChooser.showDialog(this, "选择背景颜色", new Color(bgRgb));
        if (c != null) {
            bgRgb = c.getRGB() & 0xFFFFFF;
            updateColorButton();
            fireChange();
        }
    }

    private void updateColorButton() {
        colorButton.setBackground(new Color(bgRgb));
        colorButton.setText(String.format("#%06X", bgRgb));
        // 根据底色明暗决定文字颜色，保证可读
        int r = (bgRgb >> 16) & 0xFF, g = (bgRgb >> 8) & 0xFF, b = bgRgb & 0xFF;
        double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        colorButton.setForeground(luma > 140 ? Color.BLACK : Color.WHITE);
    }

    private void syncEnabledState() {
        boolean on = enableCheck.isSelected();
        for (JComponent c : new JComponent[]{
                brightnessSlider, contrastSlider, saturationSlider,
                brightnessSpinner, contrastSpinner, saturationSpinner, bgCombo}) {
            c.setEnabled(on);
        }
        boolean bgOn = on && bgCombo.getSelectedItem() != PreprocessSettings.BackgroundMode.OFF;
        colorButton.setEnabled(bgOn);
    }

    void resetDefaults() {
        enableCheck.setSelected(false);
        brightnessSlider.setValue(100);
        contrastSlider.setValue(100);
        saturationSlider.setValue(100);
        bgCombo.setSelectedItem(PreprocessSettings.BackgroundMode.OFF);
        bgRgb = PreprocessSettings.DEFAULT_BACKGROUND_RGB;
        updateColorButton();
        syncEnabledState();
    }

    /** 打开前用已有设置初始化控件（不触发实时回调） */
    public void load(PreprocessSettings s) {
        loading = true;
        applied = false;
        enableCheck.setSelected(s.enabled);
        brightnessSlider.setValue(s.brightness);
        contrastSlider.setValue(s.contrast);
        saturationSlider.setValue(s.saturation);
        bgCombo.setSelectedItem(s.backgroundMode);
        bgRgb = s.backgroundRgb;
        updateColorButton();
        syncEnabledState();
        loading = false;
    }

    public boolean isApplied() {
        return applied;
    }

    public PreprocessSettings getSettings() {
        return new PreprocessSettings(
                enableCheck.isSelected(),
                brightnessSlider.getValue(),
                contrastSlider.getValue(),
                saturationSlider.getValue(),
                (PreprocessSettings.BackgroundMode) bgCombo.getSelectedItem(),
                bgRgb);
    }
}
