package com.axolotl.mcart.ui.titlebar;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * 紧凑模式标题栏小部件的共同基类（移植自 Pixel 的 HoverWidget）：
 * 透明面板 + 圆角悬停底色 + 点击（或平台弹出触发键）打开弹出菜单。
 */
abstract class HoverWidget extends JPanel {

    private boolean rollover;

    HoverWidget() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);
        setAlignmentY(0.5f);
        setBorder(new javax.swing.border.EmptyBorder(
                UIScale.scale(4), UIScale.scale(7), UIScale.scale(4), UIScale.scale(7)));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter handler = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { setRollover(true); }
            @Override public void mouseExited(MouseEvent e) { setRollover(false); }
            @Override public void mousePressed(MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
            private void maybeShow(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    createPopup().show(HoverWidget.this, e.getX(), e.getY());
                } else if (e.getButton() == MouseEvent.BUTTON1 && e.getID() == MouseEvent.MOUSE_PRESSED) {
                    JPopupMenu popup = createPopup();
                    popup.show(HoverWidget.this, UIScale.scale(3), getHeight() + UIScale.scale(2));
                }
            }
        };
        addMouseListener(handler);
    }

    /**
     * 统一在 addImpl 中给子组件挂转发监听器：无论调用方使用单参数 add(comp)
     * 还是 add(comp, constraints)，最终都经过 addImpl，保证子组件上的悬停与
     * 点击事件能转发到本面板，避免落在 JLabel 上时事件不冒泡导致点击无反应。
     */
    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        super.addImpl(comp, constraints, index);
        if (comp instanceof JComponent)
            comp.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { setRollover(true); }
                @Override public void mouseExited(MouseEvent e) { setRollover(false); }
                @Override public void mousePressed(MouseEvent e) {
                    for (MouseListener l : getMouseListeners())
                        l.mousePressed(e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    for (MouseListener l : getMouseListeners())
                        l.mouseReleased(e);
                }
            });
    }

    private void setRollover(boolean r) {
        if (rollover != r) {
            rollover = r;
            repaint();
        }
    }

    protected abstract JPopupMenu createPopup();

    @Override
    protected void paintComponent(Graphics g) {
        if (rollover)
            TitleTheme.paintRoundedBg((Graphics2D) g, 0, 0, getWidth(), getHeight(),
                    UIScale.scale(8), TitleTheme.HOVER_OVERLAY);
        super.paintComponent(g);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
