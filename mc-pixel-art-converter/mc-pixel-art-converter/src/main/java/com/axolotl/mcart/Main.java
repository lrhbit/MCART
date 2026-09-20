package com.axolotl.mcart;

import com.formdev.flatlaf.FlatDarkLaf;
import com.axolotl.mcart.ui.MainFrame;
import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        // 必须在任何 AWT/Swing 类（含 FlatLaf）初始化之前预加载捆绑的原生库，
        // 否则 Windows 中文路径下会因 java.library.path 乱码而加载不到 awt.dll。
        preloadBundledNativeLibraries();
        ensureJavaHome();
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    /**
     * GraalVM native-image 在 Windows 上会把“可执行文件所在目录”加入
     * {@code java.library.path}，但其路径按 UTF-8 解码系统返回的 GBK/ANSI 字节，
     * 当安装目录含中文等非 ASCII 字符时该路径会乱码（例如“MC地图画…”变成
     * “MC鍦板浘鐢?”），导致 {@code System.loadLibrary("awt")} 在乱码目录中找不到
     * 同目录的 awt.dll，启动即抛 {@link UnsatisfiedLinkError}。
     *
     * 这里在 AWT 初始化前，用基于宽字符 API 的 {@link ProcessHandle} 取得 exe 的
     * 正确 Unicode 绝对路径，并按依赖顺序 {@link System#load(String)} 预加载全部
     * 捆绑 DLL；此后 AWT 内部的 loadLibrary 会因“库已加载”而直接返回。
     * 仅在 Windows 原生镜像下生效，普通 JVM 与 Linux/macOS 行为完全不变。
     */
    private static void preloadBundledNativeLibraries() {
        boolean nativeImage = "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!nativeImage || !windows) {
            return;
        }
        File dir = null;
        try {
            String exe = ProcessHandle.current().info().command().orElse(null);
            if (exe != null) {
                File parent = new File(exe).getParentFile();
                if (parent != null && parent.isDirectory()) {
                    dir = parent;
                }
            }
        } catch (Throwable ignore) {
            // 落到 user.dir 兜底
        }
        if (dir == null) {
            dir = new File(System.getProperty("user.dir", "."));
        }
        // 按依赖顺序排列；同目录依赖 Windows 加载器也会自动解析，
        // 这里显式逐个加载是为了让 AWT 后续所有 loadLibrary 调用都命中“已加载”。
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
            if (f.isFile()) {
                try {
                    System.load(f.getAbsolutePath());
                } catch (Throwable ignore) {
                    // 已加载或由其依赖自动加载，忽略即可
                }
            }
        }
    }

    /**
     * GraalVM native-image 下 AWT/Swing 的字体子系统初始化要求 {@code java.home}
     * 指向一个<b>存在的目录</b>（Linux 走系统 fontconfig、Windows 走 GDI，
     * 并不需要完整 JRE）。普通 JVM 运行时 {@code java.home} 始终有效，此方法直接返回，
     * 行为与原来完全一致；仅在原生镜像且该属性缺失/失效时，把它指向可执行文件所在目录。
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
            // AWT 字体子系统在 Linux 上要求 java.home/lib 目录存在（内容可空，字体由
            // 系统 fontconfig 提供）；只读环境下创建失败可忽略，Windows 走 GDI 不受影响。
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
