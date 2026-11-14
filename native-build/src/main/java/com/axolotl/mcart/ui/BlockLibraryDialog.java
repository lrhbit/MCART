package com.axolotl.mcart.ui;

import com.axolotl.mcart.i18n.I18n;

import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.model.BlockPresetStore;
import com.axolotl.mcart.model.BlockTag;
import com.axolotl.mcart.model.PaletteService;
import com.axolotl.mcart.util.BlockNames;
import com.axolotl.mcart.util.TextureManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 方块库对话框：纹理卡片 + 搜索 + 标签过滤 + 预设 + 自定义方块入口。
 * 选中状态直接读写当前工程的 selectedIds 集合。
 */
public class BlockLibraryDialog extends AppDialog {

    /** 与主窗口的交互契约 */
    public interface Context {
        /** 当前工程的选中方块 ID 集合（直接读写） */
        Set<String> selectedIds();
        /** 选择变化后回调（刷新计数 / 实时重算） */
        void onSelectionChanged();
        /** 新增自定义方块后回调（重建调色板与本对话框） */
        void onBlocksChanged();
    }

    private final Context ctx;
    private final List<BlockCard> cards = new ArrayList<>();
    private final Map<BlockColor.Category, List<BlockCard>> byCategory = new EnumMap<>(BlockColor.Category.class);
    private final Map<BlockColor.Category, JPanel> sectionWraps = new EnumMap<>(BlockColor.Category.class);
    private final Map<BlockTag, JCheckBox> tagFilters = new EnumMap<>(BlockTag.class);
    private final JLabel countLabel = new JLabel();
    private JTextField searchField;
    private JPanel cardsList;
    private JComboBox<BlockPresetStore.Preset> presetCombo;
    private JLabel noMatchLabel;
    private boolean syncing;

    public BlockLibraryDialog(JFrame owner, Context ctx) {
        super(owner, I18n.t("blocks.title"), false);
        this.ctx = ctx;
        buildContent();
        setupSize(760, 800);
    }

