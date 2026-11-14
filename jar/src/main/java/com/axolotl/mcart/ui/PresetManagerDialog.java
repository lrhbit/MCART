package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.BlockPresetStore;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** 方块选择预设管理：删除 / 导入 / 导出用户预设。 */
public class PresetManagerDialog extends AppDialog {

    private final Runnable onChanged;
    private DefaultListModel<BlockPresetStore.Preset> model;
    private JList<BlockPresetStore.Preset> list;

    public PresetManagerDialog(JFrame owner, Runnable onChanged) {
        super(owner, I18n.t("preset.title"), true);
        this.onChanged = onChanged;
        build();
        reload();
        setupSize(500, 460);
    }

    private void build() {
        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setBackground(UiTheme.CARD_BG);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof BlockPresetStore.Preset p) {
                    setText(p.name() + (p.builtin() ? I18n.t("preset.builtinSuffix") : I18n.t("preset.countSuffix", p.blockIds().size())));
                    if (p.builtin()) setForeground(UiTheme.MUTED_TEXT);
                }
                return this;
            }
        });

        RoundedPanel card = card(new BorderLayout());
        card.setInsets(new Insets(ui(8), ui(8), ui(8), ui(8)));
        JScrollPane scroll = new JScrollPane(list);
        blendScroll(scroll, UiTheme.CARD_BG, false);
        card.add(scroll, BorderLayout.CENTER);
        setBody(card);

        Footer footer = new Footer();
        JButton importBtn = actionButton(I18n.t("preset.importBtn"));
        JButton exportBtn = actionButton(I18n.t("preset.exportBtn"));
        JButton deleteBtn = actionButton(I18n.t("preset.delete"));
        importBtn.addActionListener(e -> doImport());
        exportBtn.addActionListener(e -> doExport());
        deleteBtn.addActionListener(e -> doDelete());
        footer.addLeft(importBtn);
        footer.addLeft(exportBtn);
        footer.addLeft(deleteBtn);
        JButton closeBtn = primaryActionButton(I18n.t("common.close"));
        closeBtn.addActionListener(e -> close());
        footer.addButton(closeBtn, true);
        setFooter(footer);
    }

    private void reload() {
        model.clear();
        for (BlockPresetStore.Preset p : BlockPresetStore.all()) model.addElement(p);
        if (onChanged != null) onChanged.run();
    }

    private void doDelete() {
        BlockPresetStore.Preset p = list.getSelectedValue();
        if (p == null) return;
        if (p.builtin()) {
            JOptionPane.showMessageDialog(this, I18n.t("preset.builtinNoDelete"));
            return;
        }
        if (JOptionPane.showConfirmDialog(this, I18n.t("preset.deleteConfirm", p.name()),
                I18n.t("common.confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            BlockPresetStore.delete(p);
            reload();
        }
    }

    private void doExport() {
        BlockPresetStore.Preset p = list.getSelectedValue();
        if (p == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(p.name() + ".json"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BlockPresetStore.exportPreset(p, fc.getSelectedFile().toPath());
                JOptionPane.showMessageDialog(this, I18n.t("preset.exported", fc.getSelectedFile()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, I18n.t("preset.exportFail", ex.getMessage()));
            }
        }
    }

    private void doImport() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            int ok = 0;
            for (File f : fc.getSelectedFiles()) {
                try {
                    String head = Files.readString(Path.of(f.getAbsolutePath()), StandardCharsets.UTF_8);
                    if (!head.contains("blocks")) continue;
                    BlockPresetStore.importPreset(f.toPath());
                    ok++;
                } catch (Exception ignored) {
                }
            }
            reload();
            JOptionPane.showMessageDialog(this, I18n.t("preset.imported", ok));
        }
    }
}
