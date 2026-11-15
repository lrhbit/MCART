package com.axolotl.mcart;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import com.axolotl.mcart.cli.Cli;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.ui.MainFrame;
import com.axolotl.mcart.ui.titlebar.TitleTheme;
import com.axolotl.mcart.util.AppConfig;
import com.axolotl.mcart.util.CrashReporter;

import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {
        // 无头 CLI 模式：不启动 GUI
        if (args.length > 0 && (args[0].equals("--cli") || args[0].equals("-c"))) {
            int code = Cli.run(args);
            System.exit(code);
        }
        boolean loadSample = args.length > 0 && args[0].equals("--sample");

        AppConfig.load();
        I18n.setLanguage(AppConfig.language());
        CrashReporter.install();
        setupLookAndFeel(AppConfig.theme());

        if (SystemInfo.isLinux) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            // 先让首窗绘制出来，再做示例 / 会话恢复 / 窗口图标这类可能较重的工作
            frame.setVisible(true);
            frame.loadWindowIconAsync();
            SwingUtilities.invokeLater(() -> {
                if (loadSample) frame.openSampleProject();
                else frame.restoreSessionIfAny();
            });
            warmTexturesInBackground();
        });
    }

    /** 首屏显示后在后台线程预加载默认方块纹理，避免首次生成 / 打开方块库时卡顿。 */
    private static void warmTexturesInBackground() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
                java.util.List<com.axolotl.mcart.model.BlockColor> blocks = new java.util.ArrayList<>();
                for (String id : com.axolotl.mcart.model.PaletteService.defaultSelection()) {
                    com.axolotl.mcart.model.BlockColor bc = com.axolotl.mcart.model.PaletteService.byId(id);
                    if (bc != null) blocks.add(bc);
                }
                com.axolotl.mcart.util.TextureManager.warmUp(blocks);
            } catch (Throwable ignored) {
            }
        }, "texture-warmup");
        t.setDaemon(true);
        t.start();
    }

    /** 按主题配置安装外观（主题 / 语言切换后由 restartUi 重建窗口）。 */
    static void setupLookAndFeel(String theme) {
        try {
            switch (theme) {
                case AppConfig.THEME_LIGHT -> FlatLightLaf.setup();
                case AppConfig.THEME_HIGH_CONTRAST -> {
                    FlatDarkLaf.setup();
                    // 高对比度无障碍：黑底白字 + 强边框
                    UIManager.put("Panel.background", Color.BLACK);
                    UIManager.put("Panel.foreground", Color.WHITE);
                    UIManager.put("Component.foreground", Color.WHITE);
                    UIManager.put("Component.borderColor", Color.WHITE);
                    UIManager.put("Component.focusColor", Color.YELLOW);
                    UIManager.put("Button.foreground", Color.WHITE);
                    UIManager.put("Label.foreground", Color.WHITE);
                    UIManager.put("Component.hideMnemonics", false);
                }
                default -> FlatDarkLaf.setup();
            }
        } catch (Exception ignored) {
        }
        TitleTheme.apply(theme);
        // 语义配色随主题切换（查看器 / 卡片 / 分隔线 / 图标等全部颜色）
        com.axolotl.mcart.ui.UiTheme.init(theme);
        // 分隔条：去掉 FlatLaf 默认的 grip 圆点，只保留细 chrome 线
        UIManager.put("SplitPaneDivider.style", "plain");
    }

    /** 主题 / 语言切换后重建主窗口：先自动保存会话，再从会话恢复全部工程。 */
    public static void restartUi(MainFrame old) {
        try {
            old.setVisible(false);
            old.dispose();
        } catch (Exception ignored) {
        }
        setupLookAndFeel(AppConfig.theme());
        I18n.setLanguage(AppConfig.language());
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.restoreSessionIfAny();
            frame.setVisible(true);
        });
    }
}
