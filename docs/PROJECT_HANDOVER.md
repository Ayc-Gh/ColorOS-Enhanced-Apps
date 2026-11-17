# ColorOS 增强项目接手记录

2026 年 11 月 18 日更新：维护源码为 **2.1.7／23**，移除 OPPO 互联增强，截图录屏独立保留；下文保留历史接手基线，当前范围和验证以 [2.1.7 记录](CAPTURE_ONLY_2.1.7.md) 为准。作用域减少为 10 个，移除 `com.oplus.cast`。

接手日期：2026 年 11 月 15 日。11 月 17 日维护基线更新为 **2.1.5，versionCode 21**，按用户指令移除导致缩放归零的自由比例功能，见 [2.1.5 小窗修复](WINDOW_FIX_2.1.5.md)。[2.1.4 图标与主页更新](ICONS_2.1.4.md) 已发布到 [GitHub](https://github.com/Ayc-Gh/ColorOS-Enhanced-Apps/releases/tag/v2.1.4)。2.1.5 当前为本地交付；下文的接手核验保留其日期与版本。项目面向 Find X7 Ultra（PHY110）、`PHY110_17.0.0.100(CN01)`、Android 17 API 37，使用 modern libxposed API 102。

目标聊天的 70 个历史轮次已完整分页读取，并与现有源码、交付文件、GitHub 和手机状态交叉核对。最近一次全面行为回归见 [2.1.2 实测报告](FULL_REGRESSION_2.1.2.md)。本次接手重新运行了构建和现有单元测试，手机侧仅做只读检查。

## 当前基线和目录

| 项目 | 位置或状态 |
| --- | --- |
| 维护源码 | `D:\Ai\CODEX\手机\OShin-WindowCapture`，当前目录不是 Git checkout |
| Android 应用 | `app/`，Java 为窗口、原生应用适配和大部分设置界面；Kotlin 为合并的 AOD 实现与 Compose 页面 |
| 测试应用 | `probe/`，独立辅助 APK，不是正式模块运行依赖 |
| 正式交付 | `dist/ColorOSEnhance_PHY110_v2.1.2.apk`、同版本源码 ZIP、签名说明、SHA256SUMS |
| 历史原始证据 | 工作区 `.analysis/` 和工程内 `.analysis/`，含测试脚本、反编译结果和私有备份 |
| 发布用 Git checkout | `D:\Temp\ColorOS-Enhanced-Apps-publish-20261113`，接手时工作区干净，HEAD 为 `ca5ebde`，版本 2.0.0 |
| 最近构建副本 | `D:\Temp\coloros-enhance-2.0.0-20261114`，名称不代表其中源码一定仍为 2.0.0；维护时以主工程为准 |
| 本次本地核验 | 工作区 `.analysis/project-handoff-20261115/`，保留 `verification.json`、`junit.txt` 和配置摘要 |

11 月 16 日新增本地交付 `dist/ColorOSEnhance_PHY110_v2.1.3.apk` 及对应源码、签名说明和校验表，主工程与手机均更新到 2.1.3／19。最终包 147 项单元测试、构建和 Lint 通过（0 错误、30 警告），实机证据及边界以 [本轮报告](IMPROVEMENTS_2.1.3.md) 为准。原始证据位于工作区 `.analysis/improve-2.1.3-20261116/`；含私有设备备份的目录不随源码交付。

接手时 GitHub 默认分支和最新公开 Release 均为 2.0.0。**2026 年 11 月 15 日已按用户指令将 2.1.2 同步至 [main](https://github.com/Ayc-Gh/ColorOS-Enhanced-Apps/tree/main)，并发布 [v2.1.2 正式版](https://github.com/Ayc-Gh/ColorOS-Enhanced-Apps/releases/tag/v2.1.2)，设为 Latest。** 主分支与标签均指向 `0149f4ec2e0ea546a3a6b499bb541f8f378bd4ee`，260 个源码文件与对应源码 ZIP 一致，四个发布附件已校验。上表的发布用本地 checkout 仍为接手前快照，不能覆盖回主工程。

应用展示名称统一为 ColorOS 增强。内部应用 ID `io.github.oshin.windowcapture`、AOD 的 `com.op.aod.enhance` 命名空间及原签名用于升级兼容，应继续保留。第三方版权和许可保留在 `LICENSE`、`NOTICE` 和 `third_party/`；普通界面和文档沿用现有品牌表述。

## 八类功能的代码入口

Java 文件以下均位于 `app/src/main/java/io/github/oshin/windowcapture/`；AOD 文件位于 `app/src/main/kotlin/com/op/aod/enhance/`。

| 功能 | 主要文件 | 实现和边界 |
| --- | --- | --- |
| 分屏与小窗 | `MainHook.java`、`LaunchPolicy.java`、`EdgeCountPolicy.java`、`CaptionVisibilityTracker.java` | 在 system_server 修改本机窗口支持、数量、布局、启动、贴边及控件恢复。小窗最多可配置 10，贴边独立配置 1–5；最近行为验证为 5，不等于 10 窗全组合通过。 |
| OPPO 互联和捕获 | `CastHooks.java`、`CastPolicy.java`、`CastEncoderPolicy.java`，捕获入口在 `MainHook.java` | 沿用 `com.oplus.cast` 的原生协商和编码器；安全标记在服务端处理。硬件 DRM 和应用自行隐藏内容不在普通窗口捕获范围内。 |
| AOD | `ModuleEntry.kt`、`hook/MainHook.kt`、`BrightnessHook.kt`、`AodDurationController.kt`、`PanoramicHook.kt`、`LowLightHideHook.kt` | 分别适配 SystemUI 与息屏设置宿主，沿用原生显示状态机。低光隐藏与累计额度分开；全天能力仍需系统选择全天显示。 |
| 隔空手势 | `AirGestureEntry.java`、`AirGesturePolicy.java` | 只解锁额外设置入口，保留本机原生识别引擎。强制互斥引擎曾导致识别失效，不恢复该做法。 |
| 联网管理 | `NetworkEntry.java`、`NetworkPolicy.java` | 补齐符合条件应用的 WLAN／移动数据选项，由系统执行 UID 规则；共享 UID、核心身份和企业限制保留原生处理。 |
| 小布记忆 | `AimemoryEntry.java`、`AimemoryPolicy.java`、`WechatOcrAdapter.java`、`PaymentEvidence.java`、`RuleMergePolicy.java`、`AimemoryTrace.java` | 在原生记账服务补微信逐页面规则；详情页与支付结果页分别沿用网页提取和 OCR。保留金额校验、异步页面归属及交易去重。 |
| 相机与相册 | `GalleryEntry.java`、`CameraInspirationEntry.java`、两类 `RegistryPatch`、`CameraLogSession.java` | AI 修图师和云端灵感推荐使用系统服务；Log 补进程内功能表。Log 选择不持久保留，重启相机从 SDR 开始。 |
| 声音分轨 | `SoundEntry.java`、`SoundController.java`、`SoundBridgeProvider.java`、`SoundPolicyRoot.java`、`SoundPolicy.java`、`SoundNative.java`、`SoundStartupPolicy.java` | 应用范围解除同时处理后端原生策略。Root 由模块应用使用，音效宿主续租；短暂挂载策略副本并重载，立即卸载。未替换声音算法库，完全分离音轨仍未实现。 |

模块入口列表见 `app/src/main/resources/META-INF/xposed/java_init.list`。它包含八个入口类，不能仅凭某一个 MainHook 判断整个模块是否加载。`module.prop` 为 API 102、静态作用域、关闭自动热重载；更新 APK 后，旧宿主仍可能加载旧代码。

## 配置和适配约束

`SettingsBridge.java` 连接 LSPosed 配置服务。窗口、投屏及其他非 AOD 功能使用远程 `window_capture` 配置组，AOD 使用独立 `aod_config`。这些配置不能简单当作模块自身普通 SharedPreferences XML 直接编辑。AOD 还存在本地配置副本与同步流程。

`CompatibilityPolicy.java` 对 ROM 和宿主版本做精确校验；微信另受 `AimemoryPolicy.supported()` 限制，目前为微信 versionCode 3220 和记账服务 17008000。系统或应用升级后应重新抓取实际方法签名及调用行为，避免只修改版本白名单。`FeatureReadiness`、`WindowDiagnostics` 和 AOD 的状态注册按功能组处理接口缺失；运行状态需区分配置保存、宿主版本、接口加载、调用命中和实际效果。

当前应有 11 个作用域：

`system`、`com.android.systemui`、`com.oplus.cast`、`com.oplus.aod`、`com.oplus.gesture`、`com.oplus.trafficmonitor`、`com.oplus.gleanerservice`、`com.coloros.gallery3d`、`com.oplus.camera`、`com.oplus.audio.effectcenter`、`com.oplus.atlas`。

正式版不需要给微信或 AONService 加作用域，也没有集成电脑多路实验使用的 `com.oplus.linker`。不能把额外作用域当作通用修复手段。

## 接手时的实机状态

手机型号、ROM 和 API 均符合适配范围，Root Shell 可用。安装版本为 2.1.2／18；安装 APK 与本地正式 APK 的 SHA-256 一致。有效显示配置为 1080×2376、480 DPI，物理面板为 1440×3168、640 DPI。

LSPosed 数据库中模块启用，11 个作用域齐全。当前 `window_capture` 明确开启 **微信记账、AI 修图师、云端灵感推荐、Log、声音分轨**；联网关闭，其他未设置的窗口增强跟随默认关闭。AOD 三项系统亮度默认均启用，全天、入口、单击拦截、低光保持关闭，时长跟随系统。保留的 80／160／1.6 数值不表示它们正在覆盖系统值。

这里比 `provenance.json` 的 `final_enabled` 多出微信记账开关。该元数据只有四项，不能据此自动清除实机现有选择。

**检查期间手机发生了一次重启。** 随后读取到模块应用 `stopped=true`，声音文件仍记录上次启动的时间和旧宿主 PID；新音效宿主已经换 PID，未观察到新的租约。磁盘上旧的“已生效”文本不能代表本次启动已生效。当前没有启动模块应用或绕过强停限制，重启原因也未作判断。后续正常打开模块后，应核对新宿主、新租约及原生应用属性，再判断声音功能是否恢复。

2.1.2 的冷启动修复只放行指定声音 Provider 的关联名单判断，并明确保留强停、挂起等前置限制。当前强停状态不能单独作为该修复回归失败的证据。

## 已验证功能和待完成项

最近全面回归的主要证据包括：普通安全窗口截图与录屏开关恢复；小窗和贴边数量 2→5→2；启动和分屏行为；AOD 约 30 秒计时与 1.25 倍运行亮度；三种实体手势；联网 UID 规则写入；用户确认记账生成；真实 AI 修图保存、Log 成片及推荐选图；真实电脑投屏的 60／90／120 帧编码配置；声音开关恢复、租约和干净重启。细节及版本范围以 [全面回归报告](FULL_REGRESSION_2.1.2.md)为准，不将这些历史证据描述为本次重新完成的行为测试。

| 优先顺序 | 后续工作 | 完成判据 |
| --- | --- | --- |
| 1 | 复核当前声音状态 | 正常打开模块后，对应当前启动的新租约、新宿主及后端白名单属性一致；保留强停后的原生回退。 |
| 2 | 圆角和两类阴影 | 隔离启动参数、任务缓存和 getter 路径，用相同窗口完成有可见差异的关闭／开启／恢复对照。 |
| 3 | 自由调整比例 | 先获得有效的原生缩放手势，再做开关对照。已有高宽比 2／1.5 的证据不替代自由拖动验证。 |
| 4 | 记账诊断计数 | 对用户确认保存的真实事件定位实际处理进程和提取分支，解释计数为零的原因；不记录金额、收款方或订单内容。 |
| 5 | 长期与高级场景 | AOD 整夜功耗、长期低光、实体单双击组合；真实网络流量阻断；电脑实际传输帧率、弱网和高级编码组合。 |
| 6 | 后续版本发布 | 2.1.2 已完成发布；后续仍先核对源码和附件，排除私有构建、签名、设备和原始日志文件，再按发布指令更新。 |

## 11 月 16 日实测更新

11 月 16 日已继续完成一轮自动回归及用户协作实测。最新结果和当前优先级以 [实机审计](LIVE_AUDIT_20261116.md) 为准：自由调整比例、聚焦阴影、底部横条、真实 Wi-Fi 阻断、AOD 无遮挡 30 秒计时已有新增证据；声音原生策略与后台续租已重新核验。新增优先问题为投屏诊断的多进程报告冲突，以及当前嵌入式小窗路径的圆角缺口。保留整体架构，按报告开展局部修复，不再把上表已补齐项目当作未测试。

本轮用户再次确认微信账单实际生成，技术计数也捕获到详情页处理及原生接收回调，历史上的零计数现象未复现。实体手势中，接听／静音和滑动识别成功；暂停／播放页面没有学习选项，本轮未验证该动作。所有测试配置恢复至本轮开始时的基线，9 个临时应用和测试媒体已清理，正式 APK 保持不变。

## 实验边界和已有删减

- **声音完全消除**：极端和有限增益实验出现全静音或人声残留，实验补丁已经撤回。`SoundEndpointPatch.java` 不属于正式源码；不能继续把零增益读回当作算法分离成功。
- **电脑多路应用投屏**：两端配套实验验证过三路内容与输入；四路依赖负载，五路失败。电脑运行时比较值和手机端口分配必须配套处理，正式版尚未集成。见 [多路测试](PAIRED_CAST_TEST.md)。
- **本地实时灵感构图**：供体算法库与模型曾完成初始化，但推理返回 `generate_failed`，已恢复。它与已经加入的云端灵感推荐是两条不同链路。
- **RAW 和 Log 系列**：RAW、RAW MAX、专业视频、长曝光原生已有；RAW+、O-Log2、星空、和光及反差黑白滤镜没有完整可交付证据。供体滤镜移植仍未完成。见 [相机和声音实测](CAMERA_SOUND_2.1.0.md)。
- 保留 1.4.1、1.6.1、1.8.0 已确认的删减，不恢复被删除的整类页面、联网应用选择器、组件重启按钮、系统学习入口、应用过滤和黑名单开关。
- 判断重复功能时使用同条件的模块关闭／开启对照；代码、资源、入口出现或接口成功均不足以单独证明完整功能可用。

## 构建和本次验证

本机工具链位于工作区 `.analysis/location-network-20261103/toolchain/`：JDK 21.0.13、SDK 37、Build Tools 37.0.0。工程使用 AGP 9.3.1、Gradle 9.6.1、Compose 插件 2.4.0；版本以现有构建文件为准。`local.properties` 是本地 SDK 路径，不随源码公开。

在工程目录设置 JDK 后执行：

```powershell
$env:JAVA_HOME = 'D:\Ai\CODEX\手机\.analysis\location-network-20261103\toolchain\jdk21\jdk-21.0.13+9'
.\gradlew.bat --offline --console=plain :app:assembleRelease :app:lintRelease :app:compileDebugUnitTestJavaWithJavac
```

本次执行成功。Gradle 测试 worker 曾在 Windows 中文路径下遇到类加载问题，现有做法是编译测试后，以 JUnitCore 直接运行 Java 和 Kotlin 的 28 个测试类。运行类路径包含两种语言的主类和测试类目录、JUnit 4.13.2、Hamcrest 1.3 和 Kotlin stdlib 2.4.0。本次结果为 **130 项通过**；Lint 为 **0 错误、32 警告**。单元测试结果不替代实机行为验证。

本次还核实：

- Release 构建 APK 与 `dist` 正式 APK 逐字节一致，手机安装 APK 哈希亦一致。
- APK、源码 ZIP、签名说明全部匹配 `SHA256SUMS-2.1.2.txt`。
- 源码 ZIP 完整性通过，其中 260 个文件在新增本接手文档前与主工程逐字节一致。
- APK 签名验证通过，证书 SHA-256 为 `ac8dd32294627c740e436288919805d7d9403592add3436c1f006df58ab16f37`。

APK SHA-256 为 `bb229b565135e23748e03126f866b8c85d9eae60a24ff65c7d9befec9dabf8c5`。本次只增加接手文档及 README 索引；保留原有 2.1.2 交付文件，不以同版本重新打包覆盖历史证据。

## 后续维护和恢复要求

新增功能默认关闭；关闭、配置读取失败或适配不符时保留原生行为。窗口、AOD 和其他功能的恢复入口只处理自己的配置，不清除无关设置。

停用 LSPosed 模块后需重启相应宿主或手机，才能移除旧进程中的 hook。声音依靠租约和原生重载恢复；已保存的联网规则、系统应用选择、账单、图片和视频属于用户数据，不随停用删除。修改捕获开关后重新创建目标窗口，并停止、重连电脑投屏，避免互联缓存影响判断。

实测开始前保存当轮配置和宿主版本，结束后恢复当轮基线。优先使用合成图、测试应用和现有账单；不要求专门付款，不把私人画面或账单字段写入日志。不直接重跑历史清理脚本：其中含旧任务、媒体 ID、文件哈希、配置快照及混淆映射，必须先与当前环境核对。

发布沿用个人签名，保留本地 `keystore/` 和 `keystore.properties`，不打印其中内容。源码交付排除私钥、密码、本地 SDK 路径、构建缓存、原始设备日志及私有备份；保留许可和脱敏验证材料。
