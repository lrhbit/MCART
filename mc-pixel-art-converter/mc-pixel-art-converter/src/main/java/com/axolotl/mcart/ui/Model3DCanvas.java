package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.Model3DRenderer;
import com.axolotl.mcart.core.Model3DRenderer.Camera;
import com.axolotl.mcart.core.PixelArtGenerator;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

/**
 * 可交互的立体模型预览组件：
 * - 左键拖动：旋转视角（默认/复位为正俯视）
 * - 右键或中键拖动：平移
 * - 滚轮：缩放
 * - 双击或 {@link #resetView()}：一键回到复位俯视
 * 拖动过程中使用纯色快速渲染，松开后恢复方块纹理。
 */
public class Model3DCanvas extends JComponent {

    private PixelArtGenerator.Result result;
    private final Camera camera = new Camera();
    private int lastX, lastY;
    private int buttonHeld = -1;
    private boolean fastRender = false;
    private BufferedImage cached;

    public Model3DCanvas() {
        setPreferredSize(new Dimension(400, 400));
        setBackground(new Color(0xF2, 0xF2, 0xF5));
        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
                buttonHeld = e.getButton();
            }
            @Override public void mouseReleased(MouseEvent e) {
                buttonHeld = -1;
                if (fastRender) {
                    fastRender = false;
                    cached = null;
                    repaint();
                }
            }
            @Override public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastX;
                int dy = e.getY() - lastY;
                lastX = e.getX();
                lastY = e.getY();
                if (buttonHeld == MouseEvent.BUTTON1) {
                    camera.yaw += dx * 0.012;
                    camera.pitch = Math.max(0.06, Math.min(Math.PI / 2, camera.pitch - dy * 0.012));
                } else {
                    camera.panX += dx;
                    camera.panY += dy;
                }
                fastRender = true;
                cached = null;
                repaint();
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) resetView();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = Math.pow(0.9, e.getWheelRotation());
                camera.zoom = Math.max(0.4, Math.min(128, camera.zoom * factor));
                cached = null;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void setResult(PixelArtGenerator.Result r) {
        this.result = r;
        resetView();
    }

    /** 一键复位到正俯视 */
    public void resetView() {
        camera.resetTopDown();
        camera.zoom = result == null ? 4
                : Model3DRenderer.fitZoom(result, getWidth(), getHeight());
        cached = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        if (result == null) {
            g.setColor(new Color(0x88, 0x88, 0x88));
            g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            String hint = "生成后在此查看立体模型";
            g.drawString(hint, (getWidth() - g.getFontMetrics().stringWidth(hint)) / 2, getHeight() / 2);
            return;
        }

        // 复位俯视状态下，窗口缩放时自动重新适配
        if (!fastRender && camera.pitch >= Math.PI / 2 - 0.001
                && camera.yaw % (Math.PI * 2) < 0.001
                && camera.panX == 0 && camera.panY == 0) {
            camera.zoom = Model3DRenderer.fitZoom(result, getWidth(), getHeight());
        }

        if (cached == null || cached.getWidth() != getWidth() || cached.getHeight() != getHeight()) {
            cached = Model3DRenderer.render(result, camera, getWidth(), getHeight(), !fastRender);
        }
        g.drawImage(cached, 0, 0, null);

        // 视角状态角标
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(60, 60, 60, 200));
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        String mode = isTopDown() ? "正俯视（复位）" : "自由视角";
        g.drawString(mode, 10, getHeight() - 10);
    }

    private boolean isTopDown() {
        return Math.abs(camera.pitch - Math.PI / 2) < 0.01
                && Math.abs(((camera.yaw % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2)) < 0.02;
    }
}
