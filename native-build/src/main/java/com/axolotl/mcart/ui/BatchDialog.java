package com.axolotl.mcart.ui;

import com.axolotl.mcart.core.ColorMatcher;
import com.axolotl.mcart.core.DitherMethod;
import com.axolotl.mcart.core.PixelArtGenerator;
import com.axolotl.mcart.export.ExportFormat;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.model.MapMode;
import com.axolotl.mcart.model.PaletteService;
import com.axolotl.mcart.model.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 批量处理：对整个文件夹的图片按当前工程参数批量生成并导出多种格式。
 */
public class BatchDialog extends AppDialog {

    private final Project template;
    private final JTextField inDir = new JTextField(28);
    private final JTextField outDir = new JTextField(28);
    private final JTextArea log = new JTextArea(12, 50);
    private final JProgressBar progress = new JProgressBar();
    private final JButton runBtn;
    private final List<JCheckBox> formatBoxes = new ArrayList<>();
    // 批量转换参数（可在开始前统一设置，也可一键预设）
    private final JComboBox<MapMode> modeCombo = new JComboBox<>(MapMode.values());
    private final JComboBox<DitherMethod> ditherCombo = new JComboBox<>(DitherMethod.values());
    private final JSpinner strengthSpinner = new JSpinner(new SpinnerNumberModel(100, 0, 100, 5));
    private final JSpinner mapW = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    private final JSpinner mapH = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    private SwingWorker<Void, String> worker;

    public BatchDialog(JFrame owner, Project template) {
        super(owner, I18n.t("batch.title"), false);
        this.template = template;
        this.runBtn = primaryActionButton(I18n.t("batch.run"));
        initParamControls();
        build();
        setupSize(800, 680);
    }

    private void initParamControls() {
        modeCombo.setRenderer(render((v) -> v instanceof MapMode m ? I18n.t("mode." + m.name()) : null));
        ditherCombo.setRenderer(render((v) -> v instanceof DitherMethod dm ? I18n.t("dither." + dm.name()) : null));
        modeCombo.setSelectedItem(template.getMode());
        ditherCombo.setSelectedItem(template.getDither());
        strengthSpinner.setValue(template.getDitherStrength());
        mapW.setValue(template.getMapsW());
        mapH.setValue(template.getMapsH());
        for (JSpinner sp : new JSpinner[]{strengthSpinner, mapW, mapH}) {
            com.axolotl.mcart.util.InputMethodHints.disableCompositionWhileFocused(sp);
        }
    }

