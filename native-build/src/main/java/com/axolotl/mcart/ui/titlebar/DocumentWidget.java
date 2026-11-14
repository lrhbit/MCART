package com.axolotl.mcart.ui.titlebar;

import com.axolotl.mcart.i18n.I18n;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * 紧凑模式的「文档」小部件（由 Pixel 的 ProjectWidget 改编）：
 *
 *   [ 渐变色块 ] [ 当前图片名 / 应用名 ⌄ ]
 *
 * 对应 IntelliJ 里的工程块：未打开图片时显示应用名；打开图片后显示文件名。
 * 色块颜色由应用标识派生（持久化），首字母随当前文档变化；
 * 点击弹出文件相关操作（打开 / 最近打开 / 移除）。
 */
public class DocumentWidget extends HoverWidget {

    private static final String APP_NAME = "MC 地图画生成器";
    private static final String APP_KEY = "mc-pixel-art";

    private final AppTitleBar.Host host;
    private final GradientMonogram monogram;
    private final JLabel nameLabel;

    public DocumentWidget(AppTitleBar.Host host) {
        this.host = host;
        ProjectPalette palette = ProjectPalette.perLaunch(APP_KEY);

        monogram = new GradientMonogram(palette, APP_KEY,
                GradientMonogram.initialsOf(APP_KEY));

        nameLabel = new JLabel(APP_NAME);
        nameLabel.setForeground(TitleTheme.TITLE_TEXT);
        nameLabel.setFont(TitleTheme.uiFont(UIScale.scale(11f), Font.PLAIN));

        JLabel chevron = new JLabel(TitleIcons.titleChevron);
        chevron.setForeground(TitleTheme.TITLE_CHEVRON);

        add(monogram);
        add(Box.createHorizontalStrut(UIScale.scale(3)));
        add(nameLabel);
        add(Box.createHorizontalStrut(UIScale.scale(5)));
        add(chevron);
    }

    public GradientMonogram getMonogram() {
        return monogram;
    }

    /** 切换当前文档：null = 回到应用名。 */
    public void setDocument(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            nameLabel.setText(APP_NAME);
            monogram.setInitials(GradientMonogram.initialsOf(APP_KEY));
        } else {
            nameLabel.setText(fileName);
            monogram.setInitials(GradientMonogram.initialsOf(fileName));
        }
        revalidate();
        repaint();
    }

    @Override
    protected JPopupMenu createPopup() {
        JPopupMenu p = new JPopupMenu();

        JMenuItem current = new JMenuItem(host.hasImage() ? nameLabel.getText() : I18n.t("doc.noImage"));
        current.setEnabled(false);
        p.add(current);
        p.add(new JSeparator());

        JMenuItem open = new JMenuItem(I18n.t("doc.open"));
        open.setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
        open.addActionListener(e -> host.openImage());
        p.add(open);

        // 最近打开（每次展开时重建）
        JMenu recent = new JMenu(I18n.t("doc.recentImages"));
        List<String> recents = host.recentFiles();
        if (recents.isEmpty()) {
            JMenuItem empty = new JMenuItem(I18n.t("recent.empty"));
            empty.setEnabled(false);
            recent.add(empty);
        } else {
            for (String path : recents) {
                JMenuItem item = new JMenuItem(new File(path).getName());
                item.setToolTipText(path);
                item.addActionListener(e -> host.openRecent(path));
                recent.add(item);
            }
        }
        p.add(recent);

        if (host.hasImage()) {
            p.add(new JSeparator());
            JMenuItem close = new JMenuItem(I18n.t("doc.closeImage"));
            close.addActionListener(e -> host.closeImage());
            p.add(close);
        }
        return p;
    }
}
