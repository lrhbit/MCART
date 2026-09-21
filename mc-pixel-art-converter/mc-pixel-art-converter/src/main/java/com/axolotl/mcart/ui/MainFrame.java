package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.ColorMatcher;
import com.axolotl.mcart.core.DitherMethod;
import com.axolotl.mcart.core.PixelArtGenerator;
import com.axolotl.mcart.core.PreprocessSettings;
import com.axolotl.mcart.core.TexturePreviewRenderer;
import com.axolotl.mcart.export.LitematicExporter;
import com.axolotl.mcart.export.SchemExporter;
import com.axolotl.mcart.export.SchematicExporter;
import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.model.BlockPalette;
import com.axolotl.mcart.model.MapMode;
import com.axolotl.mcart.util.BlockNames;
import com.axolotl.mcart.util.SvgMiniRenderer;
import com.axolotl.mcart.util.TextureManager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 主窗口 — FlatLaf 深色 / 现代扁平专业风格
 *
 * 顶部工具栏（分组 + 分隔线，窄窗口自动换行）
 * 中部左右两个图片查看器（标题栏 + 固定视口 + 滚轮缩放 + 拖动平移）
 * 方块选择在独立的「方块库」对话框（原生复选框 + 纹理卡片 + 搜索）
 */
public class MainFrame extends JFrame {

    private static final int BLOCKS_PER_MAP = 128;
    private static final double MIN_ZOOM = 0.02;
    private static final double MAX_ZOOM = 40.0;
    private static final double ZOOM_STEP = 1.18;

    // 主题色
    private static final Color ACCENT = new Color(74, 125, 199);
    private static final Color HEADER_BG = new Color(49, 50, 54);
    private static final Color CARD_SEL = new Color(43, 60, 86);
    private static final Color CARD_BORDER = new Color(64, 66, 72);
    private static final Color CARD_SEL_BORDER = new Color(96, 140, 210);

    private BufferedImage sourceImage;
    private BufferedImage mcColorImage;
    private BufferedImage mcTextureImage;
    private PixelArtGenerator.Result lastResult;

    // 工具栏控件
    private JComboBox<MapMode> modeCombo;
    private JComboBox<DitherMethod> ditherCombo;
    private JComboBox<String> formatCombo;
    private JComboBox<String> previewModeCombo;
    private JComboBox<String> supportCombo;
    private JSpinner mapWidthSpinner;
    private JSpinner mapHeightSpinner;
    private JLabel sizeInfoLabel;
    private JLabel selectedCountLabel;

    // 查看器
    private ViewerPane sourceViewer;
    private ViewerPane previewViewer;
    private Model3DCanvas model3DCanvas;
    private CardLayout previewCards;
    private JPanel previewContainer;

    private JLabel statusMessage;

    private BlockLibraryDialog blockLibrary;

    private PreprocessSettings preprocessSettings = PreprocessSettings.disabled();
    private PreprocessSettings preprocessSnapshot;
    private JLabel preprocessStatusLabel;
    private PreprocessDialog preprocessDialog;

    public MainFrame() {
        setTitle("MC 地图画生成器");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        adaptToScreen();
        initUI();
    }

