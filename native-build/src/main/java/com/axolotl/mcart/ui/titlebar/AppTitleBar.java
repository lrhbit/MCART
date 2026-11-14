package com.axolotl.mcart.ui.titlebar;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.util.SvgMiniRenderer;

import javax.swing.*;
import javax.swing.event.MenuListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * IntelliJ New UI 风格标题栏（移植自 Pixel 的 IdeaTitleBar），按
 * 「MC 地图画生成器 专业版」的功能重新接线。
 *
 * 紧凑模式（默认）：
 *   [Logo] [☰] [色块 当前文档 ⌄] [方块库 · N 种 ⌄]   …   [🔔] [🌀] [🔍] [⚑⚙]
 * 主菜单模式（汉堡 / Alt+\）：
 *   [Logo] 文件 编辑 视图 生成 工具 帮助
 */
public class AppTitleBar extends JMenuBar {

    public enum Mode { COMPACT, MENUS }

    /** 标题栏与主窗口之间的功能契约，由 MainFrame 实现。 */
    public interface Host {
        // —— 文档 ——
        boolean hasImage();
        boolean hasResult();
        List<String> recentFiles();
        void openImage();
        void openRecent(String path);
        void closeImage();

        // —— 工程 ——
        void newProject();
        void openProject();
        void saveProject();
        void saveProjectAs();
        void saveTemplate();

        // —— 撤销 / 重做 ——
        boolean canUndo();
        boolean canRedo();
        void undo();
        void redo();

        // —— 生成 / 导出 ——
        void generate();
        void export();

        // —— 方块库 ——
        void openBlockLibrary();
        void searchBlocks();
        void selectBlocksDefault();
        void selectBlocksAll();
        void selectBlocksNone();
        int selectedBlockCount();
        int totalBlockCount();

        // —— 编辑工具 ——
        void openPreprocess();
        void openCrop();
        void openPixelEditor();

        // —— 预览（0 纹理 1 配色 2 立体 3 色域预警） ——
        void setPreviewMode(int index);
        int previewMode();
        void reset3DView();
        void fitViews();

        // —— 视图 / 主题 / 语言 ——
        void setLayoutMode(String layout);
        String layoutMode();
        void setTheme(String theme);
        String theme();
        void setLanguage(String lang);
        String language();

        // —— 工具 ——
        void openBom();
        void openBatch();
        void openSnapshots();
        void openPresets();
        void openCustomBlock();
        void openSettings();
        void openWarnings();
        /** 0 = 无警告（铃铛不显示徽标） */
        int warningCount();

        // —— 帮助 ——
        void showHelp();
        void openSample();
        void showAbout();
    }

    private final Host host;
    private final AWTEventListener globalListener = this::onGlobalEvent;

    private final JLabel logo;
    private final TitleIcons.Button burger;
    private final DocumentWidget documentWidget;
    private final MaterialsWidget materialsWidget;
    private final GradientMonogram monogram;
    private final List<JMenu> menus = new java.util.ArrayList<>();
    private JPanel compactGroup;
    private Component menuGap;

    private GearButton settingsBtn;
    private BellButton bellBtn;
    private JMenuItem exportItem;
    private JMenuItem generateItem;
    private JMenuItem closeItem;
    private JMenuItem saveItem;
    private JMenuItem undoItem, redoItem;
    private JMenuItem pixelEditItem, cropItem, bomItem, batchItem, snapshotItem, warningsItem;
    private JRadioButtonMenuItem[] previewRadios;
    private JRadioButtonMenuItem[] layoutRadios;
    private JRadioButtonMenuItem[] themeRadios;
    private JRadioButtonMenuItem[] langRadios;
    private boolean syncingPreview;
    /** 六大菜单延迟到首窗显示后再构建，加快冷启动。 */
    private boolean menusBuilt;
    private Timer menusBuildTimer;
    /** 弹性间隔：菜单必须插入到它之前，否则会排到铃铛 / 齿轮右侧。 */
    private Component menuGlue;

    private Mode mode = Mode.COMPACT;
    private Point glowCenter;
    private Color glowColor;

    private long lastShiftPress = 0L;

