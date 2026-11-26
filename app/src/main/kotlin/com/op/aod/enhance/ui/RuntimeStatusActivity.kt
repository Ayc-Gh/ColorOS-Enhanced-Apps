package com.op.aod.enhance.ui
import android.os.Bundle
import com.op.aod.enhance.data.RuntimeProtocol
import com.op.aod.enhance.data.RuntimeStatusClient
import io.github.oshin.windowcapture.BuildConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.util.Locale

class RuntimeStatusActivity:NativeSettingsActivity(){
    private lateinit var client:RuntimeStatusClient
    private var hosts=RuntimeProtocol.hosts
    private var captureOnly=false
    override val observeAod=false
    override fun onCreate(state:Bundle?){
        captureOnly=intent.getStringExtra("section")=="capture"
        hosts=when(intent.getStringExtra("section")){"windows"->RuntimeProtocol.windowHosts;"network"->listOf("com.oplus.trafficmonitor");"memory"->listOf("com.oplus.gleanerservice");"capture"->RuntimeProtocol.windowHosts;"gesture"->listOf("com.oplus.gesture");"gallery"->listOf("com.coloros.gallery3d","com.oplus.camera");"camera"->listOf("com.oplus.camera");else->RuntimeProtocol.hosts}
        client=RuntimeStatusClient(this,hosts);super.onCreate(state)
        jobs.launch{client.reports.collect{refreshPage()}};jobs.launch{client.message.collect{refreshPage()}}
    }
    override fun content(){
        header("功能运行状态","配置保存、接口加载与实际效果分别确认")
        note(client.message.value);action("刷新功能状态",true){client.refresh()}
        for(host in hosts){
            section(when(host){"android"->"系统框架";"com.android.systemui"->"系统界面";"com.oplus.trafficmonitor"->"流量管理";"com.oplus.gleanerservice"->"小布记忆";"com.oplus.gesture"->"手势体感";"com.coloros.gallery3d"->"相册";"com.oplus.camera"->"相机";else->"系统息屏设置"})
            val report=client.reports.value[host]
            if(report==null){
                note(if(host=="com.oplus.camera") "尚未收到相机回复。请打开系统相机，再回到本页刷新。" else "未知：尚未收到该进程回复。首次更新需重启手机，并打开对应系统页面后检查。")
                continue
            }
            if(report.optInt("code")!=BuildConfig.VERSION_CODE){
                val restart=if(host=="com.oplus.camera") "请强行停止并重新打开相机。首次更新后若仍显示旧版，请重启手机。" else "请重启手机一次。"
                note("仍在运行旧版 ${report.optString("version")}。$restart")
                continue
            }
            if(host in RuntimeProtocol.hosts && report.optString("process",host)!=host){note("已收到子进程报告，主进程尚未回复。");continue}
            note("已加载 ${report.optString("version")}")
            report.optJSONObject("adapter")?.let{a->note(when(a.optString("state")){"ready"->"版本与适配接口已校验";"unsupported","failed"->a.optString("reason","适配不可用，保留原生行为");else->a.optString("reason","适配接口尚未确认")})}
            report.optJSONObject("gesture")?.let{g->note(if(g.optBoolean("enabled"))"额外手势入口已开启" else "额外手势入口已关闭");note("手势识别继续使用系统原生引擎。")}
            report.optJSONObject("gallery")?.let{g->
                note(if(g.optBoolean("enabled"))"AI 修图师解锁已开启" else "AI 修图师解锁已关闭")
                note(when{!g.has("nativeSupport")->"尚未读取相册支持判断，请先进入照片编辑。";g.optBoolean("nativeSupport")->"系统已原生支持，保持原生处理。";g.optBoolean("expanded")->"已补齐本机隐藏的 AI 修图师入口。";else->"本机原生未开放 AI 修图师入口。"})
                note("修图请求、联网授权和保存结果由相册处理；入口已显示不代表本次修图成功。")
            }
            report.optJSONObject("camera")?.let{c->note(if(c.optBoolean("enabled"))"云端灵感推荐解锁已开启" else "云端灵感推荐解锁已关闭");note(if(c.optBoolean("expanded"))"已恢复相机灵感推荐入口" else "尚未触发相机入口");note(when{c.optBoolean("logEnabled")&&c.optBoolean("logExpanded")->"Log 功能表已补齐，录制由原生相机处理";c.optBoolean("logEnabled")->"Log 解锁已开启，等待重启相机进程";c.optBoolean("logExpanded")->"Log 解锁已关闭，重启相机进程后恢复原生功能表";else->"Log 解锁已关闭"});note("推荐图片、网络请求和参考／跟拍引导由相机及云端服务处理；本地实时灵感构图未加入。")}
            report.optJSONObject("camera")?.let{c->
                val x7uRequested=c.optBoolean("x7uRequested")
                val x7uStartup=c.optString("x7uStartup")
                note(when(x7uStartup){
                    "off"->if(x7uRequested) "X7 Ultra 相机增强已开启，等待重启相机进程" else "X7 Ultra 相机增强已关闭"
                    "ready"->if(x7uRequested) "X7 Ultra 相机增强接口已加载，成片效果以实拍为准" else "X7 Ultra 相机增强已关闭，重启相机进程后恢复"
                    "partial"->if(x7uRequested) "X7 Ultra 相机增强仅部分加载，详见 LSPosed 日志" else "X7 Ultra 相机增强已关闭，重启相机进程后恢复"
                    "unsupported_firmware"->"X7 Ultra 相机增强未加载，当前固件超出适配范围"
                    "unsupported_camera"->"X7 Ultra 相机增强未加载，原厂相机 APK 摘要与适配基线不符"
                    "failed"->"X7 Ultra 相机增强初始化失败，详见 LSPosed 日志"
                    else->"X7 Ultra 相机增强尚未报告加载状态"
                })
                if(x7uRequested && x7uStartup in setOf("ready", "partial", "failed")) {
                    if(c.optBoolean("x7uLutMonitorInstalled")) {
                        note("自定义 LUT 导入与监看接口已加载。录制时是否写入 LUT 取决于 GPU 直出。")
                        note(when(c.optString("x7uGpu")) {
                            "ineligible"->"GPU 直出尚未满足条件。请选择后摄 1080p、30 帧 Log 并选中自定义 LUT。"
                            "eligible"->"GPU 直出最近检测到符合条件的录像配置，选中自定义 LUT 后可尝试。"
                            "routed"->"最近检测到相机已接入 GPU 录像输入，成片效果请查看实际录像。"
                            "runtime_failed"->"GPU 直出在本次相机进程已停用。若录像中断，请检查已保存的片段。重启相机后可重试。"
                            else->"GPU 直出接口未加载，仍可使用 LUT 监看。"
                        })
                    } else {
                        note("自定义 LUT 导入、监看和 GPU 直出接口未加载。")
                    }
                }
                c.optJSONObject("filters")?.let{f->
                    note(when{
                        !f.optBoolean("installed")->"滤镜适配不可用，保留原生列表"
                        f.optBoolean("resourceFailure")->"滤镜资源校验失败，保留原生列表"
                        c.optBoolean("filtersRequested")!=f.optBoolean("sessionEnabled")->"滤镜开关已修改，重启相机进程后生效"
                        !f.optBoolean("sessionEnabled")->"滤镜补齐已关闭，使用原生列表"
                        f.optBoolean("resourcesReady")&&f.optInt("photoFilterCount")>0->"照片滤镜已补齐，共 ${f.optInt("photoFilterCount")} 项"
                        else->"滤镜补齐已开启，等待相机初始化"
                    })
                }
            }
            report.optJSONObject("memory")?.let{m->
                note(if(m.optBoolean("enabled"))"微信支持恢复已开启" else "微信支持恢复已关闭")
                note(when(m.optString("adapter")){"adapted"->"已补齐微信页面配置：${m.optInt("repaired")} 项";"mixed"->"保留原生页面配置，另补齐 ${m.optInt("repaired")} 项缺失页面";"native"->"系统已有微信支持，保持原生配置";"ready"->"适配接口已加载，尚未读取系统列表";else->"未找到可适配配置，保持原生处理"})
                note("本次进程：页面触发 ${m.optInt("pages")} · 原生提取 ${m.optInt("attempts")} · 取得必填字段 ${m.optInt("validMaps")} · 提交记录 ${m.optInt("requests")}")
                note("系统接受请求 ${m.optInt("accepted")} · 文字识别调用 ${m.optInt("ocr")} · 系统去重 ${m.optInt("duplicates")}")
                note("金额校验通过 ${m.optInt("verified")} · 拦截存疑结果 ${m.optInt("blocked")} · 修正金额 ${m.optInt("corrected")} · 依据交易证据区分 ${m.optInt("preciseDedup")}")
                note("最近金额校验：${when(m.optString("validation")){"VERIFIED"->"已确认支付成功与明确金额";"PAGE_NO_LONGER_ACTIVE"->"页面已离开，结果已丢弃";"AMOUNT_AMBIGUOUS"->"存在多个金额，未提交";"AMOUNT_NOT_CONFIRMED"->"未找到明确金额，未提交";"STATUS_NOT_CONFIRMED"->"无法确认支付成功，未提交";"CURRENCY_NOT_SUPPORTED"->"当前币种不支持，未提交";else->"尚未观察"}}")
                note("最近页面：${when(m.optString("page")){"result"->"支付结果页";"detail"->"账单详情";"transfer"->"转账";"redpacket"->"红包";else->"尚未观察"}}\n处理阶段：${when(m.optString("state")){"accepted"->"系统已接受请求";"rejected"->"系统拒绝请求";"requested"->"已提交请求";"duplicate"->"系统判定重复";"validation_blocked"->"存疑结果已拦截";"missing_fields"->"缺少记账必填字段";"fields"->"已取得必填字段";"extracting"->"正在提取";"page"->"已触发页面";else->"尚未观察"}}\n控件识别：${extractionState(m.optString("nativeResult"))}\n文字识别：${extractionState(m.optString("ocrResult"))}")
                if(m.optString("reason")!in listOf("", "none", "unknown"))note("系统拒绝原因：${when(m.optString("reason")){"SWITCH_DISABLED"->"系统总开关关闭";"SCREEN_INFO_NOT_CONSENTED"->"尚未同意使用屏幕信息";"ALREADY_COLLECTING"->"正在处理其他记忆";"SCREEN_OFF"->"屏幕已关闭";else->"系统运行条件不满足"}}")
                note("实际生成账单仍需系统识别规则支持；出现入口和配置条目均不代表已成功记账。")
            }
            report.optJSONArray("features")?.let{features->for(i in 0 until features.length()){
                val f=features.getJSONObject(i);val title=TITLES[f.optString("name")]?:continue
                if(captureOnly&&f.optString("name")!="Window.Capture")continue
                val state=when(f.optString("state")){"loaded"->"已加载";"partial"->"部分可用";else->"不可用"}
                note("$title：$state · ${if(f.optInt("hits")>0)"已触发" else "尚未触发"}")
                if(f.optInt("optionalMissing")>0)note("${f.optInt("optionalMissing")} 个可选接口不可用，对应路径保持原生处理。")
                f.optJSONObject("lastValue")?.let{v->note("最近系统取值：${v.optInt("native")} → ${v.optInt("applied")}；最终外观以实际窗口为准。")}
            }}
            report.optJSONObject("network")?.let{n->note(if(n.optBoolean("enabled"))"选项解锁已开启" else "选项解锁已关闭");note(if(n.optInt("expanded")>0)"已触发系统应用分类扩展" else "尚未触发新增应用分类");note("实际联网规则由系统保存和执行；出现控件不代表已经修改应用联网权限。")}
            if(report.optString("configurationRead")=="failed")note("配置读取失败：当前按原生行为处理，保存的设置可在连接恢复后重试。")
            report.optJSONObject("brightness")?.let{b->note(when{b.optBoolean("system")->"最近亮度处理：跟随系统";b.optBoolean("initial")->"最近亮度处理：保持初始亮度";b.optDouble("original",0.0)>0.0->"最近亮度处理：约 ${String.format(Locale.ROOT,"%.2f",b.optDouble("target")/b.optDouble("original"))} 倍";else->"最近亮度处理：保留系统特殊值"})}
            report.optJSONObject("panoramic")?.let{p->note(when{p.optBoolean("enhanced")->"最近全天能力：增强已应用";p.optBoolean("native")->"最近全天能力：系统原生支持";else->"最近全天能力：跟随系统"})}
            if(!captureOnly)report.optJSONObject("launches")?.let{launches->for((key,title) in LAUNCH_TITLES){val sample=launches.optJSONObject(key);val result=when{sample==null->"尚未观察";!sample.optBoolean("enabled")->"已关闭";else->when(sample.optString("result")){"floating"->"最近一次已观察到小窗";"requested"->"已请求，等待观察";"not_observed"->"最近一次未观察到小窗";else->"尚未确认实际窗口"}};note("$title：$result")}}
        }
        note("已加载表示适配入口可用，已触发表示系统调用过入口；最近一次结果不代表所有应用均兼容。仅在打开本页或刷新时查询。")
    }
    override fun onStart(){super.onStart();client.start()}
    override fun onStop(){client.stop();super.onStop()}
    private fun extractionState(value:String)=when(value){"SUCCESS"->"已取得结果";"VIEW_TREE_FAILED"->"无法读取页面控件";"RULE_FETCH_FAILED"->"缺少页面识别规则";"RULE_PRECONDITION_FAILED"->"页面条件不满足";"RULE_MATCH_FAILED"->"未匹配到账单字段";"SCREENSHOT_FAILED"->"无法获取页面图像";"BILL_EXTRACT_FAILED"->"未识别到支付成功与金额";else->"尚未观察"}
    companion object{
        private val TITLES=mapOf("Brightness.Init" to "初始亮度","Brightness.Running" to "亮度倍率","Duration.NativeTimeout" to "显示时长","Panoramic.AllDay" to "全天全景","SingleClick.Block" to "单击屏蔽","LowLight.Block" to "低光保持","AodSettings.AllDay" to "全天选项","Window.Capture" to "截图录屏","Window.Layout" to "小窗数量与比例","Window.Corner" to "圆角","Window.ShadowFocused" to "聚焦阴影","Window.ShadowUnfocused" to "非聚焦阴影","Window.Split" to "分屏","Window.Launch" to "启动策略","Window.Caption" to "操作条","Window.Edge" to "贴边","Window.Status" to "状态查询")
        private val LAUNCH_TITLES=mapOf("LAUNCHER" to "桌面优先小窗","REMEMBER" to "贴边恢复","JUMP" to "应用跳转小窗","NOTIFICATION" to "通知小窗")
    }
}
