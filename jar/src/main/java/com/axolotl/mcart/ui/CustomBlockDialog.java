package com.axolotl.mcart.ui;

import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.CustomBlockStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 添加自定义方块：方块 ID（命名空间:路径）、显示名、地图颜色（RGB）。
 * 保存到 mc-pixel-art-data/custom-blocks.json；游戏中需同名方块（模组/资源包）真实存在。
 */
public class CustomBlockDialog extends AppDialog {

    private boolean created;
    private final JTextField idField = new JTextField(22);
    private final JTextField nameField = new JTextField(22);
    private final JSpinner rSp = spinner(128), gSp = spinner(128), bSp = spinner(128);
    private final JPanel swatch = new JPanel();

    public CustomBlockDialog(JFrame owner) {
        super(owner, I18n.t("custom.title"), true);
        build();
        setupSize(480, 360);
    }

    private static JSpinner spinner(int v) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(v, 0, 255, 1));
        sp.setPreferredSize(new Dimension(ui(70), ui(28)));
        com.axolotl.mcart.util.InputMethodHints.disableCompositionWhileFocused(sp);
        return sp;
    }

    private void build() {
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);

        JTextArea hint = hintLabel(I18n.t("custom.hint").replace("\n", " "), 420);
        body.add(hint, BorderLayout.NORTH);

        RoundedPanel card = card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(ui(5), ui(4), ui(5), ui(4));
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; card.add(new JLabel(I18n.t("custom.idLabel")), c);
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1;
        idField.setToolTipText(I18n.t("custom.idTip"));
        card.add(idField, c);
        c.gridwidth = 1; c.weightx = 0;

        c.gridx = 0; c.gridy = 1; card.add(new JLabel(I18n.t("custom.name")), c);
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1; card.add(nameField, c);
        c.gridwidth = 1; c.weightx = 0;

        c.gridx = 0; c.gridy = 2; card.add(new JLabel(I18n.t("custom.rgb")), c);
        JPanel rgb = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rgb.setOpaque(false);
        rgb.add(rSp); rgb.add(gSp); rgb.add(bSp);
        JButton pick = actionButton(I18n.t("custom.pick"));
        pick.addActionListener(e -> {
            Color cc = JColorChooser.showDialog(this, I18n.t("custom.pickTitle"), currentColor());
            if (cc != null) {
                rSp.setValue(cc.getRed()); gSp.setValue(cc.getGreen()); bSp.setValue(cc.getBlue());
                updateSwatch();
            }
        });
        rgb.add(pick);
        c.gridx = 1; c.gridwidth = 2; card.add(rgb, c);
        c.gridwidth = 1;

        c.gridx = 0; c.gridy = 3; card.add(new JLabel(I18n.t("custom.preview")), c);
        swatch.setPreferredSize(new Dimension(ui(140), ui(34)));
        swatch.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1; card.add(swatch, c);

        body.add(card, BorderLayout.CENTER);
        setBody(body);

        javax.swing.event.ChangeListener cl = e -> updateSwatch();
        rSp.addChangeListener(cl); gSp.addChangeListener(cl); bSp.addChangeListener(cl);

        Footer footer = new Footer();
        JButton cancel = actionButton(I18n.t("common.cancel"));
        cancel.addActionListener(e -> close());
        JButton ok = primaryActionButton(I18n.t("custom.add"));
        ok.addActionListener(e -> submit());
        footer.addButton(cancel);
        footer.addButton(ok, true);
        setFooter(footer);

        updateSwatch();
    }

    private Color currentColor() {
        return new Color(intVal(rSp), intVal(gSp), intVal(bSp));
    }

    private static int intVal(JSpinner sp) { return ((Number) sp.getValue()).intValue(); }

    private void updateSwatch() {
        swatch.setBackground(currentColor());
        swatch.repaint();
    }

    private void submit() {
        String id = idField.getText().trim();
        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(this, I18n.t("custom.needId"));
            return;
        }
        if (!id.contains(":")) id = "minecraft:" + id;
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = id;
        int colorId = 1000 + Math.abs(id.hashCode()) % 9000;
        BlockColor bc = new BlockColor(colorId, name, intVal(rSp), intVal(gSp), intVal(bSp),
                id, BlockColor.Category.SPECIAL);
        if (!CustomBlockStore.add(bc)) {
            JOptionPane.showMessageDialog(this, I18n.t("custom.dupe", id));
            return;
        }
        created = true;
        close();
    }

    public boolean isCreated() { return created; }
}
