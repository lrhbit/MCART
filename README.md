# MC 像素画转换器（MCART）

一个将图片转换为 Minecraft 像素画的桌面程序。程序对图片进行量化，从指定的方块中匹配颜色，生成平面或带高度的方块布局，并可导出为结构文件、命令或数据包，在游戏中使用。

图形界面基于 Java Swing，使用 FlatLaf 外观与 MigLayout 布局；核心逻辑由 Java 与 Kotlin 混合实现，通过 Gradle 构建。除图形界面外，也提供无界面的命令行模式。

## 目录结构

仓库分为两个目录，应用源码内容一致，区别在于是否包含原生镜像构建所需的文件。

| 目录 | 内容 | 用途 |
|---|---|---|
| `jar/` | 完整 Java、Kotlin 源码与 Gradle 构建脚本，不含原生构建文件 | 构建可运行的 fat jar |
| `native-build/` | 在 `jar/` 基础上增加原生镜像配置、元数据、采集器和资源 | 构建各平台原生程序，CI 在此目录执行 |

`native-build/` 中与原生构建相关的目录：

- `native-config/`：平台无关的反射、资源等 GraalVM 配置基线。
- `linux-trace/`：在 Linux 上通过 tracing agent 采集的运行期元数据。
- `native-resources/`：Windows 平台使用的 fontconfig 二进制文件与可执行文件清单。
- `tools/NativeMetadataCollector.java`：元数据采集器，在 GraalVM JVM 下运行，配合 agent 采集反射、JNI、资源等运行期信息。

## 功能范围

- 两种生成模式：平面（方块同高）、浮雕（按图像内容生成阶梯高度，通过方块高度差呈现明暗）。
- 多种抖动方式：无抖动、蓝噪声混合、Floyd-Steinberg、Burkes、Atkinson、Stucki、Jarvis、Sierra、有序抖动；抖动强度可调。
- 图像预处理：亮度、对比度、伽马、通道增益、饱和度、色相分离、背景处理，以及裁剪区域选择。
- 方块库：按类别浏览与搜索，支持标签筛选、预设、自定义方块。
- 预览：方块纹理、地图颜色、浮雕模型、色域警告四种视图，支持缩放与平移。
- 项目管理：多标签页、撤销重做、项目文件保存、会话恢复、方案快照对比。
- 导出格式：`.litematic`、`.schem`、`.schematic`、`.nbt`、`.mcfunction`、数据包 `.zip`、物料清单 `.csv`、预览图 `.png`。
- 批量处理与命令行模式。
- 深色、浅色、高对比度三种主题，中英文界面。

## 构建

### 构建 fat jar

需要 JDK 17。

```bash
cd jar
./gradlew jar
```

产物位于 `build/libs/` 下，使用 `java -jar <jar 文件>` 运行。

### 构建原生程序

原生镜像由 GraalVM native-image 生成，对应的 workflow 位于 `.github/workflows/native-build.yml`，在推送或手动触发时执行，包含两个并行任务：

- `build-windows`：在 `windows-latest` 上构建，包含字体配置、GUI 子系统设置（无控制台窗口）和原生库压缩。
- `build-linux`：在 `ubuntu-latest` 上构建，字体通过系统 fontconfig 获取，不打包字体文件。

两个任务的构建产物均通过 Actions artifacts 提供。

本地构建原生程序需要安装 GraalVM 25 并包含 native-image 组件。native-image 不支持交叉编译，构建某个平台的程序需要在对应系统上进行。Linux 构建时运行环境需具备 fontconfig；Windows 构建所需的链接选项与字体文件仅适用于 Windows。

## 命令行模式

```bash
java -jar <jar 文件> --cli \
  --input 图片.png --output 输出.litematic \
  --mode flat --dither BLUE_NOISE --strength 80 \
  --maps 2x2 --blocks default --support stone
```

使用 `--help` 查看全部参数。

## 数据目录

程序运行产生的配置、预设、项目和日志默认保存在可执行文件同级的 `mc-pixel-art-data/` 目录，不写入用户主目录；可通过启动参数 `-Dmcart.home=路径` 修改位置。

## 技术栈与版本

- Java 17、Kotlin 2.0.20
- Gradle 8.x、FlatLaf 3.7.1、MigLayout 3.7.4
- GraalVM 25.0.2（native-image）
