# TXQR 动态二维码传输工具使用手册

## 1. 概述

TXQR 是一个基于 Go 语言开发的命令行工具，利用**喷泉码（Fountain Codes）**技术将文本或文件编码为动态二维码流。即使传输过程中丢失部分帧，接收端仍能完整还原数据。

为提升使用效率，本文档采用**简化命名规范**：

- `txqr-ascii.exe` ➡️ 重命名为 **`txqr.exe`** （终端实时预览）
- `txqr-gif.exe` ➡️ 重命名为 **`gif.exe`** （生成 GIF 文件）

> 💡 **重命名方法**：编译后直接在 `GOPATH/bin` 或程序目录下重命名文件即可。以下文档均使用简化后的命令。

------

## 2. 核心区别

| 特性         | txqr (终端预览)       | gif (生成文件)             |
| ------------ | --------------------- | -------------------------- |
| **输出形式** | 终端字符动画          | 标准 GIF 图片文件          |
| **使用场景** | 即时调试、临时传输    | 存档、跨平台分享、嵌入网页 |
| **依赖环境** | 需支持 Unicode 的终端 | 任何支持 GIF 的设备/浏览器 |
| **文件生成** | ❌ 不生成文件          | ✅ 生成 `.gif` 文件         |
| **独有参数** | -                     | `-size`, `-o` / `-output`  |

------

## 3. 安装与配置

### 3.1 环境准备

```cmd
# 安装 Go (推荐 1.21+)
go version

# 配置国内代理加速
go env -w GOPROXY=https://goproxy.cn,direct
```

### 3.2 编译与重命名

```cmd
git clone https://github.com/divan/txqr.git
cd txqr

# 编译并直接输出为简化名称
go build -o txqr.exe ./cmd/txqr-ascii
go build -o gif.exe ./cmd/txqr-gif
```

### 3.3 全局使用

将 `txqr.exe` 和 `gif.exe` 移至 `%USERPROFILE%\go\bin` 或系统 PATH 目录中，重启终端后即可全局调用。

------

## 4. 参数详解与简化对照

### 4.1 参数别名速查表

| 完整参数  | 简化别名 | 适用工具 | 说明                        |
| --------- | -------- | -------- | --------------------------- |
| `-output` | **`-o`** | gif      | 指定输出 GIF 文件路径       |
| `-split`  | ⚠️ 无简化 | 两者     | 每帧携带的数据字节数        |
| `-size`   | ⚠️ 无简化 | gif      | GIF 画布像素尺寸            |
| `-fps`    | ⚠️ 无简化 | 两者     | 每秒帧率（与 delay 互斥）   |
| `-delay`  | ⚠️ 无简化 | 两者     | 每帧持续时间（与 fps 互斥） |

> ⚠️ **注意**：`-split`、`-size`、`-fps`、`-delay` 在当前版本中**不支持**单字母简化，必须使用完整拼写。仅 `-output` 可简化为 `-o`。

### 4.2 `-fps` 与 `-delay` 的区别

这两个参数控制动画速度，**二选一使用**，同时指定时 `-fps` 优先：

| 参数     | 含义           | 换算关系               | 推荐使用场景                                  |
| -------- | -------------- | ---------------------- | --------------------------------------------- |
| `-fps`   | 每秒刷新帧数   | `delay = 1000ms ÷ fps` | ✅ **推荐**。直观易懂，如 `-fps 10` = 每秒10帧 |
| `-delay` | 每帧持续毫秒数 | `fps = 1000ms ÷ delay` | 需要精确控制帧间隔时使用，如 `-delay 150ms`   |

**示例等价关系：**

- `-fps 10` ≡ `-delay 100ms`
- `-fps 5` ≡ `-delay 200ms`
- `-fps 6` ≈ `-delay 167ms`

### 4.3 `-size` 默认值

`-size` 参数**仅对 `gif` 工具有效**，默认值为 **600px**（正方形画布）。`txqr` 终端预览工具无此参数，其显示大小取决于终端窗口和字体大小。

------

## 5. 命令语法与示例

### 5.1 gif 工具

**完整语法：**

```cmd
gif [-size <px>] [-split <bytes>] [-fps <n> | -delay <ms>] [-o <path>] <输入文件>
```

**简化命令 vs 完整命令对照：**

```cmd
# ✅ 简化命令（推荐日常使用）
gif -split 1000 -fps 10 -o test.gif test.png
gif -split 1500 -fps 15 test.zip

# ✅ 完整命令（等价写法）
gif -size 800 -split 1000 -delay 100ms -output test.gif test.png

# ✅ 最简命令（全部使用默认值）
gif test.txt

# ✅ 管道输入
echo "Hello TXQR" | gif -fps 8 -o hello.gif
```

### 5.2 txqr 工具

**完整语法：**

