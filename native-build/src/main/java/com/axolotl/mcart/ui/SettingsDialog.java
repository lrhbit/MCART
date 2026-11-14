package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.util.AppConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * 设置对话框：卡片式选项（主题 / 语言 / 布局）+ 会话恢复开关，
 * 选中项以强调色描边高亮；应用后由 MainFrame 处理主题 / 语言切换。
 */
public class SettingsDialog extends AppDialog {

    public static class Settings {
        public String theme;
        public String language;
        public String layout;
        public boolean restoreSession;
    }

    private final Consumer<Settings> onApply;
    private final ButtonGroup themeGroup = new ButtonGroup();
    private final ButtonGroup langGroup = new ButtonGroup();
    private final ButtonGroup layoutGroup = new ButtonGroup();
    private final JCheckBox restoreCheck;

    public SettingsDialog(JFrame owner, Consumer<Settings> onApply) {
        super(owner, I18n.t("settings.title"), true);
        this.onApply = onApply;

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        // —— 外观：主题 ——
        body.add(section(I18n.t("settings.appearance")));
        body.add(cardsRow(themeGroup,
                option(I18n.t("view.theme.dark"), I18n.t("settings.hint.dark"), AppConfig.THEME_DARK),
                option(I18n.t("view.theme.light"), I18n.t("settings.hint.light"), AppConfig.THEME_LIGHT),
                option(I18n.t("view.theme.hc"), I18n.t("settings.hint.hc"), AppConfig.THEME_HIGH_CONTRAST)));
        select(themeGroup, AppConfig.theme());

        // —— 语言 ——
        body.add(section(I18n.t("settings.languageSection")));
        body.add(cardsRow(langGroup,
                option(I18n.t("view.lang.zh"), "简体中文", AppConfig.LANG_ZH),
                option(I18n.t("view.lang.en"), "English", AppConfig.LANG_EN)));
        select(langGroup, AppConfig.language());

        // —— 默认布局 ——
        body.add(section(I18n.t("settings.sectionLayout")));
        body.add(cardsRow(layoutGroup,
                option(I18n.t("view.layout.full"), I18n.t("settings.hint.full"), AppConfig.LAYOUT_FULL),
                option(I18n.t("view.layout.preview"), I18n.t("settings.hint.preview"), AppConfig.LAYOUT_PREVIEW),
                option(I18n.t("view.layout.source"), I18n.t("settings.hint.source"), AppConfig.LAYOUT_SOURCE)));
        select(layoutGroup, AppConfig.layout());

        // —— 工作区：会话恢复 ——
        body.add(section(I18n.t("settings.workspace")));
        RoundedPanel sessionCard = card(new BorderLayout(10, 0));
        JPanel sessionText = new JPanel();
        sessionText.setLayout(new BoxLayout(sessionText, BoxLayout.Y_AXIS));
        sessionText.setOpaque(false);
        JLabel sTitle = new JLabel(I18n.t("settings.restoreSession"));
        sTitle.setForeground(UiTheme.SECTION_TITLE);
        sTitle.setFont(sTitle.getFont().deriveFont(Font.BOLD, 12.5f));
        JLabel sHint = new JLabel(I18n.t("settings.sessionHint"));
        sHint.setForeground(UiTheme.MUTED_TEXT);
        sHint.setFont(sHint.getFont().deriveFont(11.5f));
        sessionText.add(sTitle);
        sessionText.add(sHint);
        sessionCard.add(sessionText, BorderLayout.CENTER);
        restoreCheck = new JCheckBox();
        restoreCheck.setOpaque(false);
        restoreCheck.setSelected(AppConfig.restoreSession());
        restoreCheck.setFocusPainted(false);
        sessionCard.add(restoreCheck, BorderLayout.EAST);
        body.add(stretch(sessionCard));
        body.add(Box.createVerticalGlue());

        setBody(body);

        Footer footer = new Footer();
        JButton cancel = actionButton(I18n.t("common.cancel"));
        cancel.addActionListener(e -> close());
        JButton apply = primaryActionButton(I18n.t("common.apply"));
        apply.addActionListener(e -> applySettings());
        footer.addButton(cancel);
        footer.addButton(apply, true);
        setFooter(footer);

        setupSize(560, 560);
    }

