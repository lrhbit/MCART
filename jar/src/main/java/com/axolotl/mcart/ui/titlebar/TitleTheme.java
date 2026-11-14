package com.axolotl.mcart.ui.titlebar;

import com.axolotl.mcart.util.AppConfig;

import javax.swing.*;
import java.awt.*;

/**
 * 标题栏配色 / 字体 / 绘制工具（移植自 Pixel 的 IdeaTheme）。
 *
 * 支持三套配色：深色（New UI 默认）、浅色、高对比度（无障碍）；
 * 切换主题后由 Main 重建主窗口，所有颜色字段在绘制时实时读取。
 */
public final class TitleTheme {

    // 标题栏（New UI）
    public static Color TITLE_BG;
    public static Color TITLE_BORDER;
    public static Color TITLE_TEXT;
    public static Color TITLE_ICON;
    public static Color TITLE_CHEVRON;
    public static Color HOVER_OVERLAY;
    public static Color PRESSED_OVERLAY;
    public static Color NOTIFICATION_DOT;

    // 弹出菜单
    public static Color PANEL_BG;
    public static Color HOVER_BG;
    public static Color SOFT_BORDER;
    public static Color TEXT_BRIGHT;

    private TitleTheme() {}

    public static Color color(int rgb) {
        return new Color(rgb);
    }

    static {
        apply();
    }

    /** 按当前配置主题应用配色（兼容旧调用） */
    public static void apply() {
        apply(AppConfig.theme());
    }

    /** 按指定主题应用配色与外观键 */
    public static void apply(String theme) {
        switch (theme) {
            case AppConfig.THEME_LIGHT -> {
                TITLE_BG = color(0xF0F1F4);
                TITLE_BORDER = color(0xC9CCD3);
                TITLE_TEXT = color(0x3D4248);
                TITLE_ICON = color(0x575E6B);
                TITLE_CHEVRON = color(0x6C707E);
                HOVER_OVERLAY = new Color(0, 0, 0, 22);
                PRESSED_OVERLAY = new Color(0, 0, 0, 40);
                NOTIFICATION_DOT = color(0xD6A12C);
                PANEL_BG = color(0xFFFFFF);
                HOVER_BG = color(0xE9EDF3);
                SOFT_BORDER = color(0xD1D5DB);
                TEXT_BRIGHT = color(0x111418);
            }
            case AppConfig.THEME_HIGH_CONTRAST -> {
                TITLE_BG = color(0x000000);
                TITLE_BORDER = color(0xFFFFFF);
                TITLE_TEXT = color(0xFFFFFF);
                TITLE_ICON = color(0xFFFFFF);
                TITLE_CHEVRON = color(0xFFFF00);
                HOVER_OVERLAY = new Color(255, 255, 255, 48);
                PRESSED_OVERLAY = new Color(255, 255, 255, 80);
                NOTIFICATION_DOT = color(0xFFFF00);
                PANEL_BG = color(0x000000);
                HOVER_BG = color(0x333333);
                SOFT_BORDER = color(0xFFFFFF);
                TEXT_BRIGHT = color(0xFFFFFF);
            }
            default -> { // 深色 New UI
                TITLE_BG = color(0x26282C);
                TITLE_BORDER = color(0x191A1C);
                TITLE_TEXT = color(0xD1D3D9);
                TITLE_ICON = color(0xC3C5CB);
                TITLE_CHEVRON = color(0xAFB1B6);
                HOVER_OVERLAY = new Color(255, 255, 255, 22);
                PRESSED_OVERLAY = new Color(255, 255, 255, 36);
                NOTIFICATION_DOT = color(0xF2C55C);
                PANEL_BG = color(0x2B2B2B);
                HOVER_BG = color(0x3C3F41);
                SOFT_BORDER = color(0x393939);
                TEXT_BRIGHT = color(0xFFFFFF);
            }
        }
        putLaf();
    }

