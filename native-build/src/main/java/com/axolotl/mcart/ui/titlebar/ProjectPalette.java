package com.axolotl.mcart.ui.titlebar;

import java.awt.*;
import java.io.*;
import java.util.Properties;
import java.util.Random;

/**
 * 标题栏「文档色块」的配色方案（移植自 Pixel 的 ProjectPalette）。
 *
 * 行为：
 *   - 应用标识（mc-pixel-art）走 {@link #perLaunch}：<b>每次启动都随机一个新色相</b>，
 *     同一次运行内固定，且与上一次启动颜色差异足够明显；
 *   - 其它标识名可用 {@link #loadOrGenerate}：按名字派生稳定色、跨启动固定；
 *   - 颜色持久化到 {@code mc-pixel-art-data/palette.properties}（主要用于记录上一次色相）；
 *   - 右键色块可手动重新随机。
 *
 * 色块的对角渐变与标题栏上的柔和光晕都取自同一色相（见 {@link #glow}）。
 */
public final class ProjectPalette {

    /** 让默认应用标识落到一个好看的色相 */
    private static final float HASH_OFFSET = 0.734f;

    public final float hue;
    public final Color blockTop;    // 亮端（左上）
    public final Color blockBottom; // 暗端（右下）
    public final Color glow;        // 光晕用同色

    private ProjectPalette(float hue) {
        this.hue = hue;
        this.blockTop = Color.getHSBColor(hue, 0.86f, 0.77f);
        this.blockBottom = Color.getHSBColor(hue, 0.90f, 0.64f);
        this.glow = blockTop;
    }

    /** 由名字派生稳定的伪随机色相。 */
    public static float hueForName(String name) {
        int h = name.hashCode();
        int x = h * 0x9E3779B1;
        float unit = (x & 0xFFFFFFFFL) / (float) 0x100000000L;
        return (unit + HASH_OFFSET) % 1f;
    }

    /** 读取已存色相；没有则按名字派生并持久化。 */
    public static ProjectPalette loadOrGenerate(String key) {
        File file = configFile();
        Properties props = load(file);
        String p = "palette." + normalize(key) + ".";
        Float hue = parseHue(props.getProperty(p + "hue"));
        if (hue == null) {
            hue = hueForName(key);
            props.setProperty(p + "hue", String.format("%.4f", hue));
            save(file, props);
        }
        return new ProjectPalette(hue);
    }

    /** 强制重新随机一个色相并持久化（手动 re-roll）。 */
    public static ProjectPalette regenerate(String key) {
        File file = configFile();
        Properties props = load(file);
        float hue = new Random().nextFloat();
        String p = "palette." + normalize(key) + ".";
        props.setProperty(p + "hue", String.format("%.4f", hue));
        save(file, props);
        ProjectPalette pal = new ProjectPalette(hue);
        if (LAUNCH_KEY != null && LAUNCH_KEY.equals(key)) {
            launchPalette = pal;
        }
        return pal;
    }

    /**
     * 应用标识色块：<b>每次启动都重新随机一个色相</b>（仅在该进程内首次访问时生成一次）。
     *
     * <p>与 {@link #loadOrGenerate}（按名字派生、跨启动固定）不同：
     * <ul>
     *   <li>每次启动 JVM 都产生一个全新的随机色相，使标题栏渐变色每次打开都不同；</li>
     *   <li>进程内用静态缓存固定，避免重建标题栏 / 切换主题时颜色在会话中途跳动；</li>
     *   <li>保证与上一次启动的色相圆周距离足够大（≥65°），差异肉眼可辨；</li>
     *   <li>仍写回持久化文件，仅用于下次启动读取“上次颜色”做对比，不参与本次取值。</li>
     * </ul>
     */
    public static ProjectPalette perLaunch(String key) {
        ProjectPalette cached = launchPalette;
        if (cached != null && key.equals(LAUNCH_KEY)) {
            return cached;
        }
        synchronized (LAUNCH_LOCK) {
            if (launchPalette == null || !key.equals(LAUNCH_KEY)) {
                File file = configFile();
                Properties props = load(file);
                String p = "palette." + normalize(key) + ".";
                Float prev = parseHue(props.getProperty(p + "hue"));
                float hue = distinctRandomHue(prev);
                props.setProperty(p + "hue", String.format("%.4f", hue));
                save(file, props);
                launchPalette = new ProjectPalette(hue);
                LAUNCH_KEY = key;
            }
            return launchPalette;
        }
    }

    /** 进程级「本次启动」配色缓存：每个 JVM 只随机一次 */
    private static volatile ProjectPalette launchPalette;
    private static volatile String LAUNCH_KEY;
    private static final Object LAUNCH_LOCK = new Object();

    /** 与上一次色相圆周距离足够大（≥65°）的随机色相，避免相邻两次启动颜色太接近。 */
    private static float distinctRandomHue(Float previous) {
        float hue = new Random().nextFloat();
        if (previous == null) {
            return hue;
        }
        final float minDist = 0.18f; // ≈ 65°
        int guard = 0;
        while (hueDistance(hue, previous) < minDist && guard++ < 16) {
            hue = new Random().nextFloat();
        }
        return hue;
    }

    /** 色环上两个色相的最短距离（0..0.5）。 */
    private static float hueDistance(float a, float b) {
        float d = Math.abs(a - b) % 1f;
        return d > 0.5f ? 1f - d : d;
    }

    // ------------------------------------------------------------ internals

    static File configFile() {
        // 与全局数据目录一致：jar 同级 mc-pixel-art-data（可用 -Dmcart.home 覆盖）
        return com.axolotl.mcart.util.AppDirs.paletteFile().toFile();
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase().replaceAll("\\s+", "_");
    }

    private static Float parseHue(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            float v = Float.parseFloat(s);
            return (v % 1f + 1f) % 1f;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Properties load(File file) {
        Properties props = new Properties();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException ex) {
                // 读不到就重新生成
            }
        }
        return props;
    }

    private static void save(File file, Properties props) {
        try {
            file.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(file)) {
                props.store(out, "mc-pixel-art title-bar colors (derived from name, then fixed)");
            }
        } catch (IOException ex) {
            System.err.println("无法持久化标题栏配色: " + ex.getMessage());
        }
    }
}
