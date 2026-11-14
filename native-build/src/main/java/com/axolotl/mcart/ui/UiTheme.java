package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.util.AppConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 主界面共享的语义配色、配色工具与小型组件工厂。
 *
 * 所有颜色都是<strong>主题相关</strong>的：{@link #init(String)} 在外观安装后
 * 按深色 / 浅色 / 高对比度三套语义色板重新赋值，界面各处只引用语义字段，
 */
public final class UiTheme {

    private UiTheme() {}

    // ---------------------------------------------------------------- 语义色板

    /** 强调色（选中、主按钮）。 */
    public static Color ACCENT;
    /** 强调色上的文字色（白 / 黑）。 */
    public static Color ON_ACCENT;
    /** 查看器 / 预览 / 3D 头部条底色。 */
    public static Color HEADER_BG;
    /** 原图 / 预览画面区底色。 */
    public static Color VIEWER_BG;
    /** 标签井（标签页所在槽位）。 */
    public static Color TAB_WELL;
    /** 中央区底色。 */
    public static Color CENTER_BG;
    /** 弱化说明文字。 */
    public static Color MUTED_TEXT;
    /** 分区标题文字。 */
    public static Color SECTION_TITLE;
    /** 1px 分隔线 / 卡片描边。 */
    public static Color BORDER;
    /** 卡片 / 方块格底色。 */
    public static Color CARD_BG;
    /** 选中卡片底色。 */
    public static Color CARD_SEL;
    /** 选中卡片描边。 */
    public static Color CARD_SEL_BORDER;
    /** 输入框底色（自绘场景）。 */
    public static Color FIELD_BG;
    /** 悬停底色（弱 / 强）。 */
    public static Color HOVER, HOVER_STRONG;
    /** 图标 / 次要符号色。 */
    public static Color ICON_TEXT;
    /** 空状态占位文字色。 */
    public static Color PLACEHOLDER;
    /** 像素编辑器棋盘格两色。 */
    public static Color CHECKER_A, CHECKER_B;
    /** 像素编辑器网格线（不透明，预烘焙到离屏底图以保证绘制性能）。 */
    public static Color GRID_LINE;

    private static String kind = AppConfig.THEME_DARK;

    /** 按主题重新计算全部语义颜色（主程序安装 LAF 后调用；窗口重建时再次调用）。 */
    public static void init(String theme) {
        kind = theme;
        switch (theme) {
            case AppConfig.THEME_LIGHT -> initLight();
            case AppConfig.THEME_HIGH_CONTRAST -> initHighContrast();
            default -> initDark();
        }
    }

    public static String kind() { return kind; }

    public static boolean isLight() { return AppConfig.THEME_LIGHT.equals(kind); }
    public static boolean isHighContrast() { return AppConfig.THEME_HIGH_CONTRAST.equals(kind); }

    private static void initDark() {
        ACCENT = c(74, 125, 199);
        ON_ACCENT = Color.WHITE;
        HEADER_BG = c(49, 50, 54);
        VIEWER_BG = c(28, 29, 32);
        TAB_WELL = c(43, 44, 48);
        CENTER_BG = c(36, 37, 40);
        MUTED_TEXT = c(150, 152, 158);
        SECTION_TITLE = c(221, 221, 221);
        BORDER = c(64, 66, 72);
        CARD_BG = c(43, 44, 48);
        CARD_SEL = c(43, 60, 86);
        CARD_SEL_BORDER = c(96, 140, 210);
        FIELD_BG = c(43, 44, 48);
        HOVER = c(70, 73, 75);
        HOVER_STRONG = c(80, 82, 88);
        ICON_TEXT = c(190, 192, 198);
        PLACEHOLDER = c(125, 127, 133);
        CHECKER_A = c(28, 29, 33);
        CHECKER_B = c(34, 35, 39);
        GRID_LINE = c(18, 19, 22);
    }

    private static void initLight() {
        ACCENT = c(61, 123, 217);
        ON_ACCENT = Color.WHITE;
        HEADER_BG = c(241, 242, 244);
        VIEWER_BG = c(255, 255, 255);
        TAB_WELL = c(236, 238, 241);
        CENTER_BG = c(246, 247, 249);
        MUTED_TEXT = c(110, 114, 120);
        SECTION_TITLE = c(31, 33, 37);
        BORDER = c(201, 204, 211);
        CARD_BG = c(255, 255, 255);
        CARD_SEL = c(227, 238, 251);
        CARD_SEL_BORDER = c(61, 123, 217);
        FIELD_BG = Color.WHITE;
        HOVER = c(223, 226, 231);
        HOVER_STRONG = c(201, 205, 212);
        ICON_TEXT = c(87, 94, 107);
        PLACEHOLDER = c(154, 160, 168);
        CHECKER_A = c(255, 255, 255);
        CHECKER_B = c(238, 239, 242);
        GRID_LINE = c(178, 182, 190);
    }

    private static void initHighContrast() {
        ACCENT = c(255, 235, 0);
        ON_ACCENT = Color.BLACK;
        HEADER_BG = Color.BLACK;
        VIEWER_BG = Color.BLACK;
        TAB_WELL = Color.BLACK;
        CENTER_BG = Color.BLACK;
        MUTED_TEXT = c(200, 200, 200);
        SECTION_TITLE = Color.WHITE;
        BORDER = Color.WHITE;
        CARD_BG = Color.BLACK;
        CARD_SEL = c(51, 51, 51);
        CARD_SEL_BORDER = Color.YELLOW;
        FIELD_BG = Color.BLACK;
        HOVER = c(51, 51, 51);
        HOVER_STRONG = c(77, 77, 77);
        ICON_TEXT = Color.WHITE;
        PLACEHOLDER = c(176, 176, 176);
        CHECKER_A = Color.BLACK;
        CHECKER_B = c(26, 26, 26);
        GRID_LINE = Color.WHITE;
    }

    static {
        init(AppConfig.theme());
    }

    private static Color c(int r, int g, int b) { return new Color(r, g, b); }

    // ---------------------------------------------------------------- 工具

    /** FlatLaf RootPane 底色（外框工具栏 / 状态栏 / 分隔条统一色）。 */
    public static Color chrome() {
        Color cc = UIManager.getColor("RootPane.background");
        return cc != null ? cc : HEADER_BG;
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 返回带指定 alpha 的同色。 */
    public static Color alpha(Color base, int a) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }

    /** 递归把容器面板染成查看器底色；控件（下拉/按钮/滑块等）保留 FlatLaf 原色。 */
    public static void themePanelDark(Component c) {
        if (c instanceof JPanel || c instanceof JSplitPane || c instanceof JViewport) {
            c.setBackground(VIEWER_BG);
        } else if (c instanceof JScrollPane sp) {
            sp.setBackground(VIEWER_BG);
            sp.getViewport().setBackground(VIEWER_BG);
        } else if (c instanceof JCheckBox || c instanceof JRadioButton || c instanceof JSlider) {
            ((JComponent) c).setOpaque(false);
        } else if (c instanceof JComponent && !(c instanceof JComboBox)
                && !(c instanceof JSpinner) && !(c instanceof JButton)) {
            if (c.isOpaque()) c.setBackground(VIEWER_BG);
        }
        if (c instanceof Container ct && !(c instanceof JComboBox)
                && !(c instanceof JSpinner) && !(c instanceof JButton)
                && !(c instanceof JTextField)) {
            for (Component ch : ct.getComponents()) themePanelDark(ch);
        }
    }

    /** 分隔条统一为 3px chrome 细线（无 grip 圆点；grip 由 Main 中全局 plain 样式关闭）。 */
    public static void styleDivider(JSplitPane sp) {
        sp.setDividerSize(3);
        sp.setBackground(chrome());
    }

    /** 圆角扁平文字按钮（浅色 / 深色下均跟随 FlatLaf 控件色）。 */
    public static JButton flatButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(5, 10, 5, 10));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        return btn;
    }

    /**
     * 无边框纯图标按钮，尺寸 size×size。
     * 复用标题栏按钮组件：悬停显示圆角高亮、按下显示更深底色，三主题均有反馈。
     */
    public static JButton iconButton(Icon icon, String tip, int size) {
        com.axolotl.mcart.ui.titlebar.TitleIcons.Button btn =
                new com.axolotl.mcart.ui.titlebar.TitleIcons.Button(icon, size, 8);
        btn.setToolTipText(tip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** 把按钮设置为主按钮（强调色底 + 对比文字色）。 */
    public static void primaryButton(AbstractButton b) {
        b.setBackground(ACCENT);
        b.setForeground(ON_ACCENT);
        b.setFocusPainted(false);
        b.setContentAreaFilled(true);
        b.setOpaque(true);
        b.setBorderPainted(false);
    }

    /** 「标签 : 控件」表单行。 */
    public static JComponent formRow(String label, JComponent editor) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(I18n.isEn() ? 98 : 72, 28));
        row.add(l, BorderLayout.WEST);
        row.add(editor, BorderLayout.CENTER);
        return row;
    }

    /**
     * 无边框分区标题（替代旧的 TitledBorder：只显示加粗标题，不画外框）。
     * 标题下方带少量留白。
     */
    public static JLabel sectionHeader(String title) {
        JLabel label = new JLabel(title);
        label.setForeground(SECTION_TITLE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBorder(new EmptyBorder(2, 2, 6, 2));
        return label;
    }
}