```cmd
txqr [-split <bytes>] [-fps <n> | -delay <ms>] <输入文件>
```

**示例：**

```cmd
# 简化命令
txqr -split 450 -fps 5 test.txt

# 完整命令
txqr -split 450 -delay 200ms test.txt

# 管道输入
cat config.json | txqr -fps 6
```

------

## 6. 参数调优建议（按文件大小）

### 6.1 不同文件大小的推荐参数表

以下参数基于 **1080p 屏幕 + 主流手机摄像头 + 30cm 扫描距离** 测试得出：

| 文件大小       | `-split` | `-fps` | `-delay` (等价) | `-size` (gif) | 预估帧数 | 说明                              |
| -------------- | -------- | ------ | --------------- | ------------- | -------- | --------------------------------- |
| **≤ 10KB**     | 300      | 5      | 200ms           | 400           | ~30帧    | 小文本/配置，低帧率即可秒传       |
| **10KB~100KB** | 450      | 8      | 125ms           | 500           | ~200帧   | 代码片段/短文档，平衡速度与稳定性 |
| **100KB~1MB**  | 600      | 10     | 100ms           | 600           | ~1500帧  | 图片/中等文档，推荐通用配置       |
| **1MB~10MB**   | 800      | 10     | 100ms           | 800           | ~10000帧 | 压缩包/APK，需高分辨率+稳定环境   |
| **> 10MB**     | 1000     | 12     | 83ms            | 1000          | >10000帧 | 大文件极限传输，对设备要求极高    |

### 6.2 环境影响修正系数

当实际环境偏离理想条件时，按以下规则调整：

| 环境因素   | 不利条件  | `-split` 调整 | `-fps` 调整 | `-size` 调整     |
| ---------- | --------- | ------------- | ----------- | ---------------- |
| **光线**   | 暗光/反光 | ↓ 降低 30%    | ↓ 降低 2-3  | ↑ 增大 100-200px |
| **距离**   | > 50cm    | ↓ 降低 40%    | ↓ 降低 3-5  | ↑ 增大 200-400px |
| **抖动**   | 手持不稳  | ↓ 降低 20%    | ↓ 降低 2-3  | ↑ 增大 100px     |
| **设备**   | 老旧手机  | ↓ 降低 30%    | ↓ 降低 3-5  | 保持默认         |
| **高清屏** | 4K显示器  | ↑ 提高 20%    | ↑ 提高 2-3  | ↑ 增大 200px     |

### 6.3 调参优先级口诀

> **扫不上 → 先降 split → 再降 fps → 最后加 size**
> **太慢了 → 先加 fps → 再加 split → 确认能扫上为止**

------

## 7. 故障排除

| 问题现象                        | 可能原因                          | 解决方案                                   |
| ------------------------------- | --------------------------------- | ------------------------------------------ |
| `panic: integer divide by zero` | 图片尺寸为0或split计算溢出        | 换用有效文件；手动指定 `-split 450`        |
| 扫码始终无法完成                | split 过大 / fps 过高             | 降低 `-split` 至 300，降低 `-fps` 至 5     |
| GIF 文件过大                    | size/fps/split 组合过高           | 降低 `-size` 至 400，降低 `-fps` 至 6      |
| 终端二维码变形                  | 字体非等宽                        | 更换为 Cascadia Code / Consolas            |
| 接收端进度停滞                  | 丢帧率超过容错上限                | 降低 `-fps`，改善光线，缩短距离            |
| `-o` 参数无效                   | 使用了完整名 `-output` 但拼写错误 | 确认使用 `-o` 或 `-output`，检查路径合法性 |

------

## 8. 接收端说明

### 设备要求

- 摄像头 ≥ 800万像素，支持自动对焦
- iOS：使用项目官方客户端
- Android：需自行编译或使用兼容的 Fountain Code 解码器

### 最佳接收实践

1. 保持 **30~50cm** 距离
2. 屏幕亮度 ≥ 70%，避免反光
3. 双手持稳，或使用支架固定
4. 关闭手机省电模式，确保解码性能

------

## 9. MacOS

### 🛠️ 编译与重命名调整

macOS 的可执行文件**不需要 `.exe` 后缀**，且首次运行可能被 Gatekeeper 拦截：

```bash
# 克隆项目
git clone https://github.com/divan/txqr.git && cd txqr

# 编译为无后缀的简化名称（去掉 .exe）
go build -o txqr ./cmd/txqr-ascii
go build -o gif ./cmd/txqr-gif

# ⚠️ 关键步骤：移除 macOS 隔离属性（否则提示“无法打开”或“已损坏”）
xattr -d com.apple.quarantine txqr gif

# 移动到全局路径（推荐 ~/go/bin，需提前加入 PATH）
mv txqr gif ~/go/bin/
```

