import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Windows 原生镜像元数据采集器。
 *
 * 在 GraalVM 自带 JDK 下以 JVM 方式运行，配合 native-image-agent 采集运行期才暴露
 * 的元数据：AWT/Swing 的 JNI 绑定、UI 委托类、资源、字体、ImageIO 全格式（含
 * JPEG）、动态代理，以及真实交互路径。
 *
 * 真实交互不依赖屏幕坐标（CI runner 无桌面会话），而是用 dispatchEvent 手动派发
 * 鼠标滚轮、拖拽、点击等事件，确保滚轮处理、spinner 编辑、列表滚动等全部被采集。
 * 每个步骤独立捕获异常，单个组件失败不影响整体采集。
 */
public final class NativeMetadataCollector {

    private static final List<String> REPORT = new ArrayList<>();

    public static void main(String[] args) {
        long started = System.currentTimeMillis();
        note("采集开始，Java " + System.getProperty("java.version") + " / " + System.getProperty("os.name"));

        // agent 需要进程正常退出才会落盘配置，守护线程兜底
        Thread guard = new Thread(() -> {
            try {
                Thread.sleep(200_000);
            } catch (InterruptedException ignored) {
                return;
            }
            finish(started);
        }, "collector-guard");
        guard.setDaemon(true);
        guard.start();

        step("外观主题", NativeMetadataCollector::collectLookAndFeels);
        step("AWT 环境", NativeMetadataCollector::collectAwtEnvironment);
        step("字体", NativeMetadataCollector::collectFonts);
        step("图像编解码", NativeMetadataCollector::collectImageIo);
        step("Swing 组件", NativeMetadataCollector::collectComponents);
        step("标准对话框", NativeMetadataCollector::collectDialogs);
        step("UIManager 默认值", NativeMetadataCollector::collectUiDefaults);
        step("系统集成", NativeMetadataCollector::collectSystemIntegration);
        step("真实交互采集", NativeMetadataCollector::collectRealInteraction);

        finish(started);
    }

    // ---------------------------------------------------------------- 各步骤

    private static void collectLookAndFeels() {
        FlatLightLaf.setup();
        FlatDarkLaf.setup();
        note("FlatLaf 明暗主题已加载");
    }

    private static void collectAwtEnvironment() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        note("无头模式: " + GraphicsEnvironment.isHeadless());
        for (GraphicsDevice d : ge.getScreenDevices()) {
            note("显示设备 " + d.getIDstring() + " 类型 " + d.getType());
            for (DisplayMode m : d.getDisplayModes()) {
                note("  分辨率 " + m.getWidth() + "x" + m.getHeight());
            }
        }
        Toolkit tk = Toolkit.getDefaultToolkit();
        tk.getScreenSize();
        tk.getScreenResolution();
        note("AWT Toolkit 已初始化");

