# Xachet

面向湘潭大学的 Android 原生教务与学习专注应用，基于 [Sachet](https://github.com/wyvern1723/sachet) 二次开发，由 **Pluvist** 修改与维护。感谢上游作者 **wyvern1723** 的开源贡献。

Xachet 自 **0.19.78** 起以 Kotlin / Jetpack Compose 全量重写，**0.19.82** 起全面升级为 **Material 3 Expressive** 设计体系并换装全新平面香囊图标，集错峰课表、自定义课程、教务查询、番茄专注计时、实时活动（Live Updates / 流体云）与双尺寸桌面小组件于一体。

[English](README_EN.md) · [构建说明](README_DEV.md) · [更新日志](CHANGELOG.md) · [AGPL-3.0](LICENSE) · [第三方声明](THIRD_PARTY_NOTICES.md)

## 下载与安装

**当前正式版：0.19.82（`versionCode 58`），要求 Android 13（API 33）及以上。**

| 架构 | 适用设备 | 下载 |
| --- | --- | --- |
| arm64-v8a | 绝大多数现代 Android 手机与平板 | [ARM64 APK](https://github.com/Pluvist/Xachet/releases/download/v0.19.82/xachet-v0.19.82-arm64-v8a.apk) |
| armeabi-v7a | 运行 Android 13+ 的 32 位 ARM 设备 | [ARM v7a APK](https://github.com/Pluvist/Xachet/releases/download/v0.19.82/xachet-v0.19.82-armeabi-v7a.apk) |
| x86_64 | 64 位 x86 设备或模拟器 | [x86_64 APK](https://github.com/Pluvist/Xachet/releases/download/v0.19.82/xachet-v0.19.82-x86_64.apk) |

- 校验和（`SHA256SUMS.txt`）与签名证书指纹（`SIGNING-CERTIFICATE.txt`）附于 [最新 Release](https://github.com/Pluvist/Xachet/releases/latest)。
- 本版本包名为 `com.pluvist.xachet`，与 0.19.79–0.19.81 同包名同签名，可直接覆盖升级；早于 0.19.79（`com.pluvist.sachet.next`）或测试版 `.localtest` 为独立包名，不会自动迁移数据。
- Android 12 及更早系统可使用历史版本 [0.19.77](https://github.com/Pluvist/Xachet/releases/tag/v0.19.77)。本版精简更新说明见 [发布说明](docs/releases/0.19.82.md)。

## 核心特性

### 1. Material 3 Expressive 原生界面与新图标
- **全新应用图标**：白色底衬上呈现谷歌风格四色渐变的纯平面香囊轮廓，搭配湘潭大学三拱门造型丝带，同步适配 Android 13+ 单色主题图标与启动图标。
- **可变排版系统**：融合 `Google Sans Flex` 与小米 `MiSans` 可变字体，结合 Material 3 Expressive 弹性曲线与连续形状容器。
- **主题与全局缩放**：内置 **韶峰红**（`#9F181E`）、**湘水蓝**（`#014190`）与 **Legacy**（`#F5ABB9` / `#5BCFFA` 蓝粉双色）三套主题色预设，支持 Android 12+ 壁纸动态取色及浅色/深色/跟随系统模式；提供 `90% / 100% / 110% / 120%` 全局界面缩放调节（默认 `110%`）。

### 2. 主页、错峰课表与自定义课程
- **教学楼错峰作息**：自动识别逸夫楼、信息科技大楼并应用第 1–8 节延后 10 分钟的错峰时间表，第 9–11 节保持统一作息，并可在课程详情中手动切换作息轴。
- **高可读课表网格**：默认打开自动定位至当前或下一节课程；隐藏课表卡片内的授课教师信息，在不同界面缩放档位下自动匹配最大化字号与最大行数，保证课程名与地点完整显示；支持点击“回到本周”触发重叠逐字弹跳动画。
- **自定义课程管理**：支持创建、编辑和删除用户自行添加的课程，并与教务导入课表共享作息与颜色映射。
- **本周状态矩阵**：主页本周课程状态格配备飞出过渡动效，直达每日课程与课节详情。

### 3. 番茄专注、长计时与统计
- **双模式计时器**：支持经典番茄钟（专注/短休息/长休息循环与自定义时长）与正向累计长计时。
- **快速入口与联动**：底栏独立计时按钮，**长按**可弹出快捷浮窗一键继续上次专注或查询空教室；计时页顶部课程详情栏实时展示下一节课信息。
- **专注数据统计**：记录每日目标进度、近周与近月专注趋势以及全天黄金时段分布。

### 4. 双实时活动与桌面小组件
- **课程实时活动**：上课时精简展示数字倒计时，课间展示下一课时间与地点，无课日自动保持安静。
- **番茄专注实时活动**：采用定制纯白钟表图标，番茄钟模式支持通知栏**“停止 / 跳过”**操作，长计时模式支持**“暂停 / 继续 / 停止”**操作；若系统不支持并发多项高优实时活动，专注期间由番茄计时自动接管，并在结束后恢复课程实时活动。
- **Expressive 桌面小组件**：提供 **2×2 今日课程** 与 **4×2 横向双栏概览** 小组件，重构为 Material 3 Expressive 卡片排版并适度增加高度，共享本地课程与错峰时间缓存。

### 5. 教务查询与离线缓存
- 原生 Kotlin 教务通道涵盖培养方案、教材预订（取决于学校账号接口权限）、考试安排、成绩与绩点排名、空闲教室与 PDF 成绩单导出，支持培养方案和教材关键词搜索。
- 优先加载本地缓存并后台静默刷新，保存密码后会话失效可自动重新登录。

## 构建说明

构建要求：**JDK 21+**、**Android SDK 37**、**NDK 27.1.12297006**、**CMake 3.22.1**（工程版本详见 [README_DEV.md](README_DEV.md)）。

Windows：

```powershell
.\native\build-native.ps1
```

Linux / macOS：

```sh
bash native/gradlew -p native :app:assembleDebug :app:testDebugUnitTest --console=plain
```

正式多架构签名构建、环境变量与本地目录说明见 [README_DEV.md](README_DEV.md)。

## 许可证与致谢

本项目整体遵循 [AGPL-3.0](LICENSE) 开源；上游 Sachet 原始代码保留 [MIT 许可证](LICENSE-MIT)（`Copyright (c) 2025 wyvern1723`），Xachet 修改部分版权为 `Copyright (c) 2026 Pluvist (Xachet modifications)`。

- 上游项目：[wyvern1723/sachet](https://github.com/wyvern1723/sachet)
- 参考项目：[Tomato](https://github.com/nsh07/Tomato)（GPL-3.0）、[HyperCeiler](https://github.com/ReChronoRain/HyperCeiler)（AGPL-3.0）、[Cresto](https://github.com/Nevodev/Cresto)（Apache-2.0）、[MeiloX](https://github.com/NEORUAA/MeiloX)
- 开源字体与依赖：`Google Sans Flex`（SIL OFL 1.1）、`MiSans` 可变字体（小米字体知识产权许可）、`PdfBox-Android`（Apache-2.0）等，详情见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

问题反馈请前往 [Xachet Issues](https://github.com/Pluvist/Xachet/issues)。欢迎 Star 支持。