    private void buildContent() {
        cards.clear();
        byCategory.clear();
        sectionWraps.clear();
        tagFilters.clear();

        // —— 顶部卡片：搜索 + 标签 + 预设 ——
        RoundedPanel topCard = card();
        topCard.setLayout(new BoxLayout(topCard, BoxLayout.Y_AXIS));

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", I18n.t("blocks.searchPlaceholder"));
        searchField.setMargin(new Insets(6, 8, 6, 8));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        countLabel.setBorder(new EmptyBorder(0, 6, 0, 6));
        countLabel.setForeground(UiTheme.MUTED_TEXT);
        searchRow.add(new JLabel(I18n.t("blocks.searchLabel")), BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(countLabel, BorderLayout.EAST);
        topCard.add(searchRow);
        topCard.add(Box.createVerticalStrut(8));
        topCard.add(new JSeparator());
        topCard.add(Box.createVerticalStrut(8));

        JPanel tags = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        tags.setOpaque(false);
        tags.add(new JLabel(I18n.t("blocks.tagLabel")));
        for (BlockTag tag : BlockTag.values()) {
            JCheckBox cb = new JCheckBox(tag.label(I18n.language()));
            cb.setOpaque(false);
            cb.setToolTipText(tag.label(I18n.language()));
            cb.addActionListener(e -> applyFilter());
            tagFilters.put(tag, cb);
            tags.add(cb);
        }
        topCard.add(tags);
        topCard.add(Box.createVerticalStrut(6));

        JPanel presets = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        presets.setOpaque(false);
        presets.add(new JLabel(I18n.t("blocks.presetLabel")));
        presetCombo = new JComboBox<>();
        presetCombo.setPrototypeDisplayValue(new BlockPresetStore.Preset(I18n.t("blocks.defaultBtn"), List.of(), true, null));
        presetCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BlockPresetStore.Preset p) setText(p.name());
                return this;
            }
        });
        reloadPresets();
        presets.add(presetCombo);
        JButton applyPreset = miniButton(I18n.t("blocks.apply"));
        JButton savePreset = miniButton(I18n.t("blocks.saveAs"));
        JButton managePreset = miniButton(I18n.t("blocks.manage"));
        JButton customBtn = miniButton(I18n.t("blocks.custom"));
        applyPreset.addActionListener(e -> applyPreset());
        savePreset.addActionListener(e -> savePreset());
        managePreset.addActionListener(e -> managePresets());
        customBtn.addActionListener(e -> addCustomBlock());
        presets.add(applyPreset);
        presets.add(savePreset);
        presets.add(managePreset);
        presets.add(customBtn);
        topCard.add(presets);
        setHeader(topCard);

        // —— 卡片列表 ——
        cardsList = new VScrollList();
        cardsList.setBackground(UiTheme.CENTER_BG);
        cardsList.setBorder(new EmptyBorder(10, 2, 2, 2));
        List<BlockColor> all = PaletteService.allBlocks();
        Set<String> selected = ctx.selectedIds();
        for (BlockColor bc : all) {
            BlockCard card = new BlockCard(bc, selected.contains(bc.blockId()));
            cards.add(card);
            byCategory.computeIfAbsent(bc.category(), k -> new ArrayList<>()).add(card);
        }
        BlockColor.Category[] order = {
                BlockColor.Category.WOOL, BlockColor.Category.CONCRETE,
                BlockColor.Category.TERRACOTTA, BlockColor.Category.GLAZED_TERRACOTTA,
                BlockColor.Category.STAINED_GLASS, BlockColor.Category.NATURAL,
                BlockColor.Category.MINERAL, BlockColor.Category.SPECIAL
        };
        for (BlockColor.Category cat : order) {
            List<BlockCard> catCards = byCategory.get(cat);
            if (catCards == null || catCards.isEmpty()) continue;
            JPanel section = buildCategorySection(cat.displayName(), catCards);
            sectionWraps.put(cat, section);
            cardsList.add(section);
        }
        JLabel noMatch = new JLabel(I18n.t("blocks.noMatch"));
        noMatch.setForeground(UiTheme.MUTED_TEXT);
        noMatch.setAlignmentX(CENTER_ALIGNMENT);
        noMatch.setBorder(new EmptyBorder(ui(28), 0, ui(28), 0));
        noMatch.setVisible(false);
        cardsList.add(noMatch);
        this.noMatchLabel = noMatch;
        JScrollPane scroll = new JScrollPane(cardsList);
        blendScroll(scroll, UiTheme.CENTER_BG, false);
        setBody(scroll);

        // —— 底部按钮 ——
        Footer footer = new Footer();
        JButton defaultBtn = actionButton(I18n.t("blocks.defaultBtn"));
        JButton allBtn = actionButton(I18n.t("edit.selectAll"));
        JButton noneBtn = actionButton(I18n.t("edit.selectNone"));
        defaultBtn.addActionListener(e -> resetDefaults());
        allBtn.addActionListener(e -> setAll(true));
        noneBtn.addActionListener(e -> setAll(false));
        footer.addLeft(defaultBtn);
        footer.addLeft(allBtn);
        footer.addLeft(noneBtn);
        JButton closeBtn = primaryActionButton(I18n.t("blocks.done"));
        closeBtn.addActionListener(e -> { refreshCount(); close(); });
        footer.addButton(closeBtn, true);
        setFooter(footer);

        refreshCount();
        applyFilter();
    }

    private void reloadPresets() {
        if (presetCombo == null) return;
        syncing = true;
        presetCombo.removeAllItems();
        for (BlockPresetStore.Preset p : BlockPresetStore.all()) presetCombo.addItem(p);
        syncing = false;
    }

    private void applyPreset() {
        BlockPresetStore.Preset p = (BlockPresetStore.Preset) presetCombo.getSelectedItem();
        if (p == null) return;
        Set<String> ids = ctx.selectedIds();
        ids.clear();
        for (String id : p.blockIds()) {
            if (PaletteService.byId(id) != null) ids.add(id);
        }
        for (BlockCard c : cards) c.setSelected(ids.contains(c.bc.blockId()));
        ctx.onSelectionChanged();
        refreshCount();
        applyFilter();
    }

    private void savePreset() {
        String name = JOptionPane.showInputDialog(this, I18n.t("preset.name"), I18n.t("preset.saveTitle"), JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        BlockPresetStore.save(name.trim(), new ArrayList<>(ctx.selectedIds()));
        reloadPresets();
        JOptionPane.showMessageDialog(this, I18n.t("preset.saved", name));
    }

    private void managePresets() {
        PresetManagerDialog dlg = new PresetManagerDialog((JFrame) getOwner(), () -> reloadPresets());
        dlg.setVisible(true);
    }

    private void addCustomBlock() {
        CustomBlockDialog dlg = new CustomBlockDialog((JFrame) getOwner());
        dlg.setVisible(true);
        if (dlg.isCreated()) {
            ctx.onBlocksChanged(); // 主窗口重建本对话框
        }
    }

    private JPanel buildCategorySection(String title, List<BlockCard> catCards) {
        JPanel section = new JPanel(new BorderLayout(0, 4));
        section.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        titleLabel.setForeground(UiTheme.SECTION_TITLE);
        titleLabel.setBorder(new EmptyBorder(6, 2, 2, 0));
        header.add(titleLabel, BorderLayout.WEST);
        JPanel miniBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        miniBtns.setOpaque(false);
        JButton selectAll = miniButton(I18n.t("blocks.selectAllMini"));
        JButton clear = miniButton(I18n.t("blocks.clearMini"));
        selectAll.addActionListener(e -> catCards.forEach(c -> c.setSelected(true)));
        clear.addActionListener(e -> catCards.forEach(c -> c.setSelected(false)));
        miniBtns.add(selectAll);
        miniBtns.add(clear);
        header.add(miniBtns, BorderLayout.EAST);
        section.add(header, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(0, 2, 6, 6));
        grid.setOpaque(false);
        for (BlockCard c : catCards) grid.add(c);
        section.add(grid, BorderLayout.CENTER);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(section, BorderLayout.NORTH);
        wrap.setBorder(new EmptyBorder(2, 2, 8, 2));
        return wrap;
    }

    private JButton miniButton(String text) {
        JButton b = actionButton(text);
        b.setFont(b.getFont().deriveFont(11f));
        b.setMargin(new Insets(ui(1), ui(8), ui(1), ui(8)));
        b.setPreferredSize(null);
        return b;
    }

    private void setAll(boolean v) {
        for (BlockCard c : cards) c.setSelected(v);
        refreshCount();
        applyFilter();
    }

    void resetDefaults() {
        Set<String> def = new LinkedHashSet<>(PaletteService.defaultSelection());
        Set<String> ids = ctx.selectedIds();
        ids.clear();
        ids.addAll(def);
        for (BlockCard c : cards) c.setSelected(ids.contains(c.bc.blockId()));
        ctx.onSelectionChanged();
        refreshCount();
        applyFilter();
    }

    void focusSearch() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    List<BlockColor> getSelectedBlocks() {
        List<BlockColor> result = new ArrayList<>();
        for (BlockCard c : cards) if (c.isSelected()) result.add(c.bc);
        return result;
    }

    void refreshCount() {
        int n = ctx.selectedIds().size();
        int total = PaletteService.allBlocks().size();
        countLabel.setText(I18n.t("blocks.selected", n, total));
    }

    private void applyFilter() {
        String kw = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        List<BlockTag> activeTags = new ArrayList<>();
        for (Map.Entry<BlockTag, JCheckBox> e : tagFilters.entrySet()) {
            if (e.getValue().isSelected()) activeTags.add(e.getKey());
        }
        for (BlockCard c : cards) {
            String name = BlockNames.of(c.bc.blockId(), c.bc.name());
            boolean matchKw = kw.isEmpty()
                    || name.toLowerCase().contains(kw)
                    || c.bc.blockId().toLowerCase().contains(kw)
                    || c.bc.name().toLowerCase().contains(kw);
            boolean matchTag = activeTags.isEmpty() || BlockTag.tagsOf(c.bc).containsAll(activeTags);
            c.setVisible(matchKw && matchTag);
        }
        boolean anyVisible = false;
        for (Map.Entry<BlockColor.Category, List<BlockCard>> en : byCategory.entrySet()) {
            boolean any = en.getValue().stream().anyMatch(BlockCard::isVisible);
            sectionWraps.get(en.getKey()).setVisible(any);
            anyVisible |= any;
        }
        if (noMatchLabel != null) noMatchLabel.setVisible(!anyVisible);
        if (cardsList != null) {
            cardsList.revalidate();
            cardsList.repaint();
        }
    }

    /**
     * 方块卡片：复选框 + 纹理图标 + 名称；选中高亮；点卡片任意位置切换。
     */
    private class BlockCard extends RoundedPanel {
        final BlockColor bc;
        final JCheckBox checkBox;

        BlockCard(BlockColor bc, boolean selected) {
            super(new BorderLayout(6, 0));
            this.bc = bc;
            setArc(8);
            setBorderWidth(1);
            setInsets(new Insets(ui(5), ui(8), ui(5), ui(8)));

            checkBox = new JCheckBox();
            checkBox.setOpaque(false);
            checkBox.setSelected(selected);
            checkBox.setPreferredSize(new Dimension(22, 24));

            BufferedImage scaled = TextureManager.getScaled(bc, 26);
            JLabel iconLabel = new JLabel(new ImageIcon(scaled));
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 4));

            String cn = BlockNames.of(bc.blockId(), bc.name());
            JLabel nameLabel = new JLabel(cn);
            var tags = BlockTag.tagsOf(bc);
            nameLabel.setToolTipText("<html>" + bc.blockId()
                    + (tags.isEmpty() ? "" : "<br/>" + I18n.t("blocks.tagsPrefix") + tags.stream().map(t -> t.label(I18n.language())).reduce((a, b) -> a + "、" + b).orElse(""))
                    + "</html>");

            add(checkBox, BorderLayout.WEST);
            add(iconLabel, BorderLayout.CENTER);
            JPanel east = new JPanel(new BorderLayout());
            east.setOpaque(false);
            east.add(nameLabel, BorderLayout.WEST);
            add(east, BorderLayout.EAST);

            Consumer<Boolean> styler = s -> {
                setBackground(s ? UiTheme.CARD_SEL : UiTheme.CARD_BG);
                setBorderColor(s ? UiTheme.CARD_SEL_BORDER : UiTheme.BORDER);
                repaint();
            };
            checkBox.addItemListener(e -> {
                styler.accept(checkBox.isSelected());
                Set<String> ids = ctx.selectedIds();
                if (checkBox.isSelected()) ids.add(bc.blockId());
                else ids.remove(bc.blockId());
                ctx.onSelectionChanged();
                refreshCount();
            });
            styler.accept(selected);

            MouseAdapter clickToggle = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    checkBox.setSelected(!checkBox.isSelected());
                }
            };
            addMouseListener(clickToggle);
            iconLabel.addMouseListener(clickToggle);
            nameLabel.addMouseListener(clickToggle);
            east.addMouseListener(clickToggle);
        }

        boolean isSelected() { return checkBox.isSelected(); }

        void setSelected(boolean v) {
            checkBox.setSelected(v);
        }
    }
}
