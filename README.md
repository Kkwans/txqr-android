# TXQR Reader Android

基于 [TXQR](https://github.com/divan/txqr) 协议的 Android 接收端应用，通过摄像头扫描电脑屏幕上播放的动态二维码，实时解码并还原原始文件。

## 项目概述

TXQR 利用**喷泉码（Fountain Codes）**技术将任意文件编码为动态二维码流。即使传输过程中丢失部分帧，接收端仍能完整还原数据。本项目是 TXQR 生态中的 Android 接收端，需要配合 [TXQR 发送端](https://github.com/divan/txqr) 使用。

**传输流程：**

```
电脑端（发送端）                        手机端（接收端）
┌─────────────────┐                  ┌─────────────────┐
│  原始文件        │                  │  摄像头扫描      │
│       ↓         │                  │       ↓         │
│  喷泉码编码      │    动态二维码     │  ML Kit 识别    │
│       ↓         │  ──────────────→  │       ↓         │
│  生成二维码动画  │    屏幕播放       │  喷泉码解码      │
│       ↓         │                  │       ↓         │
│  播放 GIF/终端   │                  │  还原原始文件    │
└─────────────────┘                  └─────────────────┘
```

## 主要功能

- 📷 摄像头实时扫描动态二维码
- 🔓 自动解码 TXQR 协议（LT codes / fountain codes）
- 📁 自动识别文件类型（30+ 格式，通过文件头魔数）
- 💾 自动保存文件（支持自定义保存目录）
- 📂 一键打开文件所在目录
- 🔄 支持连续解码多个文件
- 🎯 实时进度显示（帧数、百分比、文件大小）
- ⚙️ 可配置扫描分辨率（480p / 720p / 1080p / 1440p）
- 🎨 呼吸动画状态指示

## 快速上手

### 第一步：安装发送端（电脑）

发送端是 Go 语言编写的命令行工具，支持 Windows、macOS、Linux。

#### Windows

```powershell
# 安装 Go（推荐 1.21+）
# 下载地址：https://go.dev/dl/

# 配置国内代理
go env -w GOPROXY=https://goproxy.cn,direct

# 克隆并编译
git clone https://github.com/divan/txqr.git
cd txqr
go build -o txqr.exe ./cmd/txqr-ascii
go build -o gif.exe ./cmd/txqr-gif
```

#### macOS

```bash
# 安装 Go（推荐 1.21+）
brew install go

# 配置国内代理
go env -w GOPROXY=https://goproxy.cn,direct

# 克隆并编译
git clone https://github.com/divan/txqr.git && cd txqr
go build -o txqr ./cmd/txqr-ascii
go build -o gif ./cmd/txqr-gif

# 移除 macOS 隔离属性
xattr -d com.apple.quarantine txqr gif

# 移动到全局路径
mv txqr gif ~/go/bin/
```

> 💡 若 `~/go/bin` 不在 PATH 中，需在 `~/.zshrc` 中添加 `export PATH="$HOME/go/bin:$PATH"` 并执行 `source ~/.zshrc`。

#### Linux

```bash
# 安装 Go（推荐 1.21+）
# Ubuntu/Debian: sudo apt install golang-go
# Fedora: sudo dnf install golang
# Arch: sudo pacman -S go

# 配置国内代理
go env -w GOPROXY=https://goproxy.cn,direct

# 克隆并编译
git clone https://github.com/divan/txqr.git && cd txqr
go build -o txqr ./cmd/txqr-ascii
go build -o gif ./cmd/txqr-gif

# 安装到用户目录
mkdir -p ~/.local/bin
mv txqr gif ~/.local/bin/
```

> ⚠️ Linux 的 `txqr`（终端预览）依赖图形库，若报错需安装：`sudo apt install libx11-dev libxcursor-dev libxrandr-dev libgl1-mesa-dev`

### 第二步：安装接收端（手机）

从 [Releases](https://github.com/Kkwans/txqr-reader-android/releases) 下载最新 APK，安装到 Android 手机。

### 第三步：传输文件

1. **电脑端** — 生成二维码 GIF：
   ```bash
   gif -split 1000 -fps 10 -o output.gif yourfile.zip
   ```

2. **电脑端** — 用图片查看器全屏播放生成的 GIF

3. **手机端** — 打开 TXQR Reader，点击"开始扫描"，对准屏幕

4. **手机端** — 等待解码完成，点击"📂 打开目录"查看文件

## 发送端使用指南

### 两个工具的区别

| 特性 | txqr（终端预览） | gif（生成文件） |
|------|-----------------|-----------------|
| 输出形式 | 终端字符动画 | 标准 GIF 图片文件 |
| 使用场景 | 即时调试、临时传输 | 存档、跨平台分享 |
| 文件生成 | ❌ 不生成文件 | ✅ 生成 `.gif` 文件 |

### 常用参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `-split` | 每帧携带的数据字节数 | - |
| `-fps` | 每秒帧数（与 `-delay` 互斥） | - |
| `-delay` | 每帧持续毫秒数（与 `-fps` 互斥） | - |
| `-size` | GIF 画布像素尺寸（仅 gif 工具） | 600 |
| `-o` | 输出文件路径（仅 gif 工具） | - |

### 参数调优建议

| 文件大小 | `-split` | `-fps` | `-size` | 说明 |
|----------|----------|--------|---------|------|
| ≤ 10KB | 300 | 5 | 400 | 小文本，低帧率即可 |
| 10KB~100KB | 450 | 8 | 500 | 代码/短文档 |
| 100KB~1MB | 600 | 10 | 600 | 通用推荐 |
| 1MB~10MB | 800 | 10 | 800 | 大文件，需稳定环境 |
| > 10MB | 1000 | 12 | 1000 | 极限传输 |

> 💡 **调参口诀**：扫不上 → 先降 split → 再降 fps → 最后加 size；太慢了 → 先加 fps → 再加 split

### 环境修正

| 环境因素 | 不利条件 | `-split` 调整 | `-fps` 调整 |
|----------|----------|---------------|-------------|
| 光线 | 暗光/反光 | ↓ 30% | ↓ 2-3 |
| 距离 | > 50cm | ↓ 40% | ↓ 3-5 |
| 抖动 | 手持不稳 | ↓ 20% | ↓ 2-3 |

### 故障排除

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 扫码始终无法完成 | split 过大 / fps 过高 | 降低 `-split` 至 300，降低 `-fps` 至 5 |
| GIF 文件过大 | size/fps/split 组合过高 | 降低 `-size` 至 400，降低 `-fps` 至 6 |
| 接收端进度停滞 | 丢帧率超过容错上限 | 降低 `-fps`，改善光线，缩短距离 |

> 📖 **完整使用手册**（含详细参数说明、多平台适配、故障排除）：[docs/TXQR-Usage-Guide.md](docs/TXQR-Usage-Guide.md)

## 接收端使用指南

### 界面说明

| 状态 | 圆点颜色 | 说明 |
|------|----------|------|
| 等待开始 | 🟡 黄色静态 | 等待用户点击"开始扫描" |
| 等待扫描 | 🟡 黄色呼吸 | 已开始扫描，等待二维码 |
| 正在解码 | 🔵 青色呼吸 | 正在接收并解码数据 |
| 解码完成 | 🟢 绿色静态 | 文件已保存 |

### 设置选项

- **分析分辨率**：480p（最快）/ 720p（均衡，日常推荐）/ 1080p（最准）/ 1440p（超清）
- **保存目录**：默认 `/Download/TXQR/`，支持自定义
- **进度卡片**：可配置始终显示或仅解码时显示
- **扫描区域提示**：显示扫描参考框

### 最佳接收实践

- 保持 30~50cm 距离
- 屏幕亮度 ≥ 70%，避免反光
- 双手持稳，或使用支架固定
- 关闭手机省电模式，确保解码性能

## 编译

```bash
# 通过 GitHub Actions 自动编译
git push origin master

# 或本地编译（需要 Go 1.25+、Android SDK）
cd decoder && gomobile bind -target=android -androidapi 24 -o ../app/libs/txqr.aar ./mobile
gradle assembleDebug
```

## 项目结构

```
txqr-reader-android/
├── app/                          # Android App
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/txqr/reader/
│       │   ├── MainActivity.kt      # 主界面 + 扫描逻辑
│       │   ├── SettingsActivity.kt  # 设置页面
│       │   ├── OverlayView.kt       # 扫描区域叠加层
│       │   └── BreathingDotView.kt  # 呼吸动画圆点
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── activity_settings.xml
│           └── xml/file_paths.xml
├── decoder/                      # Go txqr 解码器
│   ├── go.mod
│   └── mobile/
│       └── mobile.go                # gomobile 绑定
├── docs/
│   └── TXQR-Usage-Guide.md         # TXQR 完整使用手册
├── .github/workflows/build.yml  # GitHub Actions
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

## 技术栈

- **解码器**：Go + [google/gofountain](https://github.com/google/gofountain)（gomobile 编译为 Android AAR）
- **QR 识别**：Google ML Kit Barcode Scanning（离线）
- **相机**：CameraX
- **UI**：Kotlin + AndroidX

## 支持的文件格式

| 类别 | 格式 |
|------|------|
| 图片 | png, jpg, gif, bmp, webp |
| 文档 | pdf, doc, docx, xls, xlsx, ppt, pptx |
| 压缩包 | zip, tar.gz, tar, rar, 7z, gz, bz2 |
| 文本/代码 | txt, md, json, xml, html, css, js, ts, java, kt, py, go, rs, c, cpp, h, sql, sh, yaml, yml, toml, ini, csv, log, svg |
| 其他 | mp4, mp3, wav, flv, avi, mkv, exe, elf, apk |

## 相关链接

- **TXQR 发送端**：https://github.com/divan/txqr
- **TXQR Reader Android 下载**：https://github.com/Kkwans/txqr-reader-android/releases
- **喷泉码原理**：https://en.wikipedia.org/wiki/Fountain_code

## License

MIT
