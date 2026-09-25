# MC Pixel Art Generator (MC-ART)

English | [中文](./README_zh_CN.md)

Convert any image into Minecraft pixel art: automatic color selection, dithering, generation of flat or relief block layouts, and export to structure files, commands, data packs, or a bill of materials directly usable in the game.

## I. Quick Start (5 Steps)

1.  **Open Image**: Click the colored "Document Widget" in the title bar to open, or go to Menu → Open Image (Ctrl+O); you can also start from "Help → Load Example Project".
2.  **Select Blocks**: In the left vertical toolbar, check the blocks you want to participate in color matching in the "Block Library" (Ctrl+B); by default, 16 wool + 16 concrete (32 types total) are selected.
3.  **Set Parameters**: In the left "Pixel Art Parameters" panel, set the mode (flat/relief), dithering algorithm and strength, map count, fill block, and output type; if necessary, adjust brightness/color directly in the "Image Processing" panel above (or open the full dialog with Ctrl+P).
4.  **Generate Preview**: Click the blue "Generate Preview" on the right (Ctrl+R), and switch between four views in the top-right corner of the MC preview area: block textures, map colors, relief model, and color gamut warning.
5.  **Export**: Click "Export" in the right toolbar (Ctrl+E).

## II. Interface Layout

-   **Title Bar**: Hamburger button on the left (Alt+\\ to toggle compact/main menu), Document Widget, Block Library Widget; on the right are the warning bell, preprocessing gear, and window buttons (generation/search have been moved to the side toolbars).
-   **Tabs**: Located above the viewer column (to the right of the image processing area); multiple projects can be opened simultaneously. Click to switch, × to close; unsaved projects are marked with ●.
-   **Left Vertical Toolbar** (icons only, hover to show name and shortcut): Block Library (four squares), Search Block (magnifying glass), Bill of Materials (list), Batch Processing (stacked layers).
-   **Left Control Panel** (resizable divider for width, adjustable height, adapts to window):
    -   Top "Image Processing": All preprocessing sliders are embedded (Brightness/Contrast/Gamma/RGB/Saturation/Hue Separation/Desaturate/Background), with "Crop" and "Pixel Edit" buttons on the left and right below.
    -   Bottom "Pixel Art Parameters": Pixel art type, dithering method, strength, map count, block library, fill block, output type.
-   **Central Dual Viewer**: Left is the original image, right is the MC preview (preview mode dropdown in the top-right of the preview area, title bar includes zoom percentage and "Reset" button); the middle divider is draggable; the view menu allows switching between "Full Layout / Preview Only / Original Only".
-   **Right Vertical Toolbar** (icons only): Generate Preview (blue vortex main button), Export (arrow on tray), Save Project (floppy disk), Bill of Materials (list).
-   **Bottom Status Bar**: Current dimensions, block types, mode/dithering, and operation tips; warning count is displayed on the title bar bell.
<img width="639" height="378" alt="image" src="https://github.com/user-attachments/assets/2373e804-378d-48d3-8eef-43812a33290a" />


## III. Project System (.mcpx)

### 1. Project Files

-   **Save Project Ctrl+S**: Packs into `.mcpx` (ZIP format, containing source image and all parameters: preprocessing, block selection, dithering, map count, ROI selection, support blocks, scheme snapshots, etc.).
-   **Save Project As Ctrl+Shift+S**, **Save as Project Template .mcpt** (saves only parameters, not the image; can be applied to other images).
-   **Open Project Ctrl+Shift+O**: Opens `.mcpx` or `.mcpt`; preview regenerates automatically after opening.
-   Recently opened projects/images are in the Document Widget and "File → Recent Files".

### 2. Tabbed Multi-Project

-   Toolbar "Project → New" (Ctrl+N) creates a new blank project; when opening a new image, if the current project already has an image, a new tab will be automatically created.
-   When closing a tab with an unsaved project, a save prompt will appear.

### 3. Undo / Redo

-   **Undo Ctrl+Z, Redo Ctrl+Y**: Covers all parameter operations including mode, dithering, strength, map count, support blocks, block selection, preprocessing, crop selection, etc.
-   Pixel-level texture edit results are restored upon "Regenerate"; clicking "Discard Changes" during editing also restores them.

### 4. Session Recovery

-   Upon normal exit or crash, the next launch automatically restores all previously opened projects and tabs (can be disabled in "Tools → Settings").

### 5. Scheme Snapshots and A/B Comparison

-   "Tools → Scheme Snapshots and A/B Comparison": Saves the current set of parameters as a named snapshot (with thumbnail), which can be restored at any time.
-   Select two snapshots, click "Set as A / Set as B", then click "Side-by-Side Comparison" to view the effects and parameters of the two schemes side-by-side.

## IV. Pixel Art Parameters

### 1. Mode

-   **Flat (FLAT)**: All blocks are at the same height; the map always displays standard colors (shade 1).
-   **Relief (RELIEF)**: Strictly follows Minecraft's map item shading mechanism; generates stepped heights based on image content, presenting shading through block height differences on the map; the top layer is the display block, and below it are "fill" support blocks (invisible on the map).

