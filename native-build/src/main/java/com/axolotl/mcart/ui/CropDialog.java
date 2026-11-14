package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * 裁剪与 ROI 选区对话框：在原图上拖拽矩形，只转换选中区域（ROI）。
 * 坐标全部归一化（0..1），与工程保存格式一致。
 */
public class CropDialog extends AppDialog {

    private final BufferedImage image;
    private final float targetRatio; // 目标宽高比（张数比）
    private final Consumer<Rectangle2D.Float> onApply; // null = 清除选区
    private double scale;
    private int offX, offY;

    // 归一化选区
    private float rx, ry, rw, rh;
    private boolean hasSelection;
    private int dragStartX = -1, dragStartY = -1;

    private final JSpinner xSp = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner ySp = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner wSp = new JSpinner(new SpinnerNumberModel(100, 1, 100, 1));
    private final JSpinner hSp = new JSpinner(new SpinnerNumberModel(100, 1, 100, 1));
    private boolean syncing;
    private final JPanel canvas;

    public CropDialog(JFrame owner, BufferedImage image, Rectangle2D.Float current,
                      int mapsW, int mapsH, Consumer<Rectangle2D.Float> onApply) {
        super(owner, I18n.t("crop.title"), true);
        this.image = image;
        this.targetRatio = (float) mapsW / mapsH;
        this.onApply = onApply;
        if (current != null) {
            hasSelection = true;
            rx = current.x; ry = current.y; rw = current.width; rh = current.height;
        }

        // —— 画布卡片 ——
        canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(UiTheme.VIEWER_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                computeTransform();
                int dw = (int) Math.round(image.getWidth() * scale);
                int dh = (int) Math.round(image.getHeight() * scale);
                g2.drawImage(image, offX, offY, dw, dh, null);
                g2.setColor(UiTheme.alpha(UiTheme.BORDER, 180));
                g2.drawRect(offX - 1, offY - 1, dw + 1, dh + 1);
                if (hasSelection) {
                    int x = offX + (int) Math.round(rx * image.getWidth() * scale);
                    int y = offY + (int) Math.round(ry * image.getHeight() * scale);
                    int w = (int) Math.round(rw * image.getWidth() * scale);
                    int h = (int) Math.round(rh * image.getHeight() * scale);
                    g2.setColor(new Color(0, 0, 0, 150));
                    g2.fillRect(offX, offY, dw, dh);
                    g2.drawImage(image, x, y, w, h,
                            Math.round(rx * image.getWidth()), Math.round(ry * image.getHeight()),
                            Math.round((rx + rw) * image.getWidth()), Math.round((ry + rh) * image.getHeight()), null);
                    g2.setColor(UiTheme.ACCENT);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRect(x, y, w, h);
                }
                g2.dispose();
            }
        };
        canvas.setBackground(UiTheme.VIEWER_BG);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragStartX = e.getX();
                dragStartY = e.getY();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragStartX < 0) return;
                float x1 = toNormX(dragStartX), y1 = toNormY(dragStartY);
                float x2 = toNormX(e.getX()), y2 = toNormY(e.getY());
                rx = Math.max(0, Math.min(x1, x2));
                ry = Math.max(0, Math.min(y1, y2));
                rw = Math.min(1, Math.abs(x2 - x1));
                rh = Math.min(1, Math.abs(y2 - y1));
                if (rw > 0.01f && rh > 0.01f) {
                    hasSelection = true;
                    syncSpinners();
                    canvas.repaint();
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragStartX = -1;
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);

        RoundedPanel canvasCard = card(new BorderLayout());
        canvasCard.setInsets(new Insets(ui(8), ui(8), ui(8), ui(8)));
        canvasCard.setBackground(UiTheme.CARD_BG);
        canvasCard.add(canvas, BorderLayout.CENTER);
        setBody(canvasCard);

        // —— 控制卡片：数值 + 工具 ——
        RoundedPanel controls = card(new BorderLayout(0, 8));
        controls.setInsets(new Insets(ui(10), ui(12), ui(10), ui(12)));

        JPanel numeric = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        numeric.setOpaque(false);
        for (JComponent[] pair : new JComponent[][]{
                {new JLabel(I18n.t("crop.xp")), xSp}, {new JLabel(I18n.t("crop.yp")), ySp},
                {new JLabel(I18n.t("crop.widthP")), wSp}, {new JLabel(I18n.t("crop.heightP")), hSp}}) {
            numeric.add(pair[0]);
            numeric.add(pair[1]);
            pair[1].setPreferredSize(new Dimension(ui(58), ui(26)));
        }
        controls.add(numeric, BorderLayout.NORTH);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        JButton ratioBtn = actionButton(I18n.t("crop.ratioBtn", Math.round(targetRatio * 100)));
        ratioBtn.setToolTipText(I18n.t("crop.ratioTip"));
        ratioBtn.addActionListener(e -> fitRatio());
        JButton allBtn = actionButton(I18n.t("crop.all"));
        JButton clearBtn = actionButton(I18n.t("crop.clear"));
        allBtn.addActionListener(e -> {
            hasSelection = true; rx = 0; ry = 0; rw = 1; rh = 1; syncSpinners(); canvas.repaint();
        });
        clearBtn.addActionListener(e -> {
            hasSelection = false; rx = 0; ry = 0; rw = 1; rh = 1; syncSpinners(); canvas.repaint();
        });
        tools.add(ratioBtn); tools.add(allBtn); tools.add(clearBtn);
        controls.add(tools, BorderLayout.CENTER);

        javax.swing.event.ChangeListener cl = e -> {
            if (syncing) return;
            hasSelection = true;
            rx = intVal(xSp) / 100f; ry = intVal(ySp) / 100f;
            rw = intVal(wSp) / 100f; rh = intVal(hSp) / 100f;
            clampAndFix();
            canvas.repaint();
        };
        xSp.addChangeListener(cl); ySp.addChangeListener(cl);
        wSp.addChangeListener(cl); hSp.addChangeListener(cl);
        for (JSpinner sp : new JSpinner[]{xSp, ySp, wSp, hSp}) {
            com.axolotl.mcart.util.InputMethodHints.disableCompositionWhileFocused(sp);
        }
        syncSpinners();

        // —— 底部：控制卡片 + 按钮栏 ——
        Footer footer = new Footer();
        JButton cancel = actionButton(I18n.t("common.cancel"));
        cancel.addActionListener(e -> close());
        JButton ok = primaryActionButton(I18n.t("crop.ok"));
        ok.addActionListener(e -> {
            if (hasSelection) {
                clampAndFix();
                onApply.accept(new Rectangle2D.Float(rx, ry, rw, rh));
            } else {
                onApply.accept(null);
            }
            close();
        });
        footer.addButton(cancel);
        footer.addButton(ok, true);

        JPanel southAll = new JPanel(new BorderLayout(0, ui(12)));
        southAll.setOpaque(false);
        southAll.add(controls, BorderLayout.CENTER);
        southAll.add(footer, BorderLayout.SOUTH);
        root.add(southAll, BorderLayout.SOUTH);

        setupSize(760, 640);
    }

    private static int intVal(JSpinner sp) { return ((Number) sp.getValue()).intValue(); }

    private void syncSpinners() {
        syncing = true;
        xSp.setValue(Math.round(rx * 100));
        ySp.setValue(Math.round(ry * 100));
        wSp.setValue(Math.max(1, Math.round(rw * 100)));
        hSp.setValue(Math.max(1, Math.round(rh * 100)));
        syncing = false;
    }

    private void clampAndFix() {
        rw = Math.max(0.01f, Math.min(1 - rx, rw));
        rh = Math.max(0.01f, Math.min(1 - ry, rh));
        rx = Math.max(0, Math.min(rx, 1 - rw));
        ry = Math.max(0, Math.min(ry, 1 - rh));
        syncSpinners();
    }

    private void fitRatio() {
        if (!hasSelection) { rx = 0; ry = 0; rw = 1; rh = 1; hasSelection = true; }
        float cx = rx + rw / 2, cy = ry + rh / 2;
        float nw = rw;
        float nh = nw / targetRatio;
        if (nh > 1) { nh = 1; nw = nh * targetRatio; }
        rw = nw; rh = nh;
        rx = Math.max(0, Math.min(1 - rw, cx - rw / 2));
        ry = Math.max(0, Math.min(1 - rh, cy - rh / 2));
        syncSpinners();
        canvas.repaint();
    }

    private void computeTransform() {
        int maxW = getWidthSafe() - 16;
        int maxH = getHeightSafe() - 16;
        scale = Math.min((double) maxW / image.getWidth(), (double) maxH / image.getHeight());
        int dw = (int) Math.round(image.getWidth() * scale);
        int dh = (int) Math.round(image.getHeight() * scale);
        offX = (maxW + 16 - dw) / 2;
        offY = (maxH + 16 - dh) / 2;
    }

    private int getWidthSafe() { return Math.max(100, canvas.getWidth()); }
    private int getHeightSafe() { return Math.max(100, canvas.getHeight()); }

    private float toNormX(int px) {
        return Math.max(0, Math.min(1, (float) ((px - offX) / (image.getWidth() * scale))));
    }
    private float toNormY(int py) {
        return Math.max(0, Math.min(1, (float) ((py - offY) / (image.getHeight() * scale))));
    }
}
