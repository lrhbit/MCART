package com.axolotl.mcart.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动打点：记录各阶段相对启动时刻的毫秒数，写入
 * mc-pixel-art-data/logs/boot-trace.log，供云端构建的冒烟测试确认首窗真正显示。
 *
 * 同时输出到标准输出，每次写入立即 flush，避免进程被 kill 时日志丢失。
 */
public final class BootLog {

    private static final long START = System.currentTimeMillis();
    private static final List<String> LINES = new ArrayList<>();
    private static volatile boolean enabled = true;

    private BootLog() {}

    public static synchronized void mark(String stage) {
        long ms = System.currentTimeMillis() - START;
        String line = "[boot] " + stage + "  " + ms + " ms";
        LINES.add(line);
        System.out.println(line);
        System.out.flush();
        persist();
    }

    public static synchronized void mark(String stage, double scale) {
        long ms = System.currentTimeMillis() - START;
        String line = "[boot] " + stage + " 缩放 " + scale + "  " + ms + " ms";
        LINES.add(line);
        System.out.println(line);
        System.out.flush();
        persist();
    }

    private static void persist() {
        try {
            AppDirs.ensure();
            Path log = AppDirs.logsDir().resolve("boot-trace.log");
            Files.write(log, String.join(System.lineSeparator(), LINES).getBytes());
        } catch (Throwable ignored) {
        }
    }

    public static void setEnabled(boolean v) { enabled = v; }
}