    /**
     * 窗口尺寸按当前屏幕宽高的 85% 自适应，并在屏幕居中。
     */
    private void adaptToScreen() {
        // 1) 当前屏幕配置（未显示时回退到默认屏幕）
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
        }
        // 2) DPI 缩放系数（1.0=100%，1.5=150%...）
        AffineTransform tx = gc.getDefaultTransform();
        double scaleX = tx.getScaleX();
        double scaleY = tx.getScaleY();
        // 3) 可用工作区（逻辑坐标，已扣任务栏）
        Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        // 4) 物理 1060×600 -> 逻辑尺寸，并夹到工作区内
        int minW = clamp((int) Math.round(1060 / scaleX), 0, work.width);
        int minH = clamp((int) Math.round(600 / scaleY), 0, work.height);
        setMinimumSize(new Dimension(minW, minH));
        // 5) 初始尺寸：工作区 90%，且不小于最小尺寸
        int w = clamp((int) Math.round(work.width * 0.9), minW, work.width);
        int h = clamp((int) Math.round(work.height * 0.9), minH, work.height);
        setSize(w, h);
        // 6) 在工作区内居中（逻辑坐标）
        setLocation(work.x + (work.width - w) / 2,
                work.y + (work.height - h) / 2);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }


    private void initUI() {
        try { List<Image> iconList = SvgMiniRenderer.loadMultiSizeIconSet("/Logo/logo.svg");setIconImages(iconList); } catch (Exception ignored) {}
        setLayout(new BorderLayout(0, 0));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(0, 0, 0, 0));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildViewArea(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // ============================================================
    // 工具栏
    // ============================================================
    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 6));
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(64, 64, 68)),
                new EmptyBorder(6, 10, 6, 10)));

        JButton openBtn = flatButton("打开图片", true);
        openBtn.addActionListener(e -> chooseImage());

        // 生成设置
        modeCombo = new JComboBox<>(MapMode.values());
        modeCombo.setPrototypeDisplayValue(MapMode.RELIEF);
        modeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof MapMode m) setText(m.label);
                return this;
            }
        });
        ditherCombo = new JComboBox<>(DitherMethod.values());
        ditherCombo.setPrototypeDisplayValue(DitherMethod.FLOYD_STEINBERG);
        ditherCombo.setToolTipText("抖动算法：原始放缩 / Floyd-Steinberg / Atkinson / 有序抖动 / 蓝噪声混合（OKLab双色混合+误差扩散+边缘保护，照片与渐变推荐）");

        // 尺寸
        mapWidthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
        mapHeightSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
        Dimension spinSize = new Dimension(54, 30);
        for (JSpinner s : new JSpinner[]{mapWidthSpinner, mapHeightSpinner}) {
            s.setPreferredSize(spinSize);
            s.setMaximumSize(spinSize);
        }
        sizeInfoLabel = new JLabel();
        sizeInfoLabel.setForeground(new Color(150, 152, 158));
        Runnable updateSize = () -> {
            int w = (int) mapWidthSpinner.getValue();
            int h = (int) mapHeightSpinner.getValue();
            sizeInfoLabel.setText(w * BLOCKS_PER_MAP + "×" + h * BLOCKS_PER_MAP + " 格");
            sizeInfoLabel.setToolTipText("每张地图 " + BLOCKS_PER_MAP + "×" + BLOCKS_PER_MAP + " 格");
        };
        mapWidthSpinner.addChangeListener(e -> updateSize.run());
        mapHeightSpinner.addChangeListener(e -> updateSize.run());
        updateSize.run();

        // 预览 / 格式
        previewModeCombo = new JComboBox<>(new String[]{"方块纹理（俯视）", "地图配色（俯视）", "立体模型（可拖动）"});
        previewModeCombo.setPrototypeDisplayValue("立体模型（可拖动）");
        previewModeCombo.setToolTipText("右侧预览的显示方式；立体模型支持左键旋转、右键平移、滚轮缩放、双击复位");
        previewModeCombo.addActionListener(e -> refreshPreviewMode());
        formatCombo = new JComboBox<>(new String[]{".litematic", ".schem", ".schematic"});
        formatCombo.setPrototypeDisplayValue(".schematic");
        formatCombo.setToolTipText(".litematic = Litematica 投影；.schem = WorldEdit 7+；.schematic = 旧版 MCEdit");

        // 立体模式支撑方块（顶层显示方块的下方填充，地图不可见）
        supportCombo = new JComboBox<>(new String[]{"石头", "圆石", "泥土", "深板岩", "安山岩", "同色方块"});
        supportCombo.setPrototypeDisplayValue("同色方块");
        supportCombo.setToolTipText("仅立体地图画生效：顶层显示方块下方用什么方块填充（地图上不可见）");

        // 方块库
        JButton blockLibBtn = flatButton("方块库", false);
        blockLibBtn.setToolTipText("勾选参与配色的方块种类");
        blockLibBtn.addActionListener(e -> openBlockLibrary());
        selectedCountLabel = new JLabel("已选 0 种");
        selectedCountLabel.setForeground(new Color(150, 152, 158));

        // 图像预处理（亮度/对比度/饱和度 + 背景，对齐 mapartcraft）
        JButton preprocessBtn = flatButton("图像预处理", false);
        preprocessBtn.setToolTipText("量化前调整亮度/对比度/饱和度、铺背景色（默认关闭，不改变原图）");
        preprocessBtn.addActionListener(e -> openPreprocess());
        preprocessStatusLabel = new JLabel("预处理：关");
        preprocessStatusLabel.setForeground(new Color(150, 152, 158));

        // 操作按钮组（作为整体参与换行）
        JButton generateBtn = flatButton("生成预览", true);
        generateBtn.setFont(generateBtn.getFont().deriveFont(Font.BOLD));
        generateBtn.putClientProperty("JButton.buttonType", "roundRect");
        generateBtn.setForeground(Color.WHITE);
        generateBtn.setBackground(ACCENT);
        generateBtn.setToolTipText("生成 MC 方块预览");
        generateBtn.addActionListener(e -> generatePreview());
        JButton exportBtn = flatButton("导出文件", false);
        exportBtn.addActionListener(e -> exportSchematic());

        // —— 按功能区分组，每组带小标题，作为整体参与工具栏换行 ——
        bar.add(toolGroup("文件", openBtn));
        bar.add(toolGroup("画面", modeCombo, new JLabel("抖动"), ditherCombo));
        bar.add(toolGroup("尺寸", new JLabel("地图"), mapWidthSpinner, new JLabel("×"),
                mapHeightSpinner, new JLabel("张"), sizeInfoLabel));
        bar.add(toolGroup("材质", blockLibBtn, selectedCountLabel, new JLabel("填充"), supportCombo));
        bar.add(toolGroup("预处理", preprocessBtn, preprocessStatusLabel));
        bar.add(toolGroup("预览", previewModeCombo));
        bar.add(toolGroup("导出", formatCombo, exportBtn));
        bar.add(toolGroup("操作", generateBtn));

        SwingUtilities.invokeLater(() -> getRootPane().setDefaultButton(generateBtn));
        return bar;
    }

    /**
     * 工具栏功能分组：顶部小号灰色标题 + 一行控件；作为单个组件，
     * 窄窗口时整组一起换行，不会从组中间断开。
     */
    private static JComponent toolGroup(String title, JComponent... comps) {
        JPanel group = new JPanel(new BorderLayout(0, 3));
        group.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(11f));
        t.setForeground(new Color(140, 142, 148));
        t.setBorder(new EmptyBorder(0, 2, 0, 0));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        for (JComponent c : comps) row.add(c);
        group.add(t, BorderLayout.NORTH);
        group.add(row, BorderLayout.CENTER);
        return group;
    }

    private static JButton flatButton(String text, boolean primary) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(6, 14, 6, 14));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        return btn;
    }

    // ============================================================
    // 中部：左右两个图片查看器
    // ============================================================
    private JComponent buildViewArea() {
        sourceViewer = new ViewerPane("原图");
        previewViewer = new ViewerPane("MC 预览（俯视）");

        // 立体模型面板：标题栏（操作提示 + 复位俯视按钮）+ 可交互 3D 画布
        model3DCanvas = new Model3DCanvas();
        JPanel modelPane = new JPanel(new BorderLayout(0, 0));
        modelPane.setBorder(new LineBorder(new Color(64, 64, 68), 1));
        JPanel modelHeader = new JPanel(new BorderLayout());
        modelHeader.setBackground(HEADER_BG);
        modelHeader.setBorder(new EmptyBorder(5, 10, 5, 8));
        JLabel modelTitle = new JLabel("立体模型预览");
        modelTitle.setFont(modelTitle.getFont().deriveFont(Font.BOLD, 12.5f));
        modelHeader.add(modelTitle, BorderLayout.WEST);
        JPanel modelRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        modelRight.setOpaque(false);
        JLabel modelHint = new JLabel("左键旋转 · 右键平移 · 滚轮缩放 · 双击复位");
        modelHint.setForeground(new Color(160, 162, 168));
        JButton resetViewBtn = new JButton("复位俯视");
        resetViewBtn.setMargin(new Insets(2, 10, 2, 10));
        resetViewBtn.putClientProperty("JButton.buttonType", "roundRect");
        resetViewBtn.setFocusPainted(false);
        resetViewBtn.addActionListener(e -> model3DCanvas.resetView());
        modelRight.add(modelHint);
        modelRight.add(resetViewBtn);
        modelHeader.add(modelRight, BorderLayout.EAST);
        modelPane.add(modelHeader, BorderLayout.NORTH);
        modelPane.add(model3DCanvas, BorderLayout.CENTER);

        // 右侧用 CardLayout 在 2D 俯视预览与 3D 模型预览间切换
        previewCards = new CardLayout();
        previewContainer = new JPanel(previewCards);
        previewContainer.add(previewViewer, "2d");
        previewContainer.add(modelPane, "3d");

        // 不用 JSplitPane：等宽两栏，查看器内部图片永远相对自身视口居中
        JPanel area = new JPanel(new GridLayout(1, 2, 8, 0));
        area.setBorder(new EmptyBorder(8, 8, 8, 8));
        area.setBackground(new Color(36, 37, 40));
        area.add(sourceViewer);
        area.add(previewContainer);
        return area;
    }

    private JComponent buildStatusBar() {
        statusMessage = new JLabel(" 就绪：打开图片 → 在「方块库」勾选材料 → 生成预览 → 导出");
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(64, 64, 68)),
                new EmptyBorder(3, 8, 3, 8)));
        bar.add(statusMessage, BorderLayout.WEST);
        JLabel hint = new JLabel("滚轮缩放 · 按住拖动平移 · 双击复位  ");
        hint.setForeground(new Color(140, 142, 148));
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    /**
     * 图片查看器：标题栏（标题 + 缩放比例 + 复位按钮）+ 固定视口
     * 修复重影：视口 SIMPLE_SCROLL_MODE，画布每次整幅清空；
     * 支持滚轮以光标为锚点缩放、按住左键拖动平移、双击复位。
     */
    private class ViewerPane extends JPanel {
        private ImageCanvas canvas;
        private JScrollPane scroll;
        private final JLabel zoomLabel;
        private boolean fitMode = true;

        ViewerPane(String title) {
            super(new BorderLayout(0, 0));
            setBorder(new LineBorder(new Color(64, 64, 68), 1));

            // —— 标题栏 ——
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(HEADER_BG);
            header.setBorder(new EmptyBorder(5, 10, 5, 8));
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.5f));
            header.add(titleLabel, BorderLayout.WEST);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            zoomLabel = new JLabel("适应窗口");
            zoomLabel.setForeground(new Color(160, 162, 168));
            JButton resetBtn = new JButton("复位");
            resetBtn.setMargin(new Insets(2, 10, 2, 10));
            resetBtn.putClientProperty("JButton.buttonType", "roundRect");
            resetBtn.setFocusPainted(false);
            resetBtn.addActionListener(e -> {
                fitMode = true;
                canvas.fitToViewport();
            });
            right.add(zoomLabel);
            right.add(resetBtn);
            header.add(right, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            // —— 视口 ——
            canvas = new ImageCanvas();
            scroll = new JScrollPane(canvas);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(new Color(28, 29, 32));
            // 关键：简单滚动模式，不做位图快移，杜绝缩放/滚动重影
            scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            add(scroll, BorderLayout.CENTER);

            installInteractions();
        }

        private void installInteractions() {
            // 滚轮：以光标位置为锚点缩放
            canvas.addMouseWheelListener(e -> {
                if (canvas.getImage() == null) return;
                fitMode = false;
                double oldZoom = canvas.getZoom();
                double newZoom = oldZoom * (e.getWheelRotation() < 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP);
                newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));

                JViewport vp = scroll.getViewport();
                Rectangle vr = vp.getViewRect();
                Point mp = e.getPoint();
                // 光标对应的图像比例位置（扣除居中偏移）
                double imXRatio = ((double) vr.x + mp.x - canvas.originX())
                        / (canvas.scaledW() == 0 ? 1 : canvas.scaledW());
                double imYRatio = ((double) vr.y + mp.y - canvas.originY())
                        / (canvas.scaledH() == 0 ? 1 : canvas.scaledH());

                canvas.setZoom(newZoom);
                canvas.validate();

                int nx = (int) (imXRatio * canvas.scaledW()) + canvas.originX() - mp.x;
                int ny = (int) (imYRatio * canvas.scaledH()) + canvas.originY() - mp.y;
                nx = Math.max(0, Math.min(nx, Math.max(0, canvas.getWidth() - vr.width)));
                ny = Math.max(0, Math.min(ny, Math.max(0, canvas.getHeight() - vr.height)));
                vp.setViewPosition(new Point(nx, ny));
                vp.repaint();
                updateZoomLabel();
            });

            // 双击复位
            canvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && canvas.getImage() != null) {
                        fitMode = true;
                        canvas.fitToViewport();
                    }
                }
                @Override
                public void mousePressed(MouseEvent e) { panStart = e.getPoint(); }
            });

            // 按住拖动平移
            canvas.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (panStart == null) return;
                    fitMode = false;
                    JViewport vp = scroll.getViewport();
                    Rectangle vr = vp.getViewRect();
                    int nx = vr.x + panStart.x - e.getX();
                    int ny = vr.y + panStart.y - e.getY();
                    nx = Math.max(0, Math.min(nx, Math.max(0, canvas.getWidth() - vr.width)));
                    ny = Math.max(0, Math.min(ny, Math.max(0, canvas.getHeight() - vr.height)));
                    vp.setViewPosition(new Point(nx, ny));
                    panStart = e.getPoint();
                }
            });

            // 窗口尺寸变化时：自适应模式跟随；其余模式也要重算居中
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    canvas.revalidate();
                    canvas.repaint();
                    if (fitMode) canvas.fitToViewport();
                }
            });
        }

        private Point panStart;

        void setImage(BufferedImage img) {
            fitMode = true;
            canvas.setImage(img);
            SwingUtilities.invokeLater(() -> {
                canvas.fitToViewport();
                updateZoomLabel();
            });
        }

        void fit() {
            fitMode = true;
            canvas.fitToViewport();
            updateZoomLabel();
        }

        private void updateZoomLabel() {
            if (canvas.getImage() == null) {
                zoomLabel.setText("—");
            } else if (fitMode) {
                zoomLabel.setText("适应窗口 " + Math.round(canvas.getZoom() * 100) + "%");
            } else {
                zoomLabel.setText(Math.round(canvas.getZoom() * 100) + "%");
            }
        }
    }

    /** 图片画布：每帧整幅清空后按 zoom 绘制，彻底杜绝残影 */
    private static class ImageCanvas extends JComponent {
        private BufferedImage image;
        private double zoom = 1.0;

        ImageCanvas() {
            setOpaque(true);
            setBackground(new Color(28, 29, 32));
        }

        void setImage(BufferedImage img) {
            this.image = img;
            this.zoom = 1.0;
            updateSize();
            revalidate();
            repaint();
        }

        BufferedImage getImage() { return image; }
        double getZoom() { return zoom; }

        /** 缩放后图像的实际像素宽高 */
        int scaledW() { return image == null ? 0 : Math.max(1, (int) Math.round(image.getWidth() * zoom)); }
        int scaledH() { return image == null ? 0 : Math.max(1, (int) Math.round(image.getHeight() * zoom)); }

        /**
         * 图像在画布中的绘制原点：
         * 图像比视口小时居中，比视口大时贴顶/左（交给滚动条）。
         */
        int originX() {
            return Math.max(0, (getWidth() - scaledW()) / 2);
        }
        int originY() {
            return Math.max(0, (getHeight() - scaledH()) / 2);
        }

        void setZoom(double z) {
            this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
            updateSize();
            revalidate();
            repaint();
        }

        void fitToViewport() {
            if (image == null) return;
            Container parent = getParent();
            if (!(parent instanceof JViewport vp)) return;
            int vw = vp.getWidth();
            int vh = vp.getHeight();
            if (vw <= 0 || vh <= 0) return;
            double pad = 24;
            double fit = Math.min((vw - pad) / image.getWidth(), (vh - pad) / image.getHeight());
            setZoom(Math.max(MIN_ZOOM, fit));
        }

        private void updateSize() {
            // 画布至少与视口一样大，保证小图也能居中
            int vw = 0, vh = 0;
            Container parent = getParent();
            if (parent instanceof JViewport vp) {
                vw = vp.getWidth();
                vh = vp.getHeight();
            }
            int w = Math.max(scaledW(), vw);
            int h = Math.max(scaledH(), vh);
            setPreferredSize(new Dimension(Math.max(100, w), Math.max(100, h)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            // 1) 用背景色整幅清空（即使有脏像素也全部覆盖）
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (image == null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(125, 127, 133));
                g2.setFont(g2.getFont().deriveFont(13f));
                String hint = "图片将显示在这里";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, (getWidth() - fm.stringWidth(hint)) / 2, getHeight() / 2);
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int w = scaledW();
            int h = scaledH();
            // 始终相对父容器（视口）居中绘制
            g2.drawImage(image, originX(), originY(), w, h, null);
        }
    }

    // ============================================================
    // 方块库对话框（原生复选框 + 纹理卡片 + 搜索）
    // ============================================================
    private void openBlockLibrary() {
        if (blockLibrary == null) {
            blockLibrary = new BlockLibraryDialog(this);
        }
        blockLibrary.refreshCount();
        blockLibrary.setVisible(true);
        blockLibrary.toFront();
    }

    private void openPreprocess() {
        if (preprocessDialog == null) {
            preprocessDialog = new PreprocessDialog(this); // 非模态：打开时仍可见/可操作主窗口预览
            // 任意参数变化：实时同步并防抖刷新预览（无需点确认）
            preprocessDialog.setOnChange(s -> {
                preprocessSettings = s;
                updatePreprocessStatus();
                scheduleLiveRegen();
            });
            // 关闭：完成=保留当前；取消/叉掉=还原打开前的设置并刷新
            preprocessDialog.setOnClose(applied -> {
                preprocessSettings = applied ? preprocessDialog.getSettings() : preprocessSnapshot;
                updatePreprocessStatus();
                scheduleLiveRegen();
            });
        }
        preprocessSnapshot = preprocessSettings;
        preprocessDialog.load(preprocessSettings);
        preprocessDialog.setVisible(true);
        preprocessDialog.toFront();
    }

    private void updatePreprocessStatus() {
        if (preprocessStatusLabel == null) return;
        if (!preprocessSettings.enabled) {
            preprocessStatusLabel.setText("预处理：关");
        } else {
            preprocessStatusLabel.setText(String.format(
                    "预处理：开（亮%d 对%d 饱%d%s）",
                    preprocessSettings.brightness, preprocessSettings.contrast,
                    preprocessSettings.saturation,
                    preprocessSettings.backgroundMode == PreprocessSettings.BackgroundMode.OFF
                            ? "" : "·背景" + preprocessSettings.backgroundMode.label));
        }
    }

    private List<BlockColor> getSelectedBlocks() {
        return blockLibrary == null ? List.of() : blockLibrary.getSelectedBlocks();
    }

    private class BlockLibraryDialog extends JDialog {
        private final List<BlockCard> cards = new ArrayList<>();
        private final Map<BlockColor.Category, List<BlockCard>> byCategory = new EnumMap<>(BlockColor.Category.class);
        private final Map<BlockColor.Category, JPanel> sectionWraps = new EnumMap<>(BlockColor.Category.class);
        private final JLabel countLabel = new JLabel();
        private JTextField searchField;
        private JPanel cardsList;

        BlockLibraryDialog(JFrame owner) {
            super(owner, "方块库", false);
            setSize(580, 720);
            setLocationRelativeTo(owner);

            JPanel root = new JPanel(new BorderLayout(0, 8));
            root.setBorder(new EmptyBorder(10, 10, 10, 10));

            // 顶部：搜索 + 数量
            JPanel top = new JPanel(new BorderLayout(8, 0));
            searchField = new JTextField();
            searchField.putClientProperty("JTextField.placeholderText", "搜索方块名称…");
            searchField.setMargin(new Insets(6, 8, 6, 8));
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            });
            countLabel.setBorder(new EmptyBorder(0, 6, 0, 6));
            countLabel.setForeground(new Color(160, 162, 168));
            top.add(new JLabel("搜索"), BorderLayout.WEST);
            top.add(searchField, BorderLayout.CENTER);
            top.add(countLabel, BorderLayout.EAST);
            root.add(top, BorderLayout.NORTH);

            // 分类卡片列表
            cardsList = new JPanel();
            cardsList.setLayout(new BoxLayout(cardsList, BoxLayout.Y_AXIS));
            for (BlockColor bc : BlockPalette.fullPalette()) {
                boolean defaultSelected = bc.category() == BlockColor.Category.WOOL
                        || bc.category() == BlockColor.Category.CONCRETE;
                BlockCard card = new BlockCard(bc, defaultSelected);
                cards.add(card);
                byCategory.computeIfAbsent(bc.category(), k -> new ArrayList<>()).add(card);
            }

            BlockColor.Category[] order = {
                    BlockColor.Category.WOOL, BlockColor.Category.CONCRETE,
                    BlockColor.Category.TERRACOTTA, BlockColor.Category.NATURAL,
                    BlockColor.Category.MINERAL, BlockColor.Category.SPECIAL
            };
            for (BlockColor.Category cat : order) {
                List<BlockCard> catCards = byCategory.get(cat);
                if (catCards == null || catCards.isEmpty()) continue;
                JPanel section = buildCategorySection(cat.label, catCards);
                sectionWraps.put(cat, section);
                cardsList.add(section);
            }

            JScrollPane scroll = new JScrollPane(cardsList);
            scroll.setBorder(new LineBorder(CARD_BORDER, 1));
            scroll.getVerticalScrollBar().setUnitIncrement(18);
            scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
            root.add(scroll, BorderLayout.CENTER);

            // 底部按钮
            JPanel bottom = new JPanel(new BorderLayout());
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            JButton allBtn = new JButton("全部勾选");
            JButton noneBtn = new JButton("全部取消");
            JButton closeBtn = new JButton("完成");
            closeBtn.setBackground(ACCENT);
            closeBtn.setForeground(Color.WHITE);
            closeBtn.putClientProperty("JButton.buttonType", "roundRect");
            allBtn.putClientProperty("JButton.buttonType", "roundRect");
            noneBtn.putClientProperty("JButton.buttonType", "roundRect");
            allBtn.addActionListener(e -> setAll(true));
            noneBtn.addActionListener(e -> setAll(false));
            closeBtn.addActionListener(e -> {
                refreshCount();
                setVisible(false);
            });
            btns.add(allBtn);
            btns.add(noneBtn);
            btns.add(closeBtn);
            bottom.add(btns, BorderLayout.EAST);
            root.add(bottom, BorderLayout.SOUTH);

            setContentPane(root);
            refreshCount();
        }

        private JPanel buildCategorySection(String title, List<BlockCard> catCards) {
            JPanel section = new JPanel(new BorderLayout(0, 4));
            section.setOpaque(false);

            // 分类标题行（不用 TitledBorder，更现代）
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.5f));
            titleLabel.setBorder(new EmptyBorder(6, 2, 2, 0));
            header.add(titleLabel, BorderLayout.WEST);

            JPanel miniBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            miniBtns.setOpaque(false);
            JButton selectAll = miniButton("全选");
            JButton clear = miniButton("清空");
            selectAll.addActionListener(e -> catCards.forEach(c -> c.setSelected(true)));
            clear.addActionListener(e -> catCards.forEach(c -> c.setSelected(false)));
            miniBtns.add(selectAll);
            miniBtns.add(clear);
            header.add(miniBtns, BorderLayout.EAST);
            section.add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(0, 2, 6, 6));
            grid.setOpaque(false);
            for (BlockCard c : catCards) grid.add(c);
            section.add(grid, BorderLayout.CENTER);

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(section, BorderLayout.NORTH);
            wrap.setBorder(new EmptyBorder(2, 2, 8, 2));
            return wrap;
        }

        private JButton miniButton(String text) {
            JButton b = new JButton(text);
            b.setFont(b.getFont().deriveFont(11f));
            b.setMargin(new Insets(1, 8, 1, 8));
            b.putClientProperty("JButton.buttonType", "roundRect");
            b.setFocusPainted(false);
            return b;
        }

        private void setAll(boolean v) {
            for (BlockCard c : cards) c.setSelected(v);
        }

        List<BlockColor> getSelectedBlocks() {
            List<BlockColor> result = new ArrayList<>();
            for (BlockCard c : cards) if (c.isSelected()) result.add(c.bc);
            return result;
        }

        void refreshCount() {
            countLabel.setText("已选 " + getSelectedBlocks().size() + " / " + cards.size() + " 种");
            if (selectedCountLabel != null) {
                selectedCountLabel.setText("已选 " + getSelectedBlocks().size() + " 种");
            }
        }

        private void applyFilter() {
            String kw = searchField.getText().trim().toLowerCase();
            for (BlockCard c : cards) {
                String name = BlockNames.of(c.bc.blockId(), c.bc.name());
                boolean match = kw.isEmpty()
                        || name.toLowerCase().contains(kw)
                        || c.bc.blockId().toLowerCase().contains(kw)
                        || c.bc.name().toLowerCase().contains(kw);
                c.setVisible(match);
            }
            // 分类区块：该类无匹配则整体隐藏
            for (Map.Entry<BlockColor.Category, List<BlockCard>> en : byCategory.entrySet()) {
                boolean any = en.getValue().stream().anyMatch(BlockCard::isVisible);
                sectionWraps.get(en.getKey()).setVisible(any);
            }
            cardsList.revalidate();
            cardsList.repaint();
        }
    }

    /**
     * 方块卡片：原生复选框（FlatLaf 原生勾选样式）+ 纹理图标 + 中文名。
     * 点击卡片任意位置都能切换选中；选中时高亮。
     */
    private class BlockCard extends JPanel {
        final BlockColor bc;
        final JCheckBox checkBox;

        BlockCard(BlockColor bc, boolean selected) {
            super(new BorderLayout(6, 0));
            this.bc = bc;
            setBorder(new CompoundBorder(new LineBorder(CARD_BORDER, 1), new EmptyBorder(4, 6, 4, 6)));
            setBackground(new Color(43, 44, 48));

            checkBox = new JCheckBox();
            checkBox.setSelected(selected);
            checkBox.setPreferredSize(new Dimension(22, 24));

            BufferedImage tex = TextureManager.get(bc);
            Image scaled = tex.getScaledInstance(26, 26, Image.SCALE_REPLICATE);
            JLabel iconLabel = new JLabel(new ImageIcon(scaled));
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 4));

            String cn = BlockNames.of(bc.blockId(), bc.name());
            JLabel nameLabel = new JLabel(cn);
            nameLabel.setToolTipText(bc.blockId());

            add(checkBox, BorderLayout.WEST);
            add(iconLabel, BorderLayout.CENTER);
            JPanel east = new JPanel(new BorderLayout());
            east.setOpaque(false);
            east.add(nameLabel, BorderLayout.WEST);
            add(east, BorderLayout.EAST);

            Consumer<Boolean> styler = s -> {
                setBackground(s ? CARD_SEL : new Color(43, 44, 48));
                setBorder(new CompoundBorder(new LineBorder(s ? CARD_SEL_BORDER : CARD_BORDER, 1),
                        new EmptyBorder(4, 6, 4, 6)));
            };
            // 用 ItemListener：用户点击和程序化 setSelected 都能刷新样式/计数
            checkBox.addItemListener(e -> {
                styler.accept(checkBox.isSelected());
                if (blockLibrary != null) blockLibrary.refreshCount();
            });
            styler.accept(selected);

            // 点击卡片非复选框区域也能切换
            MouseAdapter clickToggle = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    checkBox.setSelected(!checkBox.isSelected());
                }
            };
            addMouseListener(clickToggle);
            iconLabel.addMouseListener(clickToggle);
            nameLabel.addMouseListener(clickToggle);
            east.addMouseListener(clickToggle);
        }

        boolean isSelected() { return checkBox.isSelected(); }
        void setSelected(boolean v) {
            checkBox.setSelected(v);
        }
    }

    // ============================================================
    // 业务逻辑
    // ============================================================
    private void chooseImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("图片文件", "png", "jpg", "jpeg", "bmp", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            sourceImage = javax.imageio.ImageIO.read(file);
            sourceViewer.setImage(sourceImage);
            statusMessage.setText(" 已加载：" + file.getName() + "（" +
                    sourceImage.getWidth() + "×" + sourceImage.getHeight() + "）");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载图片失败：" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generatePreview() {
        doGenerate(true, false);
    }

    /**
     * 生成预览核心。
     *
     * @param interactive  true=用户点「生成预览」：缺输入时弹窗、显示等待光标、错误弹窗；
     *                     false=参数变化时的后台实时刷新：条件不足静默返回、不打断用户
     * @param keepPreview  true=保留当前预览页（实时拖动滑块时不打断用户正在看的视图）；
     *                     false=按模式重置默认预览页
     * @return 是否成功生成
     */
    private boolean doGenerate(boolean interactive, boolean keepPreview) {
        if (sourceImage == null) {
            if (interactive) {
                JOptionPane.showMessageDialog(this, "请先打开图片", "提示", JOptionPane.WARNING_MESSAGE);
            }
            return false;
        }
        List<BlockColor> blocks = getSelectedBlocks();
        if (blocks.isEmpty()) {
            if (interactive) {
                JOptionPane.showMessageDialog(this, "请先在「方块库」中至少勾选一种方块",
                        "提示", JOptionPane.WARNING_MESSAGE);
            }
            return false;
        }

        MapMode mode = (MapMode) modeCombo.getSelectedItem();
        DitherMethod dither = (DitherMethod) ditherCombo.getSelectedItem();
        // 调色板按模式展开 shade：平面仅 shade 1，立体为 shade 0/1/2
        ColorMatcher matcher = new ColorMatcher(blocks, mode);
        int mapsW = (int) mapWidthSpinner.getValue();
        int mapsH = (int) mapHeightSpinner.getValue();
        // 输出严格按地图张数：宽=mapsW*128、高=mapsH*128，比例由用户设置决定
        int targetWidth = mapsW * BLOCKS_PER_MAP;
        int targetHeight = mapsH * BLOCKS_PER_MAP;

        if (interactive) setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            PixelArtGenerator generator =
                    new PixelArtGenerator(matcher, dither, mode, selectedSupportBlock(), preprocessSettings);
            // 原图直接拉伸缩放到目标张数尺寸（完整保留内容，不裁剪，比例不一致时会拉伸）
            PixelArtGenerator.Result result = generator.generate(sourceImage, targetWidth, targetHeight);
            lastResult = result;

            // 地图配色俯视预览：previewPixels 即游戏内地图物品上实际显示的颜色（含 shade）
            mcColorImage = new BufferedImage(
                    result.width, result.length, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < result.length; y++) {
                for (int x = 0; x < result.width; x++) {
                    mcColorImage.setRGB(x, y, 0xFF000000 | result.previewPixels[y][x]);
                }
            }

            // 方块纹理俯视预览
            mcTextureImage = TexturePreviewRenderer.render(result);

            // 可交互 3D 模型
            model3DCanvas.setResult(result);

            if (!keepPreview) {
                // 立体默认看地图实际配色（俯视），平面默认看方块纹理
                previewModeCombo.setSelectedIndex(mode == MapMode.RELIEF ? 1 : 0);
            }
            refreshPreviewMode();

            String reliefInfo = mode == MapMode.RELIEF
                    ? String.format("，模型高度 %d 格", result.height) : "";
            String pp = preprocessSettings.enabled ? "，已应用预处理" : "";
            statusMessage.setText(String.format(" 生成完成：%d×%d 格（%d×%d 张地图），使用方块类型 %d 种，%s，%s%s%s",
                    result.width, result.length, mapsW, mapsH,
                    result.palette.size(), mode.label, dither.label, reliefInfo, pp));
            return true;
        } catch (Exception ex) {
            if (interactive) {
                JOptionPane.showMessageDialog(this, "生成失败：" + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
            ex.printStackTrace();
            return false;
        } finally {
            if (interactive) setCursor(Cursor.getDefaultCursor());
        }
    }

    /** 实时刷新防抖定时器（拖动滑块时合并高频事件，停顿约 110ms 后重算一次） */
    private Timer liveTimer;

    private void scheduleLiveRegen() {
        if (liveTimer == null) {
            liveTimer = new Timer(110, e -> doGenerate(false, true));
            liveTimer.setRepeats(false);
        }
        // 已有结果才实时刷新；从未生成过则等用户点「生成预览」
        if (lastResult != null) liveTimer.restart();
    }

    /** 工具栏选择的支撑方块 ID */
    private String selectedSupportBlock() {
        return switch (supportCombo.getSelectedIndex()) {
            case 1 -> "minecraft:cobblestone";
            case 2 -> "minecraft:dirt";
            case 3 -> "minecraft:deepslate";
            case 4 -> "minecraft:andesite";
            case 5 -> "#same"; // 与该列顶层显示方块相同
            default -> "minecraft:stone";
        };
    }

    private void refreshPreviewMode() {
        if (lastResult == null) return;
        int mode = previewModeCombo.getSelectedIndex();
        if (mode == 2) {
            // 可交互 3D 模型（默认/复位为正俯视）
            model3DCanvas.setResult(lastResult);
            previewCards.show(previewContainer, "3d");
        } else {
            BufferedImage img = mode == 0 ? mcTextureImage : mcColorImage;
            if (img != null) previewViewer.setImage(img);
            previewCards.show(previewContainer, "2d");
        }
    }

    private void exportSchematic() {
        if (lastResult == null) {
            JOptionPane.showMessageDialog(this, "请先生成预览", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        int fmt = formatCombo.getSelectedIndex();
        String ext = switch (fmt) {
            case 0 -> ".litematic";
            case 1 -> ".schem";
            default -> ".schematic";
        };
        chooser.setFileFilter(new FileNameExtensionFilter(ext + " 文件", ext.substring(1)));
        chooser.setSelectedFile(new File("pixel-art" + ext));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            Path path = file.toPath();
            switch (fmt) {
                case 0 -> LitematicExporter.export(lastResult, path);
                case 1 -> SchemExporter.export(lastResult, path);
                default -> SchematicExporter.export(lastResult, path);
            }
            statusMessage.setText(" 已导出：" + file.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "导出成功！\n" + file.getAbsolutePath(),
                    "完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "导出失败：" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}
