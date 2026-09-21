# MCART

Convert any image into a Minecraft map art 🎨🟩

[![CI](https://github.com/lrhbit/MCART/actions/workflows/main.yml/badge.svg)](https://github.com/lrhbit/MCART/actions/workflows/main.yml)

<img width="1155" height="700" alt="image" align="centerr" src="https://github.com/user-attachments/assets/e7ea15a0-33b2-4304-a93a-ec33bd9ae45c" />

MCART is a lightweight Java-based tool that can convert images into map art for Minecraft : 
open a regular image, configure some parameters, and you can output a pixel art composed of the Minecraft block palette, which can be directly used for building in survival mode.

---

## ✨ Features

- **Block Palette Matching** - Maps the colors of the image to the closest block colors in Minecraft.
- **Adjustable resolution** —— freely specify the output width/height (pixel art size = number of tiles).
- **Multiple output formats** —— Export in formats such as litematic Schematic, which can be viewed in-game directly after download without conversion.
- **No network required** —— Purely offline processing, input an image and get the result.
- **CI Automation** - Automatically build and verify through GitHub Actions.

---

## 🚀 Quick Start

### Environmental requirements

---

## 🧠 Working Principle
Read and scale - Scale the original image to the target pixel size (each pixel = one square).

Build a color palette - Load the color table of available blocks in Minecraft.

Color matching - For each pixel, find the square with the smallest color difference (such as RGB/Lab spatial distance) in the color palette.

Generate output - Synthesize a preview image and tally the usage of each type of block, serving as a material list for construction.

---

## 🤝 Contribution
Welcome to upload Issues and Pull Requests!

---

## 📄 License
Copyright (C) [2026] [Jackin Lee]
The source is licensed for non-commercial sharing under the same terms

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

---

**If this project is helpful to you, please give it a star ⭐. I would be very grateful**
