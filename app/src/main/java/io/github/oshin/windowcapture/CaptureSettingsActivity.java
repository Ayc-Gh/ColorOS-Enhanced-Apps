// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/** Standalone control for ordinary secure windows, independent of casting hosts. */
public final class CaptureSettingsActivity extends Activity {
    private boolean saving;
    private final Runnable observer=()->{if(!saving&&!isFinishing()&&!isDestroyed())render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private int dp(int value){return UiKit.dp(this,value);}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(body);
        UiKit.header(this,body,"截图与录屏","解除普通窗口的截图与录屏限制");
        body.addView(UiKit.status(this,SettingsBridge.status));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);UiKit.cardRow(this,row);
        TextView label=UiKit.text(this,"解除截图录屏限制",18,true);
        label.setPadding(0,dp(8),dp(12),dp(8));row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch control=new Switch(this);control.setContentDescription("解除截图录屏限制");
        control.setChecked(SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(Config.CAPTURE,false));
        control.setEnabled(SettingsBridge.prefs!=null&&!saving);
        control.setOnCheckedChangeListener((v,on)->{
            if(saving)return;saving=true;
            SettingsBridge.save(e->e.putBoolean(Config.CAPTURE,on),ok->{
                saving=false;if(isFinishing()||isDestroyed())return;
                if(!ok)Toast.makeText(this,"保存失败，请检查框架连接。",Toast.LENGTH_LONG).show();render();
            });
        });
        row.addView(control);row.setOnClickListener(v->{if(control.isEnabled())control.toggle();});
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);body.addView(row,params);
        TextView detail=UiKit.text(this,"修改后重新打开目标应用。正在投屏时，还需停止并重新连接。\n\n适用于普通窗口的安全标记限制；不覆盖硬件 DRM、受保护的视频缓冲区或应用自行隐藏的内容。",13,false);
        detail.setTextColor(UiKit.MUTED);detail.setPadding(0,dp(16),0,dp(12));body.addView(detail);
        Button runtime=UiKit.button(this,"查看功能运行状态",false);
        runtime.setOnClickListener(v->startActivity(new Intent(this,com.op.aod.enhance.ui.RuntimeStatusActivity.class).putExtra("section","capture")));body.addView(runtime);
        setContentView(scroll);UiKit.applyBars(this,scroll);
    }
}
