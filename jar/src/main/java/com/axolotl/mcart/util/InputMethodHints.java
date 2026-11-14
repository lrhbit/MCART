package com.axolotl.mcart.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.im.InputContext;

/**
 * 数字输入框的输入法适配。
 *
 * 中文输入法处于组合状态时，按键会先进入 IME 做拼音组合，数字无法直接进入
 * 数字输入框。这里在组件获得焦点时临时关闭 IME 组合（数字键直接上屏），
 * 失去焦点后恢复，不影响其他需要中文输入的地方。
 */
public final class InputMethodHints {

    private InputMethodHints() {
    }

    /** 让数字微调框在持有焦点期间关闭输入法组合，失焦后自动恢复。 */
    public static void disableCompositionWhileFocused(JSpinner spinner) {
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            disableCompositionWhileFocused(de.getTextField());
        }
    }

    /** 让组件在持有焦点期间关闭输入法组合，失焦后自动恢复。 */
    public static void disableCompositionWhileFocused(JComponent c) {
        c.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                setComposition(c, false);
            }

            @Override public void focusLost(FocusEvent e) {
                setComposition(c, true);
            }
        });
    }

    private static void setComposition(Component c, boolean enabled) {
        try {
            InputContext ic = c.getInputContext();
            if (ic != null) {
                ic.setCompositionEnabled(enabled);
            }
        } catch (Throwable ignored) {
            // 部分输入法不支持该操作，保持默认行为，不影响主流程
        }
    }
}
