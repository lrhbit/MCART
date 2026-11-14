package com.axolotl.mcart.ui;

import com.axolotl.mcart.Main;
import com.axolotl.mcart.core.ColorMatcher;
import com.axolotl.mcart.core.PixelArtGenerator;
import com.axolotl.mcart.core.PreprocessSettings;
import com.axolotl.mcart.core.QualityChecker;
import com.axolotl.mcart.core.TexturePreviewRenderer;
import com.axolotl.mcart.export.ExportFormat;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.io.ProjectIO;
import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.model.MapMode;
import com.axolotl.mcart.model.PaletteService;
import com.axolotl.mcart.model.Project;
import com.axolotl.mcart.model.ProjectState;
import com.axolotl.mcart.util.AppConfig;
import com.axolotl.mcart.util.SvgMiniRenderer;
import com.axolotl.mcart.util.TextureManager;
import com.axolotl.mcart.ui.titlebar.AppTitleBar;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * 主窗口控制器（专业版）：多标签工程 + IntelliJ New UI 标题栏 +
 * 左右图标工具栏 + 原图 / MC 预览双查看器 + 状态栏。
 *
 * 职责划分：本类只保留工程状态与业务编排（IO / 生成 / 导出 / 对话框入口）；
 * 界面组件分别由 {@link TitleBarHost}、{@link CenterPane}、{@link ControlRibbon}、
 * {@link SideBars}、{@link StatusBar}、{@link ViewerPane} 等类承担。
 * 所有工程状态存于 {@link Project}，控件只是当前工程的视图；
 * 切换标签时把控件状态保存 / 恢复。
 */
public class MainFrame extends JFrame {

    private final List<Project> docs = new ArrayList<>();
    private int active = -1;
    private boolean syncing; // 把控件值写入工程 / 从工程载入控件时抑制监听器

    private AppTitleBar titleBar;
    private CenterPane center;
    private ControlRibbon ribbon;
    private StatusBar statusBar;

    private BlockLibraryDialog blockLibrary;
    private WarningsDialog warningsDialog;

    private Timer liveTimer;

