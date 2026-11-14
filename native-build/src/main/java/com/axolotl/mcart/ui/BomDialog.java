package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.MaterialList;
import com.axolotl.mcart.core.PixelArtGenerator;
import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** 物料清单（BOM）对话框：表格 + 合计 + 复制 / 导出 CSV。 */
public class BomDialog extends AppDialog {

    public BomDialog(JFrame owner, PixelArtGenerator.Result result, String defaultName) {
        super(owner, I18n.t("bom.title"), false);

        List<MaterialList.Row> rows = MaterialList.of(result);
        long total = MaterialList.total(rows);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);

        JLabel summary = cardTitle(I18n.t("bom.summary", rows.size(), total)
                + (result.height > 1 ? " " + I18n.t("bom.withSupport") : ""));
        summary.setBorder(new EmptyBorder(0, 2, 0, 2));
        body.add(summary, BorderLayout.NORTH);

        BomModel model = new BomModel(rows);
        JTable table = new JTable(model);
        table.setRowHeight(ui(26));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(UiTheme.CARD_BG);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.setAutoCreateRowSorter(true);

        RoundedPanel tableCard = card(new BorderLayout());
        tableCard.setInsets(new Insets(ui(8), ui(8), ui(8), ui(8)));
        JScrollPane scroll = new JScrollPane(table);
        blendScroll(scroll, UiTheme.CARD_BG, false);
        tableCard.add(scroll, BorderLayout.CENTER);
        body.add(tableCard, BorderLayout.CENTER);
        setBody(body);

        Footer footer = new Footer();
        JButton copy = actionButton(I18n.t("bom.copy"));
        JButton exportCsv = actionButton(I18n.t("bom.exportCsv"));
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(MaterialList.toCsv(rows)), null);
            copy.setText(I18n.t("bom.copied"));
            Timer t = new Timer(1200, ev -> copy.setText(I18n.t("bom.copy")));
            t.setRepeats(false);
            t.start();
        });
        exportCsv.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(safeName(defaultName) + I18n.t("bom.fileSuffix") + ".csv"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    Files.writeString(fc.getSelectedFile().toPath(),
                            MaterialList.toCsv(rows), StandardCharsets.UTF_8);
                    JOptionPane.showMessageDialog(this, I18n.t("bom.exported", fc.getSelectedFile()));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, I18n.t("bom.exportFail", ex.getMessage()));
                }
            }
        });
        footer.addLeft(copy);
        footer.addLeft(exportCsv);
        JButton close = primaryActionButton(I18n.t("common.close"));
        close.addActionListener(e -> close());
        footer.addButton(close, true);
        setFooter(footer);

        setupSize(680, 600);
    }

    private static String safeName(String n) {
        return n == null ? "mcpixel" : n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static class BomModel extends AbstractTableModel {
        private final List<MaterialList.Row> rows;
        private final String[] cols = {I18n.t("bom.colName"), I18n.t("bom.colId"), I18n.t("bom.colCategory"), I18n.t("bom.colCount")};

        BomModel(List<MaterialList.Row> rows) { this.rows = rows; }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 4; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            MaterialList.Row row = rows.get(r);
            return switch (c) {
                case 0 -> row.displayName;
                case 1 -> row.blockId;
                case 2 -> row.category;
                case 3 -> row.count;
                default -> "";
            };
        }
    }
}