    /**
     * 在当前 LAF 之上叠加标题栏 / 菜单相关外观键。
     */
    public static void putLaf() {
        // ---- 标题窗格（FlatLaf 窗口装饰）----
        UIManager.put("RootPane.background", TITLE_BG);
        UIManager.put("TitlePane.inactiveBackground", TITLE_BG);
        UIManager.put("TitlePane.foreground", TITLE_TEXT);
        UIManager.put("TitlePane.inactiveForeground", TITLE_CHEVRON);
        UIManager.put("TitlePane.borderColor", TITLE_BORDER);
        UIManager.put("TitlePane.menuBarEmbedded", true);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("TitlePane.buttonSize", new Dimension(44, 32));
        UIManager.put("TitlePane.buttonSymbolHeight", 10);

        // ---- 嵌入菜单栏 / 主菜单 ----
        UIManager.put("MenuBar.background", TITLE_BG);
        UIManager.put("MenuBar.hoverBackground", HOVER_OVERLAY);
        UIManager.put("MenuBar.selectionBackground", HOVER_OVERLAY);
        UIManager.put("MenuBar.selectionArc", 8);
        UIManager.put("MenuBar.selectionInsets", new Insets(2, 2, 2, 2));
        UIManager.put("MenuBar.selectionEmbeddedInsets", new Insets(2, 2, 2, 2));
        UIManager.put("MenuBar.itemMargins", new Insets(3, 0, 3, 0));
        UIManager.put("MenuBar.border", null);

        UIManager.put("Menu.background", TITLE_BG);
        UIManager.put("Menu.foreground", TITLE_TEXT);
        UIManager.put("Menu.selectionBackground", HOVER_OVERLAY);
        UIManager.put("Menu.selectionForeground", TEXT_BRIGHT);
        UIManager.put("Menu.mnemonicForeground", TITLE_TEXT);
        UIManager.put("Menu.mnemonicSelectionForeground", TEXT_BRIGHT);
        UIManager.put("Menu.opaque", false);
        UIManager.put("Menu.border", new javax.swing.plaf.InsetsUIResource(0, 0, 0, 0));

        UIManager.put("MenuItem.background", PANEL_BG);
        UIManager.put("MenuItem.selectionBackground", HOVER_BG);
        UIManager.put("CheckBoxMenuItem.background", PANEL_BG);
        UIManager.put("CheckBoxMenuItem.selectionBackground", HOVER_BG);
        UIManager.put("RadioButtonMenuItem.background", PANEL_BG);
        UIManager.put("RadioButtonMenuItem.selectionBackground", HOVER_BG);
        UIManager.put("PopupMenu.background", PANEL_BG);
        UIManager.put("PopupMenu.borderColor", SOFT_BORDER);

        // 始终显示菜单助记符下划线
        UIManager.put("Component.hideMnemonics", false);

        // 菜单字体
        Font ui13 = uiFont(isSegoe() ? 12f : 11.5f, Font.PLAIN);
        UIManager.put("Menu.font", ui13);
        UIManager.put("MenuItem.font", ui13);
        UIManager.put("CheckBoxMenuItem.font", ui13);
        UIManager.put("RadioButtonMenuItem.font", ui13);

        UIManager.put("ToolTip.arc", 6);
    }

    public static Font textFont() {
        Font f = UIManager.getFont("Label.font");
        return f != null ? f : new Font("SansSerif", Font.PLAIN, 12);
    }

    public static Font textFont(float size, int style) {
        return textFont().deriveFont(style, size);
    }

    /**
     * 标题栏字体：优先 Segoe UI（对齐 IntelliJ），跨平台回退 Liberation / Noto /
     * DejaVu；候选字体不含中文等字形时，回退到 LAF 默认字体（逻辑字体会自动
     * 做 CJK 回退），保证中文标题 / 菜单不出现豆腐块。
     */
    public static Font uiFont(float size, int style) {
        String cjkSample = "地图画生成器方块库种文件编辑视图工具帮助";
        Font fallback = textFont(size, style);
        for (String name : new String[]{"Segoe UI", "Liberation Sans", "Noto Sans", "DejaVu Sans"}) {
            Font f = new Font(name, style, Math.round(size));
            if (!f.getFamily().equals(Font.DIALOG)) {
                Font sized = f.deriveFont(style, size);
                if (sized.canDisplayUpTo(cjkSample) == -1)
                    return sized;
            }
        }
        return fallback;
    }

    /** 当前 UI 字体是否为 Segoe UI（用于微调间距）。 */
    public static boolean isSegoe() {
        return uiFont(12f, Font.PLAIN).getFamily().startsWith("Segoe");
    }

    /** 绘制柔和的圆角悬停 / 选中底色。 */
    public static void paintRoundedBg(Graphics2D g, int x, int y, int w, int h, int arc, Color c) {
        g = (Graphics2D) g.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c);
        g.fillRoundRect(x, y, w, h, arc, arc);
        g.dispose();
    }

    /** 两色线性插值（t: 0..1）。 */
    public static Color lerp(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }
}
