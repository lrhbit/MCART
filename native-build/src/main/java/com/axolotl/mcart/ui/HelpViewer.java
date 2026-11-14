package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** 内置帮助 / 手册查看器：渲染打包在 jar 内的 docs/manual.md。 */
public class HelpViewer extends AppDialog {

    public HelpViewer(JFrame owner) {
        super(owner, I18n.t("help.title"), false);

        String md = readManual();
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setContentType("text/html; charset=UTF-8");
        pane.setText(mdToHtml(md));
        pane.setCaretPosition(0);
        pane.setBorder(BorderFactory.createEmptyBorder(ui(16), ui(18), ui(16), ui(18)));

        RoundedPanel card = card(new BorderLayout());
        card.setInsets(new Insets(0, 0, 0, 0));
        JScrollPane scroll = new JScrollPane(pane);
        blendScroll(scroll, UiTheme.VIEWER_BG, false);
        pane.setBackground(UiTheme.VIEWER_BG);
        card.add(scroll, BorderLayout.CENTER);
        setBody(card);

        Footer footer = new Footer();
        JButton close = primaryActionButton(I18n.t("common.close"));
        close.addActionListener(e -> close());
        footer.addButton(close, true);
        setFooter(footer);

        setupSize(900, 720);
    }

    private static String readManual() {
        String path = "/docs/manual.md";
        try (InputStream in = HelpViewer.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("manual missing");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "# " + I18n.t("help.missing") + "\n\n" + escapeHtml(e.toString());
        }
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** 极简 Markdown → HTML（标题 / 列表 / 代码块 / 行内代码 / 加粗），颜色跟随当前主题。 */
    static String mdToHtml(String md) {
        String fg = hex(UiTheme.SECTION_TITLE);
        String muted = hex(UiTheme.MUTED_TEXT);
        String codeBg = hex(UiTheme.CARD_BG);
        String border = hex(UiTheme.BORDER);
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='color:").append(fg)
                .append(";font-family:'Segoe UI','Microsoft YaHei',sans-serif;font-size:13px;'>");
        boolean inCode = false;
        boolean inList = false;
        for (String raw : md.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("```")) {
                if (!inCode) {
                    sb.append("<pre style='background:").append(codeBg)
                            .append(";border:1px solid ").append(border)
                            .append(";padding:10px;border-radius:8px;white-space:pre-wrap;'>");
                    inCode = true;
                } else {
                    sb.append("</pre>");
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                sb.append(escapeHtml(line)).append("<br/>");
                continue;
            }
            if (line.startsWith("### ")) {
                if (inList) { sb.append("</ul>"); inList = false; }
                sb.append("<h3 style='color:").append(fg).append(";margin:14px 0 6px;'>")
                        .append(inline(line.substring(4))).append("</h3>");
            } else if (line.startsWith("## ")) {
                if (inList) { sb.append("</ul>"); inList = false; }
                sb.append("<h2 style='color:").append(fg).append(";margin:18px 0 8px;'>")
                        .append(inline(line.substring(3))).append("</h2>");
            } else if (line.startsWith("# ")) {
                if (inList) { sb.append("</ul>"); inList = false; }
                sb.append("<h1 style='color:").append(fg).append(";margin:10px 0 10px;'>")
                        .append(inline(line.substring(2))).append("</h1>");
            } else if (line.startsWith("- ")) {
                if (!inList) { sb.append("<ul style='margin:4px 0 8px 18px;'>"); inList = true; }
                sb.append("<li style='margin:2px 0;color:").append(muted).append(";'>")
                        .append(inline(line.substring(2))).append("</li>");
            } else if (line.isEmpty()) {
                if (inList) { sb.append("</ul>"); inList = false; }
            } else {
                if (inList) { sb.append("</ul>"); inList = false; }
                sb.append("<p style='margin:6px 0;color:").append(muted).append(";'>")
                        .append(inline(line)).append("</p>");
            }
        }
        if (inList) sb.append("</ul>");
        if (inCode) sb.append("</pre>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String inline(String s) {
        String esc = escapeHtml(s);
        // **加粗**
        esc = esc.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        // 行内代码 `xxx`
        StringBuilder out = new StringBuilder();
        boolean tick = false;
        for (String part : esc.split("`", -1)) {
            if (tick) {
                out.append("<code style='background:").append(hex(UiTheme.CARD_BG))
                        .append(";border:1px solid ").append(hex(UiTheme.BORDER))
                        .append(";padding:1px 5px;border-radius:4px;'>").append(part).append("</code>");
            } else {
                out.append(part);
            }
            tick = !tick;
        }
        return out.toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
