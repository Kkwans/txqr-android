# TXQR Android 接收器

通过摄像头扫描 Mac 屏幕上播放的 txqr-gif 动态二维码，实时解码得到原始文件。

## 功能

- 📷 摄像头实时扫描动态二维码
- 🔓 自动解码 txqr 协议（LT codes / fountain codes）
- 📁 自动识别文件类型（30+ 格式）
- 💾 自动保存文件（支持自定义保存目录）
- 📂 一键打开文件所在目录
- 🔄 支持连续解码多个文件
- 🎯 实时进度显示（帧数、百分比、文件大小）
- ⚙️ 可配置扫描分辨率（480p / 720p / 1080p / 1440p）

## 支持的文件格式

### 图片
png, jpg, gif, bmp, webp

### 文档
pdf, doc, docx, xls, xlsx, ppt, pptx

### 压缩包
zip, tar.gz, tar, rar, 7z, gz, bz2

### 文本/代码
txt, md, json, xml, html, css, js, ts, java, kt, py, go, rs, c, cpp, h, sql, sh, yaml, yml, toml, ini, csv, log, svg

### 其他
mp4, mp3, wav, flv, avi, mkv, exe, elf, apk

## 使用方法

1. **Mac 端**：用 txqr-gif 生成动态二维码 GIF
   ```bash
   txqr-gif -split 2000 -fps 15 -o output.gif yourfile
   ```

2. **安卓端**：
   - 安装 TXQR 接收器 APK
   - 打开 App，允许摄像头权限
   - 点击"开始扫描"按钮
   - 将摄像头对准 Mac 屏幕上播放的二维码动画
   - 实时查看解码进度
   - 解码完成后点击"📂 打开目录"查看文件
   - 点击"🔄 继续扫描"解码下一个文件

## 设置

- **分析分辨率**：480p（最快）/ 720p（均衡，日常使用推荐）/ 1080p（最准）/ 1440p（超清）
- **保存目录**：默认 `/Download/TXQR/`，支持自定义
- **进度卡片**：可配置始终显示或仅解码时显示
- **扫描区域提示**：显示扫描参考框

## 文件命名

解码后的文件自动命名为 `文件1.扩展名`、`文件2.扩展名`...
扩展名通过文件头魔数自动识别，确保文件可直接使用。

## 发送端（TXQR 编码工具）

本 App 是接收端，需要配合 [txqr](https://github.com/divan/txqr) 发送端使用。

### 快速入门

1. **安装发送端**（Windows / macOS / Linux 通用）：
   ```bash
   git clone https://github.com/divan/txqr.git && cd txqr
   go build -o gif ./cmd/txqr-gif
   ```

2. **生成二维码 GIF**：
   ```bash
   gif -split 1000 -fps 10 -o output.gif yourfile.zip
   ```

3. **在电脑上播放 GIF**，用本 App 扫描即可接收文件。

### 常用参数速查

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| `-split` | 每帧数据字节数 | 小文件 300-450，大文件 600-1000 |
| `-fps` | 每秒帧数 | 5-10（越高越快，但丢帧风险增大） |
| `-o` | 输出文件路径 | - |
| `-size` | GIF 画布尺寸（px） | 默认 600，大文件建议 800-1000 |

> 💡 **调参口诀**：扫不上 → 先降 split → 再降 fps；太慢了 → 先加 fps → 再加 split

📖 **完整使用手册**（含参数详解、故障排除、多平台适配）：[docs/TXQR-Usage-Guide.md](docs/TXQR-Usage-Guide.md)

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
txqr-android/
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
├── .github/workflows/build.yml  # GitHub Actions
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

## 技术栈

- **解码器**：Go + google/gofountain（gomobile 编译为 Android AAR）
- **QR 识别**：Google ML Kit Barcode Scanning（离线）
- **相机**：CameraX
- **UI**：Kotlin + AndroidX

## 下载

https://github.com/Kkwans/txqr-android/releases
