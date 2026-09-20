import com.axolotl.mcart.core.*;
import com.axolotl.mcart.export.*;
import com.axolotl.mcart.model.*;
import com.axolotl.mcart.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * native-image 元数据采集 / 原生镜像自检程序（默认包，仅构建期使用，不打包进业务 jar）。
 *
 * 同时用于：
 *  - JVM 下配合 -agentlib:native-image-agent 采集反射/资源/JNI 元数据；
 *  - 编译进原生镜像后做全功能自检（无人工交互，自动走完文件选择/生成/导出并 System.exit）。
 *
 * 覆盖：FlatLaf、全部 Swing 控件、JFileChooser 真实弹出与选择（打开图片链路）、
 * JColorChooser、ImageIO 读写 png/jpg/gif/bmp、全部方块纹理、两模式 × 九抖动、
 * 三种导出、3D 预览、图像预处理。
 */
public class NativeTrace {
    public static void main(String[] args) throws Exception {
        ensureJavaHome();
        FlatDarkLaf.setup();
        touchSwingControls();

        MainFrame frame = new MainFrame();
        frame.setSize(1280, 820);
        frame.setVisible(true);

        // ---- 准备测试图片（多种格式，覆盖“打开图片” ImageIO 链路）----
        Path tmp = Files.createTempDirectory("natrace");
        BufferedImage img = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 160, 90);
        g.setColor(new Color(30, 90, 220)); g.fillOval(25, 5, 80, 80);
        g.setColor(Color.RED); g.fillRect(0, 0, 12, 90);
        g.dispose();
        File png = tmp.resolve("sample.png").toFile();
        File jpg = tmp.resolve("sample.jpg").toFile();
        File bmp = tmp.resolve("sample.bmp").toFile();
        File gif = tmp.resolve("sample.gif").toFile();
        ImageIO.write(img, "png", png);
        ImageIO.write(img, "jpg", jpg);
        ImageIO.write(img, "bmp", bmp);
        ImageIO.write(img, "gif", gif);
        for (File f : new File[]{png, jpg, bmp, gif}) {
            BufferedImage r = ImageIO.read(f);
            if (r == null) throw new IllegalStateException("ImageIO 无法读取: " + f);
        }

        // ---- 真实弹出“打开图片”文件选择器，并自动选中文件确认（覆盖 FileChooser UI）----
        JFileChooser fc = new JFileChooser(tmp.toFile());
        fc.setFileFilter(new FileNameExtensionFilter("图片文件", "png", "jpg", "jpeg", "bmp", "gif"));
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        Timer approve = new Timer(500, e -> {
            fc.setSelectedFile(png);
            fc.approveSelection();
        });
        approve.setRepeats(false);
        approve.start();
        int rv = fc.showOpenDialog(frame);
        if (rv == JFileChooser.APPROVE_OPTION) {
            BufferedImage opened = ImageIO.read(fc.getSelectedFile());
            if (opened == null) throw new IllegalStateException("选中后 ImageIO.read 返回 null");
        }

        // ---- 保存文件选择器（导出链路同款）----
        JFileChooser save = new JFileChooser(tmp.toFile());
        save.setDialogType(JFileChooser.SAVE_DIALOG);
        Timer approve2 = new Timer(400, e -> {
            save.setSelectedFile(tmp.resolve("out.litematic").toFile());
            save.approveSelection();
        });
        approve2.setRepeats(false);
        approve2.start();
        save.showSaveDialog(frame);

        // ---- 颜色选择器（覆盖 ColorChooser UI / 调色板反射）----
        JColorChooser cc = new JColorChooser(Color.GRAY);
        JDialog ccd = JColorChooser.createDialog(frame, "颜色", false, cc, null, null);
        Timer closeCc = new Timer(400, e -> ccd.dispose());
        closeCc.setRepeats(false);
        closeCc.start();
        ccd.setVisible(true);

        setField(frame, "sourceImage", img);
        inv(frame, "openBlockLibrary");   // 加载全部 84 张纹理 + 卡片 UI
        inv(frame, "openPreprocess");     // 预处理面板

