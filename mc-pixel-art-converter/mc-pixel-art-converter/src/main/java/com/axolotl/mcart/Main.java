package com.axolotl.mcart;

import com.formdev.flatlaf.FlatDarkLaf;
import com.axolotl.mcart.ui.MainFrame;
import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 诊断版启动入口：每个启动阶段都同时输出到控制台和 exe 同目录的 launch-log.txt，
 * 每个 DLL 加载前后都落盘（flush），即便发生原生层闪退，日志最后一行即崩溃点。
 */
public class Main {
    private static PrintWriter logWriter;
    private static boolean flatLafInstalled;

    public static void main(String[] args) {
        initLog();
        log("======== MCart 启动诊断开始 ========");
        try {
            log("os.name=" + System.getProperty("os.name"));
            log("os.arch=" + System.getProperty("os.arch"));
            log("java.version=" + System.getProperty("java.version"));
            log("file.encoding=" + System.getProperty("file.encoding"));
            log("native.image.kind=" + System.getProperty("org.graalvm.nativeimage.imagecode"));
            log("user.dir=" + System.getProperty("user.dir"));
            log("java.home(before)=" + System.getProperty("java.home"));
            log("java.library.path=" + System.getProperty("java.library.path"));
            File exeDir = exeDir();
            log("exeDir(ProcessHandle)=" + (exeDir == null ? "null" : exeDir.getAbsolutePath()));

            // 全局未捕获异常（含 EDT）落盘
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                log("[未捕获异常 in " + t.getName() + "] " + e);
                if (logWriter != null) e.printStackTrace(logWriter);
                e.printStackTrace();
            });

            preloadBundledNativeLibraries();
            log("步骤[1/4] 原生库预加载完成");

            ensureJavaHome();
            log("步骤[2/4] java.home 已设置=" + System.getProperty("java.home"));

            setupFontConfig();

            log("步骤[3/4] 即将 FlatDarkLaf.setup() ...");
            flatLafInstalled = installLaf();
            log("步骤[3/4] FlatLaf.setup 返回，installed=" + flatLafInstalled);

