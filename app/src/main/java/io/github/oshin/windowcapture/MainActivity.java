// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Three independent settings destinations; upgrades keep the original preference keys. */
public final class MainActivity extends Activity {
    private LinearLayout body;
    private final Runnable observer=()->{if(!isFinishing()&&!isDestroyed())render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(26,37,57));t.setLineSpacing(dp(4),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private GradientDrawable card(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(244,247,252));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(30),dp(24),dp(26));scroll.addView(body);
        body.addView(text("ColorOS 增强",30,true));
        TextView subtitle=text("Find X7 Ultra · ColorOS 17",14,false);subtitle.setTextColor(Color.rgb(94,106,126));subtitle.setPadding(0,dp(8),0,dp(24));body.addView(subtitle);
        TextView status=text(SettingsBridge.status,13,false);status.setPadding(dp(16),dp(14),dp(16),dp(14));status.setBackground(card(Color.WHITE,16));body.addView(status);
        entrance("分屏与小窗","应用分屏、小窗数量、外观与贴边",WindowSettingsActivity.class,0xFF2669EF);
        entrance("OPPO 互联","电脑投屏与解除截图录屏限制",CastSettingsActivity.class,0xFF008C83);
        entrance("AOD","息屏亮度、显示时长与唤醒设置",com.op.aod.enhance.ui.MainActivity.class,0xFF7653C1);
        TextView footer=text("v"+BuildConfig.VERSION_NAME+" · AGPL-3.0\n调整设置后，按各页面提示使其生效。",12,false);footer.setTextColor(Color.rgb(94,106,126));footer.setPadding(0,dp(24),0,0);body.addView(footer);
        setContentView(scroll);
        getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});scroll.requestApplyInsets();
    }
    private void entrance(String title,String description,Class<?> destination,int accent){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(23),dp(18),dp(23));row.setBackground(card(Color.WHITE,20));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);TextView heading=text(title,22,true);heading.setTextColor(accent);labels.addView(heading);TextView sub=text(description,13,false);sub.setPadding(0,dp(8),dp(10),0);sub.setTextColor(Color.rgb(94,106,126));labels.addView(sub);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",28,false);arrow.setTextColor(accent);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow);
        row.setClickable(true);row.setFocusable(true);row.setContentDescription(title+"，"+description);row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Button");}});
        row.setOnClickListener(v->startActivity(new Intent(this,destination)));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(18);body.addView(row,params);
    }
}