### 2. Dithering Algorithm (9 types)

-   **No Dithering**: Each grid cell takes the nearest color; the cleanest color blocks.
-   **Blue Noise Hybrid** (default recommended): OKLab two-color mixing + error diffusion + edge preservation; good for photos and gradients.
-   **Floyd-Steinberg / Burkes / Atkinson / Stucki / Jarvis / Sierra**: Classic error diffusion algorithms with distinct styles.
-   **Ordered Dithering**: Regular Bayer dot pattern, strong retro feel.
-   **Dithering Strength (0~100%)**: Controls error diffusion / dithering amplitude; 0% is equivalent to no dithering, 100% is the full algorithm.

### 3. Dimensions (Map Count)

-   Each map is **128×128** blocks; width/height map count is 1~8. Output strictly adheres to the map count ratio; the original image will be stretched to fill. If the aspect ratio is inconsistent, it is recommended to adapt using "Crop" first.

### 4. Fill Block (Relief Only)

-   Stone / Cobblestone / Dirt / Deepslate / Andesite / **Same Color Block** (uses the same block as the top layer for support in each column, most material-efficient and prevents showing through with different colors).

## V. Image Preprocessing (Ctrl+P)

Adjustments made to the image before quantization (scaling to target grid size); off by default, does not alter the original image:

-   **Brightness / Contrast / Gamma**: Gamma < 100 brightens dark areas, > 100 darkens them.
-   **R / G / B Channel Gain**: Adjusts each channel independently.
-   **Saturation / Desaturate**: Saturation 0 is equivalent to grayscale; Desaturate directly converts to grayscale.
-   **Hue Separation**: Compresses colors into 2~32 color levels, suitable for posterized or cartoon effects.
-   **Background Processing**: Transparent PNGs can have a background color applied—"Dither" directly applies the selected color and then quantizes; "Smooth" applies the closest solid color of a selectable block.

All sliders update the preview in real-time while dragging; "Cancel" restores the state before opening, "Apply" keeps the changes.

## VI. Crop and Selection (ROI, Ctrl+J)

-   **Drag a rectangle** on the original image to convert only a local area; precise input using X/Y/Width/Height percentages is also available.
-   "Fit Map Aspect Ratio" automatically adjusts the selection to the same aspect ratio as the target map count, avoiding stretching.
-   "Clear Selection" restores conversion of the entire image.

## VII. Pixel-Level Texture Editing (Ctrl+Shift+E)

-   After generation, paint the top layer blocks pixel by pixel: select brush blocks on the left (from currently checked blocks), supports 1/3/5 pixel brushes, and right-click on the canvas to **sample color**.
-   Flat mode supports **erasing** (relief mode only supports color changes).
-   "Discard Changes" reverts the current editing session; "Generate Preview" at any time will recalculate based on parameters.

## VIII. Block Library (Ctrl+B)

### 1. Browse and Search

-   Partitioned by category (Wool / Concrete / Terracotta / Glazed Terracotta / Stained Glass / Nature / Ores / Special); each card displays the actual 16×16 texture + Chinese name. Click anywhere on the card to check it.
-   Search box supports Chinese names and English IDs; double-press Shift to globally bring up and focus the search.

### 2. Tag Filtering

-   Transparent, Emissive, Crafting Required, Ore, Nether, End, Creative Exclusive, Liquids; multiple tags use an "AND" relationship.

### 3. Presets

-   Built-in: Default (Wool + Concrete), Survival Friendly, Commonly Used for Building, Nature Blocks Only.
-   "Save as Preset" saves the current selection as JSON; "Manage" allows deletion / import / export, facilitating sharing among teams (files located in user directory presets/).

### 4. Custom Blocks

-   "+ Custom Block": Enter block ID (e.g., `mymod:ruby_block`), display name, and map color RGB.
-   Custom blocks are written to `custom-blocks.json`; multiple definitions can also be placed in the plugin directory `plugins/blocks/*.json` for batch loading.
-   Note: Custom blocks only affect generation results and export; the corresponding blocks must exist in-game (via mods / resource packs).

## IX. Export System

Select the format from the "Output" dropdown in the toolbar, then click "Export". Automatic quality check before export: errors block, warnings require confirmation.

-   **.litematic**: Litematica projection mod format, one-click paste in-game (most common).
-   **.schem**: Sponge schematic, for WorldEdit 7+ / FAWE.
-   **.schematic**: Classic MCEdit format, compatible with older toolchains.
-   **.nbt**: Vanilla structure block file, loaded directly with structure blocks.
-   **.mcfunction**: setblock command sequence, placed block by block in functions using relative coordinates.
-   **Data Pack .zip**: Contains `pack.mcmeta` and `mcpixel:build` function; unzip into the save's `datapacks` directory, execute `/function mcpixel:build` in-game to auto-build.
-   **Bill of Materials .csv**: Quantity and total for each block (UTF-8 BOM, opens correctly in Excel without garbling); can also be viewed, copied in "Tools → Bill of Materials".
-   **Preview Image .png**: Exports a 4x magnified map color preview image.

