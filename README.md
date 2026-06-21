# TXQR Android Reader

**安卓端 txqr 动态二维码解码器**

通过摄像头扫描 Mac 屏幕上播放的 txqr-gif 动态二维码，实时解码得到原始文件。

## 工作原理

```
Mac 屏幕播放 GIF (txqr-gif 生成)
        ↓ 摄像头扫描
Android CameraX 实时采集
        ↓ ML Kit 识别 QR 码
TxqrDecoder (LT codes 解码)
        ↓ Base64 解码
保存为原始文件 (Heimdall.tar.gz)
```

## 前提条件

- Android Studio Hedgehog (2023.1) 或更新
- Android SDK 34
- 安卓手机（Android 7.0+），带摄像头

## 编译步骤

1. **用 Android Studio 打开项目**
   ```
   File -> Open -> 选择 txqr-android 目录
   ```

2. **等待 Gradle 同步完成**

3. **连接安卓手机**（USB 调试模式）

4. **点击 Run ▶️ 编译安装到手机**

## 使用方法

1. Mac 上用 txqr-gif 播放 Heimdall.gif（全屏，亮度调高）
2. 安卓手机打开 TXQR Reader App
3. 将摄像头对准 Mac 屏幕
4. 保持稳定，等待解码完成
5. 文件保存到：`/Android/data/com.txqr.reader/files/decoded_file.tar.gz`

## 注意事项

- **屏幕亮度**：Mac 屏幕亮度调到最高
- **距离**：手机距离屏幕 20-40cm
- **稳定性**：尽量保持手机稳定，避免抖动
- **光线**：避免强光直射屏幕，减少反光
- **对焦**：确保摄像头对焦在二维码上

## 技术细节

- **编码协议**：txqr（fountain codes / LT codes）
- **帧格式**：`blockCode/chunkLen/total|data`
- **QR 解码**：Google ML Kit（离线，不需要网络）
- **解码算法**：纯 Kotlin 实现的 LT codes peeling decoder
- **完全离线**：整个过程不需要网络连接

## 项目结构

```
txqr-android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/txqr/reader/
│       │   ├── MainActivity.kt          # 主界面
│       │   ├── QRCodeAnalyzer.kt        # QR 码识别
│       │   └── decoder/
│       │       └── TxqrDecoder.kt       # LT codes 解码器
│       └── res/layout/
│           └── activity_main.xml        # 布局
├── build.gradle
├── settings.gradle
└── README.md
```
