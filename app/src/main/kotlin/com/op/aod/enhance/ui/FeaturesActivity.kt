package com.op.aod.enhance.ui
import android.app.AlertDialog
import android.widget.EditText
import android.text.InputType
import com.op.aod.enhance.data.AodConfigContract as C
import com.op.aod.enhance.data.AodConfigStore as Store
import com.op.aod.enhance.data.AodInputValidation

class FeaturesActivity:NativeSettingsActivity(){
    private var advanced=false
    override fun content(){
        val c=Store.config.value
        header("AOD 功能","全天能力、唤醒与自动显示时长");syncStatus();section("全天与唤醒")
        toggle("全天全景息屏","同时显示系统设置入口；请在系统中选择“全天显示”。下一次息屏生效。",c.enablePanoramic){save(Store.update(this){v->v.copy(enablePanoramic=it)})}
        toggle("单击唤醒屏蔽","避免单击误触；双击仍由原生设置管理。",c.blockSingleClick){save(Store.update(this){v->v.copy(blockSingleClick=it)})}
        toggle("低光保持显示","阻止极暗、夜间低光及本机特殊规则隐藏 AOD。",c.blockLowLightHide){save(Store.update(this){v->v.copy(blockLowLightHide=it)})}
        section("显示时长")
        action("目标显示时长  ·  "+durationModeLabel(c.aodDurationMode,c.aodDurationCustomMinutes)){
            val labels=(C.DURATION_MODE_SYSTEM..C.DURATION_MODE_CUSTOM).map{durationModeLabel(it,c.aodDurationCustomMinutes)}.toTypedArray()
            AlertDialog.Builder(this).setTitle("目标显示时长").setSingleChoiceItems(labels,c.aodDurationMode){d,index->d.dismiss();save(Store.update(this){it.copy(aodDurationMode=index)})}.setNegativeButton("取消",null).show()
        }
        if(c.aodDurationMode==C.DURATION_MODE_CUSTOM)action("自定义分钟数  ·  ${c.aodDurationCustomMinutes}"){customMinutes(c.aodDurationCustomMinutes)}
        note("目标时长只接管全景 AOD 的普通自动超时。解锁、来电和主动唤醒仍按系统逻辑退出；“始终显示”也不会阻止这些退出。")
        section("高级选项")
        action(if(advanced)"收起独立入口设置  ⌃" else "单独显示设置入口  ›"){advanced=!advanced;refreshPage()}
        if(advanced){
            if(c.enablePanoramic)note("全天全景已包含设置入口。原有独立入口选择会保留，无需再次开启。")
            else toggle("仅显示全天设置入口","只暴露系统选项，不延长全景息屏时长。",c.enableSettingsSupport){save(Store.update(this){v->v.copy(enableSettingsSupport=it)})}
        }
        note("修改会自动同步；关闭全天增强可恢复原生能力。重新打开系统息屏设置查看入口，下一次息屏使用新策略。")
    }
    private fun customMinutes(current:Int){
        val input=EditText(this).apply{setSingleLine();inputType=InputType.TYPE_CLASS_NUMBER;hint="1–1440 分钟";setText(current.toString())}
        val dialog=AlertDialog.Builder(this).setTitle("自定义分钟数").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",null).create()
        dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{val minutes=AodInputValidation.durationMinutes(input.text.toString());if(minutes==null)input.error="请输入 1–1440 的整数" else {dialog.dismiss();save(Store.update(this){it.copy(aodDurationCustomMinutes=minutes)})}}};dialog.show()
    }
}
internal fun durationModeLabel(mode:Int,minutes:Int)=when(mode){C.DURATION_MODE_30_SECONDS->"30 秒";C.DURATION_MODE_1_MINUTE->"1 分钟";C.DURATION_MODE_5_MINUTES->"5 分钟";C.DURATION_MODE_10_MINUTES->"10 分钟";C.DURATION_MODE_30_MINUTES->"30 分钟";C.DURATION_MODE_60_MINUTES->"60 分钟";C.DURATION_MODE_ALWAYS->"始终显示";C.DURATION_MODE_CUSTOM->"自定义 $minutes 分钟";else->"跟随系统"}