        // 采集输入法组合控制路径：数字输入框聚焦时会临时关闭 IME 组合
        try {
            java.awt.im.InputContext ic = java.awt.im.InputContext.getInstance();
            ic.setCompositionEnabled(false);
            ic.isCompositionEnabled();
            ic.setCompositionEnabled(true);
            note("输入法组合控制已采集");
        } catch (Throwable t) {
            note("输入法组合控制不可用，跳过: " + t);
        }
    }

    private static void collectFonts() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] families = ge.getAvailableFontFamilyNames();
        note("可用字体族数: " + families.length);
        for (int i = 0; i < Math.min(6, families.length); i++) {
            new Font(families[i], Font.PLAIN, 12);
        }
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.TRACKING, 0.02f);
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        f.deriveFont(attributes);
    }

    private static void collectImageIo() {
        String[] formats = {"png", "jpg", "jpeg", "gif", "bmp", "wbmp"};
        for (String format : formats) {
            note("读 " + format + ": " + ImageIO.getImageReadersBySuffix(format).hasNext()
                    + " 写 " + format + ": " + ImageIO.getImageWritersBySuffix(format).hasNext());
        }
        try {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            BufferedImage back = ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
            note("PNG 往返: " + back.getWidth() + "x" + back.getHeight());
        } catch (Throwable t) {
            note("PNG 往返失败: " + t);
        }
        // 真实 JPEG 编解码：触发 JPEGImageReader/Writer 的 JNI 回调元数据
        try {
            BufferedImage rgb = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            Graphics2D gg = rgb.createGraphics();
            gg.setColor(Color.RED); gg.fillRect(0, 0, 8, 16);
            gg.setColor(Color.BLUE); gg.fillRect(8, 0, 8, 16);
            gg.dispose();
            ByteArrayOutputStream jpg = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", jpg);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpg.toByteArray()));
            note("JPEG 往返: " + decoded.getWidth() + "x" + decoded.getHeight());
            try (javax.imageio.stream.ImageInputStream iis = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(jpg.toByteArray()))) {
                java.util.Iterator<javax.imageio.ImageReader> it = ImageIO.getImageReaders(iis);
                if (it.hasNext()) {
                    javax.imageio.ImageReader reader = it.next();
                    reader.setInput(iis);
                    reader.readRaster(0, null);
                    reader.dispose();
                    note("JPEG Raster 解码完成");
                }
            }
        } catch (Throwable t) {
            note("JPEG 往返失败: " + t);
        }
    }

    private static void collectComponents() {
        try {
            SwingUtilities.invokeAndWait(NativeMetadataCollector::buildComponentShowcase);
        } catch (Throwable t) {
            note("组件采集异常: " + t);
        }
    }

    private static void buildComponentShowcase() {
        JFrame frame = new JFrame("组件采集");
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        root.add(new JButton("按钮"));
        root.add(new JToggleButton("开关"));
        root.add(new JCheckBox("复选框", true));
        root.add(new JRadioButton("单选"));
        root.add(new JComboBox<>(new String[]{"选项一", "选项二", "选项三"}));
        root.add(new JSpinner(new SpinnerNumberModel(2, 1, 8, 1)));
        root.add(new JTextField("文本框", 12));
        root.add(new JPasswordField("密码", 12));
        JTextArea area = new JTextArea("多行文本", 3, 12);
        root.add(new JScrollPane(area));
        root.add(new JSlider(0, 100, 50));
        root.add(new JProgressBar(0, 100) {{ setValue(60); }});
        root.add(new JTable(new DefaultTableModel(new Object[]{"列A", "列B"}, 3)));
        JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("根")));
        root.add(new JScrollPane(tree));
        root.add(new JList<>(new String[]{"a", "b", "c"}));
        root.add(new JSeparator());
        root.add(new JFormattedTextField("格式化文本"));
        JSpinner labeled = new JSpinner();
        labeled.setBorder(new TitledBorder("微调"));
        root.add(labeled);

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("图片", "png", "jpg"));
        root.add(chooser);

        frame.setContentPane(new JScrollPane(root));
        frame.pack();
        // 不真正 setVisible，仅触发组件与 UI 委托的类加载
        frame.createBufferStrategy(1);
        for (Component c : root.getComponents()) {
            c.addNotify();
        }
        note("组件展示已构建，组件数: " + root.getComponentCount());
    }

    private static void collectDialogs() {
        // 只触发对话框类加载，不调用会阻塞的 show 方法（CI 无人工交互）
        try {
            new JDialog();
            new JOptionPane("消息", JOptionPane.INFORMATION_MESSAGE);
            new JOptionPane("输入", JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.OK_CANCEL_OPTION);
            new JColorChooser();
            Class.forName("javax.swing.JFileChooser");
            Class.forName("javax.swing.plaf.basic.BasicOptionPaneUI");
            note("标准对话框类已加载");
        } catch (Throwable t) {
            note("对话框采集异常: " + t);
        }
    }

    private static void collectUiDefaults() {
        UIDefaults defaults = UIManager.getDefaults();
        int count = 0;
        for (Map.Entry<Object, Object> e : defaults.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s && s.contains("UI")) {
                try {
                    Class.forName(s);
                    count++;
                } catch (Throwable ignored) {
                }
            }
        }
        note("UIManager 中解析的 UI 类数: " + count);
    }

    private static void collectSystemIntegration() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection("x"), null);
            Desktop.getDesktop();
            note("系统剪贴板/桌面集成已采集");
        } catch (Throwable t) {
            note("系统集成采集异常: " + t);
        }
    }

    // ------------------------------------------------------ 真实交互采集（核心）

    private static Robot ROBOT;

    private static void collectRealInteraction() {
        try {
            // 定时关闭可能弹出的模态对话框，保证交互流程不被阻塞
            Timer closer = new Timer(600, e -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof JDialog d && d.isModal() && d.isVisible()) {
                        d.setVisible(false);
                    }
                    MenuSelectionManager.defaultManager().clearSelectedPath();
                }
            });
            closer.setRepeats(true);
            closer.start();

            // 反射启动真实 Main（其内部自行 invokeLater 创建窗口）
            try {
                Class<?> main = Class.forName("com.axolotl.mcart.Main");
                Method m = main.getMethod("main", String[].class);
                m.invoke(null, (Object) new String[0]);
            } catch (Throwable t) {
                note("反射启动 Main 失败: " + t);
            }

            // 在普通线程等待主窗口，不能阻塞 EDT，否则窗口创建任务无法执行
            JFrame target = null;
            for (int i = 0; i < 60 && target == null; i++) {
                final JFrame[] found = {null};
                SwingUtilities.invokeAndWait(() -> {
                    for (Window w : Window.getWindows()) {
                        if (w instanceof JFrame f && f.isVisible()) { found[0] = f; break; }
                    }
                });
                target = found[0];
                if (target == null) {
                    Thread.sleep(200);
                }
            }
            if (target == null) {
                note("未等到主窗口，跳过真实交互");
                closer.stop();
                return;
            }
            final JFrame window = target;

            // 让窗口处于普通状态、置前并获取焦点，Robot 的 OS 级输入才能投递进去
            SwingUtilities.invokeAndWait(() -> {
                window.setExtendedState(JFrame.NORMAL);
                window.toFront();
                window.requestFocus();
            });
            Thread.sleep(1000);

            ROBOT = new Robot();
            ROBOT.setAutoWaitForIdle(true);
            ROBOT.setAutoDelay(30);
            note("Robot 已创建，开始真实 OS 级交互（键盘 / 滚轮 / 拖拽）");

            for (int round = 0; round < 2; round++) {
                // EDT 上只做只读的坐标计算，构建待执行任务；Robot 不能在 EDT 上调用
                final List<RobotTask> tasks = new ArrayList<>();
                SwingUtilities.invokeAndWait(() -> {
                    List<Component> all = new ArrayList<>();
                    for (Window w : Window.getWindows()) {
                        if (w.isVisible()) collectAll(w, all);
                    }
                    for (Component c : all) {
                        buildTask(c, tasks);
                    }
                });
                note("第 " + (round + 1) + " 轮：待执行 Robot 任务 " + tasks.size() + " 个");
                // 工作线程上用 Robot 逐个执行真实 OS 级事件
                for (RobotTask t : tasks) {
                    executeTask(t);
                }
                // 关闭本轮弹出的非主窗口，避免对话框无限累积
                SwingUtilities.invokeAndWait(() -> {
                    for (Window w : Window.getWindows()) {
                        if (w != window && w.isVisible()) {
                            w.setVisible(false);
                            w.dispose();
                        }
                    }
                });
            }
            closer.stop();
            note("Robot 真实交互采集完成");
        } catch (Throwable t) {
            note("真实交互采集异常: " + t);
        }
    }

    /** Robot 待执行任务：坐标在 EDT 上提前算好，执行阶段不再触碰 Swing 读取。 */
    private static final class RobotTask {
        static final int WHEEL = 1;
        static final int SPINNER = 2;
        static final int DRAG = 3;
        int kind;
        int x;
        int y;
        int x2;
        int y2;
        String label;
        Component spinnerRef;
        Object original;
    }

    /** 纯树递归：收集容器内所有组件。 */
    private static void collectAll(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                collectAll(child, out);
            }
        }
    }

    /**
     * EDT 上调用：只做只读的坐标计算与类型判断，构建待执行任务，不触碰 Robot。
     * 一个组件可能同时需要滚轮与拖拽（如 ImageCanvas），因此可产出多个任务。
     */
    private static void buildTask(Component c, List<RobotTask> out) {
        // 跳过标题栏系统窗口按钮（最小化/最大化/还原/关闭），避免真实改变窗口状态
        if (c.getClass().getName().startsWith("com.formdev.flatlaf.ui.FlatTitlePane$")) {
            return;
        }
        // 跳过标签页关闭按钮 "×"，避免反复关闭/重建文档
        if (c instanceof AbstractButton ab && "×".equals(ab.getText())) {
            return;
        }
        try {
            if (!c.isShowing()) return;
            Point loc = c.getLocationOnScreen();
            Dimension size = c.getSize();
            int cx = loc.x + Math.min(20, Math.max(6, size.width / 2));
            int cy = loc.y + Math.min(20, Math.max(6, size.height / 2));

            // 1) 滚轮：JScrollPane 与任何带滚轮监听器的组件（列表滚动 / 画布缩放）
            if (c instanceof JScrollPane || hasMouseWheelListeners(c)) {
                RobotTask t = new RobotTask();
                t.kind = RobotTask.WHEEL;
                t.x = cx;
                t.y = cy;
                t.label = c.getClass().getSimpleName();
                out.add(t);
            }

            // 2) JSpinner：记录编辑器点击坐标与原值，执行阶段真实键盘输入并提交
            if (c instanceof JSpinner spinner) {
                JComponent editor = spinner.getEditor();
                Point base = editor.isShowing() ? editor.getLocationOnScreen() : loc;
                RobotTask t = new RobotTask();
                t.kind = RobotTask.SPINNER;
                t.x = base.x + Math.min(25, Math.max(10, editor.getWidth() / 2));
                t.y = base.y + Math.max(6, editor.getHeight() / 2);
                t.spinnerRef = spinner;
                t.original = spinner.getValue();
                t.label = c.getClass().getSimpleName();
                out.add(t);
            }

            // 3) 拖拽：带鼠标移动监听器的画布（平移）。JScrollPane 自身不在此处理
            if (c instanceof JComponent && !(c instanceof JScrollPane)
                    && hasMouseMotionListeners(c)) {
                RobotTask t = new RobotTask();
                t.kind = RobotTask.DRAG;
                t.x = cx;
                t.y = cy;
                t.x2 = cx + 34;
                t.y2 = cy + 24;
                t.label = c.getClass().getSimpleName();
                out.add(t);
            }
        } catch (Throwable t) {
            note("构建任务异常(" + c.getClass().getSimpleName() + "): " + t);
        }
    }

    /** 工作线程上调用：用 Robot 生成真实 OS 级事件，经过原生窗口过程触发完整 JNI 链路。 */
    private static void executeTask(RobotTask t) {
        try {
            switch (t.kind) {
                case RobotTask.WHEEL -> {
                    ROBOT.mouseMove(t.x, t.y);
                    ROBOT.delay(120);
                    ROBOT.mouseWheel(3);
                    ROBOT.delay(120);
                    ROBOT.mouseWheel(-3);
                    note("Robot 滚轮 " + t.label);
                }
                case RobotTask.SPINNER -> {
                    ROBOT.mouseMove(t.x, t.y);
                    ROBOT.delay(100);
                    ROBOT.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    ROBOT.delay(60);
                    ROBOT.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    ROBOT.delay(250);
                    // 先全选原有内容，避免新数字与原值拼接成超范围数字
                    ROBOT.keyPress(KeyEvent.VK_CONTROL);
                    ROBOT.delay(40);
                    ROBOT.keyPress(KeyEvent.VK_A);
                    ROBOT.delay(40);
                    ROBOT.keyRelease(KeyEvent.VK_A);
                    ROBOT.delay(40);
                    ROBOT.keyRelease(KeyEvent.VK_CONTROL);
                    ROBOT.delay(150);
                    ROBOT.keyPress(KeyEvent.VK_3);
                    ROBOT.delay(40);
                    ROBOT.keyRelease(KeyEvent.VK_3);
                    ROBOT.delay(150);
                    ROBOT.keyPress(KeyEvent.VK_ENTER);
                    ROBOT.delay(40);
                    ROBOT.keyRelease(KeyEvent.VK_ENTER);
                    ROBOT.delay(250);
                    final Object[] after = {null};
                    SwingUtilities.invokeAndWait(() -> after[0] = ((JSpinner) t.spinnerRef).getValue());
                    note("Robot spinner 编辑：原值=" + t.original + " 提交后=" + after[0]);
                    if (t.original instanceof Integer n) {
                        SwingUtilities.invokeAndWait(() -> ((JSpinner) t.spinnerRef).setValue(n));
                    }
                }
                case RobotTask.DRAG -> {
                    ROBOT.mouseMove(t.x, t.y);
                    ROBOT.delay(100);
                    ROBOT.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    ROBOT.delay(80);
                    ROBOT.mouseMove(t.x + 18, t.y + 14);
                    ROBOT.delay(80);
                    ROBOT.mouseMove(t.x2, t.y2);
                    ROBOT.delay(80);
                    ROBOT.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    ROBOT.delay(120);
                    note("Robot 拖拽 " + t.label);
                }
            }
        } catch (Throwable e) {
            note("执行任务异常(" + t.label + "): " + e);
        }
    }

    private static boolean hasMouseWheelListeners(Component c) {
        try {
            return c.getMouseWheelListeners().length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasMouseMotionListeners(Component c) {
        try {
            return c.getMouseMotionListeners().length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- 基础设施

    private static void step(String name, Runnable r) {
        try {
            r.run();
            note("【步骤完成】" + name);
        } catch (Throwable t) {
            note("【步骤异常】" + name + " -> " + t);
        }
    }

    private static void note(String s) {
        REPORT.add(s);
        System.out.println("[metadata] " + s);
    }

    private static void finish(long started) {
        note("采集完成，耗时 " + (System.currentTimeMillis() - started) + " ms，共 " + REPORT.size() + " 条记录");
        System.out.flush();
        System.exit(0);
    }
}
