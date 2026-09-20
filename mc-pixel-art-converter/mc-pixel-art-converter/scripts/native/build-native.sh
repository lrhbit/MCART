#!/usr/bin/env bash
# =============================================================================
# MC 地图画生成器 —— GraalVM native-image 原生二进制构建脚本（Linux / macOS）
#
# 前置：
#   1. 安装 GraalVM for JDK 25（含 native-image）：https://www.graalvm.org/downloads/
#      - 可用 SDKMAN：sdk install java 25-graal
#   2. 设置环境变量 GRAALVM_HOME 指向 GraalVM 安装目录（或将其 bin 加入 PATH）
#   3. Linux 需 gcc/glibc 开发库与 X11/字体库（Ubuntu 示例）：
#        sudo apt install build-essential libz-dev libfreetype6-dev \
#                         libfontconfig1-dev libx11-dev libxext-dev libxrender-dev \
#                         libxtst-dev libxi-dev
#      无显示器的纯服务器可额外安装 xvfb（脚本会自动用 xvfb-run 跑采集）。
#      macOS 需 Xcode Command Line Tools：xcode-select --install
#
# 产物：target/native/dist/mcart（连同同目录的 *.so / *.dylib 一起分发）
# 注意：native-image 不支持交叉编译，本脚本只能产出当前操作系统的原生程序；
#       Windows 的 .exe 必须在 Windows 上运行 build-native.bat 生成。
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TARGET="$PROJECT_ROOT/target/native"
CLASSES="$TARGET/classes"
DIST="$TARGET/dist"
mkdir -p "$CLASSES" "$DIST"

if [ -n "${GRAALVM_HOME:-}" ]; then
  JAVAC="$GRAALVM_HOME/bin/javac"
  JAVA="$GRAALVM_HOME/bin/java"
  NI="$GRAALVM_HOME/bin/native-image"
else
  JAVAC="javac"; JAVA="java"; NI="native-image"
fi
echo "使用编译器: $("$JAVAC" -version 2>&1 | head -1)"
"$NI" --version >/dev/null 2>&1 || { echo "未找到 native-image，请安装 GraalVM（含 native-image）并设置 GRAALVM_HOME"; exit 1; }

# 1) 编译主代码（字节码目标 17，JDK17/25 均可运行）
echo "[1/4] 编译源码..."
find "$PROJECT_ROOT/src/main/java" -name '*.java' ! -path '*META-INF*' > "$TARGET/sources.txt"
"$JAVAC" -encoding UTF-8 --release 17 -d "$CLASSES" \
  -cp "$PROJECT_ROOT/lib/*" @"$TARGET/sources.txt"

# 2) 复制资源（textures 纹理 + META-INF/native-image 反射/资源配置）
echo "[2/4] 复制资源与 native-image 配置..."
cp -r "$PROJECT_ROOT/src/main/resources/." "$CLASSES/"

# 3) 编译采集/自检程序，并在当前平台用 native-image-agent 采集本平台专属元数据
#    （文件对话框、ImageIO、ShellFolder 等；程序会自动弹出并关闭窗口后退出）
echo "[3/4] 在当前平台采集原生镜像元数据（自动弹窗，约十几秒）..."
"$JAVAC" -encoding UTF-8 -cp "$CLASSES:$PROJECT_ROOT/lib/*" \
  -d "$CLASSES" "$SCRIPT_DIR/NativeTrace.java"
CIDIR="$CLASSES/META-INF/native-image/mcart/ci"
mkdir -p "$CIDIR"
CP="$CLASSES:$PROJECT_ROOT/lib/flatlaf-3.7.1.jar"
RUN=()
if [ "$(uname -s)" = "Linux" ] && [ -z "${DISPLAY:-}" ]; then
  if command -v xvfb-run >/dev/null 2>&1; then RUN=(xvfb-run -a); fi
fi
"${RUN[@]}" "$JAVA" -agentlib:native-image-agent=config-output-dir="$CIDIR" \
  -cp "$CP" NativeTrace || echo "警告：agent 采集未成功，将仅使用工程内预置配置"

# 4) AOT 编译为原生二进制
echo "[4/4] native-image 编译（约 3~10 分钟，取决于机器）..."
EXTRA=()
if [ "$(uname -s)" = "Linux" ]; then
  # 低内存机器建议单线程 + gold 链接器降低内存峰值
  EXTRA+=(-H:NumberOfThreads=1 -H:NativeLinkerOption=-fuse-ld=gold -J-Xmx2300m -J-XX:MaxMetaspaceSize=512m)
fi
"$NI" --no-fallback \
  -cp "$CP" \
  -H:Class=com.axolotl.mcart.Main \
  -o "$DIST/mcart" \
  -H:+ReportExceptionStackTraces \
  -H:+AddAllCharsets \
  --initialize-at-run-time=com.axolotl.mcart,com.formdev.flatlaf \
  "${EXTRA[@]}"

echo
echo "构建完成：$DIST"
echo "分发时请把 mcart 与同目录的所有 *.so(Linux)/ *.dylib(macOS) 放在一起。"
