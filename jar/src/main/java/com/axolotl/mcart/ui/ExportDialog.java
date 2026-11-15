package com.axolotl.mcart.ui;

import com.axolotl.mcart.export.ExportFormat;
import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 导出对话框：先选目标格式（含说明），PNG 可设像素放大倍数，
 * 确认后由主窗口再弹原生保存框选择路径。替代原先常驻在画面参数里的「输出类型」下拉。
 */
public class ExportDialog extends AppDialog {

    private final JList<ExportFormat> list = new JList<>(ExportFormat.values());
    private final JSpinner scaleSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 16, 1));
    private boolean approved;

    public ExportDialog(JFrame owner, ExportFormat initial) {
        super(owner, I18n.t("export.title"), true);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);

        // —— 格式卡片（单选列表，名称 + 说明两行） ——
        RoundedPanel fmtCard = card(new BorderLayout());
        JLabel fmtTitle = cardTitle(I18n.t("export.chooseFormat"));
        fmtTitle.setBorder(new EmptyBorder(0, 0, 8, 0));
        fmtCard.add(fmtTitle, BorderLayout.NORTH);

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                ExportFormat f = (ExportFormat) value;
                JPanel row = new JPanel(new BorderLayout(8, 1));
                row.setOpaque(true);
                row.setBorder(new EmptyBorder(7, 10, 7, 10));
                Color base = isSelected ? UiTheme.CARD_SEL : UiTheme.CARD_BG;
                row.setBackground(base);

                JLabel name = new JLabel(I18n.t("format." + f.name()));
                name.setFont(name.getFont().deriveFont(Font.BOLD, 12.5f));
                name.setForeground(isSelected ? UiTheme.ACCENT : UiTheme.SECTION_TITLE);
                row.add(name, BorderLayout.NORTH);

                JLabel desc = new JLabel(f.description);
                desc.setForeground(UiTheme.MUTED_TEXT);
                desc.setFont(desc.getFont().deriveFont(11.5f));
                row.add(desc, BorderLayout.SOUTH);
                return row;
            }
        });
        list.setOpaque(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(initial == null ? 0 : Math.max(0, initial.ordinal()));
        list.setFixedCellHeight(56);
        list.addListSelectionListener(e -> syncScaleEnabled());

        JScrollPane scroll = new JScrollPane(list);
        AppDialog.blendScroll(scroll, UiTheme.CARD_BG, false);
        scroll.setPreferredSize(new Dimension(440, 288));
        fmtCard.add(scroll, BorderLayout.CENTER);
        body.add(fmtCard, BorderLayout.CENTER);

        // —— PNG 放大倍数（仅预览图可选） ——
        RoundedPanel optCard = card(new BorderLayout(8, 0));
        JLabel scaleLabel = new JLabel(I18n.t("export.pngScale"));
        scaleLabel.setToolTipText(I18n.t("export.pngScaleTip"));
        optCard.add(scaleLabel, BorderLayout.WEST);
        scaleSpinner.setPreferredSize(new Dimension(96, 28));
        optCard.add(scaleSpinner, BorderLayout.EAST);
        body.add(optCard, BorderLayout.SOUTH);
        syncScaleEnabled();

        setBody(body);

        Footer footer = new Footer();
        JButton cancel = actionButton(I18n.t("common.cancel"));
        cancel.addActionListener(e -> close());
        JButton save = primaryActionButton(I18n.t("btn.export"));
        save.addActionListener(e -> { approved = true; close(); });
        footer.addButton(cancel);
        footer.addButton(save, true);
        setFooter(footer);

        setupSize(500, 520);
    }

    private void syncScaleEnabled() {
        boolean png = list.getSelectedValue() == ExportFormat.PNG;
        scaleSpinner.setEnabled(png);
    }

    public boolean isApproved() { return approved; }
    public ExportFormat selectedFormat() { return list.getSelectedValue(); }
    public int pngScale() { return (Integer) scaleSpinner.getValue(); }

    @Override public void close() {
        setVisible(false);
        dispose();
    }
}
