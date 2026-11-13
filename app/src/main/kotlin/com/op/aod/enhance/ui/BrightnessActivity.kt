package com.op.aod.enhance.ui
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodInputValidation
import io.github.oshin.windowcapture.UiKit
import java.util.Locale

class BrightnessActivity:NativeSettingsActivity(){
    override fun content(){
        val c=AodConfigStore.config.value
        header("AOD 亮度","三个亮度选项分别保存，默认跟随系统");syncStatus();section("初始亮度")
        toggle("暗光亮度跟随系统","保留 ColorOS 根据当前环境计算的初始亮度。",c.useSystemInitDark){save(AodConfigStore.update(this){v->v.copy(useSystemInitDark=it)})}
        if(c.useSystemInitDark)note("自定义值 ${c.initDark} 已保留。") else action("暗光初始亮度  ·  ${c.initDark}"){number("暗光初始亮度",c.initDark.toFloat(),false){v->save(AodConfigStore.update(this){it.copy(initDark=v.toInt())})}}
        toggle("亮光亮度跟随系统","保留 ColorOS 在亮光环境下计算的初始亮度。",c.useSystemInitBright){save(AodConfigStore.update(this){v->v.copy(useSystemInitBright=it)})}
        if(c.useSystemInitBright)note("自定义值 ${c.initBright} 已保留。") else action("亮光初始亮度  ·  ${c.initBright}"){number("亮光初始亮度",c.initBright.toFloat(),false){v->save(AodConfigStore.update(this){it.copy(initBright=v.toInt())})}}
        section("运行亮度")
        toggle("自动亮度倍率跟随系统","保留系统原有的 AOD 自动亮度结果。",c.useSystemRunningMultiplier){save(AodConfigStore.update(this){v->v.copy(useSystemRunningMultiplier=it)})}
        if(c.useSystemRunningMultiplier)note("自定义倍率 ${c.runningMultiplier}× 已保留。") else action("自动亮度倍率  ·  ${c.runningMultiplier}×"){number("自动亮度倍率",c.runningMultiplier,true){v->save(AodConfigStore.update(this){it.copy(runningMultiplier=v)})}}
        note("初始亮度在下一次息屏时使用；运行倍率由后续系统亮度回调处理。")
    }
    private fun number(title:String,current:Float,multiplier:Boolean,onSave:(Float)->Unit){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;val p=UiKit.dp(context,20);setPadding(p,p,p,p)}
        val input=EditText(this).apply{setSingleLine();inputType=InputType.TYPE_CLASS_NUMBER or(if(multiplier)InputType.TYPE_NUMBER_FLAG_DECIMAL else 0);hint=if(multiplier)"1.0–2.0" else "0–255";setText(if(multiplier)current.toString() else current.toInt().toString())};content.addView(input)
        val seek=SeekBar(this).apply{max=if(multiplier)10 else 255;progress=if(multiplier)((current-1)*10).toInt() else current.toInt();contentDescription=title+"滑块"};content.addView(seek)
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(bar:SeekBar?,p:Int,user:Boolean){if(user)input.setText(if(multiplier)String.format(Locale.ROOT,"%.1f",1+p/10f) else p.toString())};override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){} })
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(content).setNegativeButton("取消",null).setPositiveButton("保存",null).create()
        dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{
            val v=if(multiplier)AodInputValidation.multiplier(input.text.toString()) else AodInputValidation.brightness(input.text.toString())?.toFloat()
            if(v==null)input.error=if(multiplier)"请输入 1.0–2.0 的数值" else "请输入 0–255 的整数" else {dialog.dismiss();onSave(v)}
        }};dialog.show()
    }
}