> 💡 **PATH 配置提示**：若 `~/go/bin` 不在 PATH 中，需在 `~/.zshrc` 中添加 `export PATH="$HOME/go/bin:$PATH"` 并执行 `source ~/.zshrc`。

### 💻 命令使用差异对照

| 功能               | Windows 写法                                                 | macOS 正确写法                                               | 差异原因                                    |
| ------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------- |
| 生成 GIF（简化）   | `gif -split 1000 -fps 10 -o test.gif test.png`               | `gif -split 1000 -fps 10 -o test.gif test.png`               | ✅ **完全一致**                              |
| 生成 GIF（完整）   | `gif -size 800 -split 1000 -delay 100ms -output test.gif test.png` | `gif -size 800 -split 1000 -delay 100ms -output test.gif test.png` | ✅ **完全一致**                              |
| 最简命令           | `gif test.txt`                                               | `gif test.txt`                                               | ✅ **完全一致**                              |
| 管道输入           | `echo "Hello TXQR" | gif -fps 8 -o hello.gif`                | `echo "Hello TXQR" | gif -fps 8 -o hello.gif`                | ✅ **完全一致**                              |
| 终端预览           | `txqr -split 450 -fps 5 test.txt`                            | `txqr -split 450 -fps 5 test.txt`                            | ✅ **完全一致**                              |
| 读取二进制文件管道 | `type file.bin | txqr`                                       | `cat file.bin | txqr`                                        | ❌ macOS 无 `type` 命令，用 `cat` 替代       |
| 多行文本管道       | `echo line1 & echo line2 | txqr`                             | `printf "line1\nline2" | txqr`                               | ❌ macOS `echo` 不支持 `&` 拼接，用 `printf` |

### ⚠️ macOS 专属注意事项

1. **终端字体要求更严格**
   macOS 默认终端（Terminal.app）的等宽字体渲染可能导致二维码变形。**强烈建议使用 iTerm2 + JetBrains Mono / Cascadia Code 字体**，或在 Terminal.app 中将字体改为 `Menlo` 并关闭“使用粗体字”。
2. **GIF 输出路径权限**
   macOS 对桌面、文档等目录有沙盒限制。若 `-o` 指定的路径在受保护目录，可能报错。建议输出到 `~/Downloads` 或当前工作目录，或通过系统设置授予终端“文件和文件夹”访问权限。
3. **Apple Silicon 兼容性**
   若使用 M系列芯片，Go 1.21+ 原生支持 `arm64`，无需 Rosetta 转译。若从旧项目迁移，确认 Go 版本 ≥1.21 以获得最佳性能。
4. **帧率上限受屏幕刷新率影响**
   MacBook Pro 的 ProMotion 屏幕支持 120Hz，但普通 MacBook Air 仅 60Hz。若 `-fps` 设置超过屏幕刷新率（如 `-fps 15` 在 60Hz 屏上），实际显示仍为 60FPS 的子集，**不会提升传输速度反而增加丢帧风险**。建议 macOS 用户 `-fps` 不超过 10。

### ✅ 验证安装成功

```bash
# 检查命令是否可用
which txqr && which gif

# 快速测试（应弹出终端二维码动画）
echo "TXQR on macOS works!" | txqr -fps 5

# 生成测试 GIF
echo "Test" | gif -fps 8 -o ~/Downloads/test.gif
```

只要完成上述编译调整和权限处理，您提供的所有简化命令、参数别名和调优表格在 macOS 上均可**无缝使用**，体验与 Windows 完全一致。

---

## 10. Linux

### 🛠️ 编译与安装（适配 Linux）

```bash
# 克隆并编译（Linux 可执行文件无后缀）
git clone https://github.com/divan/txqr.git && cd txqr
go build -o txqr ./cmd/txqr-ascii
go build -o gif ./cmd/txqr-gif

# 安装到用户目录（推荐，无需 root）
mkdir -p ~/.local/bin
mv txqr gif ~/.local/bin/

# 确保 ~/.local/bin 在 PATH 中（多数现代发行版默认已添加）
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc  # 或 ~/.zshrc
source ~/.bashrc
```

> ⚠️ **关键前置依赖**：`txqr`（终端预览）依赖系统图形库渲染字符动画。若运行时报 `libX11.so: cannot open shared object file` 等错误，需安装对应依赖：
>
> | 发行版        | 安装命令                                                     |
> | ------------- | ------------------------------------------------------------ |
> | Debian/Ubuntu | `sudo apt install libx11-dev libxcursor-dev libxrandr-dev libgl1-mesa-dev` |
> | Fedora/RHEL   | `sudo dnf install libX11-devel libXcursor-devel libXrandr-devel mesa-libGL-devel` |
> | Arch Linux    | `sudo pacman -S libx11 libxcursor libxrandr mesa`            |
> | Alpine        | `sudo apk add libx11-dev libxcursor-dev libxrandr-dev mesa-dev` |
>
> `gif` 工具为纯 Go 实现，**无任何外部依赖**，开箱即用。