    public AppTitleBar(Host host) {
        this.host = host;
        setOpaque(false);
        setBorder(null);

        ProjectPalette palette = ProjectPalette.perLaunch("mc-pixel-art");
        glowColor = palette.glow;

        logo = createLogo();
        burger = new TitleIcons.Button(TitleIcons.hamburger, 31, 28, 8);
        burger.setToolTipText(I18n.t("titlebar.mainMenu"));
        burger.addActionListener(e -> toggleMode());

        documentWidget = new DocumentWidget(host);
        materialsWidget = new MaterialsWidget(host);
        monogram = documentWidget.getMonogram();
        monogram.setPaletteChangedCallback(() -> {
            glowColor = monogram.getPalette().glow;
            repaint();
        });

        add(Box.createHorizontalStrut(UIScale.scale(2)));
        add(logo);

        compactGroup = new JPanel();
        compactGroup.setOpaque(false);
        compactGroup.setLayout(new BoxLayout(compactGroup, BoxLayout.X_AXIS));
        compactGroup.add(Box.createHorizontalStrut(UIScale.scale(10)));
        compactGroup.add(burger);
        compactGroup.add(Box.createHorizontalStrut(UIScale.scale(12)));
        compactGroup.add(documentWidget);
        // 方块库入口已收敛到左侧工具栏与顶部参数条，标题栏不再重复放置「方块库·N 种」小部件
        compactGroup.add(materialsWidget);
        add(compactGroup);

        menuGap = Box.createHorizontalStrut(UIScale.scale(11));
        menuGap.setVisible(false);
        add(menuGap);

        // 菜单不在构造期构建：首窗先显示，稍后在 addNotify 中延迟补建
        Box.Filler glue = (Box.Filler) Box.createHorizontalGlue();
        glue.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, Boolean.TRUE);
        menuGlue = glue;
        add(glue);;

        // 🔔 质量警告中心
        bellBtn = new BellButton();
        bellBtn.setToolTipText(I18n.t("titlebar.bell"));
        bellBtn.addActionListener(e -> host.openWarnings());
        add(bellBtn);

        // ⚙ 设置（齿轮即「设置」入口；图像调整已移至左侧工具栏 / 顶部参数条）
        settingsBtn = new GearButton();
        settingsBtn.setToolTipText(I18n.t("titlebar.settings"));
        settingsBtn.addActionListener(e -> host.openSettings());

