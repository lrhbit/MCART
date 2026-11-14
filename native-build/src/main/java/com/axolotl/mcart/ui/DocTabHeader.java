package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.Project;

import javax.swing.*;
import java.awt.*;

/** 工程标签页的自定义标签头：工程名（脏标记 ●）+ 关闭按钮。 */
public class DocTabHeader extends JPanel {

    public DocTabHeader(Project p, int index, boolean active, Runnable onClose) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setOpaque(false);

        JLabel name = new JLabel(p.getName() + (p.isDirty() ? " ●" : ""));
        if (active) {
            name.setForeground(UIManager.getColor("TabbedPane.selectedForeground"));
            if (name.getForeground() == null) name.setForeground(UiTheme.ACCENT);
        } else {
            name.setForeground(UIManager.getColor("TabbedPane.foreground"));
            if (name.getForeground() == null) name.setForeground(UiTheme.ICON_TEXT);
        }
        add(name);

        JButton close = new JButton("×");
        close.setMargin(new Insets(0, 4, 0, 4));
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusable(false);
        close.setForeground(UiTheme.MUTED_TEXT);
        close.setToolTipText(I18n.t("tab.close"));
        close.addActionListener(e -> onClose.run());
        add(close);
    }
}