    // ---------------------------------------------------------------- 组件

    private static JComponent section(String title) {
        JLabel l = new JLabel(title);
        l.setForeground(UiTheme.SECTION_TITLE);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12.5f));
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(12, 2, 6, 0));
        return l;
    }

    private static JComponent stretch(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        JPanel w = new JPanel(new BorderLayout());
        w.setOpaque(false);
        w.setBorder(new EmptyBorder(0, 0, 0, 0));
        w.add(c, BorderLayout.CENTER);
        w.setAlignmentX(LEFT_ALIGNMENT);
        w.setMaximumSize(new Dimension(Short.MAX_VALUE, 64));
        return w;
    }

    /** 一行可选中卡片，均分宽度。 */
    private static JComponent cardsRow(ButtonGroup group, OptionCard... cards) {
        JPanel row = new JPanel(new GridLayout(1, cards.length, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Short.MAX_VALUE, 70));
        for (OptionCard c : cards) {
            group.add(c);
            row.add(c);
        }
        return row;
    }

    private OptionCard option(String title, String hint, String value) {
        return new OptionCard(title, hint, value);
    }

    private static void select(ButtonGroup group, String value) {
        for (java.util.Enumeration<AbstractButton> e = group.getElements(); e.hasMoreElements(); ) {
            AbstractButton b = e.nextElement();
            if (value.equals(b.getClientProperty("value"))) b.setSelected(true);
        }
    }

    private static String selectedValue(ButtonGroup group) {
        for (java.util.Enumeration<AbstractButton> e = group.getElements(); e.hasMoreElements(); ) {
            AbstractButton b = e.nextElement();
            if (b.isSelected()) return (String) b.getClientProperty("value");
        }
        return null;
    }

    private void applySettings() {
        Settings s = new Settings();
        s.theme = selectedValue(themeGroup);
        s.language = selectedValue(langGroup);
        s.layout = selectedValue(layoutGroup);
        s.restoreSession = restoreCheck.isSelected();
        onApply.accept(s);
        close();
    }

    /** 可选中的设置卡片：标题 + 说明；选中时强调色描边与底色。 */
    private static class OptionCard extends JToggleButton {
        OptionCard(String title, String hint, String value) {
            putClientProperty("value", value);
            setLayout(new BorderLayout());
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(9, 12, 9, 12));

            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            JLabel t = new JLabel(title);
            t.setForeground(UiTheme.SECTION_TITLE);
            t.setFont(t.getFont().deriveFont(Font.BOLD, 12.5f));
            t.setAlignmentX(LEFT_ALIGNMENT);
            JLabel h = new JLabel(hint);
            h.setForeground(UiTheme.MUTED_TEXT);
            h.setFont(h.getFont().deriveFont(11f));
            h.setAlignmentX(LEFT_ALIGNMENT);
            text.add(t);
            text.add(Box.createVerticalStrut(2));
            text.add(h);
            add(text, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { repaint(); }
                @Override public void mouseExited(MouseEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 10;
            boolean sel = isSelected();
            boolean hover = getMousePosition() != null;
            Color bg = sel ? UiTheme.alpha(UiTheme.ACCENT, 30)
                    : (hover ? UiTheme.HOVER : UiTheme.CARD_BG);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1f, getHeight() - 1f, arc, arc));
            g2.setColor(sel ? UiTheme.ACCENT : UiTheme.BORDER);
            g2.setStroke(new BasicStroke(sel ? 1.6f : 1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2f, getHeight() - 2f, arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
