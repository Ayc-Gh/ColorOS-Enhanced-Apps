// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Independent settings destinations; upgrades keep the original preference keys. */
public final class MainActivity extends Activity {
    private LinearLayout body;
    private final Runnable observer=()->{if(!isFinishing()&&!isDestroyed())render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private int dp(int value){return UiKit.dp(this,value);}
    private TextView text(String value,float size,boolean bold){return UiKit.text(this,value,size,bold);}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(UiKit.BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(22),dp(20),dp(26));scroll.addView(body);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(20),dp(18),dp(20),dp(18));hero.setBackground(UiKit.rounded(Color.rgb(232,239,255),dp(22)));body.addView(hero);
        TextView title=text("ColorOS 增强",28,true);title.setPadding(0,dp(2),0,0);hero.addView(title);
        TextView subtitle=text("Find X7 Ultra · ColorOS 17",14,false);subtitle.setTextColor(UiKit.MUTED);subtitle.setPadding(0,dp(5),0,dp(14));hero.addView(subtitle);
        hero.addView(UiKit.status(this,SettingsBridge.status));
        TextView hint=text("按需开启，设置自动保存。",12,false);hint.setTextColor(UiKit.MUTED);hint.setPadding(0,dp(12),0,0);hero.addView(hint);
        entrance(1,"分屏与小窗","应用分屏、小窗数量、外观与贴边",WindowSettingsActivity.class,0xFF2669EF);
        entrance(2,"OPPO 互联","电脑投屏与解除截图录屏限制",CastSettingsActivity.class,0xFF008C83);
        entrance(3,"AOD","息屏亮度、显示时长与唤醒设置",com.op.aod.enhance.ui.MainActivity.class,0xFF7653C1);
        entrance(4,"隔空手势","解锁设置入口，保留原生手势识别",GestureSettingsActivity.class,0xFFB36916);
        entrance(5,"联网管理","所有可管理应用的 WLAN 与移动数据",NetworkSettingsActivity.class,0xFF427B57);
        entrance(6,"小布记忆","解锁微信自动记账支持",AimemorySettingsActivity.class,0xFF8B4D8B);
        entrance(7,"相机与相册","AI 修图、灵感推荐与 Log 视频",GallerySettingsActivity.class,0xFFB15C54);
        entrance(8,"声音分轨","解除原生分轨功能的应用限制",SoundSettingsActivity.class,0xFF437E94);
        TextView footer=text("v"+BuildConfig.VERSION_NAME+"  ·  AGPL-3.0\n调整设置后，按各页面提示使其生效。",12,false);footer.setTextColor(UiKit.MUTED);footer.setPadding(dp(4),dp(22),dp(4),0);body.addView(footer);
        setContentView(scroll);UiKit.applyBars(this,scroll);
    }
    private void entrance(int number,String title,String description,Class<?> destination,int accent){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);UiKit.cardRow(this,row);
        TextView badge=text(String.valueOf(number),14,true);badge.setGravity(Gravity.CENTER);badge.setTextColor(Color.WHITE);badge.setBackground(UiKit.rounded(accent,dp(12)));row.addView(badge,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(14),0,dp(8),0);
        TextView heading=text(title,19,true);labels.addView(heading);
        TextView sub=text(description,13,false);sub.setPadding(0,dp(4),dp(10),0);sub.setTextColor(UiKit.MUTED);labels.addView(sub);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("›",27,false);arrow.setTextColor(accent);arrow.setGravity(Gravity.CENTER);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(dp(22),-2));
        row.setClickable(true);row.setFocusable(true);row.setContentDescription(title+"，"+description);row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Button");}});
        row.setOnClickListener(v->startActivity(new Intent(this,destination)));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);body.addView(row,params);
    }
}
