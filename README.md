# MCART

> 把任意图片转换成 Minecraft 像素画（方块艺术）🎨🟩

[![CI](https://github.com/lrhbit/MCART/actions/workflows/main.yml/badge.svg)](https://github.com/lrhbit/MCART/actions/workflows/main.yml)

MCART 是一个轻量的像素画转换工具：输入一张普通图片，输出一张由 Minecraft 方块调色板拼成的像素画，可直接用于建筑规划、地图画（Map Art）制作或生存模式搭建。

---

## ✨ 特性

- **方块调色板匹配** —— 将图片颜色映射到最接近的 Minecraft 方块颜色，而不是简单的 256 色量化。
- **可调分辨率** —— 自由指定输出宽度/高度（像素画尺寸 = 方块数量）。
- **多种输出格式** —— 导出预览图（PNG），方便在动手搭建前预览效果。
- **无需 Minecraft 客户端** —— 纯离线处理，输入图片即可得到结果。
- **CI 自动化** —— 通过 GitHub Actions 自动构建与校验。

---

## 📁 项目结构
MCART/
├── .github/
│ └── workflows/
│ └── main.yml # GitHub Actions 工作流
├── mc-pixel-art-converter/ # 核心转换器
└── README.md

text

---

## 🚀 快速开始

### 环境要求

- Python 3.8+
- 依赖见 `mc-pixel-art-converter/requirements.txt`（如使用其他语言实现，请以实际为准）

### 安装

```bash
git clone https://github.com/lrhbit/MCART.git
cd MCART

# 安装依赖（如有 requirements.txt）
pip install -r mc-pixel-art-converter/requirements.txt
使用
bash
cd mc-pixel-art-converter

# 基本用法：把 input.png 转成 64 格宽的像素画
python main.py input.png --width 64

# 指定输出路径
python main.py input.png --width 64 --output output.png
参数名称与入口文件名请以 mc-pixel-art-converter/ 目录下的实际实现为准。
```

---

## 🧠 工作原理
读取与缩放 —— 将原图缩放到目标像素尺寸（每个像素 = 一个方块）。

构建调色板 —— 加载 Minecraft 可用方块的颜色表。

颜色匹配 —— 对每个像素，在调色板中寻找色差（如 RGB / Lab 空间距离）最小的方块。

生成输出 —— 合成预览图，并统计每种方块的用量，作为搭建时的材料清单。

---

## 🤝 贡献
欢迎提交 Issue 和 Pull Request！

Fork 本仓库

创建分支：git checkout -b feature/your-feature

提交改动：git commit -m "Add some feature"

推送分支：git push origin feature/your-feature

发起 Pull Request

---

## 📄 许可证
本项目暂未声明许可证。如需开源使用，建议补充 LICENSE 文件（如 MIT）。

---

##⭐ 致谢
如果这个项目对你有帮助，欢迎点个 Star ⭐