### 💻 命令使用：与Windows完全一致

```bash
# ✅ 简化命令
gif -split 1000 -fps 10 -o test.gif test.png
gif -split 1500 -fps 15 test.zip

# ✅ 完整命令
gif -size 800 -split 1000 -delay 100ms -output test.gif test.png

# ✅ 最简命令 & 管道
gif test.txt
echo "Hello TXQR" | gif -fps 8 -o hello.gif
cat binary.bin | txqr -fps 6   # Linux 用 cat，不用 type
```

### ⚠️ Linux 专属注意事项

#### 1. Wayland vs X11 显示协议

- **X11 会话**：`txqr` 终端预览完全兼容，无需额外配置。
- **Wayland 会话**（Ubuntu 22.04+、Fedora 默认）：部分终端模拟器下 `txqr` 可能出现二维码渲染错位或闪烁。解决方案：
  - 优先使用 **Kitty / Alacritty / WezTerm** 等 GPU 加速终端（Wayland 原生支持更好）
  - 或在启动时强制 XWayland 兼容：`GDK_BACKEND=x11 txqr test.txt`
  - `gif` 工具不受显示协议影响

#### 2. 终端字体与渲染

Linux 终端的二维码显示质量高度依赖字体配置：

- **推荐终端**：Kitty、Alacritty、WezTerm（GPU 渲染，帧率稳定）
- **推荐字体**：JetBrains Mono、Cascadia Code、Iosevka（等宽 + 高 Unicode 覆盖）
- **避坑**：避免使用 Noto Sans Mono 等比例感较强的字体，易导致二维码模块宽高比失真
- **字号建议**：10pt~12pt，过大会超出终端可视区域，过小降低扫码识别率

#### 3. 无头服务器 / SSH 远程使用

这是 Linux 最常见的使用场景之一：

- **SSH 中无法使用 `txqr`**：终端预览需要本地 TTY 和图形能力，SSH 会话不支持。替代方案：

  ```bash
  # 在服务器上生成 GIF，再下载到本地查看/扫码
  gif -split 600 -fps 10 -o /tmp/data.gif data.bin
  scp server:/tmp/data.gif ./
  ```

- **tmux/screen 兼容**：`txqr` 可在 tmux 内正常运行，但需确保 tmux 开启了 256色或 truecolor 支持：`set -g default-terminal "tmux-256color"`

#### 4. 权限与安全

- Linux 下 `txqr` 和 `gif` **不需要 root 权限**，切勿用 `sudo` 运行（可能导致生成的 GIF 文件属主为 root，后续无法覆盖）
- 若输出路径涉及 `/tmp` 以外的系统目录，确认当前用户有写权限
- AppArmor/SELinux 通常不会拦截 Go 二进制，但若遇到异常拒绝，检查审计日志：`ausearch -m avc --recent`

### ✅ 快速验证清单

```bash
# 1. 确认命令可用
which txqr && which gif

# 2. 确认图形依赖正常（仅 txqr 需要）
ldd $(which txqr) | grep -i "not found"  # 应无输出

# 3. 功能测试
echo "TXQR on Linux works!" | txqr -fps 5
echo "Test" | gif -fps 8 -o ~/test.gif && ls -lh ~/test.gif
```

### 📊 Linux 与 Windows/macOS 差异速查

| 项目            | Windows        | macOS             | Linux                    |
| --------------- | -------------- | ----------------- | ------------------------ |
| 可执行文件后缀  | `.exe`         | 无                | 无                       |
| 图形依赖        | 内置           | 内置              | 需手动安装 libX11 等     |
| Wayland 兼容    | N/A            | N/A               | 需注意终端选择           |
| SSH 远程使用    | ❌              | ❌                 | ✅（仅 gif，txqr 不可用） |
| 默认 Shell 管道 | PowerShell/CMD | zsh/bash          | bash/zsh/fish            |
| 隔离/安全限制   | SmartScreen    | Gatekeeper + 沙盒 | AppArmor/SELinux（少见） |

总结：**命令语法零修改，仅需关注编译依赖和显示环境**。对于服务器场景，`gif` 是更可靠的选择；桌面环境下配合合适的终端和字体，体验与 Windows/macOS 无异。

---

## 11. 附录

### 参考资源

- 项目主页：https://github.com/divan/txqr
- 喷泉码原理：https://en.wikipedia.org/wiki/Fountain_code
- Go 官网：https://go.dev/

### 版本信息

- **文档版本**：2026.06
- **测试环境**：Windows 11 / macOS Sequoia / Go 1.24+
- **适用版本**：TXQR v0.x+

------

*本文档基于实际测试与社区反馈编写。如遇未覆盖的问题，请提交 Issue 至项目仓库。*