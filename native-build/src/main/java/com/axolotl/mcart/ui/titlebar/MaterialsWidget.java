package com.axolotl.mcart.ui.titlebar;

import com.axolotl.mcart.i18n.I18n;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;

/**
 * 紧凑模式的「方块库」小部件（由 Pixel 的 VcsWidget 改编）：
 *
 *   方块库 · N 种 ⌄
 *
 * 对应 IntelliJ 里 Version Control 那个纯文字下拉：这里用来显示当前勾选的
 * 材料数量，点击弹出方块库快捷操作（打开对话框 / 默认 / 全选 / 全不选）。
 */
public class MaterialsWidget extends HoverWidget {

    private final AppTitleBar.Host host;
    private final JLabel nameLabel;

    public MaterialsWidget(AppTitleBar.Host host) {
        this.host = host;

        nameLabel = new JLabel(I18n.t("materials.lib"));
        nameLabel.setForeground(TitleTheme.TITLE_TEXT);
        nameLabel.setFont(TitleTheme.uiFont(UIScale.scale(11f), Font.PLAIN));

        JLabel chevron = new JLabel(TitleIcons.titleChevron);
        chevron.setForeground(TitleTheme.TITLE_CHEVRON);

        add(nameLabel);
        add(Box.createHorizontalStrut(UIScale.scale(5)));
        add(chevron);

        setCount(host.selectedBlockCount(), host.totalBlockCount());
    }

    /** 刷新已选数量显示。 */
    public void setCount(int selected, int total) {
        nameLabel.setText(I18n.t("materials.libCount", selected));
        nameLabel.setToolTipText(I18n.t("materials.selectedTip", selected, total));
        setToolTipText(I18n.t("materials.selectedTip", selected, total));
        revalidate();
        repaint();
    }

    @Override
    protected JPopupMenu createPopup() {
        JPopupMenu p = new JPopupMenu();

        JMenuItem count = new JMenuItem(
                I18n.t("materials.selectedTip", host.selectedBlockCount(), host.totalBlockCount()));
        count.setEnabled(false);
        p.add(count);
        p.add(new JSeparator());

        JMenuItem open = new JMenuItem(I18n.t("doc.openLib"));
        open.setAccelerator(KeyStroke.getKeyStroke("ctrl B"));
        open.addActionListener(e -> host.openBlockLibrary());
        p.add(open);

        p.add(new JSeparator());

        JMenuItem def = new JMenuItem(I18n.t("edit.defaultSelect"));
        def.addActionListener(e -> {
            host.selectBlocksDefault();
            setCount(host.selectedBlockCount(), host.totalBlockCount());
        });
        p.add(def);

        JMenuItem all = new JMenuItem(I18n.t("edit.selectAll"));
        all.addActionListener(e -> {
            host.selectBlocksAll();
            setCount(host.selectedBlockCount(), host.totalBlockCount());
        });
        p.add(all);

        JMenuItem none = new JMenuItem(I18n.t("edit.selectNone"));
        none.addActionListener(e -> {
            host.selectBlocksNone();
            setCount(host.selectedBlockCount(), host.totalBlockCount());
        });
        p.add(none);

        return p;
    }
}
