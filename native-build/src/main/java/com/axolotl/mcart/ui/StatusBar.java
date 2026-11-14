package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** 底部状态栏：左侧状态消息，右侧操作提示。 */
public class StatusBar extends JPanel {

    private final JLabel message;

    public StatusBar() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(UiTheme.chrome());
        setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 0, UiTheme.BORDER),
                new EmptyBorder(4, 8, 3, 8)));
        message = new JLabel(I18n.t("status.readyLong"));
        add(message, BorderLayout.WEST);
        JLabel hint = new JLabel(I18n.t("status.hint") + "  ");
        hint.setForeground(UiTheme.MUTED_TEXT);
        add(hint, BorderLayout.EAST);
    }

    public void setMessage(String text) {
        message.setText(text);
    }
}