    public MainFrame() {
        setTitle(I18n.t("app.title"));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { onExit(); }
        });
        adaptToScreen();
        initUI();
        newProject(); // 启动时一个空白工程
        installScaleListener(); // 系统缩放 / 跨屏后自动重排重绘
    }

    // ============================================================ 内部状态

    Project cur() { return active >= 0 && active < docs.size() ? docs.get(active) : null; }
    int activeIndex() { return active; }
    List<Project> docs() { return docs; }

    private void adaptToScreen() {
        updateMinimumSize();
        Dimension min = getMinimumSize();
        Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = UiTheme.clamp((int) Math.round(work.width * 0.9), min.width, work.width);
        int h = UiTheme.clamp((int) Math.round(work.height * 0.9), min.height, work.height);
        setSize(w, h);
        setLocation(work.x + (work.width - w) / 2, work.y + (work.height - h) / 2);
    }

    /** 按当前显示器 DPI 重新计算最小窗口尺寸（缩放变化时单独调用）。 */
    private void updateMinimumSize() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = gc.getDefaultTransform();
        double scaleX = tx.getScaleX(), scaleY = tx.getScaleY();
        Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int minW = UiTheme.clamp((int) Math.round(1060 / scaleX), 0, work.width);
        int minH = UiTheme.clamp((int) Math.round(600 / scaleY), 0, work.height);
        setMinimumSize(new Dimension(minW, minH));
    }

    /**
     * 监听窗口所在显示器的 graphicsConfiguration 变化（系统缩放调整 / 窗口拖到不同 DPI 屏幕）。
     * Swing 不会自动重新布局，需要主动 revalidate + repaint。
     */
    private void installScaleListener() {
        PropertyChangeListener l = evt -> SwingUtilities.invokeLater(this::onDisplayScaleChanged);
        addPropertyChangeListener("graphicsConfiguration", l);
    }

    private void onDisplayScaleChanged() {
        updateMinimumSize();
        Dimension min = getMinimumSize();
        Dimension cur = getSize();
        if (cur.width < min.width || cur.height < min.height) {
            setSize(Math.max(cur.width, min.width), Math.max(cur.height, min.height));
        }
        revalidate();
        repaint();
        // 图标是按当前缩放光栅化的，缩放变化后重新生成，避免模糊
        loadWindowIconAsync();
    }

    private void initUI() {
        getRootPane().putClientProperty(com.formdev.flatlaf.FlatClientProperties.MENU_BAR_EMBEDDED, true);
        getRootPane().putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_SHOW_ICON, false);
        getRootPane().putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
        titleBar = new AppTitleBar(new TitleBarHost(this));
        setJMenuBar(titleBar);

        setLayout(new BorderLayout(0, 0));

        ribbon = new ControlRibbon(this::onParamChange);
        ribbon.setOnAdjust(this::applyInlinePreprocess);

        center = new CenterPane(new CenterPane.Listener() {
            @Override public void newProject() { captureControls(); MainFrame.this.newProject(); }
            @Override public void selectDoc(int index) { MainFrame.this.selectDoc(index); }
            @Override public void previewModeChanged(int index) { onPreviewModeChanged(index); }
        });

        SideBars.Actions railActions = new SideBars.Actions() {
            // —— 左侧：图像处理 ——
            @Override public void openBlockLibrary() { MainFrame.this.openBlockLibrary(); }
            @Override public void openAdjust(Component anchor) { ribbon.showAdjustPopup(anchor); }
            @Override public void openCrop() { MainFrame.this.openCrop(); }
            @Override public void openPixelEditor() { MainFrame.this.openPixelEditor(); }
            // —— 右侧：导出相关 ——
            @Override public void generate() { generatePreview(); }
            @Override public void export() { exportSelected(); }
            @Override public void openBatch() { MainFrame.this.openBatch(); }
            @Override public void openBom() { MainFrame.this.openBom(); }
        };
        add(SideBars.left(railActions), BorderLayout.WEST);
        add(SideBars.right(railActions), BorderLayout.EAST);

        // 中央：顶部参数条（可折叠）+ 文档标签页 / 双预览
        JPanel centerRoot = new JPanel(new BorderLayout());
        centerRoot.setBackground(UiTheme.CENTER_BG);
        centerRoot.add(ribbon, BorderLayout.NORTH);
        centerRoot.add(center, BorderLayout.CENTER);
        add(centerRoot, BorderLayout.CENTER);

        statusBar = new StatusBar();
        add(statusBar, BorderLayout.SOUTH);

        applyLayout(AppConfig.layout());
        registerGlobalKeys();
    }

    /**
     * 全局快捷键：直接绑定到根窗格的 WHEN_IN_FOCUSED_WINDOW，
     * 不依赖菜单是否已构建 / 是否可见，也不依赖当前焦点组件是否为空——
     * 这是「Ctrl+Z 等快捷键偶发失效」的根因修复。
     * 文本输入框内的同名快捷键（如 Ctrl+Z）由输入框自身的 WHEN_FOCUSED 绑定优先处理。
     */
    private void registerGlobalKeys() {
        JRootPane rp = getRootPane();
        int w = JComponent.WHEN_IN_FOCUSED_WINDOW;
        bind(rp, w, "ctrl N", this::newProject);
        bind(rp, w, "ctrl O", this::chooseImage);
        bind(rp, w, "ctrl shift O", this::openProjectFromChooser);
        bind(rp, w, "ctrl S", () -> saveProjectInteractive(cur()));
        bind(rp, w, "ctrl shift S", this::saveProjectAs);
        bind(rp, w, "ctrl E", this::exportSelected);
        bind(rp, w, "ctrl shift E", this::openPixelEditor);
        bind(rp, w, "ctrl R", this::generatePreview);
        bind(rp, w, "ctrl B", this::openBlockLibrary);
        bind(rp, w, "ctrl P", this::openPreprocess);
        bind(rp, w, "ctrl J", this::openCrop);
        bind(rp, w, "ctrl Z", this::undo);
        bind(rp, w, "ctrl Y", this::redo);
        bind(rp, w, "ctrl alt S", this::openSettingsDialog);
        bind(rp, w, "F1", this::showHelp);
    }

    private static void bind(JRootPane rp, int condition, String stroke, Runnable action) {
        KeyStroke ks = KeyStroke.getKeyStroke(stroke);
        if (ks == null) return;
        String key = "mcart." + stroke;
        rp.getInputMap(condition).put(ks, key);
        rp.getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    /** 窗口多尺寸图标在首屏绘制后再加载（多张 SVG 光栅化较耗时，避免拖慢首窗出现）。 */
    public void loadWindowIconAsync() {
        SwingUtilities.invokeLater(() -> {
            try {
                setIconImages(SvgMiniRenderer.loadMultiSizeIconSet("/Logo/logo.svg"));
            } catch (Exception ignored) {}
        });
    }

    private void saveProjectAs() {
        Project p = cur();
        if (p == null) return;
        p.setProjectFile(null);
        saveProjectInteractive(p);
    }

    // ============================================================ 标签页

    /** 重建所有标签页，同步工程状态到 JTabbedPane */
    private void rebuildTabs() {
        center.reloadTabs(docs, active, this::closeDoc);
    }

    void newProject() {
        docs.add(new Project());
        active = docs.size() - 1;
        loadProjectToUi();
    }

    private void selectDoc(int index) {
        if (index == active || index < 0 || index >= docs.size()) return;
        captureControls();
        active = index;
        loadProjectToUi();
    }

    void closeDoc(int index) {
        Project p = docs.get(index);
        if (p.isDirty() && (p.hasImage() || p.getProjectFile() != null)) {
            int r = JOptionPane.showConfirmDialog(this,
                    I18n.t("tab.closeDirty", p.getName()),
                    I18n.t("tab.closeProjectTitle"), JOptionPane.YES_NO_CANCEL_OPTION);
            if (r == JOptionPane.CANCEL_OPTION) return;
            if (r == JOptionPane.YES_OPTION) {
                active = index;
                if (!saveProjectInteractive(p)) return;
            }
        }
        docs.remove(index);
        if (docs.isEmpty()) {
            newProject();
            return;
        }
        if (active >= docs.size()) active = docs.size() - 1;
        else if (active > index) active--;
        else if (active == index) active = Math.min(active, docs.size() - 1);
        loadProjectToUi();
    }

    private void applyInlinePreprocess() {
        Project p = cur();
        if (p == null || syncing) return;
        p.setPreprocess(ribbon.getPreprocessSettings());
        updatePreprocessStatus();
        scheduleLiveRegen();
        rebuildTabs();
    }

    // ============================================================ 工程 ↔ 控件

    void captureControls() {
        Project p = cur();
        if (p == null || syncing) return;
        ribbon.captureTo(p);
        p.setPreviewMode(center.previewMode());
    }

    private void loadProjectToUi() {
        Project p = cur();
        if (p == null) return;
        syncing = true;
        ribbon.loadFrom(p);
        center.setPreviewModeIndex(p.getPreviewMode());
        syncing = false;

        center.sourceViewer().setImage(p.getSourceImage());
        if (p.result != null) {
            refreshPreviewMode();
        } else {
            center.previewViewer().setImage(null);
            Model3DCanvas m3 = center.model3DOrNull();
            if (m3 != null) m3.setResult(null);
            center.showCard(CenterPane.CARD_2D);
        }
        updatePreprocessStatus();
        refreshCounts();
        if (titleBar != null) {
            titleBar.setDocument(p.hasImage() ? p.getSourceImageName() : null);
            titleBar.setPreviewModeIndex(p.getPreviewMode());
            titleBar.updateActions();
        }
        statusBar.setMessage(p.hasImage()
                ? I18n.t("status.loadedF", p.getSourceImageName(),
                p.getSourceImage().getWidth(), p.getSourceImage().getHeight())
                : I18n.t("status.readyLong"));
        rebuildTabs();
    }

    private void onParamChange() {
        Project p = cur();
        if (p == null || syncing) return;
        p.pushUndo();
        ribbon.captureTo(p);
        scheduleLiveRegen();
        refreshTitleActions();
        rebuildTabs();
    }

    private void onPreviewModeChanged(int idx) {
        Project p = cur();
        if (p != null) p.setPreviewMode(idx);
        titleBar.setPreviewModeIndex(idx);
        refreshPreviewMode();
    }

    private void refreshTitleActions() {
        if (titleBar != null) titleBar.updateActions();
    }

    private void refreshCounts() {
        Project p = cur();
        int n = p == null ? 0 : p.getSelectedBlockIds().size();
        ribbon.setSelectedCount(n);
        if (titleBar != null) {
            titleBar.refreshBlockCounts();
            titleBar.updateActions();
        }
    }

    /** 方块快捷选择 / 方块库改动后的统一刷新。 */
    void afterBlockSelectionChanged() {
        refreshCounts();
        scheduleLiveRegen();
        rebuildTabs();
    }

    // ============================================================ 图片 / 工程 IO

    void chooseImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("filter.images"), "png", "jpg", "jpeg", "bmp", "gif", "webp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Project p = cur();
        if (p != null && p.hasImage()) {
            newProject();
        }
        loadImage(chooser.getSelectedFile());
    }

    void loadImage(File file) {
        Project p = cur();
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) throw new IllegalArgumentException(I18n.t("msg.unsupportedImage"));
            p.pushUndo();
            p.setSourceImage(img, file.getName());
            AppConfig.pushRecent(file.getAbsolutePath());
            center.sourceViewer().setImage(img);
            statusBar.setMessage(I18n.t("status.loadedF", file.getName(), img.getWidth(), img.getHeight()));
            rebuildTabs();
            refreshTitleActions();
            if (titleBar != null) titleBar.setDocument(file.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, I18n.t("msg.loadImageFail", ex.getMessage()),
                    I18n.t("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    void openRecent(String path) {
        File f = new File(path);
        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, I18n.t("msg.fileMissing", path));
            return;
        }
        if (path.toLowerCase().endsWith(".mcpx") || path.toLowerCase().endsWith(".mcpt")) {
            captureControls();
            openProjectFile(f);
        } else loadImage(f);
    }

    void openProjectFromChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter(I18n.t("filter.projectTemplate"), "mcpx", "mcpt"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            captureControls();
            openProjectFile(fc.getSelectedFile());
        }
    }

    private void openProjectFile(File file) {
        try {
            Project p = ProjectIO.open(file.toPath());
            docs.add(p);
            active = docs.size() - 1;
            AppConfig.pushRecent(file.getAbsolutePath());
            loadProjectToUi();
            doGenerate(false, false);
            statusBar.setMessage(I18n.t("status.openedProject", file.getName()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, I18n.t("msg.openProjectFail", ex.getMessage()),
                    I18n.t("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    boolean saveProjectInteractive(Project p) {
        if (p == null) return false;
        try {
            File f = p.getProjectFile();
            if (f == null) {
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileNameExtensionFilter(I18n.t("filter.project"), "mcpx"));
                fc.setSelectedFile(new File(safeName(p.getName()) + ".mcpx"));
                if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
                f = fc.getSelectedFile();
                if (!f.getName().toLowerCase().endsWith(".mcpx")) f = new File(f.getAbsolutePath() + ".mcpx");
            }
            captureControls();
            ProjectIO.save(p, f.toPath());
            AppConfig.pushRecent(f.getAbsolutePath());
            statusBar.setMessage(I18n.t("status.savedPath", f.getAbsolutePath()));
            rebuildTabs();
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, I18n.t("msg.saveFail", ex.getMessage()),
                    I18n.t("common.error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    void saveTemplate() {
        Project p = cur();
        if (p == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter(I18n.t("filter.template"), "mcpt"));
        fc.setSelectedFile(new File(safeName(p.getName()) + ".mcpt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".mcpt")) f = new File(f.getAbsolutePath() + ".mcpt");
        try {
            captureControls();
            ProjectIO.saveTemplate(p, f.toPath());
            JOptionPane.showMessageDialog(this, I18n.t("msg.templateSaved", f));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, I18n.t("msg.templateSaveFail", ex.getMessage()));
        }
    }

    private static String safeName(String n) {
        if (n == null || n.isBlank()) return "mcpixel-project";
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // ============================================================ 撤销 / 重做

    void undo() {
        Project p = cur();
        if (p != null && p.canUndo()) { p.undo(); loadProjectToUi(); doGenerate(false, true); }
    }

    void redo() {
        Project p = cur();
        if (p != null && p.canRedo()) { p.redo(); loadProjectToUi(); doGenerate(false, true); }
    }

    // ============================================================ 生成

    void generatePreview() { doGenerate(true, false); }

    private boolean doGenerate(boolean interactive, boolean keepPreview) {
        Project p = cur();
        if (p == null || !p.hasImage()) {
            if (interactive) JOptionPane.showMessageDialog(this, I18n.t("msg.noImage"),
                    I18n.t("common.notice"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        captureControls();
        List<BlockColor> blocks = selectedBlocks(p);
        if (blocks.isEmpty()) {
            if (interactive) {
                JOptionPane.showMessageDialog(this, I18n.t("msg.noBlocks"),
                        I18n.t("common.notice"), JOptionPane.WARNING_MESSAGE);
            }
            updateBell();
            return false;
        }
        if (interactive) setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            ColorMatcher matcher = new ColorMatcher(blocks, p.getMode());
            PixelArtGenerator generator = new PixelArtGenerator(matcher, p.getDither(), p.getMode(),
                    p.getSupportBlockId(), p.getPreprocess(), p.getDitherStrength() / 100.0, p.roi());
            PixelArtGenerator.Result result = generator.generate(p.getSourceImage(), p.targetWidth(), p.targetHeight());
            p.result = result;

            p.mcColorImage = new BufferedImage(result.width, result.length, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < result.length; z++)
                for (int x = 0; x < result.width; x++)
                    p.mcColorImage.setRGB(x, z, 0xFF000000 | result.previewPixels[z][x]);
            p.mcTextureImage = TexturePreviewRenderer.render(result);
            Model3DCanvas m3 = center.model3DOrNull();
            if (m3 != null) m3.setResult(result);

            if (!keepPreview) {
                int idx = p.getMode() == MapMode.RELIEF ? 1 : 0;
                p.setPreviewMode(idx);
                center.setPreviewModeIndex(idx);
            }
            refreshPreviewMode();

            String reliefInfo = p.getMode() == MapMode.RELIEF
                    ? I18n.t("status.modelHeight", result.height) : "";
            statusBar.setMessage(I18n.t("status.generatedFull",
                    result.width, result.length, p.getMapsW(), p.getMapsH(),
                    result.palette.size(), I18n.t("mode." + p.getMode().name()),
                    I18n.t("dither." + p.getDither().name()), reliefInfo));
            p.markDirty();
            rebuildTabs();
            updateBell();
            refreshTitleActions();
            return true;
        } catch (Exception ex) {
            if (interactive) JOptionPane.showMessageDialog(this, I18n.t("msg.generateFail", ex.getMessage()),
                    I18n.t("common.error"), JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return false;
        } finally {
            if (interactive) setCursor(Cursor.getDefaultCursor());
        }
    }

    private List<BlockColor> selectedBlocks(Project p) {
        List<BlockColor> list = new ArrayList<>();
        for (String id : p.getSelectedBlockIds()) {
            BlockColor bc = PaletteService.byId(id);
            if (bc != null) list.add(bc);
        }
        return list;
    }

    private void scheduleLiveRegen() {
        if (liveTimer == null) {
            liveTimer = new Timer(110, e -> doGenerate(false, true));
            liveTimer.setRepeats(false);
        }
        Project p = cur();
        if (p != null && p.result != null) liveTimer.restart();
    }

    private void refreshPreviewMode() {
        Project p = cur();
        if (p == null || p.result == null) return;
        int mode = center.previewMode();
        p.setPreviewMode(mode);
        if (titleBar != null) titleBar.setPreviewModeIndex(mode);
        if (mode == 2) {
            center.model3D().setResult(p.result);
            center.showCard(CenterPane.CARD_3D);
        } else if (mode == 3) {
            BufferedImage heat = QualityChecker.gamutHeatmap(p, 1);
            BufferedImage base = p.mcColorImage;
            if (heat != null && base != null) {
                BufferedImage composite = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = composite.createGraphics();
                g.drawImage(base, 0, 0, null);
                g.drawImage(heat, 0, 0, null);
                g.dispose();
                center.previewViewer().setImage(composite);
            } else center.previewViewer().setImage(base);
            center.showCard(CenterPane.CARD_2D);
        } else {
            BufferedImage img = mode == 0 ? p.mcTextureImage : p.mcColorImage;
            if (img != null) center.previewViewer().setImage(img);
            center.showCard(CenterPane.CARD_2D);
        }
    }

    // ============================================================ 导出

    void exportSelected() {
        Project p = cur();
        if (p == null || p.result == null) {
            MessageDialog.show(this, MessageDialog.Kind.WARNING,
                    I18n.t("common.notice"), I18n.t("msg.needPreview"));
            return;
        }
        captureControls();
        if (!confirmExportQuality(p)) return;

        // 1) 先选导出格式（PNG 可设放大倍数）
        ExportDialog dlg = new ExportDialog(this, ExportFormat.fromIndex(p.getExportFormat()));
        dlg.setVisible(true);
        if (!dlg.isApproved()) return;
        ExportFormat fmt = dlg.selectedFormat();
        int pngScale = dlg.pngScale();
        p.setExportFormat(fmt.ordinal());

        // 2) 再选保存路径
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter(
                I18n.t("format." + fmt.name()) + " (*" + fmt.extension + ")", fmt.extension.substring(1)));
        fc.setSelectedFile(new File(safeName(p.getName()) + fmt.extension));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(fmt.extension))
            file = new File(file.getAbsolutePath() + fmt.extension);
        try {
            BufferedImage png = scaleImageNearest(p.mcColorImage, pngScale);
            fmt.export(p.result, png, file.toPath());
            statusBar.setMessage(I18n.t("msg.exported", file.getAbsolutePath()));
            MessageDialog.show(this, MessageDialog.Kind.INFO,
                    I18n.t("common.complete"), I18n.t("msg.exportSuccess", file.getAbsolutePath()));
        } catch (Exception ex) {
            MessageDialog.show(this, MessageDialog.Kind.ERROR,
                    I18n.t("common.error"), I18n.t("msg.exportFail", ex.getMessage()));
            ex.printStackTrace();
        }
    }

    private boolean confirmExportQuality(Project p) {
        List<QualityChecker.Issue> issues = QualityChecker.check(p);
        List<QualityChecker.Issue> warns = new ArrayList<>();
        List<QualityChecker.Issue> errors = new ArrayList<>();
        for (QualityChecker.Issue i : issues) {
            if (i.level == QualityChecker.Level.ERROR) errors.add(i);
            else if (i.level == QualityChecker.Level.WARN) warns.add(i);
        }
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder(I18n.t("msg.cannotExport")).append('\n');
            errors.forEach(i -> sb.append("• ").append(i.message).append('\n'));
            MessageDialog.show(this, MessageDialog.Kind.ERROR,
                    I18n.t("msg.cannotExportTitle"), sb.toString().trim());
            return false;
        }
        if (!warns.isEmpty()) {
            StringBuilder sb = new StringBuilder(I18n.t("msg.exportWarnPrefix")).append('\n');
            warns.forEach(i -> sb.append("• ").append(i.message).append('\n'));
            return MessageDialog.confirm(this, MessageDialog.Kind.WARNING,
                    I18n.t("msg.exportWarnTitle"), sb.toString().trim());
        }
        return true;
    }

    static BufferedImage scaleImageNearest(BufferedImage src, int factor) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(src.getWidth() * factor, src.getHeight() * factor, src.getType());
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    // ============================================================ 对话框入口

    void openBlockLibrary() {
        if (blockLibrary != null) blockLibrary.dispose();
        blockLibrary = new BlockLibraryDialog(this, new BlockLibraryDialog.Context() {
            @Override public java.util.Set<String> selectedIds() { return cur().getSelectedBlockIds(); }
            @Override public void onSelectionChanged() {
                Project p = cur();
                p.markDirty();
                refreshCounts();
                scheduleLiveRegen();
                rebuildTabs();
            }
            @Override public void onBlocksChanged() {
                PaletteService.invalidate();
                TextureManager.invalidate();
                boolean wasVisible = blockLibrary != null && blockLibrary.isVisible();
                if (blockLibrary != null) blockLibrary.dispose();
                blockLibrary = null;
                refreshCounts();
                if (wasVisible) openBlockLibrary();
            }
        });
        cur().pushUndo();
        blockLibrary.refreshCount();
        blockLibrary.setVisible(true);
        blockLibrary.toFront();
    }

    void searchBlocks() {
        openBlockLibrary();
        if (blockLibrary != null) blockLibrary.focusSearch();
    }

    /** 打开「图像调整」弹层（顶部参数条 / 左侧工具栏 / Ctrl+P 共用）。 */
    void openPreprocess() {
        if (cur() == null) return;
        ribbon.showAdjustPopup();
    }

    private void updatePreprocessStatus() {
        Project p = cur();
        PreprocessSettings s = p == null ? PreprocessSettings.disabled() : p.getPreprocess();
        String text;
        if (!s.enabled) text = I18n.t("pp.off");
        else text = I18n.t("pp.on",
                s.brightness, s.contrast, s.gamma, s.saturation,
                s.backgroundMode == PreprocessSettings.BackgroundMode.OFF
                        ? "" : I18n.t("pp.bgPrefix") + I18n.t("bg." + s.backgroundMode.name()));
        ribbon.setPreprocessStatus(text);
    }

    void openCrop() {
        Project p = cur();
        if (p == null || !p.hasImage()) {
            JOptionPane.showMessageDialog(this, I18n.t("msg.noImage"),
                    I18n.t("common.notice"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        CropDialog dlg = new CropDialog(this, p.getSourceImage(), p.roi(),
                p.getMapsW(), p.getMapsH(), roi -> {
            p.pushUndo();
            if (roi == null) p.clearRoi();
            else p.setRoi(true, roi.x, roi.y, roi.width, roi.height);
            scheduleLiveRegen();
            rebuildTabs();
            statusBar.setMessage(roi == null ? I18n.t("status.cropCleared")
                    : I18n.t("status.cropSet",
                    Math.round(roi.width * 100), Math.round(roi.height * 100),
                    Math.round(roi.x * 100), Math.round(roi.y * 100)));
        });
        dlg.setVisible(true);
    }

    void openPixelEditor() {
        Project p = cur();
        if (p == null || p.result == null) {
            JOptionPane.showMessageDialog(this, I18n.t("pixel.needResult"),
                    I18n.t("common.notice"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        PixelEditorDialog dlg = new PixelEditorDialog(this, p.result, selectedBlocks(p), p.getMode(), () -> {
            p.mcColorImage = new BufferedImage(p.result.width, p.result.length, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < p.result.length; z++)
                for (int x = 0; x < p.result.width; x++)
                    p.mcColorImage.setRGB(x, z, 0xFF000000 | p.result.previewPixels[z][x]);
            p.mcTextureImage = TexturePreviewRenderer.render(p.result);
            Model3DCanvas m3 = center.model3DOrNull();
            if (m3 != null) m3.setResult(p.result);
            refreshPreviewMode();
            p.markDirty();
            rebuildTabs();
        });
        if (dlg.isReady()) dlg.setVisible(true);
    }

    void openBom() {
        Project p = cur();
        if (p == null || p.result == null) {
            MessageDialog.show(this, MessageDialog.Kind.WARNING,
                    I18n.t("common.notice"), I18n.t("msg.needPreview"));
            return;
        }
        new BomDialog(this, p.result, p.getName()).setVisible(true);
    }

    void openBatch() {
        new BatchDialog(this, cur()).setVisible(true);
    }

    void openWarnings() {
        if (warningsDialog != null) warningsDialog.dispose();
        warningsDialog = new WarningsDialog(this, this::cur, () -> center.setPreviewModeIndex(3));
        warningsDialog.setVisible(true);
    }

    private void updateBell() {
        if (titleBar != null) titleBar.updateActions();
    }

    int warningCount() {
        Project p = cur();
        if (p == null) return 0;
        int n = 0;
        for (QualityChecker.Issue i : QualityChecker.check(p))
            if (i.level != QualityChecker.Level.INFO) n++;
        return n;
    }

    // ============================================================ 快照

    private void saveSnapshot(String name) {
        Project p = cur();
        if (p == null) return;
        p.addSnapshot(name);
        Project.NamedSnapshot s = p.snapshots.get(p.snapshots.size() - 1);
        if (p.mcColorImage != null) s.previewPng = encodePng(scaleImageNearest(p.mcColorImage,
                Math.max(1, 140 / Math.max(1, p.mcColorImage.getWidth()))));
    }

    private void restoreSnapshot(Project.NamedSnapshot s) {
        Project p = cur();
        p.pushUndo();
        p.restore(s.state);
        loadProjectToUi();
        doGenerate(false, false);
    }

    private void compareSnapshots(Project.NamedSnapshot a, Project.NamedSnapshot b) {
        Project p = cur();
        if (p == null || !p.hasImage()) return;
        ProjectState original = p.snapshot();
        BufferedImage ia = renderState(a.state);
        BufferedImage ib = renderState(b.state);
        p.restore(original);
        doGenerate(false, false);
        if (ia == null || ib == null) return;
        SnapshotCompareView.show(this, a, b, ia, ib);
    }

    private BufferedImage renderState(ProjectState state) {
        Project p = cur();
        p.restore(state);
        loadProjectToUi();
        boolean ok = doGenerate(false, false);
        return ok ? scaleImageNearest(p.mcColorImage, Math.max(1, 280 / Math.max(1, p.mcColorImage.getWidth()))) : null;
    }

    private static byte[] encodePng(BufferedImage img) {
        if (img == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    void openSnapshotsDialog() {
        Project p = cur();
        SnapshotsDialog dlg = new SnapshotsDialog(this, new SnapshotsDialog.Host() {
            @Override public List<Project.NamedSnapshot> snapshots() { return p.snapshots; }
            @Override public void saveSnapshot(String name) { MainFrame.this.saveSnapshot(name); }
            @Override public void restoreSnapshot(Project.NamedSnapshot s) { MainFrame.this.restoreSnapshot(s); }
            @Override public void deleteSnapshot(Project.NamedSnapshot s) { p.snapshots.remove(s); }
            @Override public void compareSnapshots(Project.NamedSnapshot a, Project.NamedSnapshot b) {
                MainFrame.this.compareSnapshots(a, b);
            }
        });
        dlg.setVisible(true);
    }

    void openPresetsDialog() {
        new PresetManagerDialog(this, () -> {
            if (blockLibrary != null) blockLibrary.dispose();
            blockLibrary = null;
        }).setVisible(true);
    }

    void openCustomBlockDialog() {
        CustomBlockDialog dlg = new CustomBlockDialog(this);
        dlg.setVisible(true);
        if (dlg.isCreated()) {
            PaletteService.invalidate();
            TextureManager.invalidate();
            refreshCounts();
        }
    }

    void openSettingsDialog() {
        new SettingsDialog(this, s -> {
            AppConfig.set("restoreSession", String.valueOf(s.restoreSession));
            AppConfig.save();
            boolean needRestart = !s.theme.equals(AppConfig.theme()) || !s.language.equals(AppConfig.language());
            AppConfig.setTheme(s.theme);
            AppConfig.setLanguage(s.language);
            AppConfig.setLayout(s.layout);
            AppConfig.save();
            applyLayout(s.layout);
            if (needRestart) Main.restartUi(MainFrame.this);
        }).setVisible(true);
    }

    void showHelp() {
        new HelpViewer(this).setVisible(true);
    }

    void showAbout() {
        JOptionPane.showMessageDialog(this, I18n.t("about.text"),
                I18n.t("about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    // ============================================================ 预览 / 布局（供标题栏调用）

    void setPreviewModeCombo(int idx) {
        center.setPreviewModeIndex(idx);
    }

    void reset3DView() {
        Model3DCanvas m3 = center.model3DOrNull();
        if (m3 != null) m3.resetView();
    }

    void fitViews() {
        center.sourceViewer().fit();
        center.previewViewer().fit();
        reset3DView();
    }

    void applyLayout(String layout) {
        if (center != null) center.applyLayout(layout);
    }

    // ============================================================ 示例 / 退出 / 会话

    public void openSampleProject() {
        BufferedImage img = SampleImage.creeper();
        captureControls();
        Project empty = cur();
        if (empty != null && (empty.hasImage() || empty.getProjectFile() != null)) newProject();
        Project p = cur();
        p.setSourceImage(img, I18n.t("sample.name"));
        center.sourceViewer().setImage(img);
        if (titleBar != null) titleBar.setDocument(I18n.t("sample.name"));
        statusBar.setMessage(I18n.t("status.sampleLoaded"));
        doGenerate(true, false);
    }

    private void onExit() {
        captureControls();
        for (Project p : docs) {
            if (p.isDirty() && (p.hasImage() || p.getProjectFile() != null)) {
                int r = JOptionPane.showConfirmDialog(this,
                        I18n.t("exit.dirty", p.getName()),
                        I18n.t("exit.title"), JOptionPane.YES_NO_CANCEL_OPTION);
                if (r == JOptionPane.CANCEL_OPTION) return;
                if (r == JOptionPane.YES_OPTION && !saveProjectInteractive(p)) return;
            }
        }
        if (AppConfig.restoreSession()) ProjectIO.saveSession(docs, active);
        else ProjectIO.clearSession();
        AppConfig.save();
        dispose();
        System.exit(0);
    }

    public boolean restoreSessionIfAny() {
        if (!AppConfig.restoreSession()) return false;
        List<Project> list = ProjectIO.loadSession();
        if (list == null || list.isEmpty()) return false;
        docs.clear();
        docs.addAll(list);
        active = 0;
        loadProjectToUi();
        for (Project p : docs) {
            if (p.hasImage()) doGenerate(false, false);
        }
        ProjectIO.clearSession();
        return true;
    }
}