### Batch Processing (Tools → Batch Processing)

-   Select image folder and output folder, check desired formats, and batch convert an entire folder using the **current project's parameters** (block selection / preprocessing / map count / dithering), with progress and logs.

## X. Quality Warning Center (🔔)

When the bell displays a red number, it indicates issues in the current project that require attention; click to view details:

-   Color Gamut Insufficient: Over 15% of pixels have a large color difference with the nearest block (ΔE>12); adjust preprocessing or add more block types; switch to "Color Gamut Warning" view in the preview; the more transparent red, the harder it is to reproduce.
-   Creative Exclusive / Hard-to-Obtain Survival Blocks Used.
-   Custom Blocks Used (require mods / resource packs).
-   Large Dimensions (over 4x4 maps, approx. 260,000 blocks), workload reminder.
-   No blocks selected, not generated, or other blocking errors.

## XI. View, Layout, Theme, Language

-   **Four Previews**: Block texture top-down, map color top-down (actual in-game map effect), interactive relief model (left-click rotate / right-click pan / scroll zoom / double-click reset), color gamut warning.
-   **Layout** (View menu or Settings): Full Dual Panel / Preview Only Large / Original Only.
-   **Theme**: Dark (default) / Light / High Contrast (Accessibility); interface rebuilds automatically after switching.
-   **Language**: Chinese / English.

## XII. Command Line (CLI / Headless Mode)

Runs without opening the GUI, suitable for server pipelines and script batch processing:

```
java -jar main.jar --cli \
  --input cat.png --output out.litematic \
  --mode flat --dither BLUE_NOISE --strength 80 \
  --maps 2x2 --blocks survival --support stone
```

Parameters:

-   `--input/-i` input image; `--output/-o` output file (format inferred from extension, or specify with `--format`).
-   `--mode flat|relief`; `--dither` algorithm name (NONE/BLUE_NOISE/FLOYD_STEINBERG/BURKES/ATKINSON/STUCKI/JARVIS/SIERRA/ORDERED).
-   `--strength 0~100`; `--maps 2x2` or `--maps-w/--maps-h`.
-   `--blocks default|survival|building|natural|all`, or a comma-separated list of block IDs or a preset JSON file.
-   `--support stone|cobblestone|dirt|deepslate|andesite|same`.
-   `--format litematic|schem|schematic|nbt|mcfunction|datapack|csv|png`.
-   `--help` to view all parameters. Exit codes: 0 success, 1 conversion failed, 2 parameter error.

## XIII. User Data Directory

All user data is separated from the program itself and located in **`~/.mc-pixel-art/`** (can be overridden with `-Dmcart.home=directory`):

-   `config.properties`: Theme, language, layout, recent files, session recovery toggle.
-   `palette.properties`: Title bar document color theme.
-   `custom-blocks.json`: Custom blocks.
-   `presets/*.json`: User block presets.
-   `plugins/blocks/*.json`, `plugins/presets/*.json`: Plugin-based block and preset extensions.
-   `projects/session/`: Automatic session save during crashes / exits.
-   `logs/`: Runtime logs and crash reports (crash pop-up indicates log location for feedback).

Plugin Block JSON Example:

```
{ "blocks": [
  { "id": "mymod:ruby_block", "name": "Ruby Block", "r": 220, "g": 40, "b": 90 }
] }
```

## XIV. Frequently Asked Questions

-   **Colors are wrong in-game after export?** Confirm game version and blocks are consistent; relief pixel art shading comes from block height differences, map items must be viewed facing the correct direction (map facing north).
-   **Want colors closer to the original image?** Increase block variety (terracotta, glazed terracotta, nature blocks), try blue noise dithering, fine-tune contrast and gamma in preprocessing, and use "Color Gamut Warning" to check hard-to-reproduce areas.
-   **Relief mode uses too many blocks?** For fill blocks, choose "Same Color Block" or cheap blocks; reduce height map count; flat mode is the most material-efficient.
-   **CSV opens with garbled characters?** Built-in UTF-8 BOM, open directly with Excel; if still abnormal, use Excel's "Data → From Text" and select UTF-8.
-   **Transparent image background appears black?** In preprocessing, set background to "Dither" or "Smooth" and select a background color.

## XV. Shortcut Key Summary

-   Ctrl+N New Project; Ctrl+O Open Image; Ctrl+Shift+O Open Project; Ctrl+S Save Project; Ctrl+Shift+S Save As; Ctrl+E Export.
-   Ctrl+Z / Ctrl+Y Undo / Redo.
-   Ctrl+R Generate Preview; Ctrl+B Block Library; Double-press Shift Search Block.
-   Ctrl+P Preprocessing; Ctrl+J Crop; Ctrl+Shift+E Pixel Edit.
-   Alt+\\ Toggle Compact / Main Menu; F1 Open Manual.
