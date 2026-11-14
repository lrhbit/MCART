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
        // 原生镜像适配必须在任何 AWT 类初始化之前完成
        ensureJavaHome();
        com.axolotl.mcart.util.NativeRuntimeHealer.heal();
        com.axolotl.mcart.util.BootLog.mark("main 入口");
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
        com.axolotl.mcart.util.BootLog.mark("外观就绪");

        if (SystemInfo.isLinux) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }

        // 主窗口创建前先显示应用内闪屏（native 下替代 java 启动器原生闪屏）
        com.axolotl.mcart.util.AppSplash.show();

        // 用后台线程编排启动，避免在 EDT 上 sleep；保证闪屏关闭后再显示主窗口
        Thread starter = new Thread(() -> {
            try {
                // 闪屏展示期间，在 EDT 上构建主窗口（先不显示）
                final MainFrame[] holder = new MainFrame[1];
                SwingUtilities.invokeAndWait(() -> holder[0] = new MainFrame());
                MainFrame frame = holder[0];
                // 补足闪屏最短展示时长，避免性能好的机器上一闪而过
                com.axolotl.mcart.util.AppSplash.awaitMinimum();
                // 先关闭闪屏，再让主窗口可见，避免主窗口空背景从闪屏下露出
                com.axolotl.mcart.util.AppSplash.close();
                SwingUtilities.invokeAndWait(() -> {
                    frame.setVisible(true);
                    com.axolotl.mcart.util.BootLog.mark("首窗 setVisible",
                            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                                    .getDefaultScreenDevice().getDefaultConfiguration()
                                    .getDefaultTransform().getScaleX());
                });
                frame.loadWindowIconAsync();
                SwingUtilities.invokeLater(() -> {
                    if (loadSample) frame.openSampleProject();
                    else frame.restoreSessionIfAny();
                });
                warmTexturesInBackground();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "启动编排");
        starter.setDaemon(true);
        starter.start();
    }

    /**
     * 原生镜像下 java.home 可能未设置，导致字体配置初始化失败。
     * 缺失时用当前可执行文件位置推断其根目录，再退回工作目录。
     */
    private static void ensureJavaHome() {
        String home = System.getProperty("java.home");
        if (home != null && !home.isBlank()) return;
        String inferred = null;
        try {
            java.util.Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                java.nio.file.Path exe = java.nio.file.Paths.get(cmd.get());
                java.nio.file.Path home0 = exe.getParent();
                // 标准 JDK 布局为 home/bin/exe，需上一层；原生镜像布局为 home/exe，直接用父目录
                if (home0 != null && "bin".equals(
                        home0.getFileName() != null ? home0.getFileName().toString() : "")) {
                    home0 = home0.getParent();
                }
                if (home0 != null) inferred = home0.toString();
            }
        } catch (Throwable ignored) {
        }
        if (inferred == null || inferred.isBlank()) {
            inferred = System.getProperty("user.dir");
        }
        System.setProperty("java.home", inferred);
        System.out.println("[boot] java.home 未设置，已推断为 " + inferred);
        System.out.flush();
        // Linux 原生镜像下，字体子系统依据 java.home/lib 是否存在决定是否走系统 fontconfig
        // （FcFontConfiguration）。该目录缺失时会跳过系统字体并因读不到 fontconfig 文件而崩溃，
        // 这里确保目录存在，使其使用 Linux 默认字体。
        try {
            java.io.File libDir = new java.io.File(inferred, "lib");
            if (!libDir.exists()) {
                libDir.mkdirs();
            }
        } catch (Throwable ignored) {
        }
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