            log("步骤[4/4] 即将创建主窗口 ...");
            SwingUtilities.invokeLater(() -> {
                try {
                    new MainFrame().setVisible(true);
                    log("步骤[4/4] 主窗口已显示，启动成功");
                } catch (Throwable t) {
                    log("EDT 创建窗口失败: " + t);
                    if (logWriter != null) t.printStackTrace(logWriter);
                    t.printStackTrace();
                }
            });
        } catch (Throwable t) {
            log("!!! 启动致命错误: " + t);
            if (logWriter != null) t.printStackTrace(logWriter);
            t.printStackTrace();
            closeLog();
            try {
                JOptionPane.showMessageDialog(null,
                        "启动失败：\n" + t,
                        "MCart 启动错误", JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignore) {
                // GUI 都起不来时忽略
            }
            System.exit(1);
        }
    }

    private static void initLog() {
        try {
            File dir = exeDir();
            if (dir == null) dir = new File(System.getProperty("user.dir", "."));
            File lf = new File(dir, "launch-log.txt");
            OutputStream os = new FileOutputStream(lf, false);
            logWriter = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
        } catch (Throwable t) {
            logWriter = null;
            System.out.println("[log] 无法创建 launch-log.txt: " + t);
        }
    }

    private static void log(String msg) {
        String line = "[" + System.currentTimeMillis() + "] " + msg;
        System.out.println(line);
        System.out.flush();
        if (logWriter != null) {
            logWriter.println(line);
            logWriter.flush();
        }
    }

    private static void closeLog() {
        if (logWriter != null) {
            try { logWriter.flush(); logWriter.close(); } catch (Throwable ignore) {}
        }
    }

    private static File exeDir() {
        try {
            String exe = ProcessHandle.current().info().command().orElse(null);
            if (exe != null) {
                File p = new File(exe).getParentFile();
                if (p != null && p.isDirectory()) return p;
            }
        } catch (Throwable ignore) {
            // 忽略，返回 null
        }
        return null;
    }

    /**
     * GraalVM native-image 在 Windows 上把 exe 目录加入 java.library.path 时，
     * 对非 ASCII（中文）路径会按 UTF-8 误解码 GBK 字节而产生乱码，导致
     * loadLibrary("awt") 在乱码目录中找不到同目录 DLL。这里在 AWT 初始化前
     * 用宽字符 API 得到的正确 Unicode 绝对路径逐个 System.load。
     * 仅 Windows 原生镜像生效；普通 JVM 与 Linux/macOS 不变。
     */
    private static void preloadBundledNativeLibraries() {
        boolean nativeImage = "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        log("preload 判定: nativeImage=" + nativeImage + ", windows=" + windows);
        if (!nativeImage || !windows) {
            log("preload 跳过（非 Windows 原生镜像）");
            return;
        }
        File dir = exeDir();
        if (dir == null) dir = new File(System.getProperty("user.dir", "."));
        log("preload 目录=" + dir.getAbsolutePath());

        File[] all = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".dll"));
        if (all != null) {
            for (File f : all) log("  目录内 dll: " + f.getName() + " (" + f.length() + " 字节)");
        }

        String[] dlls = {
                "jvm.dll",
                "java.dll",
                "freetype.dll",
                "lcms.dll",
                "awt.dll",
                "jawt.dll",
                "fontmanager.dll",
                "javajpeg.dll",
                "javaaccessbridge.dll"
        };
        for (String name : dlls) {
            File f = new File(dir, name);
            if (!f.isFile()) {
                log("  [缺失] " + name + " -> " + f.getAbsolutePath());
                continue;
            }
            log("  加载中 " + name + " ...");
            try {
                System.load(f.getAbsolutePath());
                log("  [成功] " + name);
            } catch (Throwable t) {
                log("  [失败] " + name + " : " + t);
            }
        }
    }

    /**
     * 显式指定随包携带的字体配置文件，修复 GraalVM Windows 原生镜像
     * “Fontconfig head is null” 导致 FlatLaf 初始化失败、所有 Swing 控件
     * 报 no ComponentUI class 的问题。必须在任何 AWT/Font 类初始化之前调用。
     */
    private static void setupFontConfig() {
        try {
            File dir = exeDir();
            if (dir == null) dir = new File(System.getProperty("user.dir", "."));
            String[] candidates = {
                    "lib" + File.separator + "fontconfig.bfc",
                    "lib" + File.separator + "fontconfig.properties",
                    "fontconfig.bfc",
                    "fontconfig.properties"
            };
            for (String rel : candidates) {
                File f = new File(dir, rel);
                if (f.isFile()) {
                    System.setProperty("sun.awt.fontconfig", f.getAbsolutePath());
                    log("sun.awt.fontconfig -> " + f.getAbsolutePath() + " (" + f.length() + " 字节)");
                    return;
                }
            }
            log("未找到随包 fontconfig 文件，依赖镜像内置资源");
        } catch (Throwable t) {
            log("setupFontConfig 异常: " + t);
        }
    }

    /**
     * 安装 FlatLaf 并检测是否真正成功。FlatLaf.setup() 内部会吞掉字体子系统
     * 异常（仅记 SEVERE）而正常返回，因此用当前 LookAndFeel 类名二次确认。
     */
    private static boolean installLaf() {
        try {
            FlatDarkLaf.setup();
            LookAndFeel laf = UIManager.getLookAndFeel();
            boolean ok = laf != null && laf.getClass().getName().toLowerCase().contains("flat");
            if (!ok) {
                log("FlatLaf 未生效，当前 LAF=" + (laf == null ? "null" : laf.getClass().getName()));
            }
            return ok;
        } catch (Throwable t) {
            log("FlatLaf.setup 抛出: " + t);
            if (logWriter != null) t.printStackTrace(logWriter);
            t.printStackTrace();
            return false;
        }
    }

    /**
     * GraalVM native-image 下 AWT/Swing 字体子系统要求 java.home 指向存在的目录。
     * 普通 JVM 直接返回；原生镜像缺失时指向 exe 目录并建立空 lib 目录。
     */
    private static void ensureJavaHome() {
        try {
            String home = System.getProperty("java.home");
            if (home != null && Files.isDirectory(Path.of(home))) {
                return;
            }
            String exe = ProcessHandle.current().info().command().orElse(null);
            if (exe != null) {
                Path dir = Paths.get(exe).getParent();
                if (dir != null && Files.isDirectory(dir)) {
                    home = dir.toString();
                }
            }
            if (home == null || !Files.isDirectory(Path.of(home))) {
                home = System.getProperty("user.dir", ".");
            }
            System.setProperty("java.home", home);
            try {
                Files.createDirectories(Path.of(home, "lib"));
            } catch (Throwable ignore) {
                // 尽力而为
            }
        } catch (Throwable ignore) {
            if (System.getProperty("java.home") == null) {
                System.setProperty("java.home", System.getProperty("user.dir", "."));
            }
        }
    }
}
