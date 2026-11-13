package com.op.aod.enhance.ui
import android.app.AlertDialog
import android.content.Intent
import com.op.aod.enhance.data.AodConfig
import com.op.aod.enhance.data.AodConfigStore

class MainActivity:NativeSettingsActivity(){
    override fun content(){
        header("AOD","息屏亮度、时长与唤醒行为")
        syncStatus();section("显示设置")
        action("亮度设置  ›"){startActivity(Intent(this,BrightnessActivity::class.java))}
        note("初始亮度与自动亮度倍率，可分别选择跟随系统。")
        action("功能与显示时长  ›"){startActivity(Intent(this,FeaturesActivity::class.java))}
        note("全天全景、单击屏蔽、低光保持与自动显示时长。")
        section("诊断与恢复")
        action("查看功能运行状态",true){startActivity(Intent(this,RuntimeStatusActivity::class.java))}
        action("恢复 AOD 系统默认"){
            AlertDialog.Builder(this).setTitle("恢复 AOD 系统默认").setMessage("亮度和时长跟随系统，关闭 AOD 增强。其他模块设置保留。").setNegativeButton("取消",null).setPositiveButton("恢复"){_,_->save(AodConfigStore.write(this,AodConfig()))}.show()
        }
    }
}