        @SuppressWarnings("unchecked")
        JComboBox<MapMode> modeCombo = (JComboBox<MapMode>) getField(frame, "modeCombo");
        @SuppressWarnings("unchecked")
        JComboBox<DitherMethod> ditherCombo = (JComboBox<DitherMethod>) getField(frame, "ditherCombo");
        Method doGenerate = MainFrame.class.getDeclaredMethod("doGenerate", boolean.class, boolean.class);
        doGenerate.setAccessible(true);
        for (MapMode mm : MapMode.values()) {
            for (DitherMethod dm : DitherMethod.values()) {
                modeCombo.setSelectedItem(mm);
                ditherCombo.setSelectedItem(dm);
                doGenerate.invoke(frame, false, true);
            }
        }

        @SuppressWarnings("unchecked")
        JComboBox<String> previewCombo = (JComboBox<String>) getField(frame, "previewModeCombo");
        previewCombo.setSelectedIndex(2);
        inv(frame, "refreshPreviewMode");
        Object canvas = getField(frame, "model3DCanvas");
        canvas.getClass().getMethod("resetView").invoke(canvas);

        frame.revalidate();
        BufferedImage shot = new BufferedImage(1280, 820, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = shot.createGraphics();
        frame.printAll(pg);
        pg.dispose();

        PixelArtGenerator.Result r = (PixelArtGenerator.Result) getField(frame, "lastResult");
        LitematicExporter.export(r, tmp.resolve("a.litematic"));
        SchemExporter.export(r, tmp.resolve("a.schem"));
        SchematicExporter.export(r, tmp.resolve("a.schematic"));

        BufferedImage ti = TexturePreviewRenderer.render(r);
        ImageIO.write(ti, "png", tmp.resolve("t.png").toFile());
        ImageIO.read(tmp.resolve("t.png").toFile());

        for (PreprocessSettings.BackgroundMode bm : PreprocessSettings.BackgroundMode.values()) {
            PreprocessSettings ps = new PreprocessSettings(true, 112, 108, 124, bm,
                    PreprocessSettings.DEFAULT_BACKGROUND_RGB);
            ColorMatcher matcher = new ColorMatcher(BlockPalette.fullPalette(), MapMode.FLAT);
            new PixelArtGenerator(matcher, DitherMethod.BLUE_NOISE, MapMode.FLAT, "minecraft:stone", ps)
                    .generate(img, 64, 48);
        }

        new JOptionPane("x", JOptionPane.INFORMATION_MESSAGE);

        Thread.sleep(700);
        System.out.println("NATIVE TRACE DONE");
        System.exit(0);
    }

    /** 与 Main.ensureJavaHome 相同的原生镜像 java.home 自举（自检镜像独立运行时需要）。 */
    private static void ensureJavaHome() {
        String h = System.getProperty("java.home");
        if (h != null && Files.isDirectory(Path.of(h))) {
            try { Files.createDirectories(Path.of(h, "lib")); } catch (Exception ignore) {}
            return;
        }
        try {
            String exe = ProcessHandle.current().info().command().orElse(null);
            if (exe != null) {
                Path home = Path.of(exe).toAbsolutePath().getParent();
                Files.createDirectories(home.resolve("lib"));
                System.setProperty("java.home", home.toString());
                return;
            }
        } catch (Exception ignore) {}
        try {
            Path cwd = Path.of("").toAbsolutePath();
            Files.createDirectories(cwd.resolve("lib"));
            System.setProperty("java.home", cwd.toString());
        } catch (Exception ignore) {}
    }

    private static void touchSwingControls() {        JComponent[] cs = {
            new JButton(), new JToggleButton(), new JCheckBox(), new JRadioButton(),
            new JComboBox<>(), new JSpinner(), new JSlider(), new JTextField(), new JTextArea(),
            new JScrollPane(), new JList<>(), new JTree(), new JTable(2, 2), new JTabbedPane(),
            new JProgressBar(), new JMenuBar(), new JMenu(), new JMenuItem(), new JPopupMenu(),
            new JToolBar(), new JSeparator(), new JPanel(), new JLabel(), new JPasswordField(),
            new JFormattedTextField(), new JScrollBar(), new JCheckBoxMenuItem(), new JRadioButtonMenuItem()
        };
        for (JComponent c : cs) c.getUI();
    }

    private static Object getField(Object o, String n) throws Exception {
        Field f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        return f.get(o);
    }
    private static void setField(Object o, String n, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        f.set(o, v);
    }
    private static void inv(Object o, String n) throws Exception {
        Method m = o.getClass().getDeclaredMethod(n);
        m.setAccessible(true);
        m.invoke(o);
    }
}
