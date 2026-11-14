package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.ui.titlebar.TitleIcons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * 左右两条纯图标竖向工具栏（chrome 底色，宽度固定 52px）。
 *
 * <ul>
 *   <li><b>左侧</b>＝图像处理：方块库 / 图像调整 / 裁剪 / 像素编辑；</li>
 *   <li><b>右侧</b>＝导出相关：生成 / 导出 / 批量 / 物料清单。</li>
 * </ul>
 */
public final class SideBars {

    private SideBars() {}

    /** 工具栏动作集合，由主窗口提供。 */
    public interface Actions {
        /** 左侧：图像类。 */
        void openBlockLibrary();
        void openAdjust(Component anchor);
        void openCrop();
        void openPixelEditor();
        /** 右侧：导出类。 */
        void generate();
        void export();
        void openBatch();
        void openBom();
    }

    private static final int WIDTH = 52;

    private static JPanel rail() {
        JPanel rail = new JPanel();
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBackground(UiTheme.chrome());
        rail.setPreferredSize(new Dimension(WIDTH, 16));
        rail.setMinimumSize(new Dimension(WIDTH, 16));
        rail.setMaximumSize(new Dimension(WIDTH, Integer.MAX_VALUE));
        return rail;
    }

    private static TitleIcons.Button iconButton(Icon icon, String tip, Runnable action) {
        TitleIcons.Button b = new TitleIcons.Button(icon, 36, 36, 9);
        b.setToolTipText(tip);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.onClick(action);
        return b;
    }

    private static TitleIcons.Button iconButton(Icon icon, String tip, java.util.function.Consumer<Component> action) {
        TitleIcons.Button b = new TitleIcons.Button(icon, 36, 36, 9);
        b.setToolTipText(tip);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.onClick(() -> action.accept(b));
        return b;
    }

    /** 左侧：图像处理 —— 方块库 / 图像调整 / 裁剪 / 像素编辑。 */
    public static JComponent left(Actions a) {
        JPanel rail = rail();
        rail.add(Box.createVerticalStrut(8));
        rail.add(iconButton(TitleIcons.blocksGrid, I18n.t("rail.blocks"), a::openBlockLibrary));
        rail.add(Box.createVerticalStrut(6));
        rail.add(iconButton(TitleIcons.sliders, I18n.t("rail.adjust"), a::openAdjust));
        rail.add(Box.createVerticalStrut(6));
        rail.add(iconButton(TitleIcons.cropMarks, I18n.t("btn.crop.tip"), a::openCrop));
        rail.add(Box.createVerticalStrut(6));
        rail.add(iconButton(TitleIcons.pencil, I18n.t("btn.pixelEdit.tip"), a::openPixelEditor));
        rail.add(Box.createVerticalGlue());
        return rail;
    }

    /** 右侧：导出相关 —— 生成（主按钮）/ 导出（纯图标）/ 批量 / 物料。 */
    public static JComponent right(Actions a) {
        JPanel rail = rail();
        rail.add(Box.createVerticalStrut(8));

        TitleIcons.Button generateBtn = new TitleIcons.Button(TitleIcons.swirl, 38, 38, 10).primary();
        generateBtn.setToolTipText(I18n.t("rail.generate"));
        generateBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        generateBtn.onClick(a::generate);
        rail.add(generateBtn);

        rail.add(Box.createVerticalStrut(10));
        rail.add(exportButton(a::export));
        rail.add(Box.createVerticalStrut(10));
        rail.add(iconButton(TitleIcons.stackLayers, I18n.t("rail.batch"), a::openBatch));
        rail.add(Box.createVerticalStrut(6));
        rail.add(iconButton(TitleIcons.bomList, I18n.t("rail.bom"), a::openBom));
        rail.add(Box.createVerticalGlue());
        return rail;
    }

    /** 纯图标按钮（保留强调色背景），用于强调「导出」；文字信息放在 tooltip。 */
    private static JComponent exportButton(Runnable action) {
        JButton b = new JButton(TitleIcons.exportTray) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 10;
                boolean pressed = getModel().isPressed();
                boolean hover = getMousePosition() != null;
                Color bg = pressed ? UiTheme.alpha(UiTheme.ACCENT, 200)
                        : hover ? UiTheme.alpha(UiTheme.ACCENT, 170)
                          : UiTheme.alpha(UiTheme.ACCENT, 140);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1f, getHeight() - 1f, arc, arc));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorder(new EmptyBorder(0, 0, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setPreferredSize(new Dimension(40, 40));
        b.setMinimumSize(new Dimension(40, 40));
        b.setMaximumSize(new Dimension(40, 40));
        b.setToolTipText(I18n.t("rail.export"));
        b.addActionListener(e -> action.run());
        return b;
    }
}