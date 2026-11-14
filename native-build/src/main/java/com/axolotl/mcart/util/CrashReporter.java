package com.axolotl.mcart.util;

import javax.swing.JOptionPane;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 崩溃报告：捕获所有未处理异常，写入 mc-pixel-art-data/logs/crash-*.log，
 * GUI 环境下弹窗提示日志位置，方便反馈 bug。
 */
public final class CrashReporter {

    private static volatile boolean installed = false;

    private CrashReporter() {}

    public static void install() {
        if (installed) return;
        installed = true;
        AppDirs.ensure();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            Path log = writeCrash(thread, ex);
            System.err.println("未捕获异常（线程 " + thread.getName() + "），日志：" + log);
            ex.printStackTrace();
            try {
                JOptionPane.showMessageDialog(null,
                        "程序遇到未处理的异常：\n" + ex
                                + "\n\n崩溃日志已写入：\n" + log.toAbsolutePath()
                                + "\n\n请将该日志文件反馈给开发者，谢谢！",
                        "MC 地图画生成器 - 崩溃报告",
                        JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) {
                // 弹窗失败（无头环境）时仅保留日志文件
            }
        });
    }

    public static Path writeCrash(Thread thread, Throwable ex) {
        AppDirs.ensure();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path log = AppDirs.logsDir().resolve("crash-" + stamp + ".log");
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            pw.println("MC Pixel Art Converter - crash report");
            pw.println("time=" + LocalDateTime.now());
            pw.println("java=" + System.getProperty("java.version")
                    + " (" + System.getProperty("java.vendor") + ")");
            pw.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            pw.println("thread=" + thread.getName());
            pw.println("--------------------------------------------------");
            ex.printStackTrace(pw);
        }
        try {
            Files.writeString(log, sw.toString());
        } catch (Exception ignored) {
        }
        return log;
    }

    /** 追加普通运行日志（环形覆盖最近一次会话） */
    public static void log(String message) {
        System.out.println("[mcart] " + message);
    }
}
