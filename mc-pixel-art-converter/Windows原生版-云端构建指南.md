# 一键生成 Windows 原生 exe（GitHub 云端自动编译，全程不用装环境）

GraalVM 的 native-image **不能跨平台编译**：要得到 Windows 的 `.exe`，就必须在一台装了
Visual Studio 2022 (MSVC) 的 **Windows 机器**上编译。本机没有 Windows 也没关系——
GitHub 免费给每个账号提供一台装好 VS2022 的 Windows 云端虚拟机（Actions），
本工程已经配好脚本，你只要把代码传上去，它会自动编译，然后你下载 exe 即可。

> 私有仓库每月免费 2000 分钟，本项目编译一次约 5～8 分钟，免费额度完全够用。

## 操作步骤（网页点选，零命令行）

1. 打开 https://github.com 注册并登录账号（已有账号直接登录）。
2. 右上角 **`+` → `New repository`**：
   - Repository name 随便填，例如 `mcart`；
   - 选择 **Private（私有）**；
   - 点 **Create repository**。
3. 在新建好的空仓库页面，点 **“uploading an existing file”**（上传已有文件）链接。
4. 解压本压缩包，进入最外层的 `mc-pixel-art-converter` 文件夹，
   **把里面的全部内容**拖到网页上传区。里面应当能看到：
   - `.github`（**隐藏文件夹**，必须传上去，自动化脚本就在里面）
   - 内层 `mc-pixel-art-converter` 文件夹（真正的工程）
   - `*.iml` 等 IDEA 文件

   > Windows 看不到 `.github`？资源管理器顶部 **“查看” → “显示” → 勾选“隐藏的项目”**。
   > 上传时请确认仓库根目录下直接就是 `.github` 和内层 `mc-pixel-art-converter`，
   > **不要再多套一层 `mc-pixel-art-converter` 文件夹**。
5. 页面底部点 **Commit changes**。
6. 推送完成后，点仓库顶部的 **Actions** 标签，会看到一条正在运行的任务
   **“构建 Windows 原生版 (GraalVM native-image)”**，等它变成绿色对勾（约 5～8 分钟）。
7. 点这条运行记录，拉到页面最下方 **Artifacts**，下载 **`mcart-windows-x64`**。
8. 解压下载的 zip，**整个文件夹一起保留**（里面是 `mcart.exe` + 若干同名 `.dll`，
   这些 dll 是 AWT 图形/图片库，不能删、不能只拿 exe），双击 `mcart.exe` 即可运行，
   目标机器无需安装 Java。

## 想重新编译时

- 改了代码再 Commit，会自动重新构建；
- 不改代码也想重跑：Actions 页左侧选中该工作流 → 右侧 **Run workflow** 手动触发。

## 如果构建失败（红叉）

点进失败的任务，点开报错的那一步，把日志截图或复制发回给我，我来调整脚本。
常见原因一般是 GraalVM 版本号变动或网络波动，重跑一次（Run workflow）往往即可。

---

## 附：在自己电脑上本地编译（可选）

若你本机已装 GraalVM CE JDK25（含 native-image）和 Visual Studio 2022 Build Tools
（勾选“使用 C++ 的桌面开发”），可打开 **x64 Native Tools Command Prompt for VS 2022**，
进入内层工程目录运行：

```
scripts\native\build-native.bat
```

产物在 `target\native\dist`（mcart.exe + dll）。详见
`mc-pixel-art-converter/scripts/native/README-native.md`。
