# TXQR Android 接收器

通过摄像头扫描 Mac 屏幕上播放的 txqr-gif 动态二维码，实时解码得到原始文件。

## 功能

- 📷 摄像头实时扫描动态二维码
- 🔓 自动解码 txqr 协议（LT codes / fountain codes）
- 📁 自动识别文件类型（30+ 格式）
- 💾 保存到 `/Download/TXQR/` 目录
- 📂 一键打开文件所在目录
- 🔄 支持连续解码多个文件

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
   - 将摄像头对准 Mac 屏幕上播放的二维码动画
   - 等待解码完成
   - 点击"📂 打开目录"查看文件
   - 点击"🔄 继续扫描"解码下一个文件

## 文件命名

解码后的文件自动命名为 `文件1.扩展名`、`文件2.扩展名`...
扩展名通过文件头魔数自动识别，确保文件可直接使用。

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
│       │   ├── MainActivity.kt      # 主界面
│       │   └── QRCodeAnalyzer.kt    # QR 码识别
│       └── res/
│           ├── layout/activity_main.xml
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
