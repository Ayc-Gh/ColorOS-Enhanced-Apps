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
    override val observeAod=false
    override fun onCreate(state:Bundle?){
        hosts=when(intent.getStringExtra("section")){"windows"->RuntimeProtocol.windowHosts;"network"->listOf("com.oplus.trafficmonitor");"memory"->listOf("com.oplus.gleanerservice");"cast"->listOf("com.oplus.cast");"gesture"->listOf("com.oplus.gesture");"gallery"->listOf("com.coloros.gallery3d","com.oplus.camera");"camera"->listOf("com.oplus.camera");else->RuntimeProtocol.hosts}
        client=RuntimeStatusClient(this,hosts);super.onCreate(state)
        jobs.launch{client.reports.collect{refreshPage()}};jobs.launch{client.message.collect{refreshPage()}}
    }
    override fun content(){
        header("功能运行状态","配置保存、接口加载与实际效果分别确认")
        note(client.message.value);action("刷新功能状态",true){client.refresh()}
        for(host in hosts){
            section(when(host){"android"->"系统框架";"com.android.systemui"->"系统界面";"com.oplus.trafficmonitor"->"流量管理";"com.oplus.gleanerservice"->"小布记忆";"com.oplus.cast"->"屏幕投屏";"com.oplus.gesture"->"手势体感";"com.coloros.gallery3d"->"相册";"com.oplus.camera"->"相机";else->"系统息屏设置"})
            val report=client.reports.value[host]
            if(report==null){note("未知：尚未收到该进程回复。首次更新需重启手机，并打开对应系统页面后检查。");continue}
            if(report.optInt("code")!=BuildConfig.VERSION_CODE){note("仍在运行旧版 ${report.optString("version")}，请重启手机一次。");continue}
            note("已加载 ${report.optString("version")}")
            report.optJSONObject("adapter")?.let{a->note(when(a.optString("state")){"ready"->"版本与适配接口已校验";"unsupported","failed"->a.optString("reason","适配不可用，保留原生行为");else->"适配接口尚未确认"})}
            report.optJSONObject("cast")?.let{c->
                note(if(c.optBoolean("enabled"))"投屏优化已开启" else "投屏优化已关闭")
                if(c.has("codec"))note("最近编码器：${c.optString("codec")}\n格式：${c.optString("mime")} · ${c.optInt("width")}×${c.optInt("height")}\n已配置帧率：${c.optInt("configuredFps")} 帧；当前尺寸硬件校验上限：${c.optInt("validatedFps")} 帧")
                else note("尚未观察到启用增强后的编码协商，请重连投屏后刷新。")
                note("编码配置与实际传输帧率分别判断；此处不测量电脑收到的实时帧率。")
            }
            report.optJSONObject("gesture")?.let{g->note(if(g.optBoolean("enabled"))"额外手势入口已开启" else "额外手势入口已关闭");note("手势识别继续使用系统原生引擎。")}
            report.optJSONObject("gallery")?.let{g->
                note(if(g.optBoolean("enabled"))"AI 修图师解锁已开启" else "AI 修图师解锁已关闭")
                note(when{!g.has("nativeSupport")->"尚未读取相册支持判断，请先进入照片编辑。";g.optBoolean("nativeSupport")->"系统已原生支持，保持原生处理。";g.optBoolean("expanded")->"已补齐本机隐藏的 AI 修图师入口。";else->"本机原生未开放 AI 修图师入口。"})
                note("修图请求、联网授权和保存结果由相册处理；入口已显示不代表本次修图成功。")
            }
            report.optJSONObject("camera")?.let{c->note(if(c.optBoolean("enabled"))"云端灵感推荐解锁已开启" else "云端灵感推荐解锁已关闭");note(if(c.optBoolean("expanded"))"已恢复相机灵感推荐入口" else "尚未触发相机入口");note(when{c.optBoolean("logEnabled")&&c.optBoolean("logExpanded")->"Log 功能表已补齐，录制由原生相机处理";c.optBoolean("logEnabled")->"Log 解锁已开启，等待重启相机进程";c.optBoolean("logExpanded")->"Log 解锁已关闭，重启相机进程后恢复原生功能表";else->"Log 解锁已关闭"});note("推荐图片、网络请求和参考／跟拍引导由相机及云端服务处理；本地实时灵感构图未加入。")}
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
                val state=when(f.optString("state")){"loaded"->"已加载";"partial"->"部分可用";else->"不可用"}
                note("$title：$state · ${if(f.optInt("hits")>0)"已触发" else "尚未触发"}")
            }}
            report.optJSONObject("network")?.let{n->note(if(n.optBoolean("enabled"))"选项解锁已开启" else "选项解锁已关闭");note(if(n.optInt("expanded")>0)"已触发系统应用分类扩展" else "尚未触发新增应用分类");note("实际联网规则由系统保存和执行；出现控件不代表已经修改应用联网权限。")}
            if(report.optString("configurationRead")=="failed")note("配置读取失败：当前按原生行为处理，保存的设置可在连接恢复后重试。")
            report.optJSONObject("brightness")?.let{b->note(when{b.optBoolean("system")->"最近亮度处理：跟随系统";b.optBoolean("initial")->"最近亮度处理：保持初始亮度";b.optDouble("original",0.0)>0.0->"最近亮度处理：约 ${String.format(Locale.ROOT,"%.2f",b.optDouble("target")/b.optDouble("original"))} 倍";else->"最近亮度处理：保留系统特殊值"})}
            report.optJSONObject("panoramic")?.let{p->note(when{p.optBoolean("enhanced")->"最近全天能力：增强已应用";p.optBoolean("native")->"最近全天能力：系统原生支持";else->"最近全天能力：跟随系统"})}
            report.optJSONObject("launches")?.let{launches->for((key,title) in LAUNCH_TITLES){val sample=launches.optJSONObject(key);val result=when{sample==null->"尚未观察";!sample.optBoolean("enabled")->"已关闭";else->when(sample.optString("result")){"floating"->"最近一次已观察到小窗";"requested"->"已请求，等待观察";"not_observed"->"最近一次未观察到小窗";else->"尚未确认实际窗口"}};note("$title：$result")}}
        }
        note("已加载表示适配入口可用，已触发表示系统调用过入口；最近一次结果不代表所有应用均兼容。仅在打开本页或刷新时查询。")
    }
    override fun onStart(){super.onStart();client.start()}
    override fun onStop(){client.stop();super.onStop()}
    private fun extractionState(value:String)=when(value){"SUCCESS"->"已取得结果";"VIEW_TREE_FAILED"->"无法读取页面控件";"RULE_FETCH_FAILED"->"缺少页面识别规则";"RULE_PRECONDITION_FAILED"->"页面条件不满足";"RULE_MATCH_FAILED"->"未匹配到账单字段";"SCREENSHOT_FAILED"->"无法获取页面图像";"BILL_EXTRACT_FAILED"->"未识别到支付成功与金额";else->"尚未观察"}
    companion object{
        private val TITLES=mapOf("Brightness.Init" to "初始亮度","Brightness.Running" to "亮度倍率","Duration.NativeTimeout" to "显示时长","Panoramic.AllDay" to "全天全景","SingleClick.Block" to "单击屏蔽","LowLight.Block" to "低光保持","AodSettings.AllDay" to "全天选项","Window.Capture" to "截图录屏","Window.Layout" to "小窗数量与外观","Window.Split" to "分屏","Window.Launch" to "启动策略","Window.Caption" to "操作条","Window.Edge" to "贴边","Window.Status" to "状态查询")
        private val LAUNCH_TITLES=mapOf("LAUNCHER" to "桌面优先小窗","REMEMBER" to "贴边恢复","JUMP" to "应用跳转小窗","NOTIFICATION" to "通知小窗")
    }
}
