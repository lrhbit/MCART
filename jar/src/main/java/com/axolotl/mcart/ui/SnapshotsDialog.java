package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;

/** 方案快照与 A/B 对比：保存命名参数快照、恢复、并排对比两种方案。 */
public class SnapshotsDialog extends AppDialog {

    public interface Host {
        List<Project.NamedSnapshot> snapshots();
        void saveSnapshot(String name);
        void restoreSnapshot(Project.NamedSnapshot s);
        void deleteSnapshot(Project.NamedSnapshot s);
        void compareSnapshots(Project.NamedSnapshot a, Project.NamedSnapshot b);
    }

    private final Host host;
    private final DefaultListModel<Project.NamedSnapshot> model = new DefaultListModel<>();
    private final JList<Project.NamedSnapshot> list;
    private Project.NamedSnapshot a, b;
    private JLabel emptyLabel;
    private JScrollPane scrollPane;
    private final JLabel aLabel = new JLabel(I18n.t("snap.aNone"));
    private final JLabel bLabel = new JLabel(I18n.t("snap.bNone"));

    public SnapshotsDialog(JFrame owner, Host host) {
        super(owner, I18n.t("snapshot.title"), false);
        this.host = host;

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);

        // —— 左：快照列表卡片 ——
        list = new JList<>(model);
        list.setBackground(UiTheme.CARD_BG);
        list.setCellRenderer(new Renderer());
        list.setFixedCellHeight(ui(64));
        RoundedPanel listCard = card(new BorderLayout());
        listCard.setInsets(new Insets(ui(8), ui(8), ui(8), ui(8)));
        JScrollPane scroll = new JScrollPane(list);
        blendScroll(scroll, UiTheme.CARD_BG, false);
        JLabel emptyLabel = hintLabel(I18n.t("snap.emptyHint"), 380);
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setVerticalAlignment(SwingConstants.CENTER);
        emptyLabel.setBorder(new EmptyBorder(ui(24), ui(16), ui(24), ui(16)));
        listCard.add(scroll, BorderLayout.CENTER);
        listCard.add(emptyLabel, BorderLayout.CENTER);
        this.emptyLabel = emptyLabel;
        this.scrollPane = scroll;
        body.add(listCard, BorderLayout.CENTER);

        // —— 右：操作卡片 ——
        RoundedPanel actions = card();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setPreferredSize(new Dimension(ui(196), 0));
        JButton save = wideButton(I18n.t("snap.saveNow"));
        JButton restore = wideButton(I18n.t("snapshot.restore"));
        JButton del = wideButton(I18n.t("snap.delSelected"));
        save.addActionListener(e -> doSave());
        restore.addActionListener(e -> doRestore());
        del.addActionListener(e -> doDelete());
        actions.add(save); actions.add(Box.createVerticalStrut(6));
        actions.add(restore); actions.add(Box.createVerticalStrut(6));
        actions.add(del);
        actions.add(Box.createVerticalStrut(12));
        JLabel abTitle = cardTitle(I18n.t("snap.abLabel"));
        abTitle.setAlignmentX(LEFT_ALIGNMENT);
        actions.add(abTitle);
        actions.add(Box.createVerticalStrut(6));
        JButton setA = wideButton(I18n.t("snap.setA"));
        JButton setB = wideButton(I18n.t("snap.setB"));
        setA.addActionListener(e -> {
            Project.NamedSnapshot s = list.getSelectedValue();
            if (s != null) { a = s; aLabel.setText(I18n.t("snap.aPrefix") + s.name); }
        });
        setB.addActionListener(e -> {
            Project.NamedSnapshot s = list.getSelectedValue();
            if (s != null) { b = s; bLabel.setText(I18n.t("snap.bPrefix") + s.name); }
        });
        actions.add(setA);
        aLabel.setAlignmentX(LEFT_ALIGNMENT);
        aLabel.setBorder(new EmptyBorder(3, 2, 3, 2));
        actions.add(aLabel);
        actions.add(setB);
        bLabel.setAlignmentX(LEFT_ALIGNMENT);
        bLabel.setBorder(new EmptyBorder(3, 2, 3, 2));
        actions.add(bLabel);
        actions.add(Box.createVerticalStrut(10));
        JButton compare = wideButton(I18n.t("snap.compareBtn"));
        UiTheme.primaryButton(compare);
        compare.addActionListener(e -> {
            if (a != null && b != null) host.compareSnapshots(a, b);
            else JOptionPane.showMessageDialog(this, I18n.t("snap.needBoth"));
        });
        actions.add(compare);
        actions.add(Box.createVerticalGlue());
        body.add(actions, BorderLayout.EAST);

