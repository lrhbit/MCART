package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * 图片查看器：标题栏 + 可缩放 / 平移的滚动画布。
 * 从 MainFrame 抽出，原图与 MC 预览各用一个实例。
 */
public class ViewerPane extends JPanel {

    private static final double MIN_ZOOM = 0.02;
    private static final double MAX_ZOOM = 40.0;
    private static final double ZOOM_STEP = 1.18;

    private ImageCanvas canvas;
    private final JScrollPane scroll;
    private final JLabel zoomLabel;
    private JLabel externalZoomLabel;
    private boolean fitMode = true;
    private Point panStart;

    public ViewerPane(String title) {
        this(title, true);
    }

    public ViewerPane(String title, boolean showHeader) {
        super(new BorderLayout());

        if (showHeader) {
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(UiTheme.HEADER_BG);
            header.setBorder(new EmptyBorder(5, 10, 5, 8));
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.5f));
            header.add(titleLabel, BorderLayout.WEST);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            zoomLabel = new JLabel(I18n.t("viewer.fit"));
            zoomLabel.setForeground(UiTheme.MUTED_TEXT);
            JButton resetBtn = UiTheme.iconButton(
                    com.axolotl.mcart.ui.titlebar.TitleIcons.fitCorners, I18n.t("viewer.reset"), 24);
            resetBtn.addActionListener(e -> { fitMode = true; canvas.fitToViewport(); });
            right.add(zoomLabel);
            right.add(resetBtn);
            header.add(right, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
        } else {
            zoomLabel = new JLabel(I18n.t("viewer.fit"));
        }

        canvas = new ImageCanvas();
        scroll = new JScrollPane(canvas);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiTheme.VIEWER_BG);
        scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);

        canvas.addMouseWheelListener(e -> {
            if (canvas.getImage() == null) return;
            fitMode = false;
            double oldZoom = canvas.getZoom();
            double newZoom = oldZoom * (e.getWheelRotation() < 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP);
            newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
            JViewport vp = scroll.getViewport();
            Rectangle vr = vp.getViewRect();
            Point mp = e.getPoint();
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
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && canvas.getImage() != null) {
                    fitMode = true; canvas.fitToViewport();
                }
            }
            @Override public void mousePressed(MouseEvent e) { panStart = e.getPoint(); }
        });
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
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
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    canvas.revalidate();
                    canvas.repaint();
                    if (fitMode) {
                        canvas.fitToViewport();
                        updateZoomLabel();
                    }
                });
            }
        });
    }

    public void setExternalZoomLabel(JLabel l) { this.externalZoomLabel = l; }

    public void setImage(BufferedImage img) {
        fitMode = true;
        canvas.setImage(img);
        Runnable refit = () -> { canvas.fitToViewport(); updateZoomLabel(); };
        SwingUtilities.invokeLater(refit);
        Timer t = new Timer(180, e -> { if (fitMode) { canvas.fitToViewport(); updateZoomLabel(); } });
        t.setRepeats(false);
        t.start();
    }

    public void fit() {
        fitMode = true;
        canvas.fitToViewport();
        updateZoomLabel();
    }

    private void updateZoomLabel() {
        String text;
        if (canvas.getImage() == null) text = "—";
        else if (fitMode) text = I18n.t("viewer.fit") + " " + Math.round(canvas.getZoom() * 100) + "%";
        else text = Math.round(canvas.getZoom() * 100) + "%";
        zoomLabel.setText(text);
        if (externalZoomLabel != null) externalZoomLabel.setText(text);
    }

    /** 最近邻缩放画布，居中绘制，空白处显示占位提示。 */
    private static class ImageCanvas extends JComponent {
        private BufferedImage image;
        private double zoom = 1.0;

        ImageCanvas() {
            setOpaque(true);
            setBackground(UiTheme.VIEWER_BG);
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

        int scaledW() { return image == null ? 0 : Math.max(1, (int) Math.round(image.getWidth() * zoom)); }
        int scaledH() { return image == null ? 0 : Math.max(1, (int) Math.round(image.getHeight() * zoom)); }
        int originX() { return Math.max(0, (getWidth() - scaledW()) / 2); }
        int originY() { return Math.max(0, (getHeight() - scaledH()) / 2); }

        void setZoom(double z) {
            this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
            updateSize();
            revalidate();
            repaint();
        }

        void fitToViewport() {
            if (image == null) return;
            Container parent = getParent();
            int vw = 0, vh = 0;
            if (parent instanceof JViewport vp) {
                vw = vp.getWidth();
                vh = vp.getHeight();
                if (vw <= 0 || vh <= 0) {
                    Container anc = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                    if (anc != null) { vw = anc.getWidth() - 20; vh = anc.getHeight() - 20; }
                    Window win = SwingUtilities.getWindowAncestor(this);
                    if ((vw <= 0 || vh <= 0) && win != null) { vw = win.getWidth() / 2 - 40; vh = win.getHeight() / 2; }
                }
            }
            if (vw <= 0 || vh <= 0) return;
            double pad = 24;
            double fit = Math.min((vw - pad) / image.getWidth(), (vh - pad) / image.getHeight());
            setZoom(Math.max(MIN_ZOOM, fit));
        }

        private void updateSize() {
            int vw = 0, vh = 0;
            Container parent = getParent();
            if (parent instanceof JViewport vp) { vw = vp.getWidth(); vh = vp.getHeight(); }
            setPreferredSize(new Dimension(Math.max(100, Math.max(scaledW(), vw)),
                    Math.max(100, Math.max(scaledH(), vh))));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (image == null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(UiTheme.PLACEHOLDER);
                g2.setFont(g2.getFont().deriveFont(13f));
                String hint = I18n.t("viewer.placeholder");
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, (getWidth() - fm.stringWidth(hint)) / 2, getHeight() / 2);
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(image, originX(), originY(), scaledW(), scaledH(), null);
        }
    }
}
