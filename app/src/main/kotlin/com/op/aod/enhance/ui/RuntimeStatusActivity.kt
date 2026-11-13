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
        hosts=when(intent.getStringExtra("section")){"windows"->RuntimeProtocol.windowHosts;"network"->listOf("com.oplus.trafficmonitor");else->RuntimeProtocol.hosts}
        client=RuntimeStatusClient(this,hosts);super.onCreate(state)
        jobs.launch{client.reports.collect{refreshPage()}};jobs.launch{client.message.collect{refreshPage()}}
    }
    override fun content(){
        header("功能运行状态","配置保存、接口加载与实际效果分别确认")
        note(client.message.value);action("刷新功能状态",true){client.refresh()}
        for(host in hosts){
            section(when(host){"android"->"系统框架";"com.android.systemui"->"系统界面";"com.oplus.trafficmonitor"->"流量管理";else->"系统息屏设置"})
            val report=client.reports.value[host]
            if(report==null){note("未知：尚未收到该进程回复。首次更新需重启手机，并打开对应系统页面后检查。");continue}
            if(report.optInt("code")!=BuildConfig.VERSION_CODE){note("仍在运行旧版 ${report.optString("version")}，请重启手机一次。");continue}
            note("已加载 ${report.optString("version")}")
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
    companion object{
        private val TITLES=mapOf("Brightness.Init" to "初始亮度","Brightness.Running" to "亮度倍率","Duration.NativeTimeout" to "显示时长","Panoramic.AllDay" to "全天全景","SingleClick.Block" to "单击屏蔽","LowLight.Block" to "低光保持","AodSettings.AllDay" to "全天选项","Window.Capture" to "截图录屏","Window.Layout" to "小窗数量与外观","Window.Split" to "分屏","Window.Launch" to "启动策略","Window.Caption" to "操作条","Window.Edge" to "贴边","Window.Status" to "状态查询")
        private val LAUNCH_TITLES=mapOf("LAUNCHER" to "桌面优先小窗","REMEMBER" to "贴边恢复","JUMP" to "应用跳转小窗","NOTIFICATION" to "通知小窗")
    }
}
