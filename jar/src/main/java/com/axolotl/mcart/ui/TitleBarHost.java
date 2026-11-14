package com.axolotl.mcart.ui;

import com.axolotl.mcart.Main;
import com.axolotl.mcart.i18n.I18n;
import com.axolotl.mcart.model.BlockColor;
import com.axolotl.mcart.model.PaletteService;
import com.axolotl.mcart.model.Project;
import com.axolotl.mcart.ui.titlebar.AppTitleBar;
import com.axolotl.mcart.util.AppConfig;

import java.util.List;

/**
 * 标题栏功能契约的实现：全部动作委托给 {@link MainFrame}，
 * 原先塞在 MainFrame 里的 190 行匿名内部类抽到此处。
 */
public class TitleBarHost implements AppTitleBar.Host {

    private final MainFrame frame;

    public TitleBarHost(MainFrame frame) {
        this.frame = frame;
    }

    // —— 文档 ——
    @Override public boolean hasImage() {
        Project p = frame.cur();
        return p != null && p.hasImage();
    }

    @Override public boolean hasResult() {
        Project p = frame.cur();
        return p != null && p.result != null;
    }

    @Override public List<String> recentFiles() { return AppConfig.recentFiles(); }

    @Override public void openImage() { frame.chooseImage(); }
    @Override public void openRecent(String path) { frame.openRecent(path); }

    @Override public void closeImage() {
        Project p = frame.cur();
        if (p != null && p.hasImage()) frame.closeDoc(frame.activeIndex());
    }

    // —— 工程 ——
    @Override public void newProject() { frame.captureControls(); frame.newProject(); }
    @Override public void openProject() { frame.openProjectFromChooser(); }
    @Override public void saveProject() { frame.saveProjectInteractive(frame.cur()); }

    @Override public void saveProjectAs() {
        Project p = frame.cur();
        if (p == null) return;
        p.setProjectFile(null);
        frame.saveProjectInteractive(p);
    }

    @Override public void saveTemplate() { frame.saveTemplate(); }

    // —— 撤销 / 重做 ——
    @Override public boolean canUndo() { Project p = frame.cur(); return p != null && p.canUndo(); }
    @Override public boolean canRedo() { Project p = frame.cur(); return p != null && p.canRedo(); }
    @Override public void undo() { frame.undo(); }
    @Override public void redo() { frame.redo(); }

    // —— 生成 / 导出 ——
    @Override public void generate() { frame.generatePreview(); }
    @Override public void export() { frame.exportSelected(); }

    // —— 方块库 ——
    @Override public void openBlockLibrary() { frame.openBlockLibrary(); }
    @Override public void searchBlocks() { frame.searchBlocks(); }

    @Override public void selectBlocksDefault() {
        Project p = frame.cur();
        if (p == null) return;
        p.pushUndo();
        p.getSelectedBlockIds().clear();
        p.getSelectedBlockIds().addAll(PaletteService.defaultSelection());
        frame.afterBlockSelectionChanged();
    }

    @Override public void selectBlocksAll() {
        Project p = frame.cur();
        if (p == null) return;
        p.pushUndo();
        p.getSelectedBlockIds().clear();
        for (BlockColor bc : PaletteService.allBlocks()) p.getSelectedBlockIds().add(bc.blockId());
        frame.afterBlockSelectionChanged();
    }

    @Override public void selectBlocksNone() {
        Project p = frame.cur();
        if (p == null) return;
        p.pushUndo();
        p.getSelectedBlockIds().clear();
        frame.afterBlockSelectionChanged();
    }

    @Override public int selectedBlockCount() {
        Project p = frame.cur();
        return p == null ? 0 : p.getSelectedBlockIds().size();
    }

    @Override public int totalBlockCount() { return PaletteService.allBlocks().size(); }

    // —— 编辑工具 ——
    @Override public void openPreprocess() { frame.openPreprocess(); }
    @Override public void openCrop() { frame.openCrop(); }
    @Override public void openPixelEditor() { frame.openPixelEditor(); }

    // —— 预览 ——
    @Override public void setPreviewMode(int index) { frame.setPreviewModeCombo(index); }
    @Override public int previewMode() {
        Project p = frame.cur();
        return p == null ? 0 : p.getPreviewMode();
    }
    @Override public void reset3DView() { frame.reset3DView(); }
    @Override public void fitViews() { frame.fitViews(); }

    // —— 视图 / 主题 / 语言 ——
    @Override public void setLayoutMode(String layout) {
        AppConfig.setLayout(layout);
        AppConfig.save();
        frame.applyLayout(layout);
    }
    @Override public String layoutMode() { return AppConfig.layout(); }

    @Override public void setTheme(String theme) {
        AppConfig.setTheme(theme);
        AppConfig.save();
        Main.restartUi(frame);
    }
    @Override public String theme() { return AppConfig.theme(); }

    @Override public void setLanguage(String lang) {
        AppConfig.setLanguage(lang);
        AppConfig.save();
        Main.restartUi(frame);
    }
    @Override public String language() { return AppConfig.language(); }

    // —— 工具 ——
    @Override public void openBom() { frame.openBom(); }
    @Override public void openBatch() { frame.openBatch(); }
    @Override public void openSnapshots() { frame.openSnapshotsDialog(); }
    @Override public void openPresets() { frame.openPresetsDialog(); }
    @Override public void openCustomBlock() { frame.openCustomBlockDialog(); }
    @Override public void openSettings() { frame.openSettingsDialog(); }
    @Override public void openWarnings() { frame.openWarnings(); }
    @Override public int warningCount() { return frame.warningCount(); }

    // —— 帮助 ——
    @Override public void showHelp() { frame.showHelp(); }
    @Override public void openSample() { frame.openSampleProject(); }
    @Override public void showAbout() { frame.showAbout(); }
}
