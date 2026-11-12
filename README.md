# ColorOS 增强

面向 OPPO Find X7 Ultra（PHY110）、ColorOS 17.0.0.100 CN01 / Android 17 API 37 的独立 LSPosed 模块。

主界面包含三个设置入口：

| 入口 | 功能 |
| --- | --- |
| 分屏与小窗 | 应用分屏、小窗数量与比例、圆角与阴影、优先小窗、应用过滤、贴边与展开恢复 |
| OPPO 互联 | 电脑投屏码率、分辨率、DPI、帧率请求，以及解除普通窗口截图录屏限制 |
| AOD | 初始亮度、运行倍率、系统默认选项、显示时长、全天全景、单击防误触、低光保持与功能运行状态 |

## 下载

[下载 v1.2.1 安装包](https://github.com/Ayc-Gh/ColorOS-Enhanced-Apps/releases/download/v1.2.1/ColorOSEnhance_PHY110_v1.2.1.apk) · [发布页与校验文件](https://github.com/Ayc-Gh/ColorOS-Enhanced-Apps/releases/tag/v1.2.1)

## 当前版本

1.2.1 清理应用界面和普通文档中的旧名称及来源展示文字。安装标识、签名、设置键、作用域和功能策略保留，可覆盖已有版本并保留配置。详细变更与本轮验证范围见 [1.2.1 更新说明](docs/BRANDING_1.2.1.md)。

## 安装与使用

1. 安装 `dist/ColorOSEnhance_PHY110_v1.2.1.apk`。
2. 在支持 modern API 102 的 LSPosed 中启用模块，勾选系统框架（`system`）、屏幕投屏（`com.oplus.cast`）、系统界面（`com.android.systemui`）和息屏（`com.oplus.aod`）。
3. 停用其他重叠功能模块，更新后重启手机并打开“ColorOS 增强”。

截图录屏开关位于 **OPPO 互联 → 截图与录屏**，与投屏优化总开关独立。修改捕获开关、窗口比例、圆角或阴影后，重新打开目标应用。投屏参数改变后，停止并重新发起电脑投屏。

建议投屏保留原生协商分辨率、请求 60 帧、均衡码率和当前 DPI。本机原生尺寸为 1440×3168，有效 DPI 为 560；实际发送帧率、码率按内容和网络变化，90/120 帧请求不代表始终达到对应帧率。

分屏、投屏功能在全新安装时默认关闭。AOD 提供独立的“使用系统默认”选项。已有设备迁移的 12 项 AOD 设置保留，其他设备的新安装不会读取另一应用的私有配置。

## 配置与恢复

分屏与投屏使用 `window_capture` 配置组；AOD 使用独立的 `aod_config` 配置组。分屏页面恢复默认仅处理窗口设置；恢复投屏参数保留截图开关。AOD 页面分别显示配置同步与宿主加载状态。

可在对应页面恢复设置，或停用模块后重启。模块不替换系统文件、不写入分屏开发者选项。电脑工程的 `keystore/` 和 `keystore.properties` 用于后续同签名更新，交付源码不包含私钥与密码。

## 实现与验证

- [分屏、小窗与捕获实现](docs/IMPLEMENTATION.md)
- [历史实机验证记录](docs/DEVICE_VALIDATION.md)
- [电脑投屏链路与适配](docs/OPPO_CONNECT_CAST.md)
- [AOD 合并与配置迁移](docs/AOD_INTEGRATION.md)

历史验证结果按对应版本保留；原始截图、视频和日志单独归档，普通交付文档保留结果摘要，不改写原始记录。

普通窗口安全标记的解除不覆盖硬件 DRM、受保护的解码缓冲区或应用自身的内容隐藏。不可调整大小的应用仍可能存在布局问题。贴边保活不能阻止应用主动暂停。

AOD 时长是单次显示上限，解锁、来电和系统结束事件仍遵循原状态机。不强制面板刷新率；真实扫描频率、整夜功耗和全部实体单双击组合尚未扩大验证。

## 构建

需要 JDK 21、Android SDK 37 / Build Tools 37.0.0。工程使用 AGP 9.3.1、Gradle 9.6.1、Compose 插件 2.4.0，以及 libxposed API / Service 102.0.0。正式包仅包含 arm64-v8a。

在 `local.properties` 配置 `sdk.dir`，运行：

```powershell
.\gradlew.bat :app:assembleRelease :app:lintRelease
.\gradlew.bat :app:testDebugUnitTest
```

Windows 上建议在不含中文的路径构建，以避开测试进程的路径识别问题。Release 在存在签名配置时使用个人签名，否则生成未签名包。`probe/` 是独立测试应用，不是正式模块的运行依赖。

## 许可

合并项目采用 AGPL-3.0-or-later。第三方组件的 MIT 版权与许可证完整保留。许可证、必要版权及来源声明统一见 [LICENSE](LICENSE)、[NOTICE](NOTICE) 和 `third_party` 目录。
