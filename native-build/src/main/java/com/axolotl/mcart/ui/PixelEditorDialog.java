package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.ColorMatcher;
import com.axolotl.mcart.core.PixelArtGenerator;
import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.model.MapMode;
import com.axolotl.mcart.util.BlockNames;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.util.TextureManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 像素级 MC 纹理编辑：在生成结果上逐格涂抹顶层方块（笔刷 / 取色 / 擦除）。
 * 编辑直接改写 {@link PixelArtGenerator.Result} 的顶层数据；
 * 「重新生成」会按参数还原。仅平面模式支持擦除。
 *
 * <p><b>性能策略</b>：画布按当前缩放预渲染一张离屏底图（backing image），
 * 每格纹理走 {@link TextureManager#getScaled} 缓存，顶层坐标 {@code topY} 也缓存；
 * 涂抹时只重画笔刷覆盖的脏格并 {@code repaint(脏矩形)}，
 * 整帧只做一次底图 blit，避免每帧上万次纹理缩放。
 * 当底图过大（高缩放大图）时退化为按可见裁剪区域逐格绘制，防止 OOM。
 */
public class PixelEditorDialog extends AppDialog {

    /** 可选缩放档位（初始缩放会吸附到最近的档位，保证下拉框显示值与实际一致）。 */
    private static final int[] ZOOMS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20};

    private static int nearestZoom(int raw) {
        int best = ZOOMS[0];
        for (int z : ZOOMS) {
            if (Math.abs(z - raw) < Math.abs(best - raw)) best = z;
        }
        return best;
    }

    private final PixelArtGenerator.Result result;
    private final Runnable onEdit;
    private final ColorMatcher matcher;
    private final List<BlockColor> brushBlocks;

    /** 方块 → 18px 列表图标（只缩放一次）。 */
    private final Map<BlockColor, Icon> blockIconCache = new HashMap<>();

    private BlockColor currentBlock;
    private int brushSize = 1;
    private boolean eraser;
    private int zoom;

    private GridCanvas grid;
    private JList<BlockColor> blockList;
    private boolean ready = true;

    // 取消时还原
    private final int[][][] backupData;
    private final int[][] backupPreview;
    private final int[][] backupHeight;

    public PixelEditorDialog(JFrame owner, PixelArtGenerator.Result result, List<BlockColor> available,
                             MapMode mode, Runnable onEdit) {
        super(owner, I18n.t("pixel.title"), true);
        this.result = result;
        this.onEdit = onEdit;
        this.brushBlocks = new ArrayList<>(available);
        this.matcher = new ColorMatcher(brushBlocks, mode);
        this.backupData = deepCopy(result.blockIds);
        this.backupPreview = deepCopy2(result.previewPixels);
        this.backupHeight = deepCopy2(result.heightMap);

        if (brushBlocks.isEmpty()) {
            JOptionPane.showMessageDialog(owner, I18n.t("pixel.needBlocks"));
            ready = false;
            close();
            return;
        }
        currentBlock = brushBlocks.get(0);
        int raw = Math.max(1, Math.min(20, 760 / Math.max(result.width, result.length)));
        zoom = nearestZoom(raw);

        build();
        setupSize(1020, 740);
    }

    /** 构造时若缺少可用方块则为 false，调用方不应再显示该对话框。 */
    public boolean isReady() { return ready; }

    private void build() {
        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);

        // —— 左侧工具卡片 ——
        RoundedPanel tools = card();
        tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));
        tools.setInsets(new Insets(ui(12), ui(12), ui(12), ui(12)));
        tools.setPreferredSize(new Dimension(ui(220), 0));

        JLabel brushTitle = cardTitle(I18n.t("pixel.brushBlock"));
        brushTitle.setAlignmentX(LEFT_ALIGNMENT);
        brushTitle.setBorder(new EmptyBorder(0, 0, ui(8), 0));
        tools.add(brushTitle);

        DefaultListModel<BlockColor> lm = new DefaultListModel<>();
        for (BlockColor bc : brushBlocks) {
            lm.addElement(bc);
            blockIconCache.put(bc, new ImageIcon(TextureManager.getScaled(bc, 18)));
        }
        blockList = new JList<>(lm);
        blockList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blockList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof BlockColor bc) {
                    setText(BlockNames.of(bc.blockId(), bc.name()));
                    setIcon(blockIconCache.get(bc));
                }
                return this;
            }
        });
        blockList.setSelectedIndex(0);
        blockList.setBackground(UiTheme.CARD_BG);
        blockList.addListSelectionListener(e -> {
            BlockColor bc = blockList.getSelectedValue();
            if (bc != null) { currentBlock = bc; eraser = false; }
        });
        JScrollPane blockScroll = new JScrollPane(blockList);
        blendScroll(blockScroll, UiTheme.CARD_BG, true);
        blockScroll.setAlignmentX(LEFT_ALIGNMENT);
        tools.add(blockScroll);

        tools.add(Box.createVerticalStrut(10));
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        sizeRow.setOpaque(false);
        sizeRow.setAlignmentX(LEFT_ALIGNMENT);
        sizeRow.add(new JLabel(I18n.t("pixel.brushSize")));
        JComboBox<Integer> sizeCombo = new JComboBox<>(new Integer[]{1, 3, 5});
        sizeCombo.addActionListener(e -> brushSize = (Integer) sizeCombo.getSelectedItem());
        sizeRow.add(sizeCombo);
        tools.add(sizeRow);

        JCheckBox eraseCheck = new JCheckBox(I18n.t("pixel.eraseFlat"));
        eraseCheck.setOpaque(false);
        eraseCheck.setAlignmentX(LEFT_ALIGNMENT);
        eraseCheck.addActionListener(e -> eraser = eraseCheck.isSelected());
        tools.add(eraseCheck);

        Integer[] zoomItems = new Integer[ZOOMS.length];
        for (int i = 0; i < ZOOMS.length; i++) zoomItems[i] = ZOOMS[i];
        JComboBox<Integer> zoomCombo = new JComboBox<>(zoomItems);
        zoomCombo.setSelectedItem(zoom);
        zoomCombo.addActionListener(e -> {
            zoom = (Integer) zoomCombo.getSelectedItem();
            grid.resize();
        });
        JPanel zoomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        zoomRow.setOpaque(false);
        zoomRow.setAlignmentX(LEFT_ALIGNMENT);
        zoomRow.add(new JLabel(I18n.t("pixel.zoom")));
        zoomRow.add(zoomCombo);
        tools.add(zoomRow);

        tools.add(Box.createVerticalStrut(8));
        JTextArea hint = hintLabel(I18n.t("pixel.hint2"), 190);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        tools.add(hint);
        tools.add(Box.createVerticalGlue());

        body.add(tools, BorderLayout.WEST);

        // —— 画布卡片 ——
        RoundedPanel canvasCard = card(new BorderLayout());
        canvasCard.setInsets(new Insets(ui(8), ui(8), ui(8), ui(8)));
        grid = new GridCanvas();
        JScrollPane gridScroll = new JScrollPane(grid);
        blendScroll(gridScroll, UiTheme.VIEWER_BG, false);
        canvasCard.add(gridScroll, BorderLayout.CENTER);
        body.add(canvasCard, BorderLayout.CENTER);

        setBody(body);

        // —— 底部按钮 ——
        Footer footer = new Footer();
        JButton cancel = actionButton(I18n.t("pixel.abandon"));
        cancel.addActionListener(e -> {
            restore(backupData, backupPreview, backupHeight);
            onEdit.run();
            close();
        });
        JButton ok = primaryActionButton(I18n.t("pixel.done"));
        ok.addActionListener(e -> { onEdit.run(); close(); });
        footer.addButton(cancel);
        footer.addButton(ok, true);
        setFooter(footer);
    }

    private int topY(int z, int x) {
        for (int y = result.height - 1; y >= 0; y--) {
            int idx = result.blockIds[y][z][x];
            if (idx >= 0) return y;
        }
        return -1;
    }

    private void paintAt(int x, int z, boolean pick) {
        if (x < 0 || z < 0 || x >= result.width || z >= result.length) return;
        if (pick) {
            int y = topY(z, x);
            if (y >= 0) {
                int idx = result.blockIds[y][z][x];
                BlockColor bc = result.palette.get(idx).baseColor;
                int li = brushBlocks.indexOf(bc);
                if (li >= 0) {
                    blockList.setSelectedIndex(li);
                    currentBlock = bc;
                }
            }
            return;
        }
        int radius = brushSize / 2;
        int minX = x, minZ = z, maxX = x, maxZ = z;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int xx = x + dx, zz = z + dz;
                if (xx < 0 || zz < 0 || xx >= result.width || zz >= result.length) continue;
                applyCell(xx, zz);
                if (xx < minX) minX = xx;
                if (zz < minZ) minZ = zz;
                if (xx > maxX) maxX = xx;
                if (zz > maxZ) maxZ = zz;
            }
        }
        grid.cellsChanged(minX, minZ, maxX, maxZ);
    }

    private void applyCell(int x, int z) {
        int y = topY(z, x);
        if (eraser) {
            if (result.height != 1) return; // 仅平面
            result.blockIds[0][z][x] = PixelArtGenerator.AIR;
            result.previewPixels[z][x] = 0;
            result.topEntries[z][x] = null;
            result.heightMap[z][x] = 0;
            return;
        }
        int wantedShade = 1;
        if (y >= 0) wantedShade = result.palette.get(result.blockIds[y][z][x]).shade;
        ColorMatcher.PaletteEntry e = matcher.require(currentBlock, wantedShade);
        int idx = result.palette.indexOf(e);
        if (idx < 0) {
            result.palette.add(e);
            idx = result.palette.size() - 1;
        }
        if (y < 0) {
            result.blockIds[0][z][x] = idx;
            result.heightMap[z][x] = 1;
        } else {
            result.blockIds[y][z][x] = idx;
        }
        result.previewPixels[z][x] = (e.r << 16) | (e.g << 8) | e.b;
        result.topEntries[z][x] = e;
    }

    private static int[][][] deepCopy(int[][][] src) {
        int[][][] dst = new int[src.length][][];
        for (int y = 0; y < src.length; y++) {
            dst[y] = new int[src[y].length][];
            for (int z = 0; z < src[y].length; z++) dst[y][z] = src[y][z].clone();
        }
        return dst;
    }
    private static int[][] deepCopy2(int[][] src) {
        int[][] dst = new int[src.length][];
        for (int z = 0; z < src.length; z++) dst[z] = src[z].clone();
        return dst;
    }
    private void restore(int[][][] data, int[][] preview, int[][] height) {
        for (int y = 0; y < result.height; y++)
            for (int z = 0; z < result.length; z++) {
                System.arraycopy(data[y][z], 0, result.blockIds[y][z], 0, data[y][z].length);
            }
        for (int z = 0; z < result.length; z++) {
            System.arraycopy(preview[z], 0, result.previewPixels[z], 0, preview[z].length);
            System.arraycopy(height[z], 0, result.heightMap[z], 0, height[z].length);
        }
    }

    /** 方块网格画布（离屏底图 + 局部重绘）。 */
    private class GridCanvas extends JComponent {
        /** 离屏底图像素上限（约 48MB），超出则退化为裁剪绘制。 */
        private static final long MAX_BACKING_PIXELS = 12_000_000L;

        private BufferedImage backing;
        private int[][] topYCache;
        private boolean useBacking;
        private final int gridW, gridH;

        GridCanvas() {
            gridW = result.width;
            gridH = result.length;
            setOpaque(false);
            resize();
        }

        void resize() {
            setPreferredSize(new Dimension(gridW * zoom, gridH * zoom));
            rebuildTopCache();
            rebuildBacking();
            revalidate();
            repaint();
        }

        private void rebuildTopCache() {
            topYCache = new int[gridH][gridW];
            for (int z = 0; z < gridH; z++)
                for (int x = 0; x < gridW; x++)
                    topYCache[z][x] = topY(z, x);
        }

        /** 按当前缩放把整张网格预渲染到离屏底图。 */
        private void rebuildBacking() {
            long total = (long) gridW * zoom * gridH * zoom;
            useBacking = total <= MAX_BACKING_PIXELS;
            if (!useBacking) {
                backing = null;
                return;
            }
            backing = new BufferedImage(gridW * zoom, gridH * zoom, BufferedImage.TYPE_INT_RGB);
            Graphics2D b = backing.createGraphics();
            b.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            b.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            for (int z = 0; z < gridH; z++)
                for (int x = 0; x < gridW; x++)
                    drawCell(b, x, z);
            if (zoom >= 6) paintGridLines(b, 0, 0, gridW, gridH);
            b.dispose();
        }

        /** 某格涂抹 / 擦除后：刷新该列顶层坐标，只重绘脏格并局部重绘。 */
        void cellsChanged(int minX, int minZ, int maxX, int maxZ) {
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++)
                    topYCache[z][x] = topY(z, x);
            if (useBacking && backing != null) {
                Graphics2D b = backing.createGraphics();
                b.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                b.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                for (int z = minZ; z <= maxZ; z++)
                    for (int x = minX; x <= maxX; x++)
                        drawCell(b, x, z);
                if (zoom >= 6) paintGridLines(b, minX, minZ, maxX + 1, maxZ + 1);
                b.dispose();
            }
            repaint(minX * zoom, minZ * zoom,
                    (maxX - minX + 1) * zoom, (maxZ - minZ + 1) * zoom);
        }

        /** 绘制单格：空气画棋盘底，否则画缓存的缩放纹理。 */
        private void drawCell(Graphics2D g, int x, int z) {
            int px = x * zoom, pz = z * zoom;
            int y = topYCache[z][x];
            if (y < 0) {
                g.setColor(((x + z) & 1) == 0 ? UiTheme.CHECKER_B : UiTheme.CHECKER_A);
                g.fillRect(px, pz, zoom, zoom);
            } else {
                BlockColor bc = result.palette.get(result.blockIds[y][z][x]).baseColor;
                g.drawImage(TextureManager.getScaled(bc, zoom), px, pz, null);
            }
        }

        /**
         * 在单元格坐标范围 [x0..x1]×[z0..z1] 上绘制不透明网格线（含右、下边界）。
         * 网格线被烘焙进离屏底图，运行时不再逐帧用半透明色绘制，避免卡顿。
         */
        private void paintGridLines(Graphics2D g, int x0, int z0, int x1, int z1) {
            int right = Math.min(gridW * zoom, x1 * zoom);
            int bottom = Math.min(gridH * zoom, z1 * zoom);
            g.setColor(UiTheme.GRID_LINE);
            for (int x = x0; x <= x1; x++) {
                int px = Math.min(gridW * zoom, x * zoom);
                g.drawLine(px, z0 * zoom, px, bottom);
            }
            for (int z = z0; z <= z1; z++) {
                int pz = Math.min(gridH * zoom, z * zoom);
                g.drawLine(x0 * zoom, pz, right, pz);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            Rectangle clip = g2.getClipBounds();
            if (clip == null) clip = new Rectangle(0, 0, getWidth(), getHeight());

            if (useBacking && backing != null) {
                // 网格与棋盘底已烘焙进 backing，整帧只需一次位图拷贝
                g2.drawImage(backing, 0, 0, null);
            } else {
                g2.setColor(UiTheme.VIEWER_BG);
                g2.fillRect(clip.x, clip.y, clip.width, clip.height);
                int x0 = Math.max(0, clip.x / zoom);
                int x1 = Math.min(gridW - 1, (clip.x + clip.width) / zoom);
                int z0 = Math.max(0, clip.y / zoom);
                int z1 = Math.min(gridH - 1, (clip.y + clip.height) / zoom);
                for (int z = z0; z <= z1; z++)
                    for (int x = x0; x <= x1; x++)
                        drawCell(g2, x, z);
                if (zoom >= 6) paintGridLines(g2, x0, z0, x1 + 1, z1 + 1);
            }
            g2.dispose();
        }

        {
            MouseAdapter ma = new MouseAdapter() {
                private boolean painting;
                private int button;
                private void act(MouseEvent e) {
                    int x = e.getX() / zoom, z = e.getY() / zoom;
                    paintAt(x, z, button == 3);
                }
                @Override public void mousePressed(MouseEvent e) {
                    painting = true; button = e.getButton(); act(e);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (painting && button == 1) act(e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (painting) { painting = false; onEdit.run(); }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }
    }
}
