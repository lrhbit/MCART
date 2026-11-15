package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.Project.NamedSnapshot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

/** 两个命名快照的并排 A/B 对比窗口。 */
public final class SnapshotCompareView {

    private SnapshotCompareView() {}

    public static void show(JFrame owner, NamedSnapshot a, NamedSnapshot b,
                            BufferedImage imgA, BufferedImage imgB) {
        JDialog dlg = new JDialog(owner, I18n.t("ab.title"), false);
        dlg.setSize(900, 560);
        dlg.setLocationRelativeTo(owner);
        JPanel grid = new JPanel(new GridLayout(1, 2, 8, 0));
        grid.setBorder(new EmptyBorder(8, 8, 8, 8));
        grid.add(panel(a.name, a.state, imgA));
        grid.add(panel(b.name, b.state, imgB));
        dlg.setContentPane(new JScrollPane(grid));
        dlg.setVisible(true);
    }

    private static JPanel panel(String name, com.axolotl.mcart.model.ProjectState s, BufferedImage img) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JLabel title = new JLabel("<html><b>" + name + "</b><br>" + I18n.t("mode." + s.mode.name())
                + " · " + I18n.t("dither." + s.dither.name())
                + I18n.t("ab.strength") + s.ditherStrength + "% · " + s.mapsW + "×" + s.mapsH
                + I18n.t("ab.mapsSuffix") + " · " + s.blockIds.size() + I18n.t("ab.kindsSuffix") + "</html>");
        panel.add(title, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JLabel(new ImageIcon(img))), BorderLayout.CENTER);
        return panel;
    }
}
