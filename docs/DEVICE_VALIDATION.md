# 实机验证记录

测试时间：2026-11-11 至 2026-11-12（北京时间）。设备：OPPO Find X7 Ultra / PHY110，ColorOS 17.0.0.100 CN01、Android 17 API 37；LSPosed 2.2.1（7912）API 102。

## 已验证

| 项目 | 实际结果 |
| --- | --- |
| Release 构建与签名 | 成功；最终测试与交付 APK SHA-256 为 `464baa710b3ced9a097537a949b0b482476846fe6dc299de5ed900c361ab029d` |
| 静态检查 | `:app:lintDebug`：0 错误、6 提示，主要为本地中文字符串和版本等提示 |
| 框架启动 | 最终正式版在 system_server 安装 62 个 hook；日志与实际签名吻合 |
| 缺失接口处理 | Android 17 本机没有可挂的 `WindowSurfaceController.setSecure`，由 `WindowState.setSecureLocked` 和 `Task.setSecure` 覆盖；旧版黑名单入口、ActivityRecord 的 Task 专属分支缺失时单独跳过 |
| 设置读写 | 设置页连接 API 102，显示系统框架已勾选；开关保存、关闭重开和数值范围校验正常 |
| 截图限制关闭 | 测试 Activity 主动设置 FLAG_SECURE；模块功能关闭并重开页面后，截图内容区域为黑色，绿色标记位置 RGB=(0,0,0) |
| 截图限制开启 | 同一测试 Activity 仍报告 `secure=true`；截图恢复绿色和红色标记。绿色位置 RGB=(0,200,122)；服务端窗口不再保留 SECURE |
| 录屏限制对照 | 每种状态各录制 4 秒，720×1584；关闭时抽帧为黑色，开启时绿色位置约 RGB=(0,198,120)。原始视频和抽帧已归档 |
| 即时开关恢复 | 不更新 APK 的同一轮会话中，开启→关闭→重新开启均可同步；关闭后重开测试页面恢复限制 |
| 五个原生小窗 | 任务 583、584、585、586、587 同时出现在系统 `mFlexibleTasks`，scenario=1、hasCaption=true、state=1；后方独立模块设置页正常显示。Probe1 的任务仍标记 nonResizable |
| 数量策略 | 系统策略日志确认 `maxNum=8`、`maxFlexibleTaskNum=5`，最后 `allVisibleWindowSize=6`（5 个小窗 + 后方全屏页） |
| 小窗位置 | 五个窗口可见，竖向有约 32dp 的错位。原始截图已归档 |
| 真正分屏 | 任务 594（Probe1，Manifest 为 resizeableActivity=false）与模块设置页上下分屏并正常绘制；两个 Activity 均为 RESUMED。系统使用 `com.oplus.pscanvas` 原生分屏容器；原始截图已归档 |
| 贴边运行 | 任务 597 收到右侧边缘后命中 `park foreground 597`；测试心跳文件从 ticks=17 增至 52，期间仍为 resumed=true |
| 展开恢复 | 点击侧边图标后画面恢复，任务心跳增至 91；原始截图已归档 |
| 测试任务退出 | 测试任务从最近任务删除，测试应用卸载成功，独立模块保留安装 |

`ProbeActivity` 的画面文字不作为心跳计数依据：测试程序通过自己的私有文件持续记录 ticks。界面截图中的文字可能保留初始绘制值。

## 测试入口与证据说明

小窗测试使用 OEM 原生 `FlexibleWindowManager.startAnyActivity`，显式设置 launch scenario=1、LaunchEmbedded=true 和窗口模式 100。单独只设置 scenario 的 root 测试请求可能回到全屏；这不作为模块小窗成功的证据。最终结论以实际 `mFlexibleTasks`、截图和系统策略日志为准。

分屏测试使用 OEM `OplusSplitScreenManager.splitScreenForRecentTasks` 后选择另一应用。两个测试 Activity 放在同一个包中不能代表跨应用分屏完成，因此最终使用不同包的测试页面与模块设置页验证。

证据仅包含本模块和测试页面。没有把桌面、私人应用、完整框架配置数据库或完整系统日志装入交付源码包。

## 未覆盖的范围

未逐一验证全部 1–10 数量、所有圆角 / 阴影极值、每种自定义比例、隐藏控件所有刷新路径、全部通知与应用跳转入口、黑白名单组合、记住贴边状态的全部重启场景、多 UID 同时贴边、多用户、外接屏和长期稳定性。上述附加开关保留独立控制，交付时关闭。

没有验证银行、支付、视频会员 DRM 等第三方真实受保护内容，也没有去除截屏 / 录屏检测。模块只解除普通窗口的 FLAG_SECURE 限制。

## 最终手机状态

- 独立模块已启用，作用域仅系统框架。
- 已开启：解除小窗应用限制、允许不支持的应用分屏、解除截图录屏限制。
- 小窗上限在测试 5 个后设为 **3 个**；强制多模式、自由比例、启动改写、外观和贴边附加项关闭。
- 原有 ColorOS 小窗模块保持用户停用状态；参考实现 的截图限制解除开关关闭，其他作用域恢复。
- 临时测试过的 `force_resizable_activities` 已恢复原值 0，正式模块不依赖该设置。
- LSPosed 开发者模式、hook 调试均恢复关闭。
- 测试 APK 已卸载，专用临时文件已删除。工程、签名、安装包与分析材料保存在电脑。

系统日志可能保留正常安装、启动和卸载事件，本次没有清除其他应用或系统的历史记录。
