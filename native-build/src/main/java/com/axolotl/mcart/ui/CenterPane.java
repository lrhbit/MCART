package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.Project;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 中央区：文档标签栏（仅位于查看器列上方）+ 原图 / MC 预览双查看器，
 * MC 预览支持纹理 / 配色 / 3D / 色域四种模式（3D 画布懒加载）。
 * 从 MainFrame.buildCenterArea 抽出。
 */
public class CenterPane extends JPanel {

    public interface Listener {
        /** 标签栏加号：新建工程（主窗口负责先保存当前控件状态）。 */
        void newProject();
        /** 切换工程标签。 */
        void selectDoc(int index);
        /** 用户切换预览模式（0 纹理 1 配色 2 立体 3 色域）。 */
        void previewModeChanged(int index);
    }

    public static final String CARD_2D = "2d";
    public static final String CARD_3D = "3d";

    private final Listener listener;

    private final JTabbedPane tabbedPane;
    private final ViewerPane sourceViewer = new ViewerPane(I18n.t("viewer.source"));
    private final ViewerPane previewViewer = new ViewerPane("", false);
    private final JComboBox<String> previewModeCombo;
    private final CardLayout previewCards = new CardLayout();
    private final JPanel previewContainer = new JPanel(previewCards);
    private JSplitPane viewersSplit;

    private Model3DCanvas model3DCanvas;
    private JPanel modelPane;
    private boolean syncingTabs;