    private static DefaultListCellRenderer render(java.util.function.Function<Object, String> textOf) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String t = textOf.apply(value);
                if (t != null) setText(t);
                return this;
            }
        };
    }

    private void build() {
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);

        // —— 目录卡片 ——
        RoundedPanel dirs = card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(ui(4), ui(4), ui(4), ui(4));
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0; c.gridx = 0; dirs.add(new JLabel(I18n.t("batch.inputLabel")), c);
        c.gridx = 1; c.weightx = 1; dirs.add(inDir, c);
        JButton inBtn = actionButton(I18n.t("batch.chooseBtn"));
        inBtn.addActionListener(e -> chooseDir(inDir));
        c.gridx = 2; c.weightx = 0; dirs.add(inBtn, c);
        c.gridy = 1; c.gridx = 0; dirs.add(new JLabel(I18n.t("batch.outputLabel")), c);
        c.gridx = 1; c.weightx = 1; dirs.add(outDir, c);
        JButton outBtn = actionButton(I18n.t("batch.chooseBtn"));
        outBtn.addActionListener(e -> chooseDir(outDir));
        c.gridx = 2; c.weightx = 0; dirs.add(outBtn, c);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        dirs.setAlignmentX(LEFT_ALIGNMENT);
        top.add(dirs);
        top.add(Box.createVerticalStrut(10));
        top.add(buildParamsCard());
        body.add(top, BorderLayout.NORTH);

        // —— 中部：格式卡片 + 日志卡片 ——
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);

        RoundedPanel fmtCard = card();
        fmtCard.setLayout(new BoxLayout(fmtCard, BoxLayout.Y_AXIS));
        JLabel fmtTitle = cardTitle(I18n.t("batch.formatsBorder"));
        fmtTitle.setAlignmentX(LEFT_ALIGNMENT);
        fmtTitle.setBorder(new EmptyBorder(0, 0, ui(6), 0));
        fmtCard.add(fmtTitle);
        WrapLayout wrap = new WrapLayout(FlowLayout.LEFT, 8, 4);
        JPanel fmtPanel = new JPanel(wrap);
        fmtPanel.setOpaque(false);
        for (ExportFormat f : ExportFormat.values()) {
            JCheckBox cb = new JCheckBox(I18n.t("format." + f.name()));
            cb.setOpaque(false);
            cb.setName(f.name());
            if (f == ExportFormat.LITEMATIC || f == ExportFormat.CSV || f == ExportFormat.PNG) cb.setSelected(true);
            formatBoxes.add(cb);
            fmtPanel.add(cb);
        }
        fmtCard.add(fmtPanel);
        center.add(fmtCard, BorderLayout.NORTH);

        RoundedPanel logCard = card(new BorderLayout(0, 8));
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setBackground(UiTheme.VIEWER_BG);
        log.setCaretColor(UiTheme.SECTION_TITLE);
        JScrollPane logScroll = new JScrollPane(log);
        blendScroll(logScroll, UiTheme.VIEWER_BG, false);
        logCard.add(logScroll, BorderLayout.CENTER);
        logCard.add(progress, BorderLayout.SOUTH);
        center.add(logCard, BorderLayout.CENTER);
        body.add(center, BorderLayout.CENTER);

        setBody(body);

        Footer footer = new Footer();
        JButton close = actionButton(I18n.t("common.close"));
        close.addActionListener(e -> close());
        runBtn.addActionListener(e -> run());
        footer.addButton(runBtn, true);
        footer.addButton(close);
        setFooter(footer);
    }

    private void chooseDir(JTextField target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private List<ExportFormat> selectedFormats() {
        List<ExportFormat> list = new ArrayList<>();
        for (JCheckBox cb : formatBoxes) if (cb.isSelected()) list.add(ExportFormat.valueOf(cb.getName()));
        return list;
    }

    /** 转换参数卡片：类型 / 抖动 / 强度 / 张数 + 快速预设。 */
    private JComponent buildParamsCard() {
        RoundedPanel card = card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel title = cardTitle(I18n.t("batch.params"));
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(0, 0, ui(4), 0));
        card.add(title);
        JLabel hint = new JLabel(I18n.t("batch.paramsHint"));
        hint.setForeground(UiTheme.MUTED_TEXT);
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setAlignmentX(LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(0, 0, ui(8), 0));
        card.add(hint);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(ui(3), ui(4), ui(3), ui(4));
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        c.gridx = 0; grid.add(new JLabel(I18n.t("form.mapType")), c);
        c.gridx = 1; c.weightx = 1; grid.add(modeCombo, c);
        c.gridx = 2; c.weightx = 0; grid.add(new JLabel(I18n.t("form.dither")), c);
        c.gridx = 3; c.weightx = 1; grid.add(ditherCombo, c);
        c.gridy = 1;
        c.gridx = 0; c.weightx = 0; grid.add(new JLabel(I18n.t("form.strength")), c);
        c.gridx = 1; c.weightx = 1; grid.add(strengthSpinner, c);
        c.gridx = 2; c.weightx = 0; grid.add(new JLabel(I18n.t("form.maps")), c);
        JPanel maps = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        maps.setOpaque(false);
        mapW.setPreferredSize(new Dimension(60, 28));
        mapH.setPreferredSize(new Dimension(60, 28));
        maps.add(mapW); maps.add(new JLabel("×")); maps.add(mapH);
        c.gridx = 3; c.weightx = 1; grid.add(maps, c);
        card.add(grid);

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        presets.setOpaque(false);
        presets.setAlignmentX(LEFT_ALIGNMENT);
        JLabel pl = new JLabel(I18n.t("batch.presets"));
        pl.setForeground(UiTheme.MUTED_TEXT);
        presets.add(pl);
        presets.add(presetButton(I18n.t("batch.preset.flatDither"), MapMode.FLAT, DitherMethod.FLOYD_STEINBERG, 100));
        presets.add(presetButton(I18n.t("batch.preset.flatClean"), MapMode.FLAT, DitherMethod.NONE, 100));
        presets.add(presetButton(I18n.t("batch.preset.relief"), MapMode.RELIEF, DitherMethod.BLUE_NOISE, 100));
        JButton useCur = actionButton(I18n.t("batch.useCurrent"));
        useCur.addActionListener(e -> initParamControls());
        presets.add(useCur);
        card.add(presets);
        return card;
    }

    private JButton presetButton(String text, MapMode mode, DitherMethod dither, int strength) {
        JButton b = actionButton(text);
        b.addActionListener(e -> {
            modeCombo.setSelectedItem(mode);
            ditherCombo.setSelectedItem(dither);
            strengthSpinner.setValue(strength);
        });
        return b;
    }

    /** 依据当前批量参数构造一个工作工程（复制模板的方块 / 预处理 / 填充 / 选区，覆盖转换参数）。 */
    private Project buildWorkProject() {
        Project work = new Project();
        work.restore(template.snapshot());
        work.setMode((MapMode) modeCombo.getSelectedItem());
        work.setDither((DitherMethod) ditherCombo.getSelectedItem());
        work.setDitherStrength((Integer) strengthSpinner.getValue());
        work.setMapsW((Integer) mapW.getValue());
        work.setMapsH((Integer) mapH.getValue());
        return work;
    }

    private void run() {
        if (worker != null && !worker.isDone()) return;
        Path in = Path.of(inDir.getText().trim());
        Path out = Path.of(outDir.getText().trim());
        if (!Files.isDirectory(in)) {
            JOptionPane.showMessageDialog(this, I18n.t("batch.needInput"));
            return;
        }
        List<ExportFormat> formats = selectedFormats();
        if (formats.isEmpty()) {
            JOptionPane.showMessageDialog(this, I18n.t("batch.needFormat"));
            return;
        }
        try { Files.createDirectories(out); } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, I18n.t("batch.mkdirFail", ex.getMessage()));
            return;
        }
        File[] files = in.toFile().listFiles((d, n) -> {
            String s = n.toLowerCase(Locale.ROOT);
            return s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg")
                    || s.endsWith(".webp") || s.endsWith(".bmp") || s.endsWith(".gif");
        });
        if (files == null || files.length == 0) {
            JOptionPane.showMessageDialog(this, I18n.t("batch.noImages"));
            return;
        }

        runBtn.setEnabled(false);
        progress.setValue(0);
        progress.setMaximum(files.length);
        log.setText("");

        worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                final Project work = buildWorkProject();
                List<BlockColor> blocks = new ArrayList<>();
                for (String id : work.getSelectedBlockIds()) {
                    BlockColor bc = PaletteService.byId(id);
                    if (bc != null) blocks.add(bc);
                }
                if (blocks.isEmpty()) {
                    publish(I18n.t("batch.errNoBlocks"));
                    return null;
                }
                int done = 0;
                for (File f : files) {
                    if (isCancelled()) break;
                    String base = f.getName().replaceAll("\\.[^.]+$", "");
                    try {
                        BufferedImage img = javax.imageio.ImageIO.read(f);
                        if (img == null) throw new Exception(I18n.t("batch.unreadable"));
                        ColorMatcher matcher = new ColorMatcher(blocks, work.getMode());
                        PixelArtGenerator gen = new PixelArtGenerator(matcher, work.getDither(),
                                work.getMode(), work.getSupportBlockId(),
                                work.getPreprocess(), work.getDitherStrength() / 100.0,
                                work.roi());
                        PixelArtGenerator.Result result = gen.generate(img,
                                work.targetWidth(), work.targetHeight());
                        BufferedImage png = renderPng(result);
                        for (ExportFormat fmt : formats) {
                            Path target = out.resolve(base + fmt.extension);
                            fmt.export(result, png, target);
                        }
                        publish(I18n.t("batch.lineOk", f.getName(), formats.size()));
                    } catch (Exception ex) {
                        publish("✗ " + f.getName() + "：" + ex.getMessage());
                    }
                    done++;
                    final int doneNow = done;
                    SwingUtilities.invokeLater(() -> progress.setValue(doneNow));
                }
                publish(I18n.t("batch.finished", done));
                return null;
            }
            @Override protected void process(List<String> chunks) {
                for (String s : chunks) log.append(s + "\n");
            }
            @Override protected void done() {
                runBtn.setEnabled(true);
            }
        };
        worker.execute();
    }

    /** 由 Result 的地图配色像素生成放大 4 倍的 PNG 预览 */
    private static BufferedImage renderPng(PixelArtGenerator.Result r) {
        int scale = 4;
        BufferedImage img = new BufferedImage(r.width * scale, r.length * scale, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        for (int z = 0; z < r.length; z++) {
            for (int x = 0; x < r.width; x++) {
                g.setColor(new Color(r.previewPixels[z][x]));
                g.fillRect(x * scale, z * scale, scale, scale);
            }
        }
        g.dispose();
        return img;
    }
}