        add(settingsBtn);
        add(Box.createHorizontalStrut(UIScale.scale(3)));

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.ALT_DOWN_MASK), "toggleMainMenu");
        getActionMap().put("toggleMainMenu", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleMode(); }
        });

        applyMode();
    }

    // ------------------------------------------------------------- 对外状态

    public void setDocument(String fileName) {
        documentWidget.setDocument(fileName);
    }

    public void refreshBlockCounts() {
        materialsWidget.setCount(host.selectedBlockCount(), host.totalBlockCount());
    }

    /** 齿轮已改为「设置」入口；预处理开关状态改由顶部参数条「调整」按钮呈现。 */
    public void setPreprocessActive(boolean active) {
        // 保留方法签名以兼容主窗口调用，标题栏不再显示预处理圆点
    }

    public void setPreviewModeIndex(int idx) {
        if (previewRadios == null || idx < 0 || idx >= previewRadios.length) return;
        syncingPreview = true;
        try {
            previewRadios[idx].setSelected(true);
        } finally {
            syncingPreview = false;
        }
    }

    /** 刷新全部菜单项 / 图标的可用性与选中态（文档切换、参数变化后调用）。 */
    public void updateActions() {
        boolean img = host.hasImage();
        boolean res = host.hasResult();
        if (generateItem != null) generateItem.setEnabled(img);
        if (exportItem != null) exportItem.setEnabled(res);
        if (closeItem != null) closeItem.setEnabled(img);
        if (saveItem != null) saveItem.setEnabled(img);
        if (undoItem != null) undoItem.setEnabled(host.canUndo());
        if (redoItem != null) redoItem.setEnabled(host.canRedo());
        if (cropItem != null) cropItem.setEnabled(img);
        if (pixelEditItem != null) pixelEditItem.setEnabled(res);
        if (bomItem != null) bomItem.setEnabled(res);
        if (batchItem != null) batchItem.setEnabled(true);
        if (snapshotItem != null) snapshotItem.setEnabled(img);
        if (warningsItem != null) warningsItem.setText(warningsItemText());
        // 单选状态同步
        syncRadios(layoutRadios, host.layoutMode());
        syncRadios(themeRadios, host.theme());
        syncRadios(langRadios, host.language());
        setPreviewModeIndex(host.previewMode());
        bellBtn.setCount(host.warningCount());
    }

    private String warningsItemText() {
        int n = host.warningCount();
        return n > 0 ? I18n.t("titlebar.bell") + " (" + n + ")" : I18n.t("titlebar.bell");
    }

    private static void syncRadios(JRadioButtonMenuItem[] radios, String value) {
        if (radios == null) return;
        for (JRadioButtonMenuItem r : radios) {
            if (value != null && value.equals(r.getClientProperty("value"))) r.setSelected(true);
        }
    }

    // ------------------------------------------------------------------ logo

    private static JLabel createLogo() {
        JLabel label = null;
        try {
            String svg = SvgMiniRenderer.readSvgResource("/Logo/logo.svg");
            int px = UIScale.scale(22);
            java.awt.image.BufferedImage img = SvgMiniRenderer.render(svg, px, px);
            label = new JLabel(new ImageIcon(img));
        } catch (Exception ex) {
            System.err.println("[AppTitleBar] 渲染 Logo 失败: " + ex.getMessage());
        }
        if (label == null) label = new JLabel();
        int s = UIScale.scale(22);
        Dimension d = new Dimension(s, s);
        label.setPreferredSize(d);
        label.setMinimumSize(d);
        label.setMaximumSize(d);
        label.setOpaque(false);
        label.setBorder(null);
        label.setAlignmentY(0.5f);
        return label;
    }

    // ------------------------------------------------------------------ mode

    public Mode getMode() { return mode; }
    public void toggleMode() { setMode(mode == Mode.COMPACT ? Mode.MENUS : Mode.COMPACT); }

    public void setMode(Mode newMode) {
        if (mode == newMode) return;
        if (newMode == Mode.MENUS) ensureMenusBuilt();
        mode = newMode;
        applyMode();
    }

    private void applyMode() {
        boolean compact = (mode == Mode.COMPACT);
        compactGroup.setVisible(compact);
        menuGap.setVisible(!compact);
        for (JMenu menu : menus) menu.setVisible(!compact);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------- painting

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = UIScale.scale(40);
        return d;
    }

    @Override
    public void doLayout() {
        super.doLayout();
        if (compactGroup.isVisible() && monogram.isVisible()) {
            compactGroup.doLayout();
            documentWidget.doLayout();
            if (monogram.getWidth() > 0)
                glowCenter = SwingUtilities.convertPoint(
                        monogram, monogram.getWidth() / 2, monogram.getHeight() / 2, this);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth(), h = getHeight();
        try {
            if (compactGroup.isVisible() && monogram.getWidth() > 0) {
                glowCenter = SwingUtilities.convertPoint(
                        monogram, monogram.getWidth() / 2, monogram.getHeight() / 2, this);
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(TitleTheme.TITLE_BG);
            g2.fillRect(0, 0, w, h);
            paintGlow(g2, w, h);
            g2.setColor(TitleTheme.TITLE_BORDER);
            g2.fillRect(0, h - UIScale.scale(1), w, UIScale.scale(1));
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private void paintGlow(Graphics2D g2, int w, int h) {
        if (glowCenter == null || glowColor == null) return;
        int cx = glowCenter.x;
        float[][] stops = {
                {-100, 0.00f}, {-66, 0.10f}, {-51, 0.16f}, {-21, 0.26f}, {-8, 0.30f},
                {31, 0.32f}, {53, 0.28f}, {127, 0.24f}, {201, 0.19f}, {319, 0.13f},
                {438, 0.07f}, {556, 0.00f},
        };
        float[] fractions = new float[stops.length];
        Color[] colors = new Color[stops.length];
        int x0 = cx + UIScale.scale((int) stops[0][0]);
        int x1 = cx + UIScale.scale((int) stops[stops.length - 1][0]);
        for (int i = 0; i < stops.length; i++) {
            float dx = UIScale.scale((int) stops[i][0]);
            fractions[i] = (cx + dx - x0) / Math.max(1, x1 - x0);
            int a = Math.round(stops[i][1] * 255);
            colors[i] = new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), a);
        }
        g2.setPaint(new LinearGradientPaint(x0, 0, x1, 0, fractions, colors));
        g2.fillRect(Math.max(0, x0), 0, Math.min(w, x1) - Math.max(0, x0), h);
    }

    // ----------------------------------------------------------------- menus

    public GradientMonogram getMonogram() { return monogram; }
    public List<JMenu> getMenus() { return menus; }

    private void buildMenus() {
        addMenu(buildFileMenu());
        addMenu(buildEditMenu());
        addMenu(buildViewMenu());
        addMenu(buildGenerateMenu());
        addMenu(buildToolsMenu());
        addMenu(buildHelpMenu());
    }

    /** 懒构建六大菜单（首屏绘制后再执行，不阻塞窗口出现）。 */
    private void ensureMenusBuilt() {
        if (menusBuilt) return;
        menusBuilt = true;
        buildMenus();
        applyMode();
        updateActions();
        revalidate();
        repaint();
    }

    private void addMenu(JMenu menu) {
        menu.setVisible(false);
        menus.add(menu);
        // 插入到弹性间隔之前，保证菜单排在 logo 一侧、铃铛 / 齿轮在最右
        int idx = menuGlue != null ? getComponentZOrder(menuGlue) : getComponentCount();
        add(menu, idx);
    }

    private JMenu createMenu(String title, int mnemonic) {
        JMenu menu = new JMenu(title);
        menu.setMnemonic(mnemonic);
        float menuSize = TitleTheme.isSegoe() ? 12f : 11.5f;
        menu.setFont(TitleTheme.uiFont(UIScale.scale(menuSize), Font.PLAIN));
        menu.setForeground(TitleTheme.TITLE_TEXT);
        menu.setOpaque(false);
        int menuPad = TitleTheme.isSegoe() ? 7 : 4;
        menu.setBorder(BorderFactory.createEmptyBorder(
                UIScale.scale(4), UIScale.scale(menuPad), UIScale.scale(4), UIScale.scale(menuPad)));
        return menu;
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    private JMenu buildFileMenu() {
        JMenu menu = createMenu(" "+I18n.t("menu.file")+" ", KeyEvent.VK_F);

        JMenuItem n = item(I18n.t("file.new"), host::newProject);
        n.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        menu.add(n);

        JMenuItem openImg = item(I18n.t("file.openImage"), host::openImage);
        openImg.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        menu.add(openImg);

        JMenuItem openProj = item(I18n.t("file.openProject"), host::openProject);
        openProj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        menu.add(openProj);

        JMenu recent = new JMenu(I18n.t("file.recent"));
        recent.addMenuListener(new MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                recent.removeAll();
                List<String> files = host.recentFiles();
                if (files.isEmpty()) {
                    JMenuItem empty = new JMenuItem(I18n.t("recent.empty"));
                    empty.setEnabled(false);
                    recent.add(empty);
                } else {
                    for (String path : files) {
                        JMenuItem mi = new JMenuItem(new java.io.File(path).getName());
                        mi.setToolTipText(path);
                        mi.addActionListener(ev -> host.openRecent(path));
                        recent.add(mi);
                    }
                }
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });
        menu.add(recent);

        closeItem = item(I18n.t("doc.closeImage"), host::closeImage);
        menu.add(closeItem);

        menu.addSeparator();
        saveItem = item(I18n.t("file.saveProjectFull"), host::saveProject);
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        menu.add(saveItem);
        menu.add(item(I18n.t("file.saveAsMenu"), host::saveProjectAs));
        menu.add(item(I18n.t("file.templateMenu"), host::saveTemplate));

        menu.addSeparator();
        exportItem = item(I18n.t("file.quickExport"), host::export);
        exportItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
        menu.add(exportItem);

        menu.addSeparator();
        JMenuItem quit = item(I18n.t("file.quit"), () -> {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null) win.dispatchEvent(new WindowEvent(win, WindowEvent.WINDOW_CLOSING));
        });
        menu.add(quit);
        return menu;
    }

    private JMenu buildEditMenu() {
        JMenu menu = createMenu(" "+I18n.t("menu.edit")+" ", KeyEvent.VK_E);

        undoItem = item(I18n.t("edit.undo"), host::undo);
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        menu.add(undoItem);
        redoItem = item(I18n.t("edit.redo"), host::redo);
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        menu.add(redoItem);

        menu.addSeparator();
        JMenuItem preprocess = item(I18n.t("edit.preprocess"), host::openPreprocess);
        preprocess.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        menu.add(preprocess);
        cropItem = item(I18n.t("edit.crop"), host::openCrop);
        cropItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK));
        menu.add(cropItem);
        pixelEditItem = item(I18n.t("edit.pixelEdit"), host::openPixelEditor);
        pixelEditItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        menu.add(pixelEditItem);

        menu.addSeparator();
        JMenuItem blocks = item(I18n.t("edit.blocks"), host::openBlockLibrary);
        blocks.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK));
        menu.add(blocks);
        menu.add(item(I18n.t("edit.defaultSelect"), () -> { host.selectBlocksDefault(); refreshBlockCounts(); }));
        menu.add(item(I18n.t("edit.selectAll"), () -> { host.selectBlocksAll(); refreshBlockCounts(); }));
        menu.add(item(I18n.t("edit.selectNone"), () -> { host.selectBlocksNone(); refreshBlockCounts(); }));
        return menu;
    }

    private JMenu buildViewMenu() {
        JMenu menu = createMenu(" "+I18n.t("menu.view")+" ", KeyEvent.VK_V);

        String[] names = {I18n.t("view.pv.texture"), I18n.t("view.pv.color"), I18n.t("view.pv.model3d"), I18n.t("view.pv.gamut")};
        previewRadios = new JRadioButtonMenuItem[4];
        ButtonGroup pg = new ButtonGroup();
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            JRadioButtonMenuItem radio = new JRadioButtonMenuItem(names[i]);
            radio.addActionListener(e -> { if (!syncingPreview) host.setPreviewMode(idx); });
            pg.add(radio);
            previewRadios[i] = radio;
            menu.add(radio);
        }
        previewRadios[0].setSelected(true);

        menu.addSeparator();
        JMenu layout = new JMenu(I18n.t("view.layout"));
        layoutRadios = radioGroup(layout, new String[]{I18n.t("view.layout.full"), I18n.t("view.layout.preview"), I18n.t("view.layout.source")},
                new String[]{com.axolotl.mcart.util.AppConfig.LAYOUT_FULL,
                        com.axolotl.mcart.util.AppConfig.LAYOUT_PREVIEW,
                        com.axolotl.mcart.util.AppConfig.LAYOUT_SOURCE},
                host::setLayoutMode, host.layoutMode());
        menu.add(layout);

        JMenu theme = new JMenu(I18n.t("view.theme"));
        themeRadios = radioGroup(theme, new String[]{I18n.t("view.theme.dark"), I18n.t("view.theme.light"), I18n.t("view.theme.hc")},
                new String[]{com.axolotl.mcart.util.AppConfig.THEME_DARK,
                        com.axolotl.mcart.util.AppConfig.THEME_LIGHT,
                        com.axolotl.mcart.util.AppConfig.THEME_HIGH_CONTRAST},
                host::setTheme, host.theme());
        menu.add(theme);

        JMenu lang = new JMenu(I18n.t("view.language"));
        langRadios = radioGroup(lang, new String[]{I18n.t("view.lang.zh"), I18n.t("view.lang.en")},
                new String[]{com.axolotl.mcart.util.AppConfig.LANG_ZH,
                        com.axolotl.mcart.util.AppConfig.LANG_EN},
                host::setLanguage, host.language());
        menu.add(lang);

        menu.addSeparator();
        menu.add(item(I18n.t("view.reset3d"), host::reset3DView));
        menu.add(item(I18n.t("view.fit"), host::fitViews));
        return menu;
    }

    private JRadioButtonMenuItem[] radioGroup(JMenu parent, String[] labels, String[] values,
                                              java.util.function.Consumer<String> onChange,
                                              String current) {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem[] radios = new JRadioButtonMenuItem[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final String v = values[i];
            JRadioButtonMenuItem r = new JRadioButtonMenuItem(labels[i]);
            r.putClientProperty("value", v);
            r.setSelected(v.equals(current));
            r.addActionListener(e -> onChange.accept(v));
            g.add(r);
            radios[i] = r;
            parent.add(r);
        }
        return radios;
    }

    private JMenu buildGenerateMenu() {
        JMenu menu = createMenu(" "+I18n.t("menu.generate")+" ", KeyEvent.VK_G);
        generateItem = item(I18n.t("generate.run"), host::generate);
        generateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        menu.add(generateItem);
        return menu;
    }

    private JMenu buildToolsMenu() {
        JMenu menu = createMenu(" "+I18n.t("menu.tools")+" ", KeyEvent.VK_T);

        JMenuItem blocks = item(I18n.t("edit.blocks"), host::openBlockLibrary);
        blocks.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK));
        menu.add(blocks);
        menu.add(item(I18n.t("tools.search"), host::searchBlocks));
        menu.add(item(I18n.t("tools.presets"), host::openPresets));
        menu.add(item(I18n.t("tools.customBlock"), host::openCustomBlock));

        menu.addSeparator();
        bomItem = item(I18n.t("tools.bom"), host::openBom);
        menu.add(bomItem);
        batchItem = item(I18n.t("tools.batch"), host::openBatch);
        menu.add(batchItem);
        snapshotItem = item(I18n.t("tools.snapshots"), host::openSnapshots);
        menu.add(snapshotItem);

        menu.addSeparator();
        warningsItem = item(I18n.t("titlebar.bell"), host::openWarnings);
        menu.add(warningsItem);
        JMenuItem settings = item(I18n.t("tools.settings"), host::openSettings);
        settings.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
        menu.add(settings);
        return menu;
    }

    private JMenu buildHelpMenu() {
        JMenu menu = createMenu(" "+I18n.t("menu.help")+" ", KeyEvent.VK_H);
        JMenuItem manual = item(I18n.t("help.howto"), host::showHelp);
        manual.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        menu.add(manual);
        menu.add(item(I18n.t("help.sample"), host::openSample));
        menu.addSeparator();
        menu.add(item(I18n.t("help.about"), host::showAbout));
        return menu;
    }

    // ------------------------------------------------------- 全局鼠标 / 快捷键

    private void onGlobalEvent(AWTEvent e) {
        if (e instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_PRESSED) {
            if (mode != Mode.MENUS) return;
            Component src = me.getComponent();
            if (src == null) return;
            if (SwingUtilities.isDescendingFrom(src, this)) return;
            SwingUtilities.invokeLater(() -> {
                if (mode == Mode.MENUS) setMode(Mode.COMPACT);
            });
            return;
        }

        if (e instanceof KeyEvent ke && ke.getID() == KeyEvent.KEY_PRESSED) {
            // 全局快捷键已改为根窗格 WHEN_IN_FOCUSED_WINDOW 绑定（见 MainFrame.registerGlobalKeys），
            // 这里只保留「双击 Shift 聚焦方块库搜索」这一特殊手势。
            if (ke.getKeyCode() != KeyEvent.VK_SHIFT) { lastShiftPress = 0L; return; }
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win == null || focusOwner == null) return;
            if (!SwingUtilities.isDescendingFrom(focusOwner, win)) return;
            if (focusOwner instanceof JTextComponent) return;
            long now = ke.getWhen();
            if (now - lastShiftPress <= 450) {
                lastShiftPress = 0L;
                host.searchBlocks();
            } else {
                lastShiftPress = now;
            }
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Toolkit.getDefaultToolkit().addAWTEventListener(
                globalListener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        // 窗口显示后再补建菜单，避免菜单构建拖慢首屏
        if (!menusBuilt && menusBuildTimer == null) {
            menusBuildTimer = new Timer(200, e -> ensureMenusBuilt());
            menusBuildTimer.setRepeats(false);
            menusBuildTimer.start();
        }
    }

    @Override
    public void removeNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(globalListener);
        if (menusBuildTimer != null) { menusBuildTimer.stop(); menusBuildTimer = null; }
        super.removeNotify();
    }

    // ------------------------------------------------------------- 图标按钮

    /** 预处理齿轮：琥珀圆点表示预处理开启。 */
    private static class GearButton extends TitleIcons.Button {
        private boolean dot;
        GearButton() { super(TitleIcons.gearOutline, 36, 30, 8); }

        void setDot(boolean dot) {
            if (this.dot != dot) { this.dot = dot; repaint(); }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!dot) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = UIScale.scale(6);
                int x = getWidth() / 2 + UIScale.scale(2);
                int y = getHeight() / 2 - UIScale.scale(9);
                g2.setColor(TitleTheme.NOTIFICATION_DOT);
                g2.fill(new RoundRectangle2D.Double(x, y, d, d, d, d));
            } finally {
                g2.dispose();
            }
        }
    }

    /** 质量警告铃铛：有警告时显示红色徽标数字。 */
    private class BellButton extends TitleIcons.Button {
        private int count;
        BellButton() {
            super(TitleIcons.bell, 34, 30, 8);
        }
        void setCount(int n) {
            if (this.count != n) { this.count = n; repaint(); }
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (count <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = UIScale.scale(13);
                int x = getWidth() / 2 + UIScale.scale(3);
                int y = getHeight() / 2 - UIScale.scale(11);
                g2.setColor(new Color(0xE55765));
                g2.fillOval(x, y, d, d);
                g2.setColor(Color.WHITE);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 9f));
                FontMetrics fm = g2.getFontMetrics();
                String t = count > 9 ? "!" : String.valueOf(count);
                g2.drawString(t, x + (d - fm.stringWidth(t)) / 2f, y + d - 3.5f);
            } finally {
                g2.dispose();
            }
        }
    }
}
