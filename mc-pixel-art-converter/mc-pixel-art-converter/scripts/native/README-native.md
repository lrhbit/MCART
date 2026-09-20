# GraalVM native-image 原生可执行文件构建说明

本工程除了可以用 `java -jar` / IDEA 运行、用 jlink 打包绿色版外，还支持用
**GraalVM native-image** 把程序 AOT 编译成**原生可执行文件**：启动几乎是秒开、
运行时不需要安装 Java。

> 已在 GraalVM CE **JDK 25**（Linux x64）实测：主窗口、FlatLaf 深色主题、
> 全部 84 张方块纹理、平面/立体两种模式 × 9 种抖动、`.litematic/.schem/.schematic`
> 三种导出、3D 预览、图像预处理面板均可在原生镜像下正常运行。

---

## 一、重要前提（务必先读）

1. **native-image 不支持交叉编译。**
   - 在 Linux 上只能编出 Linux 原生程序，在 macOS 上只能编出 macOS 程序，
     **Windows 的 `mcart.exe` 必须在一台 Windows 机器上构建**（或 Windows 容器/CI）。
   - 本仓库已提供等价的 Linux/macOS 脚本与 Windows 批处理，流程完全一致。

2. **产物不是“单独一个文件”。**
   AWT/Swing 的本地库会以 `*.so`(Linux) / `*.dll`(Windows) / `*.dylib`(macOS)
   的形式输出到可执行文件旁边（如 `awt`、`fontmanager`、`lcms`、`javajpeg` 等）。
   **分发时把整个输出目录（可执行文件 + 同目录所有本地库）一起拷贝。**

3. **体积并不比 jlink 小。**
   实测原生镜像约 **63MB**（jlink 绿色版约 43MB）。native-image 的优势是
   **启动快、无需随附完整 JRE、冷启动内存占用低**，而不是体积更小。

4. 程序入口 `Main` 已内置 `java.home` 自举：原生镜像下 AWT 字体子系统需要
   `java.home` 指向一个存在的目录，程序会在最早期自动把它设为可执行文件所在目录
   （普通 JVM 运行时该逻辑直接跳过，**不影响任何原有行为**）。
   - Linux 字体由系统 fontconfig 提供；Windows 由 GDI 提供。
   - 若在某台精简版 Windows 上出现字体相关报错，可在 exe 同目录放一个最小
     JRE 目录并用 `mcart.exe -Djava.home=<目录>` 启动作为兜底。

---

## 二、Windows 构建（产出 mcart.exe）

1. 安装 **GraalVM for JDK 25 (Windows x64)**，含 native-image：
   https://www.graalvm.org/downloads/
   设置环境变量，例如：
   ```
   setx GRAALVM_HOME "C:\graalvm-community-openjdk-25.0.2+10.1"
   ```
2. 安装 **Visual Studio 2022 Build Tools**，勾选「使用 C++ 的桌面开发」
   （包含 MSVC 编译器与 Windows SDK）。native-image 链接阶段需要 `cl.exe`/`link.exe`。
3. 打开开始菜单里的 **“x64 Native Tools Command Prompt for VS 2022”**，执行：
   ```
   cd 脚本所在目录\scripts\native
   build-native.bat
   ```
4. 产物在 `target\native\dist\`：`mcart.exe` 与同目录若干 `*.dll`，
   把整个 `dist` 文件夹拷走即可双击运行，目标机器无需安装 Java。

> 内存建议 ≥ 6GB；若机器内存较小，可在 bat 的 native-image 命令后追加
> `-J-Xmx3g`（并确保有足够虚拟内存/页面文件）。

## 三、Linux / macOS 构建

```bash
export GRAALVM_HOME=/path/to/graalvm-community-openjdk-25
# Linux 还需：build-essential、libz-dev、libfreetype6-dev、libfontconfig1-dev、
#             libx11-dev、libxext-dev、libxrender-dev、libxtst-dev、libxi-dev
bash scripts/native/build-native.sh
```
产物在 `target/native/dist/mcart`（连同同目录 `*.so` / `*.dylib`）。

---

## 四、native-image 配置文件位置

反射/资源元数据由两部分组成，已随工程提交，**正常构建无需重新生成**：

- `src/main/resources/META-INF/native-image/mcart/app/reachability-metadata.json`
  —— 用 native-image agent 实跑全功能自动采集（FlatLaf/Swing 反射、84 张纹理、
  ImageIO SPI、属性资源等）。
- `src/main/resources/META-INF/native-image/mcart/extra/reflect-config.json`
  —— 对本项目 `com.axolotl.mcart` 下所有类的全反射注册（兜底）。

### 何时需要重新采集

新增了大量依赖反射的第三方库、或新增 UI 组件/资源后原生镜像运行报
`ClassNotFound/反射/资源找不到`时，用 agent 重新采集：

```bash
# 先用普通 GraalVM JVM 启动程序，手动把所有功能点一遍（打开图、生成、切预览、
# 开方块库与预处理面板、三种格式各导出一次），关闭后配置生成到 agent 目录：
"$GRAALVM_HOME/bin/java" \
  -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/mcart/app \
  -cp "target/native/classes:lib/flatlaf-3.7.1.jar" com.axolotl.mcart.Main
```
然后重新执行构建脚本即可。

---

## 五、与其它打包方式对比

| 方式 | 是否需目标机装 Java | 体积(解压后) | 启动 | 说明 |
|---|---|---|---|---|
| 普通 `main.jar` | 需要 Java 17+ | ~1.4MB | 一般 | 跨平台，最省事 |
| jlink + 启动器绿色版 | 不需要 | ~43MB | 较快 | 内置裁剪 JRE，最稳妥 |
| GraalVM native-image | 不需要 | ~63MB(含本地库) | **最快(秒开)** | 原生 AOT，需按平台分别构建 |
