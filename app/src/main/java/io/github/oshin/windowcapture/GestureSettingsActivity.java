// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings for the extra gesture entries; system learning and component control stay in ColorOS. */
public final class GestureSettingsActivity extends Activity {
    private ScrollView scroll;
    private boolean saving;
    private final Runnable observer=()->{if(!saving&&!isFinishing()&&!isDestroyed())render();};

    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}

    private int dp(int value){return UiKit.dp(this,value);}
    private TextView text(String value,int size,boolean bold){TextView t=UiKit.text(this,value,size,bold);t.setPadding(0,dp(6),0,dp(10));return t;}

    private void render(){
        int y=scroll==null?0:scroll.getScrollY();
        scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(body);
        UiKit.header(this,body,"隔空手势","解锁额外入口，保留系统原生识别方式");
        String scopeState=SettingsBridge.scope.contains(AirGesturePolicy.PACKAGE)?"已勾选":"未勾选";
        body.addView(UiKit.status(this,SettingsBridge.status+"\n手势体感"+scopeState));

        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);UiKit.cardRow(this,row);
        LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(10);
        TextView label=text("解锁隔空手势设置",18,true);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setContentDescription("解锁隔空手势设置");
        toggle.setChecked(SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(Config.AIR_GESTURE,false));
        toggle.setEnabled(SettingsBridge.prefs!=null&&!saving);
        toggle.setOnCheckedChangeListener((v,on)->{if(saving)return;saving=true;SettingsBridge.save(e->e.putBoolean(Config.AIR_GESTURE,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查框架连接。",Toast.LENGTH_SHORT).show();if(!isFinishing()&&!isDestroyed())render();});});
        row.addView(toggle);row.setOnClickListener(v->{if(toggle.isEnabled())toggle.toggle();});body.addView(row,rowParams);
        android.widget.Button system=UiKit.button(this,"打开系统隔空手势设置",!saving);system.setOnClickListener(v->openSystemSettings());body.addView(system);
        setContentView(scroll);UiKit.applyBars(this,scroll);ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
    }

    private void openSystemSettings(){
        try{startActivity(new Intent("com.oplus.action.AONGestureMenuActivity").setPackage(AirGesturePolicy.PACKAGE));}
        catch(Exception e){Toast.makeText(this,"系统未提供此手势页面",Toast.LENGTH_LONG).show();}
    }
}
