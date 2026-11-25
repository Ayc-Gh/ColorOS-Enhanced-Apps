# ColorOS 增强 2.1.9 实机挂载与冲突审计

2026-11-25，OPPO Find X7 Ultra（PHY110），固件 `PHY110_17.0.0.100(CN01)`，Android 17 / API 37。检查对象为手机实际安装的 `io.github.oshin.windowcapture` 2.1.9（25），APK SHA-256 为 `f956d07384b206b232606db376ed007ac563835f05e1f6dbc48616b4dcb1350c`。以用户 0 的 LSPosed 配置、当前进程和声音原生服务为准。设备侧全程只读，保持原有设置和运行现场。

**结论**　10 个 LSPosed 作用域、8 个 Java 入口、固件框架文件和相机资源均对得上。声音分轨当前生效，原生服务的 498 次独立查询全部符合预期，系统声音 XML 当前没有单文件挂载。确定存在声音属性解码错误、恢复校验过宽、联网增强影响备份恢复路径，以及微信 OCR 校验和窗口恢复的边界错误。另有两项运行风险：声音临时挂载使用与 init 相同的命名空间，升级后五个常驻宿主仍沿用升级前的进程。现场没有证据把这些问题归因于第三方模块。

## 一、安装包、作用域和宿主

LSPosed 2.2.1（7912），modern API 102。比对安装 APK 的 `META-INF/xposed`、DEX 类定义与框架数据库，声明的 10 项作用域与用户 0 实际启用项完全相同。8 个入口类和单独运行的 `SoundPolicyRoot` 均存在。3 个框架 JAR 均位于 `/system/framework/`，`services.jar`、`oplus-services.jar`、`oplus-framework.jar` 的 SHA-256 均与项目固件记录相符。兼容性表中 8 个 Android 宿主的 versionCode 全部匹配。[入口清单](../app/src/main/resources/META-INF/xposed/java_init.list) · [作用域](../app/src/main/resources/META-INF/xposed/scope.list) · [版本门控](../app/src/main/java/io/github/oshin/windowcapture/CompatibilityPolicy.java) · [固件记录](../provenance.json)

| 功能 | 实际作用域和宿主 | 本机证据及覆盖范围 |
|---|---|---|
| 截图与录屏、分屏与小窗 | `system` → `system_server` | PID 3307 在新版安装前已启动，历史日志记录 59 个 hook 注册。当前远程配置中相关增强默认关闭。 |
| 息屏显示 | `com.android.systemui`（179902）、`com.oplus.aod`（170010021） | SystemUI PID 5570 早于更新，六组 AOD 功能曾完成注册。单击分组跳过一个候选类，另有三条路径。息屏设置宿主在采样时未运行。当前时长与亮度均跟随系统。 |
| 隔空手势 | `com.oplus.gesture`（8600） | PID 5154 的加载日志明确为 2.1.8。新版配置没有开启该增强。 |
| 联网管理 | `com.oplus.trafficmonitor`（17003005） | 主进程采样时未运行，增强关闭。检查了实机所装 APK 的清单与 DEX，发现分类器也用于备份恢复。 |
| 微信自动记账 | `com.oplus.gleanerservice`（17008000） | PID 26102 已加载 2.1.9，微信 8.0.79（3182）的 8 条规则已补齐。此前同一版本实机打开已有账单详情，用户确认小布新增记录。微信自身无需加入作用域。 |
| 相册 AI 修图 | `com.coloros.gallery3d`（17010066） | PID 10592 的加载日志明确为 2.1.8。新版配置中该增强关闭。 |
| 相机灵感、Log、滤镜 | `com.oplus.camera`（60000） | PID 14047 早于更新。已装 APK 中 30 份 LUT 的 SHA-256 逐项匹配资源清单；开启时从 APK 载入带写保护的匿名内存文件，以相机进程自己的 `/proc/self/fd/` 路径提供给原生读取器。当前开关关闭。 |
| 声音分轨 | `com.oplus.audio.effectcenter`（17000018）及 `com.oplus.atlas`（17000001）→ `com.oplus.persist.multimedia` | PID 24830 在更新后启动。两个包共用该进程，代码以音效包的版本为兼容性条件，并防止重复启动续租。租约、root 状态与 APK 路径一致。 |

