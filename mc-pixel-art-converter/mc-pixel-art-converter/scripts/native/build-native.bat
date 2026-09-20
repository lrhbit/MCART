@echo off
rem ============================================================================
rem MC 地图画生成器 -- GraalVM native-image 原生 exe 构建脚本（Windows x64）
rem
rem 前置条件：
rem   1. 安装 GraalVM for JDK 25（含 native-image），并设置环境变量 GRAALVM_HOME
rem      下载：https://www.graalvm.org/downloads/  （选 Windows x64, JDK 25）
rem      设置示例（按你的实际路径）：
rem        setx GRAALVM_HOME "C:\graalvm-community-openjdk-25.0.2+10.1"
rem   2. 安装 Visual Studio 2022 Build Tools，勾选「使用 C++ 的桌面开发」
rem      （含 MSVC 编译器、Windows SDK）。native-image 链接时必须有 cl.exe/link.exe。
rem   3. 在开始菜单打开 "x64 Native Tools Command Prompt for VS 2022"，
rem      cd 到本脚本目录，运行 build-native.bat
rem
rem 产物：target\native\dist\mcart.exe，连同同目录的 *.dll 一起分发（整个 dist 文件夹）
rem 注意：native-image 不能在 Linux/macOS 上交叉编译出 Windows exe，必须在 Windows 构建。
rem ============================================================================
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%..\.."
set "TARGET=%PROJECT_ROOT%\target\native"
set "CLASSES=%TARGET%\classes"
set "DIST=%TARGET%\dist"

if not exist "%CLASSES%" mkdir "%CLASSES%"
if not exist "%DIST%" mkdir "%DIST%"

if defined GRAALVM_HOME (
  set "JAVAC=%GRAALVM_HOME%\bin\javac.cmd"
  set "JAVA=%GRAALVM_HOME%\bin\java.exe"
  set "NI=%GRAALVM_HOME%\bin\native-image.cmd"
) else (
  set "JAVAC=javac"
  set "JAVA=java"
  set "NI=native-image"
)

rem 确认 native-image 可用
call "%NI%" --version >nul 2>&1
if errorlevel 1 (
  echo [错误] 未找到 native-image。请安装 GraalVM^（含 native-image^）并设置 GRAALVM_HOME。
  echo        且请在 "x64 Native Tools Command Prompt for VS 2022" 中运行本脚本。
  exit /b 1
)
for /f "delims=" %%v in ('call "%JAVAC%" -version 2^>^&1') do echo 使用编译器: %%v

rem 1) 编译主代码
echo [1/4] 编译源码...
dir /s /b "%PROJECT_ROOT%\src\main\java\*.java" > "%TARGET%\sources.txt"
call "%JAVAC%" -encoding UTF-8 --release 17 -d "%CLASSES%" -cp "%PROJECT_ROOT%\lib\*" "@%TARGET%\sources.txt"
if errorlevel 1 ( echo [错误] 编译失败 & exit /b 1 )

rem 2) 复制资源（textures 纹理 + native-image 反射/资源配置）
echo [2/4] 复制资源与 native-image 配置...
xcopy /e /i /y /q "%PROJECT_ROOT%\src\main\resources\*" "%CLASSES%\" >nul

rem 3) 编译采集程序，并在本机 Windows 上采集专属元数据（文件对话框 / ImageIO /
rem    Win32ShellFolder 等；程序会自动弹出并关闭几个窗口后退出，约十几秒，请勿手动操作）
echo [3/4] 在本机采集 Windows 原生镜像元数据（自动弹窗）...
call "%JAVAC%" -encoding UTF-8 -cp "%CLASSES%;%PROJECT_ROOT%\lib\*" -d "%CLASSES%" "%SCRIPT_DIR%NativeTrace.java"
if errorlevel 1 ( echo [错误] 采集程序编译失败 & exit /b 1 )
set "CIDIR=%CLASSES%\META-INF\native-image\mcart\ci"
if not exist "%CIDIR%" mkdir "%CIDIR%"
"%JAVA%" -agentlib:native-image-agent=config-output-dir="%CIDIR%" -cp "%CLASSES%;%PROJECT_ROOT%\lib\flatlaf-3.7.1.jar" NativeTrace
if errorlevel 1 echo [警告] agent 采集未成功，将仅使用工程内预置配置继续构建。

rem 4) AOT 编译为原生 exe（约 3~10 分钟）
echo [4/4] native-image 编译中，请耐心等待...
call "%NI%" --no-fallback ^
  -cp "%CLASSES%;%PROJECT_ROOT%\lib\flatlaf-3.7.1.jar" ^
  -H:Class=com.axolotl.mcart.Main ^
  -o "%DIST%\mcart.exe" ^
  -H:+ReportExceptionStackTraces ^
  -H:+AddAllCharsets ^
  --initialize-at-run-time=com.axolotl.mcart,com.formdev.flatlaf
if errorlevel 1 ( echo [错误] native-image 构建失败 & exit /b 1 )

echo.
echo 构建完成：%DIST%
echo 分发时请把 mcart.exe 与同目录所有 *.dll 放在一起（整个 dist 文件夹拷贝）。
endlocal
