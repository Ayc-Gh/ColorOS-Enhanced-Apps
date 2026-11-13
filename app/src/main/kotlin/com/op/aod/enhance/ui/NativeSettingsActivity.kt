package com.op.aod.enhance.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodSyncState
import io.github.oshin.windowcapture.UiKit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** The AOD pages use the same native controls as every other module page. */
abstract class NativeSettingsActivity: Activity() {
    protected lateinit var body: LinearLayout
    private var scroll: ScrollView?=null
    protected val jobs=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val handler=Handler(Looper.getMainLooper())
    private val redraw=Runnable { if(!isFinishing&&!isDestroyed)render() }
    protected open val observeAod=true
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        if(observeAod)AodConfigStore.read(this)
        render()
        if(observeAod){
            jobs.launch { AodConfigStore.config.collect { refreshPage() } }
            jobs.launch { AodConfigStore.syncStatus.collect { refreshPage() } }
        }
    }
    protected fun refreshPage(){handler.removeCallbacks(redraw);handler.post(redraw)}
    protected abstract fun content()
    private fun render(){
        val y=scroll?.scrollY?:0
        val page=ScrollView(this).apply { setBackgroundColor(UiKit.BG);isFillViewport=true }
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;val p=UiKit.dp(context,20);setPadding(p,UiKit.dp(context,16),p,p) }
        page.addView(body);scroll=page;content();setContentView(page);UiKit.applyBars(this,page);page.post{page.scrollTo(0,y)}
    }
    protected fun header(title:String,subtitle:String)=UiKit.header(this,body,title,subtitle)
    protected fun section(title:String){body.addView(UiKit.section(this,title))}
    protected fun note(value:String){body.addView(UiKit.note(this,value))}
    protected fun action(title:String,primary:Boolean=false,callback:()->Unit){body.addView(UiKit.button(this,title,primary).apply{setOnClickListener{callback()}})}
    protected fun toggle(title:String,summary:String,value:Boolean,callback:(Boolean)->Unit){body.addView(UiKit.toggle(this,title,summary,value,true){callback(it)})}
    protected fun syncStatus(){
        val state=AodConfigStore.syncStatus.value
        val message=when(state.state){AodSyncState.SYNCED->"已同步 · 设置已保存到框架";AodSyncState.PENDING->"待同步 · 本机修改已保留";AodSyncState.FAILED->"同步失败 · 点击重试，当前配置已保留"}
        body.addView(UiKit.status(this,message).apply{setOnClickListener{
            if(state.state!=AodSyncState.SYNCED){AodConfigStore.retry(this@NativeSettingsActivity);refreshPage()}
            else android.app.AlertDialog.Builder(this@NativeSettingsActivity).setTitle("配置同步").setMessage("配置已写入框架。实际加载与运行结果请在功能运行状态中查看。").setPositiveButton("知道了",null).show()
        }})
    }
    protected fun save(ok:Boolean){if(!ok)Toast.makeText(this,"未能完成同步，本机设置已保留，请检查配置连接",Toast.LENGTH_SHORT).show();refreshPage()}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);jobs.cancel();super.onDestroy()}
}