新版安装于 09:51:55，`autoHotReload=false`。手势和相册有旧版本号的直接日志。系统框架、SystemUI 和相机由进程启动时间、安装时间及热重载配置判断，缺少本次新版加载记录。框架数据库目前只显式开启了声音分轨和微信记账；旧宿主开机时打印的开关值不能代表当前设置。其余关闭或未运行的功能，此次没有执行触发测试。历史账单详情成功和本次发现的 OCR 去重问题涉及不同处理路径。[加载配置](../app/src/main/resources/META-INF/xposed/module.prop) · [相机资源读取](../app/src/main/java/io/github/oshin/windowcapture/CameraFilterAdapter.java#L84) · [此前实机验证](evidence/sound-wechat-2.1.9/validation.json)

安装 APK 的合并清单有 3 个 provider、1 个静态 receiver、0 个 Android Service。系统包管理器能把三个 authority 分别解析到声音桥、libxposed 配置 provider 和 AndroidX 初始化 provider。声音桥的 authority、宿主调用的 `lease` 方法及实际共享音效进程一致。ActivityManager 显示宿主 PID 24830（UID 1000）持有模块主进程 PID 29555（UID 10413）中声音桥的稳定引用。桥入口按 UID 校验，root 辅助进程再按租约 PID、进程名、有效期及模块版本复核。静态 receiver 为有 `android.permission.DUMP` 保护的 AndroidX ProfileInstallReceiver。[声音桥](../app/src/main/java/io/github/oshin/windowcapture/SoundBridgeProvider.java#L9) · [声音宿主调用](../app/src/main/java/io/github/oshin/windowcapture/SoundEntry.java#L52)

## 二、声音挂载与原生状态

| 检查项 | 实机结果 |
|---|---|
| 原生服务 | `/system_ext/bin/mmlistservice`，采样时 PID 2728。 |
| 固件策略文件 | `/system_ext/etc/Multimedia_Daemon_List.xml`，198016 字节，SHA-256 为 `ab631bcdf76aeb055c8574d84ae263a3bc23620749d8e3177605b368d015234d`；`/system_ext` 为只读 EROFS。init、原生服务、系统框架、音效宿主、模块及 root 辅助进程看到相同设备号与 inode。 |
| 临时覆盖和在线配置 | 采样时各受检视图均无该 XML 的独立 bind mount。临时 `runtime-policy.xml` 与厂商在线覆盖文件 `/data/oplus/multimedia/Multimedia_Daemon_Online_List.xml` 均未出现。 |
| 模块工作目录 | `/data/user_de/0/io.github.oshin.windowcapture/files/sound-policy`，权限 0700，属于新安装的模块 UID。当前的 `lease`、`status`、`lock`、`expanded` 和 `native-active-baseline` 是续租及恢复记录。 |
| 执行代码 | root 辅助进程的启动 shell 通过 FD 3 保留当前正式版 `base.apk`。该 FD 与在装 APK 的设备号、inode、长度一致。租约记录中的宿主为 PID 24830，版本码 25。 |
| 挂载命名空间 | init 与原生服务同为 `mnt:[4026531841]`，其中 `/system_ext` 为 `shared:10`。模块与其 root 辅助进程同为 `mnt:[4026536220]`，其中 `/system_ext` 为 `master:10`。 |

**全量查询。** 从固件的 26 个原生条目和当前用户安装包独立计算预期集合。原生服务逐项返回 471 个扩展条目和 26 个保留条目的正确属性，对一个不存在的负对照返回未找到，共 498/498 符合预期。`expanded` 记录与独立计算的 471 项完全一致。查询前后包集合、服务 PID 和固件 XML 内容一致。此项验证覆盖已知名单及一个负对照，验证的是原生策略值，未测量音频的实际听感。

## 三、发现及修正方向

| 优先级 | 发现、证据和影响 | 修正方向 |
|---|---|---|
| 高 | **声音两位数属性被错误读取，恢复可能误报。** 实机有 7 条多位数返回，均为单字节数字。两位数 `17` 的原始回包为 `00000002 00003731 00000001`。模块用 `Parcel.readString()` 按 UTF-16 读取，会连同末尾“找到”标志一起消费，结果可变成 `null`。读取单字符 `3` 正常。恢复流程只抽一个扩展包，即使原生期望值存在，也接受空值。此次 471 个扩展包中没有原始属性为多位数的条目，当前全量结果正确。 | 按原生回包格式读取并校验长度、终止符和 found 标志。分别处理未找到、格式异常、找到但数值不符；恢复按原始条目是否存在判定，覆盖原生单/多位与新增包。 [读取器](../app/src/main/java/io/github/oshin/windowcapture/SoundNative.java#L17) · [恢复抽查](../app/src/main/java/io/github/oshin/windowcapture/SoundPolicyRoot.java#L56) · [恢复判定](../app/src/main/java/io/github/oshin/windowcapture/SoundPolicy.java#L30) |
| 高 | **临时挂载的异常清理缺少归属校验。** 代码在外部检查挂载点，然后进入原生服务命名空间，绑定成功前就注册按路径卸载的退出处理。模块锁约束自己的辅助进程，无法约束其他挂载者。检查与卸载之间如有同路径竞争，可能卸掉后来者的挂载。清理进程若在成功绑定后被强制终止，后备恢复只重载服务，不能识别和清理遗留绑定。手机此刻没有残留。由于服务与 init 共用命名空间，同一命名空间其他进程也能看到临时绑定，共享挂载还可能向从属命名空间传播。本次未捕获短暂绑定期间的跨进程传播。 | 成功绑定后记录该次挂载的身份，清理前比对命名空间和当前挂载 ID，清理后再查挂载表。为异常恢复保留可核验的记录。避免调整整个系统的传播设置。 [挂载及恢复实现](../app/src/main/java/io/github/oshin/windowcapture/SoundPolicyRoot.java#L43) · [Linux 内核挂载传播说明](https://www.kernel.org/doc/html/latest/filesystems/sharedsubtree.html) |
| 中 | **联网增强超出联网设置页。** `DataUsageUtils.isDataApp` 已限定在页面上下文内，但另两个分类器 `G3.k.T/U(String)` 开启功能后在整个主进程都返回 false。实机的流量管理 17.3.5 DEX 表明，备份恢复方法 `processKeepWifiAppPolicy` 也按 `U` 的返回值选择策略，且恢复服务使用默认进程。当前功能关闭，没有执行实际恢复。 | 给这两个分类器应用已知列表或详情页的调用范围，另测设置页和备份恢复过程。 [联网入口](../app/src/main/java/io/github/oshin/windowcapture/NetworkEntry.java#L63) |
| 中 | **微信 OCR 的订单号冲突可导致错误去重。** 对两份相同的合成页面文本传入不同的原生解析订单号，本地 Release 类均判为 VERIFIED，随后把两份证据判为不同交易。原因是页面订单号只在原生字段缺失时写入，冲突值可进入覆盖原生去重的判断。此前账单详情实测未走 OCR，未观察到个人账单重复。 | 可见订单号与解析结果有冲突时降级为证据不足，保持原生去重；补测相同页面被重复识别。 [证据提取](../app/src/main/java/io/github/oshin/windowcapture/PaymentEvidence.java#L53) · [去重](../app/src/main/java/io/github/oshin/windowcapture/WechatOcrAdapter.java#L81) |
| 中 | **微信金额校验会接受部分不支持的币种。** 本地 Release 类对合成文本“支付成功 实付金额1000韩元”返回 VERIFIED 和 `1000.00`；相同格式的“美元”被拒绝，带 `￥` 的对照通过。现有排除列表覆盖部分币种，金额正则允许省略币种。 | 在金额旁识别明确标示的币种；遇到非支持币种或证据冲突时交回原生逻辑。 [金额与币种校验](../app/src/main/java/io/github/oshin/windowcapture/PaymentEvidence.java#L15) |
| 中 | **贴边小窗可能丢失恢复机会。** 当任务 surface、parent 或 transaction 暂不可用时，代码跳过可见性恢复，随后仍删除 `parked` 中的任务记录。系统若未自行恢复，模块已失去重试依据。当前开关关闭，本次未观察到窗口卡住。 | 只在成功恢复或确认任务已销毁时清除记录，保留暂时无效时的重试机会。 [恢复贴边任务](../app/src/main/java/io/github/oshin/windowcapture/MainHook.java#L433) |
| 中 | **分屏的两个显示区域判断范围偏宽，框架初始化失败也缺少重试。** 普通任务分支有类型限制，两个共享的显示区域方法只检查分屏总开关。框架入口在连接配置前即标记安装已开始，若连接或监听器注册抛错，该进程失去再次初始化和注册状态接收器的路径。两者均由源码确认，此次开机日志未显示因此导致的故障。 | 将共享判断限制到能识别的分屏调用上下文，将诊断注册和可重试初始化分开。 [分屏作用范围](../app/src/main/java/io/github/oshin/windowcapture/MainHook.java#L199) · [框架初始化](../app/src/main/java/io/github/oshin/windowcapture/MainHook.java#L53) |
| 低 | **模块设置页的作用域摘要漏列 Atlas。** 框架数据库与安装包均包含 Atlas，但管理页只把音效包拼入状态文字，用户无法从该摘要核对第二个声音宿主。 | 用同一份作用域清单生成摘要。 [管理页摘要](../app/src/main/java/io/github/oshin/windowcapture/SettingsBridge.java#L38) |

声音读取错误还核对了手机本身的 `/system/framework/framework.jar`。其中 `Parcel.readString()` 经默认 helper 转到 `nativeReadString16`。Android 17 原生实现读取两位 UTF-16 字符和终止符时向前推进 8 字节，恰好吃掉该 OEM 回包中的 found 字段。这里使用了实机原始回包、设备自身 DEX 和原生读取代码进行交叉核对。[Android 17 原生 Parcel 实现](https://android.googlesource.com/platform/frameworks/native/+/ae266dcb706d083868578cfedce381ef44488a07/libs/binder/Parcel.cpp#2537)

## 四、与其他模块共存及验证边界

- LSPosed 安装 6 个模块、启用 5 个（含本模块）。其余模块中仅 Scene（`com.omarea.vtools`）与本模块在用户 0 显式共同勾选了 `system`。对 Scene 的安装 APK 抽查 18 个相关方法名，只出现通用名 `isFocusable`，没有找到同一目标类与方法的配对证据。抽查范围不能覆盖动态构造和原生库中的挂钩。
- 8 个已启用的 root 模块中，对声音策略 XML、在线策略和 3 个框架 JAR 的同名覆盖与相关脚本文本检查没有命中。此结论限于受检文件、脚本和当时的挂载视图，第三方运行时代码仍可能动态修改状态。
- 旧版本启动日志、当前设置、功能实际生效是三个不同时间点的证据。若要完成全宿主 2.1.9 回归，下次正常重启后应以新 PID 和新加载日志为起点，再验证分屏、小窗、息屏、手势、联网与相机/相册。现场审计保留了当前进程，未做重启、故障注入、真实备份恢复或个人账单采集。
