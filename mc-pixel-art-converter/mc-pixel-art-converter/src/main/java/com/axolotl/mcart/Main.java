package com.axolotl.mcart;

import com.formdev.flatlaf.FlatDarkLaf;
import com.axolotl.mcart.ui.MainFrame;
import javax.swing.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 启动入口。
 *
 * GraalVM native-image（Windows）打包 Swing/FlatLaf 的关键，依据可运行的成功实践：
 *  1. 不要手动 System.load(...) 任何 JDK dll —— native-image 已内置 AWT 原生库，
 *     运行时会自动从 exe 同目录加载，手动加载在非 ASCII 路径下必失败，纯属干扰。
 *  2. 不要设置 sun.awt.fontconfig —— 该属性会让 JDK 把文件强制按“文本 properties”
 *     解析，二进制 fontconfig.bfc 会被错读，进而在 FontConfiguration.getInitELC 抛 NPE。
 *  3. native-image 下 java.home 为空，需要把它指向 exe 所在目录，并随包在其 lib/ 下
 *     放置【二进制】fontconfig.bfc 与 flavormap.properties；AWT 会按默认规则以二进制
 *     方式加载 java.home/lib/fontconfig.bfc，FlatLaf 字体初始化即可成功。
 *
 * launch-log.txt 仅用于 CI 真机冒烟与用户排错，逐行 flush。
 */
public class Main {
    private static PrintWriter logWriter;

    public static void main(String[] args) {
        initLog();
        for (String a : args) {
            if ("--selftest".equals(a)) {
                runSelfTest();
                return;
            }
        }
        log("======== MCart 启动开始 ========");
        try {
            log("os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                    + ", java=" + System.getProperty("java.version")
                    + ", native=" + System.getProperty("org.graalvm.nativeimage.imagecode")
                    + ", encoding=" + System.getProperty("file.encoding"));
            log("user.dir=" + System.getProperty("user.dir"));
            log("java.home(before)=" + System.getProperty("java.home"));

            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                log("[未捕获异常 in " + t.getName() + "] " + e);
                if (logWriter != null) e.printStackTrace(logWriter);
                e.printStackTrace();
            });

            // 唯一需要的运行时修正：让 java.home 指向 exe 目录（其下有随包 lib/fontconfig.bfc）
            ensureJavaHome();
            log("java.home(after)=" + System.getProperty("java.home"));

            log("安装 FlatLaf ...");
            boolean installed = installLaf();
            log("FlatLaf installed=" + installed);