    public CenterPane(Listener listener) {
        super(new BorderLayout());
        this.listener = listener;
        setBackground(UiTheme.VIEWER_BG);

        // 标签页颜色互换：标签槽位用深色，整条标签栏背景用 RootPane 色
        UIManager.put("TabbedPane.focusColor", UiTheme.TAB_WELL);
        tabbedPane = new JTabbedPane() {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        tabbedPane.putClientProperty("JTabbedPane.tabType", "underlined");
        tabbedPane.putClientProperty("JTabbedPane.showTabSeparators", false);
        tabbedPane.putClientProperty("JTabbedPane.tabHeight", 30);
        tabbedPane.putClientProperty("JTabbedPane.tabAreaAlignment", "leading");
        tabbedPane.putClientProperty("JTabbedPane.tabInsets", new Insets(4, 12, 4, 12));
        tabbedPane.putClientProperty("JTabbedPane.hasFullBorder", false);
        tabbedPane.setBorder(null);
        tabbedPane.setOpaque(true);
        tabbedPane.setBackground(UiTheme.TAB_WELL);
        tabbedPane.addChangeListener(e -> {
            if (syncingTabs) return;
            int idx = tabbedPane.getSelectedIndex();
            if (idx >= 0) listener.selectDoc(idx);
        });

        add(buildTabBar(), BorderLayout.NORTH);
        add(buildViewers(), BorderLayout.CENTER);

        previewModeCombo = new JComboBox<>(new String[]{
                I18n.t("view.pv.texture"), I18n.t("view.pv.color"),
                I18n.t("view.pv.model3d"), I18n.t("view.pv.gamut")});
        previewModeCombo.setPrototypeDisplayValue(I18n.t("view.pv.texture"));
        previewModeCombo.setToolTipText(I18n.t("viewer.mode.tip"));
        previewModeCombo.addActionListener(e ->
                listener.previewModeChanged(previewModeCombo.getSelectedIndex()));
        // 预览头部右侧：适应比例 + 复位 + 模式切换
        previewHeaderRight.add(previewModeCombo);
    }

    // ---------------------------------------------------------------- 标签栏

    private final JPanel previewHeaderRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

    private JComponent buildTabBar() {
        JButton plus = new JButton();
        Icon plusIcon = new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                g2.setStroke(new BasicStroke(1.5f));
                int cx = x + 7, cy = y + 7;
                g2.drawLine(cx - 4, cy, cx + 4, cy);
                g2.drawLine(cx, cy - 4, cx, cy + 4);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 14; }
            @Override public int getIconHeight() { return 14; }
        };
        Color chrome = UiTheme.chrome();
        plus.setIcon(plusIcon);
        plus.setFocusPainted(false);
        plus.setContentAreaFilled(false);
        plus.setOpaque(true);
        plus.setBackground(chrome);
        plus.setForeground(UiTheme.ICON_TEXT);
        plus.setToolTipText(I18n.t("tab.newTip"));
        plus.setPreferredSize(new Dimension(28, 28));
        plus.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                plus.setBackground(UiTheme.HOVER); plus.setForeground(UiTheme.SECTION_TITLE);
            }
            @Override public void mouseExited(MouseEvent e) {
                plus.setBackground(chrome); plus.setForeground(UiTheme.ICON_TEXT);
            }
            @Override public void mousePressed(MouseEvent e) { plus.setBackground(UiTheme.HOVER_STRONG); }
            @Override public void mouseReleased(MouseEvent e) {
                plus.setBackground(plus.getMousePosition() != null ? UiTheme.HOVER : chrome);
            }
        });
        plus.addActionListener(e -> listener.newProject());

        JPanel tabContainer = new JPanel(new GridBagLayout());
        tabContainer.setBackground(chrome);
        tabContainer.setOpaque(true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        tabContainer.add(tabbedPane, gbc);
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 2, 0, 0);
        tabContainer.add(plus, gbc);
        gbc.gridx = 2; gbc.weightx = 1;
        tabContainer.add(Box.createHorizontalGlue(), gbc);
        tabContainer.setPreferredSize(new Dimension(0, 32));
        return tabContainer;
    }

    /** 重建标签头（工程增删 / 改名 / 脏标记变化后调用）。 */
    public void reloadTabs(List<Project> docs, int active, java.util.function.IntConsumer onClose) {
        syncingTabs = true;
        tabbedPane.removeAll();
        for (int i = 0; i < docs.size(); i++) {
            Project p = docs.get(i);
            JPanel dummy = new JPanel();
            dummy.setPreferredSize(new Dimension(0, 0));
            final int index = i;
            tabbedPane.addTab(p.getName(), dummy);
            tabbedPane.setTabComponentAt(i, new DocTabHeader(p, i, i == active, () -> onClose.accept(index)));
        }
        if (active >= 0 && active < docs.size()) tabbedPane.setSelectedIndex(active);
        syncingTabs = false;
    }

    // ---------------------------------------------------------------- 查看器

    private JComponent buildViewers() {
        previewContainer.add(previewViewer, CARD_2D);
        // 3D 卡片在首次切换到立体模式时才创建（懒加载，加快启动）

        JPanel previewHeader = new JPanel(new BorderLayout(8, 0));
        previewHeader.setBackground(UiTheme.HEADER_BG);
        previewHeader.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                new EmptyBorder(4, 10, 4, 8)));
        JLabel previewTitle = new JLabel(I18n.t("viewer.mc"));
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD, 12.5f));
        previewHeader.add(previewTitle, BorderLayout.WEST);
        previewHeaderRight.setOpaque(false);
        JLabel previewZoomLabel = new JLabel(I18n.t("viewer.fit"));
        previewZoomLabel.setForeground(UiTheme.MUTED_TEXT);
        previewViewer.setExternalZoomLabel(previewZoomLabel);
        JButton previewResetBtn = UiTheme.iconButton(
                com.axolotl.mcart.ui.titlebar.TitleIcons.fitCorners, I18n.t("viewer.reset"), 24);
        previewResetBtn.addActionListener(e -> previewViewer.fit());
        previewHeaderRight.add(previewZoomLabel, 0);
        previewHeaderRight.add(previewResetBtn, 1);
        previewHeader.add(previewHeaderRight, BorderLayout.EAST);

        JPanel previewArea = new JPanel(new BorderLayout());
        previewArea.add(previewHeader, BorderLayout.NORTH);
        previewArea.add(previewContainer, BorderLayout.CENTER);

        viewersSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourceViewer, previewArea);
        viewersSplit.setResizeWeight(0.5);
        viewersSplit.setContinuousLayout(true);
        viewersSplit.setBorder(null);
        UiTheme.styleDivider(viewersSplit);
        return viewersSplit;
    }

    /** 懒加载 3D 画布及其标题栏。 */
    public Model3DCanvas model3D() {
        if (model3DCanvas == null) {
            model3DCanvas = new Model3DCanvas();
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(UiTheme.HEADER_BG);
            header.setBorder(new EmptyBorder(5, 10, 5, 8));
            JLabel hint = new JLabel(I18n.t("view.3dhint"));
            hint.setForeground(UiTheme.MUTED_TEXT);
            header.add(hint, BorderLayout.WEST);
            JButton resetViewBtn = UiTheme.iconButton(
                    com.axolotl.mcart.ui.titlebar.TitleIcons.fitCorners, I18n.t("view.resetTop"), 24);
            resetViewBtn.addActionListener(e -> model3DCanvas.resetView());
            header.add(resetViewBtn, BorderLayout.EAST);
            modelPane = new JPanel(new BorderLayout());
            modelPane.add(header, BorderLayout.NORTH);
            modelPane.add(model3DCanvas, BorderLayout.CENTER);
            previewContainer.add(modelPane, CARD_3D);
        }
        return model3DCanvas;
    }

    /** 已创建才返回，避免仅为复位视角而强制构建 3D 画布。 */
    public Model3DCanvas model3DOrNull() { return model3DCanvas; }

    public void showCard(String card) {
        if (CARD_3D.equals(card)) model3D();
        previewCards.show(previewContainer, card);
    }

    // ---------------------------------------------------------------- 对外

    public ViewerPane sourceViewer() { return sourceViewer; }
    public ViewerPane previewViewer() { return previewViewer; }
    public JSplitPane viewersSplit() { return viewersSplit; }

    public int previewMode() { return previewModeCombo.getSelectedIndex(); }

    /** 程序化同步预览模式（不触发回调）。 */
    public void setPreviewModeIndex(int idx) {
        if (idx < 0 || idx >= previewModeCombo.getItemCount()) return;
        if (previewModeCombo.getSelectedIndex() != idx) previewModeCombo.setSelectedIndex(idx);
    }

    /** 布局模式：完整 / 仅预览 / 仅原图。 */
    public void applyLayout(String layout) {
        sourceViewer.setVisible(true);
        switch (layout) {
            case com.axolotl.mcart.util.AppConfig.LAYOUT_PREVIEW -> {
                viewersSplit.setDividerSize(0);
                SwingUtilities.invokeLater(() -> viewersSplit.setDividerLocation(0.0));
            }
            case com.axolotl.mcart.util.AppConfig.LAYOUT_SOURCE -> {
                viewersSplit.setDividerSize(0);
                SwingUtilities.invokeLater(() -> viewersSplit.setDividerLocation(1.0));
            }
            default -> {
                UiTheme.styleDivider(viewersSplit);
                SwingUtilities.invokeLater(() -> viewersSplit.setDividerLocation(0.5));
            }
        }
        viewersSplit.revalidate();
        viewersSplit.repaint();
    }
}
