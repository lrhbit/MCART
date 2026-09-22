# MCART 🎨🟩

> 把任意图片转换成 Minecraft 地图画：打开图片、调几个参数、导出投影文件，生存模式照着搭就行。

**中文** | [English](./README.md)

[![CI](https://github.com/lrhbit/MCART/actions/workflows/main.yml/badge.svg)](https://github.com/lrhbit/MCART/actions/workflows/main.yml)

<img width="1127" height="700" alt="MCART 界面截图" src="https://github.com/user-attachments/assets/479cdc10-40e5-4eaa-8d58-f9af5310319a" />

MCART 是一个轻量的 Java 桌面工具：读入普通图片，按 Minecraft 官方地图配色（MapColor）做颜色量化，输出由方块拼成的像素画预览与投影文件（`.litematic` / `.schem` / `.schematic`），可直接用于生存模式搭建。

**目录**：[特性](#-特性) · [快速开始](#-快速开始) · [工作原理](#-工作原理) · [从源码构建](#-从源码构建) · [项目结构](#-项目结构) · [贡献](#-贡献) · [许可证](#-许可证)

---

## ✨ 特性

- 🎨 **方块调色板匹配** —— 采用 Java 版官方地图 MapColor（与 mapartcraft 现代配色对齐），同一地图颜色支持多个候选方块（如白羊毛 / 白混凝土 / 雪块），按材料易得程度自由取舍。
- 📐 **尺寸可控** —— 输出 1×1 至 8×8 张地图，每张地图 128×128 格（最大 1024×1024 格）。
- 🧱 **平面 / 立体双模式** —— 平面地图画（贴墙贴地），或立体地图画（按亮度映射方块高度，浮雕效果，可指定支撑方块）。
- 🌈 **9 种抖动算法** —— 原始放缩、蓝噪声混合（OKLab 双色混合 + 误差扩散，推荐）、Floyd-Steinberg、Burkes、Atkinson、Stucki、Jarvis-Judice-Ninke、Sierra、有序抖动。
- 🖼️ **图像预处理** —— 亮度 / 对比度 / 饱和度调节，背景处理（关 / 抖动 / 平滑），滤镜链对齐 mapartcraft；参数改动实时刷新预览。
- 👁️ **三种预览** —— 方块纹理俯视图、游戏内实际地图配色俯视图、可拖动旋转的 3D 立体模型。
- 📦 **方块库** —— 按类别勾选参与匹配的方块，带真实纹理卡片与搜索过滤。
- 💾 **多格式导出** —— `.litematic`（Litematica 投影）、`.schem`（WorldEdit 7+）、`.schematic`（旧版 MCEdit），导出即可用，无需二次转换。
- 🚫 **无需网络** —— 纯离线处理，图片不出本机。
- ⚙️ **CI 自动化** —— GitHub Actions 自动构建 Windows 原生 exe，并在真机上冒烟自测。

---

## 🚀 快速开始

### 下载

| 方式 | 说明 |
| --- | --- |
| [Releases](https://github.com/lrhbit/MCART/releases/latest) → `mcart.jar` | 跨平台通用，需 Java 17+ |
| [Releases](https://github.com/lrhbit/MCART/releases/latest) → `mcart-native-linux-x64-v2.tar.gz` | Linux x64 原生版，免装 Java |
| [Actions 构建产物](https://github.com/lrhbit/MCART/actions/workflows/main.yml) → `mcart-windows-x64` | Windows 原生 `mcart.exe`，免装 Java |

### 环境要求

- **运行**
  - Windows / Linux 桌面环境（其他平台可从源码自行构建）
  - `mcart.jar` 需要 Java 17+；原生版（`.exe` / Linux 原生包）无需安装 Java
- **从源码构建**
  - JDK 17+（打包 Windows 原生 exe 需 GraalVM 25 + native-image）
  - IntelliJ IDEA 可选；第三方依赖（FlatLaf、MigLayout）已随仓库放在 `lib/`

### 使用步骤

1. **打开图片** —— 支持常见图片格式。
2. **设定尺寸** —— 选择地图宽 × 高（1–8 × 1–8 张，每张 128×128 格）。
3. **挑选方块** —— 在「方块库」中勾选允许使用的材料（按类别筛选、可搜索）。
4. **调参数** —— 画面模式（平面 / 立体）、抖动算法、预处理（亮度 / 对比度 / 饱和度 / 背景）；改动会实时刷新预览。
5. **看预览** —— 在方块纹理俯视图、地图配色俯视图、3D 立体模型之间切换确认效果。
6. **导出** —— 保存 `.litematic` / `.schem` / `.schematic`，用 Litematica / WorldEdit 等模组在游戏中加载搭建。

---

## 🧠 工作原理

1. **读取与缩放** —— 将原图缩放到目标方块尺寸（每个像素 = 一个方块）。
2. **构建调色板** —— 加载 Minecraft 方块的官方地图颜色表（含各亮度变体）。
3. **颜色匹配** —— 对每个像素，在调色板中寻找色差（RGB / Lab 空间距离）最小的方块，配合抖动算法还原明暗层次。
4. **生成输出** —— 合成方块纹理预览与游戏内地图配色预览，可选立体浮雕模型，并导出投影文件供游戏内搭建。

---

## 🛠️ 从源码构建

```bash
cd mc-pixel-art-converter/mc-pixel-art-converter

# 编译（Windows 下把路径分隔符 ":" 换成 ";"）
javac -encoding UTF-8 --release 17 -d out/classes -cp "lib/*" \
  $(find src/main/java -name '*.java')

# 运行
java -cp "out/classes:lib/*" com.axolotl.mcart.Main
```

也可以用 IntelliJ IDEA 打开 `mc-pixel-art-converter` 目录，直接运行 `com.axolotl.mcart.Main`。

Windows 原生 exe 由 GitHub Actions（`.github/workflows/main.yml`）基于 GraalVM native-image 自动构建：推送改动到 `main` 即触发，也可在 Actions 页面手动 “Run workflow”，产物为 `mcart-windows-x64`。

---

## 📂 项目结构

```text
mc-pixel-art-converter/mc-pixel-art-converter/
├── src/main/java/com/axolotl/mcart/
│   ├── core/      # 缩放、颜色匹配、抖动、预处理、3D 渲染
│   ├── model/     # 方块调色板、地图画模式
│   ├── export/    # .litematic / .schem / .schematic 导出
│   ├── ui/        # Swing 界面（FlatLaf）
│   └── util/      # 纹理管理等
└── lib/           # 第三方依赖 jar
```

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

## 📄 许可证

Copyright (C) [2026] [Jackin Lee]
源可用非商业相同方式共享许可

<details>
<summary>展开查看完整条款</summary>

1. 授权范围
允许所有人复制、查看、学习、修改本软件源代码，但仅限非商业用途。

2. 衍生作品规则（Copyleft）
如果你修改、基于本项目开发衍生作品，并对外分发（发布二进制程序/源码）：
- 衍生作品必须完整公开源代码；
- 衍生作品必须使用和本协议完全相同的协议；
- 必须标注原项目版权信息。

3. 禁止商业用途
严禁将本软件、衍生作品用于任何商业目的，包括但不限于售卖、付费托管、广告盈利、打包进付费产品、商业服务。
公益自愿捐赠不属于商业用途。

4. 版权声明
分发时，不得删除、遮挡本版权与许可声明。修改的文件需要标注变更记录。

</details>

---

**如果这个项目对你有帮助，麻烦点个 Star ⭐，非常感激**
