package com.axolotl.mcart.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 原生镜像运行时自愈。
 *
 * GraalVM 在 Windows 上的原生库加载器按系统 ANSI 代码页转换路径，目录含非 ASCII
 * （中文等）时会被转成 '?'，导致 LoadLibrary 失败（错误码 126）。本类在 main 最
 * 早期、任何 AWT 类初始化之前运行：若自身路径含非 ASCII，就把同目录的原生库与 lib
 * 子目录复制到纯英文缓存目录，并把 java.library.path 指向那里。
 *
 * 纯英文路径下直接返回，不做任何复制，零额外开销。
 */
public final class NativeRuntimeHealer {

    private static final String CACHE_DIR_NAME = "mcart-runtime";
    private static final String READY_MARKER = ".ready";

    private NativeRuntimeHealer() {}

    public static void heal() {
        try {
            Path self = selfExecutable();
            Path appDir = self != null ? self.getParent() : Paths.get(System.getProperty("user.dir"));
            if (appDir == null || isPureAscii(appDir.toString())) {
                return;
            }
            Path cache = cacheDir();
            Files.createDirectories(cache);
            Path marker = cache.resolve(READY_MARKER);
            if (!Files.exists(marker)) {
                copyRuntimeLibraries(appDir, cache);
                Files.writeString(marker, "ok");
            }
            System.setProperty("java.library.path", cache.toString());
            // 让 JVM 重新解析库路径
            try {
                FieldAccessible.resetSysPath(cache);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            // 自愈失败不应阻断启动，交给后续加载逻辑暴露真实错误
            System.err.println("原生运行时自愈失败：" + t);
        }
    }

    /** 定位当前可执行文件（原生镜像下即 exe 自身）。 */
    private static Path selfExecutable() {
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path p = Paths.get(cmd.get());
                if (Files.exists(p)) return p;
            }
        } catch (Throwable ignored) {
        }
        String home = System.getProperty("java.home");
        if (home != null) {
            Path p = Paths.get(home);
            if (Files.exists(p)) return p.resolve("bin").resolve("mcart.exe");
        }
        return null;
    }

    private static Path cacheDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Paths.get(local).resolve(CACHE_DIR_NAME);
        }
        return Paths.get(System.getProperty("user.home"), CACHE_DIR_NAME);
    }

    private static void copyRuntimeLibraries(Path appDir, Path cache) throws Exception {
        // 复制同目录所有 dll
        try (Stream<Path> s = Files.list(appDir)) {
            s.filter(Files::isRegularFile)
             .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dll"))
             .forEach(p -> copy(p, cache.resolve(p.getFileName().toString())));
        }
        // 复制 lib 子目录（fontconfig.bfc 等）
        Path libSrc = appDir.resolve("lib");
        if (Files.isDirectory(libSrc)) {
            Path libDst = cache.resolve("lib");
            Files.createDirectories(libDst);
            try (Stream<Path> s = Files.walk(libSrc)) {
                s.filter(Files::isRegularFile).forEach(p ->
                        copy(p, libDst.resolve(libSrc.relativize(p).toString())));
            }
        }
    }

    private static void copy(Path src, Path dst) {
        try {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isPureAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) return false;
        }
        return true;
    }

    /** 通过反射重置 ClassLoader 的 usr_paths，使新的 java.library.path 立即生效。 */
    private static final class FieldAccessible {
        static void resetSysPath(Path cache) {
            try {
                Class<?> cl = Class.forName("java.lang.ClassLoader");
                java.lang.reflect.Field f = cl.getDeclaredField("sys_paths");
                f.setAccessible(true);
                f.set(null, null);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 供外部判断当前是否处于英文缓存（测试/日志用）。 */
    public static File cacheLocation() {
        return cacheDir().toFile();
    }
}