            SwingUtilities.invokeLater(() -> {
                try {
                    new MainFrame().setVisible(true);
                    log("主窗口已显示，启动成功");
                } catch (Throwable t) {
                    log("EDT 创建窗口失败: " + t);
                    if (logWriter != null) t.printStackTrace(logWriter);
                    t.printStackTrace();
                }
            });
        } catch (Throwable t) {
            log("启动致命错误: " + t);
            if (logWriter != null) t.printStackTrace(logWriter);
            t.printStackTrace();
            closeLog();
            try {
                JOptionPane.showMessageDialog(null,
                        "启动失败：\n" + t, "MCart 启动错误", JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignore) {
                // GUI 不可用时忽略
            }
            System.exit(1);
        }
    }

    /**
     * 隐藏的原生镜像交互自检（CI 用 mcart.exe --selftest 调用，用户双击不带此参数）。
     * 在真实原生 exe 内展开每一个下拉框、编辑每一个数字框并断言结果，
     * 用于在交付前自动发现 native-image 漏收录的弹窗/格式化器反射元数据。
     * 全部通过退出码 0，任一失败退出码 1，并把结论写入 launch-log.txt。
     */
    private static void runSelfTest() {
        log("======== MCart 交互自检开始 ========");
        int failures = 0, comboCount = 0, spinnerCount = 0;
        try {
            ensureJavaHome();
            boolean installed = installLaf();
            log("selftest FlatLaf installed=" + installed);

            MainFrame frame = new MainFrame();
            edt(() -> { frame.setSize(1280, 820); frame.setVisible(true); });
            Thread.sleep(900);

            List<Field> fields = new ArrayList<>();
            for (Field f : MainFrame.class.getDeclaredFields()) fields.add(f);

            for (Field f : fields) {
                Class<?> t = f.getType();
                if (JComboBox.class.isAssignableFrom(t)) {
                    comboCount++;
                    String name = f.getName();
                    try {
                        f.setAccessible(true);
                        JComboBox<?> combo = (JComboBox<?>) f.get(frame);
                        if (combo == null) throw new AssertionError("字段为 null");
                        final boolean[] shown = {false};
                        edt(() -> combo.setPopupVisible(true));
                        Thread.sleep(350);
                        edt(() -> shown[0] = combo.isPopupVisible());
                        if (!shown[0]) throw new AssertionError("setPopupVisible(true) 后弹窗未显示");
                        edt(() -> combo.setPopupVisible(false));
                        Thread.sleep(150);
                        log("selftest 下拉[" + name + "] 展开/收起 OK（" + combo.getItemCount() + " 项）");
                    } catch (Throwable ex) {
                        failures++;
                        log("selftest 下拉[" + name + "] 失败: " + ex);
                        if (logWriter != null) ex.printStackTrace(logWriter);
                    }
                } else if (JSpinner.class.isAssignableFrom(t)) {
                    spinnerCount++;
                    String name = f.getName();
                    try {
                        f.setAccessible(true);
                        JSpinner spinner = (JSpinner) f.get(frame);
                        if (spinner == null) throw new AssertionError("字段为 null");
                        if (!(spinner.getEditor() instanceof JSpinner.DefaultEditor))
                            throw new AssertionError("编辑器不是 DefaultEditor");
                        JFormattedTextField ftf = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
                        edt(() -> {
                            ftf.setText("5");
                            try { ftf.commitEdit(); }
                            catch (Exception e) { throw new RuntimeException(e); }
                        });
                        Thread.sleep(150);
                        final int[] val = {0};
                        edt(() -> val[0] = ((Number) spinner.getValue()).intValue());
                        if (val[0] != 5) throw new AssertionError("提交文本后值=" + val[0] + "，期望 5");
                        final Object[] next = {null};
                        edt(() -> next[0] = spinner.getNextValue());
                        if (next[0] != null) { edt(() -> spinner.setValue(next[0])); Thread.sleep(120); }
                        edt(() -> spinner.setValue(2));
                        log("selftest 数字框[" + name + "] 文本改值/箭头 OK");
                    } catch (Throwable ex) {
                        failures++;
                        log("selftest 数字框[" + name + "] 失败: " + ex);
                        if (logWriter != null) ex.printStackTrace(logWriter);
                    }
                }
            }

            Thread.sleep(300);
            edt(frame::dispose);
            if (failures == 0) {
                log("交互自检通过：" + comboCount + " 个下拉、" + spinnerCount + " 个数字框全部可交互");
                closeLog();
                System.exit(0);
            } else {
                log("交互自检失败：" + failures + " 项异常（下拉 " + comboCount + "、数字框 " + spinnerCount + "）");
                closeLog();
                System.exit(1);
            }
        } catch (Throwable t) {
            log("交互自检异常: " + t);
            if (logWriter != null) t.printStackTrace(logWriter);
            closeLog();
            System.exit(1);
        }
    }

    /** 在 EDT 上同步执行（Swing 组件必须在事件分派线程操作）。 */
    private static void edt(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeAndWait(r);
    }

    /** 安装 FlatLaf 并二次确认是否真正生效（FlatLaf.setup 会吞掉字体异常，需主动校验）。 */
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
     * native-image 下 AWT 字体子系统要求 java.home 指向一个含 lib/fontconfig.bfc 的目录。
     * 普通 JVM（开发环境，java.home 指向真正 JDK 且含 lib）直接返回，不做任何改动。
     */
    private static void ensureJavaHome() {
        try {
            String home = System.getProperty("java.home");
            if (home != null && Files.isDirectory(Path.of(home))
                    && new File(home, "lib").isDirectory()) {
                return;
            }
            File dir = exeDir();
            if (dir == null) dir = new File(System.getProperty("user.dir", "."));
            System.setProperty("java.home", dir.getAbsolutePath());
            File lib = new File(dir, "lib");
            if (!lib.isDirectory()) lib.mkdirs();
            log("ensureJavaHome 指向 " + dir.getAbsolutePath()
                    + "，lib/fontconfig.bfc 存在=" + new File(lib, "fontconfig.bfc").isFile());
        } catch (Throwable ignore) {
            if (System.getProperty("java.home") == null) {
                System.setProperty("java.home", ".");
            }
        }
    }

    /** exe（或普通 JVM 下 java 可执行文件）所在目录。 */
    private static File exeDir() {
        try {
            String exe = ProcessHandle.current().info().command().orElse(null);
            if (exe != null) {
                File p = new File(exe).getParentFile();
                if (p != null && p.isDirectory()) return p;
            }
        } catch (Throwable ignore) {
            // 忽略
        }
        return null;
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
}