        setBody(body);

        Footer footer = new Footer();
        JButton close = primaryActionButton(I18n.t("common.close"));
        close.addActionListener(e -> close());
        footer.addButton(close, true);
        setFooter(footer);

        setupSize(680, 560);
        reload();
    }

    private JButton wideButton(String t) {
        JButton b = actionButton(t);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, ui(30)));
        b.setPreferredSize(new Dimension(Short.MAX_VALUE, ui(30)));
        return b;
    }

    public void reload() {
        model.clear();
        for (Project.NamedSnapshot s : host.snapshots()) model.addElement(s);
        boolean empty = model.isEmpty();
        if (emptyLabel != null) emptyLabel.setVisible(empty);
        if (scrollPane != null) scrollPane.setVisible(!empty);
    }

    private void doSave() {
        String name = JOptionPane.showInputDialog(this, I18n.t("snap.namePrompt"), I18n.t("snap.defaultName") + " " + (model.size() + 1));
        if (name != null && !name.isBlank()) {
            host.saveSnapshot(name.trim());
            reload();
        }
    }

    private void doRestore() {
        Project.NamedSnapshot s = list.getSelectedValue();
        if (s == null) return;
        if (JOptionPane.showConfirmDialog(this, I18n.t("snap.restoreConfirm", s.name),
                I18n.t("common.confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            host.restoreSnapshot(s);
            close();
        }
    }

    private void doDelete() {
        Project.NamedSnapshot s = list.getSelectedValue();
        if (s == null) return;
        host.deleteSnapshot(s);
        reload();
    }

    private static class Renderer extends JPanel implements ListCellRenderer<Project.NamedSnapshot> {
        private final JLabel thumb = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel detail = new JLabel();
        private final JPanel txt;

        Renderer() {
            super(new BorderLayout(8, 0));
            setOpaque(true);
            setBorder(new EmptyBorder(4, 6, 4, 6));
            thumb.setPreferredSize(new Dimension(72, 56));
            thumb.setHorizontalAlignment(SwingConstants.CENTER);
            txt = new JPanel();
            txt.setOpaque(true);
            txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
            name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
            detail.setForeground(UiTheme.MUTED_TEXT);
            detail.setFont(detail.getFont().deriveFont(11f));
            txt.add(name); txt.add(detail);
            add(thumb, BorderLayout.WEST);
            add(txt, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Project.NamedSnapshot> l,
                                                      Project.NamedSnapshot s, int i, boolean sel, boolean f) {
            name.setText(s.name);
            detail.setText(I18n.t("mode." + s.state.mode.name()) + " · " + I18n.t("dither." + s.state.dither.name())
                    + I18n.t("snap.strength") + s.state.ditherStrength + "% · "
                    + s.state.mapsW + "×" + s.state.mapsH + I18n.t("ab.mapsSuffix") + " · " + s.state.blockIds.size() + I18n.t("ab.kindsSuffix"));
            Icon icon = null;
            if (s.previewPng != null) {
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(s.previewPng));
                    if (img != null) icon = new ImageIcon(img.getScaledInstance(72, 56, Image.SCALE_FAST));
                } catch (Exception ignored) {
                }
            }
            thumb.setIcon(icon);
            thumb.setText(icon == null ? I18n.t("snap.noPreview") : "");
            Color bg = sel ? UiTheme.CARD_SEL : UiTheme.CARD_BG;
            setBackground(bg);
            txt.setBackground(bg);
            thumb.setBackground(bg);
            thumb.setOpaque(true);
            name.setForeground(UiTheme.SECTION_TITLE);
            detail.setForeground(UiTheme.MUTED_TEXT);
            return this;
        }
    }
}
