package com.axolotl.mcart.util;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * 应用内启动闪屏。
 *
 * GraalVM 原生可执行文件不支持 java 启动器那种“JVM 启动前”的原生闪屏
 * （MANIFEST 的 SplashScreen-Image 在 native 下不生效，getSplashScreen() 返回 null）。
 * 因此在主窗口创建前，用一个无边框、逐像素透明的 JWindow 显示同一张闪屏图，
 * 主窗口就绪后关闭，视觉效果与原启动闪屏一致。
 */
public final class AppSplash {

    /** 最短展示时长，避免在性能好的机器上一闪而过。 */
    private static final long MIN_VISIBLE_MS = 600L;

    private static JWindow window;
    private static long shownAt;

    private AppSplash() {
    }

    /** 显示闪屏（在 EDT 上同步完成，返回时窗口已可见）。 */
    public static void show() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> {
                Image image = loadImage();
                if (image == null) {
                    return;
                }
                JWindow w = new JWindow();
                // 逐像素透明，让 PNG 的透明区域不显示底色
                w.setBackground(new Color(0, 0, 0, 0));
                JLabel label = new JLabel(new ImageIcon(image));
                label.setBorder(null);
                label.setOpaque(false);
                w.setContentPane(label);
                w.getRootPane().setOpaque(false);
                w.pack();
                w.setLocationRelativeTo(null);
                w.setAlwaysOnTop(true);
                window = w;
                w.setVisible(true);
                shownAt = System.currentTimeMillis();
            });
        } catch (Throwable ignored) {
            // 闪屏失败不应影响主程序启动
            window = null;
        }
    }

    /** 在调用线程阻塞，直到满足最短展示时长（适合在后台启动线程调用，不阻塞 EDT）。 */
    public static void awaitMinimum() {
        if (window == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed < MIN_VISIBLE_MS) {
            try {
                Thread.sleep(MIN_VISIBLE_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** 关闭并释放闪屏（在 EDT 上执行；可从任意线程调用）。 */
    public static void close() {
        if (window == null) {
            return;
        }
        Runnable task = () -> {
            JWindow w = window;
            window = null;
            if (w != null) {
                w.setVisible(false);
                w.dispose();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Image loadImage() {
        try {
            URL url = AppSplash.class.getResource("/splash.png");
            if (url == null) {
                return null;
            }
            return ImageIO.read(url);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
