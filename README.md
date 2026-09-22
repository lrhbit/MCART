# MCART 🎨🟩

> Convert any image into Minecraft map art: open an image, tweak a few options, export a schematic — then build it in survival mode.

**English** | [中文](./README_zh_CN.md)

[![CI](https://github.com/lrhbit/MCART/actions/workflows/main.yml/badge.svg)](https://github.com/lrhbit/MCART/actions/workflows/main.yml)

<img width="1155" height="700" alt="MCART screenshot" src="https://github.com/user-attachments/assets/e7ea15a0-33b2-4304-a93a-ec33bd9ae45c" />

MCART is a lightweight Java desktop tool that turns regular images into Minecraft map art: it quantizes your image against the official Minecraft map colors (MapColor), previews the result as blocks, and exports ready-to-use schematic files (`.litematic` / `.schem` / `.schematic`) for building in survival mode.

**Contents**: [Features](#-features) · [Quick Start](#-quick-start) · [How It Works](#-how-it-works) · [Build from Source](#-build-from-source) · [Project Layout](#-project-layout) · [Contributing](#-contributing) · [License](#-license)

---

## ✨ Features

- 🎨 **Block palette matching** — uses the official Java-edition map MapColor table (aligned with mapartcraft's modern palette); several candidate blocks share the same map color (e.g. white wool / white concrete / snow), so you can pick whichever is easiest to gather.
- 📐 **Adjustable resolution** — output from 1×1 up to 8×8 maps, 128×128 blocks per map (up to 1024×1024 blocks).
- 🧱 **Flat & relief modes** — flat map art (for walls or floors), or relief map art (block height mapped from brightness for a 3D embossed look, with a configurable support block).
- 🌈 **9 dithering algorithms** — none, blue-noise blend (OKLab two-color blend + error diffusion, recommended), Floyd-Steinberg, Burkes, Atkinson, Stucki, Jarvis-Judice-Ninke, Sierra, ordered dithering.
- 🖼️ **Image preprocessing** — brightness / contrast / saturation sliders plus background handling (off / dithered / smooth), filter chain aligned with mapartcraft; the preview refreshes live as you adjust.
- 👁️ **Three preview modes** — block-texture top view, in-game map-color top view, and a draggable 3D model.
- 📦 **Block library** — choose which blocks may be used, with real texture cards, categories and search.
- 💾 **Multiple export formats** — `.litematic` (Litematica), `.schem` (WorldEdit 7+), `.schematic` (legacy MCEdit); no post-conversion needed.
- 🚫 **No network required** — fully offline; your images never leave your machine.
- ⚙️ **CI automation** — GitHub Actions builds the native Windows exe and smoke-tests it on a real runner.

---

## 🚀 Quick Start

### Download

| Option | Notes |
| --- | --- |
| [Releases](https://github.com/lrhbit/MCART/releases/latest) → `mcart.jar` | cross-platform, requires Java 17+ |
| [Releases](https://github.com/lrhbit/MCART/releases/latest) → `mcart-native-linux-x64-v2.tar.gz` | native Linux x64 build, no Java needed |
| [Actions artifacts](https://github.com/lrhbit/MCART/actions/workflows/main.yml) → `mcart-windows-x64` | native Windows `mcart.exe`, no Java needed |

### Requirements

- **Running**
  - A Windows / Linux desktop (other platforms: build from source)
  - `mcart.jar` needs Java 17+; the native builds (`.exe` / Linux tarball) need no Java at all
- **Building from source**
  - JDK 17+ (GraalVM 25 + native-image for the Windows exe)
  - IntelliJ IDEA optional; third-party jars (FlatLaf, MigLayout) are bundled in `lib/`

### Usage

1. **Open an image** — any common image format.
2. **Set the size** — maps wide × maps tall (1–8 × 1–8, each 128×128 blocks).
3. **Pick blocks** — tick the materials you want in the block library (categories + search).
4. **Tune options** — flat/relief mode, dithering algorithm, preprocessing (brightness / contrast / saturation / background); the preview refreshes live.
5. **Check the previews** — switch between block texture, in-game map color and the interactive 3D model.
6. **Export** — save as `.litematic` / `.schem` / `.schematic` and load it with Litematica / WorldEdit in game.

---

## 🧠 How It Works

1. **Read & scale** — the source image is resized to the target block resolution (one pixel = one block).
2. **Build the palette** — load the official Minecraft map color table (including brightness variants).
3. **Match colors** — each pixel is mapped to the palette entry with the smallest color distance (RGB / Lab), with optional dithering to preserve gradients.
4. **Generate output** — render the block-texture and map-color previews (plus the optional relief model) and export the schematic for in-game building.

---

## 🛠️ Build from Source

```bash
cd mc-pixel-art-converter/mc-pixel-art-converter

# compile (use ";" instead of ":" on Windows)
javac -encoding UTF-8 --release 17 -d out/classes -cp "lib/*" \
  $(find src/main/java -name '*.java')

# run
java -cp "out/classes:lib/*" com.axolotl.mcart.Main
```

You can also open `mc-pixel-art-converter` in IntelliJ IDEA and run `com.axolotl.mcart.Main` directly.

The native Windows exe is built by GitHub Actions (`.github/workflows/main.yml`) with GraalVM native-image: every push to `main` triggers it, or run the workflow manually from the Actions tab; the artifact is `mcart-windows-x64`.

---

## 📂 Project Layout

```text
mc-pixel-art-converter/mc-pixel-art-converter/
├── src/main/java/com/axolotl/mcart/
│   ├── core/      # scaling, color matching, dithering, preprocessing, 3D rendering
│   ├── model/     # block palette, map art modes
│   ├── export/    # .litematic / .schem / .schematic exporters
│   ├── ui/        # Swing UI (FlatLaf)
│   └── util/      # texture management, helpers
└── lib/           # third-party jars
```

---

## 🤝 Contributing

Issues and Pull Requests are welcome!

---

## 📄 License

Copyright (C) [2026] [Jackin Lee]
Source-available non-commercial share-alike license

<details>
<summary>Expand for the full terms</summary>

1. Scope of authorization
Everyone is allowed to copy, view, study, and modify the source code of this software, but only for non-commercial purposes.

2. Derivative works rules (Copyleft)
If you modify, develop derivative works based on this project, and distribute (publish binary programs/source code) externally:
- Derivative works must fully disclose the source code;
- Derivative works must use a license that is identical to this license;
- The copyright information of the original project must be indicated.

3. Prohibition of commercial use
It is strictly prohibited to use this software and its derivative works for any commercial purposes, including but not limited to selling, paid hosting, advertising for profit, packaging into paid products, and commercial services.
Voluntary donations for public welfare purposes are not for commercial use.

4. Copyright Notice
When distributing, the copyright and license notices must not be removed or obscured. Modified files need to be marked with a change log.

</details>

---

**If this project helps you, please give it a star ⭐ — much appreciated!**
