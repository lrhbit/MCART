package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.QualityChecker;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.Project;
import com.axolotl.mcart.ui.titlebar.TitleIcons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/** 质量警告中心：列出当前工程的全部校验问题，导出前也会弹出。 */
public class WarningsDialog extends AppDialog {

    private final Supplier<Project> projectSupplier;
    private final Runnable openGamut;
    private final JPanel list;
    private final JScrollPane scroll;

    public WarningsDialog(JFrame owner, Supplier<Project> projectSupplier, Runnable openGamut) {
        super(owner, I18n.t("warn.title"), false);
        this.projectSupplier = projectSupplier;
        this.openGamut = openGamut;

        // 顶部：标题 + 重新检查
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(0, 2, 2, 0));
        JLabel title = cardTitle(I18n.t("warn.current"));
        top.add(title, BorderLayout.WEST);
        JButton refresh = actionButton(I18n.t("warn.recheck"));
        refresh.addActionListener(e -> rebuild());
        top.add(refresh, BorderLayout.EAST);
        setHeader(top);

        // 中央：问题卡片列表
        list = new VScrollList();
        list.setBackground(UiTheme.CENTER_BG);
        list.setBorder(new EmptyBorder(2, 2, 2, 2));
        scroll = new JScrollPane(list);
        blendScroll(scroll, UiTheme.CENTER_BG, false);
        setBody(scroll);

        // 底部：色域预警视图 + 关闭
        Footer footer = new Footer();
        JButton gamut = actionButton(I18n.t("warn.gamutBtn"));
        gamut.addActionListener(e -> {
            if (this.openGamut != null) this.openGamut.run();
            close();
        });
        footer.addLeft(gamut);
        JButton close = primaryActionButton(I18n.t("common.close"));
        close.addActionListener(e -> close());
        footer.addButton(close, true);
        setFooter(footer);

        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { rebuild(); }
        });

        rebuild();
        int n = QualityChecker.check(projectSupplier.get()).size();
        int h = n == 0 ? 300 : Math.min(640, 250 + n * 70);
        setupSize(600, h);
    }

    private void rebuild() {
        list.removeAll();
        Project p = projectSupplier.get();
        List<QualityChecker.Issue> issues = QualityChecker.check(p);
        if (issues.isEmpty()) {
            list.add(emptyRow());
        } else {
            for (QualityChecker.Issue issue : issues) {
                list.add(issueRow(issue));
                list.add(Box.createVerticalStrut(8));
            }
        }
        list.revalidate();
        list.repaint();
    }

    private JComponent emptyRow() {
        RoundedPanel row = card(new BorderLayout(10, 0));
        row.setInsets(new Insets(ui(16), ui(14), ui(16), ui(14)));
        JLabel icon = new JLabel(TitleIcons.sevOk);
        icon.setBorder(new EmptyBorder(0, 0, 0, 4));
        row.add(icon, BorderLayout.WEST);
        JLabel msg = new JLabel(I18n.t("warn.noWarn"));
        msg.setForeground(UiTheme.SECTION_TITLE);
        msg.setFont(msg.getFont().deriveFont(Font.PLAIN, 13f));
        row.add(msg, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private JComponent issueRow(QualityChecker.Issue issue) {
        RoundedPanel row = card(new BorderLayout(10, 0));
        row.setInsets(new Insets(ui(10), ui(12), ui(10), ui(12)));
        Icon icon = switch (issue.level) {
            case ERROR -> TitleIcons.sevError;
            case WARN -> TitleIcons.sevWarn;
            default -> TitleIcons.sevInfo;
        };
        JLabel ic = new JLabel(icon);
        ic.setBorder(new EmptyBorder(2, 0, 0, 2));
        row.add(ic, BorderLayout.WEST);
        // 用 JTextArea 自动换行，不依赖 Swing HTML 引擎（native 下 HTML 渲染会空白）
        JTextArea msg = new JTextArea(issue.message);
        msg.setLineWrap(true);
        msg.setWrapStyleWord(true);
        msg.setEditable(false);
        msg.setOpaque(false);
        msg.setFocusable(false);
        msg.setForeground(UiTheme.SECTION_TITLE);
        msg.setFont(msg.getFont().deriveFont(Font.PLAIN, 13f));
        msg.setBorder(new EmptyBorder(2, 0, 0, 0));
        row.add(msg, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + ui(8)));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }
}
