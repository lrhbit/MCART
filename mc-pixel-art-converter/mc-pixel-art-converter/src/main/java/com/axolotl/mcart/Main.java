package com.axolotl.mcart;

import com.formdev.flatlaf.FlatDarkLaf;
import com.axolotl.mcart.ui.MainFrame;
import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        ensureJavaHome();
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
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
