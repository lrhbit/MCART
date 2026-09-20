package com.axolotl.mcart.ui;

import java.awt.*;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * 可自动换行的 FlowLayout。
 * 修复了标准 FlowLayout 在 BorderLayout.NORTH 等区域中换行后
 * 不能正确报告 preferred 高度、导致第二行被裁剪的问题。
 * 来源：Swing 官方教程公开实现 WrapLayout（Rob Camick），可自由使用。
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout() {
        super();
    }

    public WrapLayout(int align) {
        super(align);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            // 解决首次布局时自身宽度为0的鸡生蛋问题：向上找已有宽度的祖先
            Container container = target;
            while (container.getSize().width == 0 && container.getParent() != null) {
                container = container.getParent();
            }
            int targetWidth = container.getSize().width;
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    dim.width = Math.max(rowWidth, dim.width);
                    dim.height += rowHeight + vgap;
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += d.width + hgap;
                rowHeight = Math.max(rowHeight, d.height);
            }
            dim.width = Math.max(rowWidth, dim.width);
            dim.height += rowHeight;

            dim.width += insets.left + insets.right + hgap * 2;
            dim.height += insets.top + insets.bottom + vgap * 2;

            // 在 JScrollPane 视口内时使用无界宽度
            Container scrollPane = SwingUtilities.getUnwrappedParent(target);
            while (scrollPane != null && !(scrollPane instanceof JScrollPane)) {
                scrollPane = scrollPane.getParent();
            }
            if (scrollPane != null) {
                dim.width -= (getHgap() + 1);
            }
            return dim;
        }
    }
}
