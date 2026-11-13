// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small shared visual language for the programmatic settings screens. */
public final class UiKit {
    public static final int BG=Color.rgb(244,247,252), CARD=Color.WHITE, INK=Color.rgb(25,35,53), MUTED=Color.rgb(94,106,126), BLUE=Color.rgb(38,105,239);
    private UiKit() {}
    public static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static GradientDrawable outlined(int color,int stroke,int strokeColor,int radius){GradientDrawable d=rounded(color,radius);d.setStroke(stroke,strokeColor);return d;}
    public static TextView text(Context c,String value,float size,boolean bold){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(INK);v.setLineSpacing(dp(c,3),1);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    public static TextView section(Context c,String value){TextView v=text(c,value,13,true);v.setTextColor(MUTED);v.setLetterSpacing(.02f);v.setPadding(dp(c,4),dp(c,22),0,dp(c,8));return v;}
    public static TextView status(Context c,String value){boolean connected=value.startsWith("配置服务已连接");TextView v=text(c,connected?"配置服务已连接 · 点击查看组件状态":value,13,false);v.setTextColor(connected?BLUE:MUTED);v.setPadding(dp(c,16),dp(c,13),dp(c,16),dp(c,13));v.setBackground(rounded(CARD,dp(c,16)));v.setContentDescription("配置连接状态");v.setOnClickListener(x->new android.app.AlertDialog.Builder(c).setTitle("组件与配置状态").setMessage(value).setPositiveButton("知道了",null).show());return v;}
    public static Button button(Context c,String label,boolean primary){Button b=new Button(c);styleButton(c,b,label,primary);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,8);b.setLayoutParams(p);return b;}
    static void styleButton(Context c,Button b,String label,boolean primary){b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:INK);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(c,46));b.setMinimumHeight(dp(c,46));b.setPadding(dp(c,16),dp(c,2),dp(c,16),dp(c,2));b.setStateListAnimator(null);b.setBackground(rounded(primary?BLUE:CARD,dp(c,14)));b.setElevation(dp(c,primary?2:0));}
    public static void applyBars(Activity activity,View root){activity.getWindow().setStatusBarColor(BG);activity.getWindow().setNavigationBarColor(BG);WindowInsetsController controller=activity.getWindow().getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});root.requestApplyInsets();}
    public static void cardRow(Context c,LinearLayout row){row.setBackground(rounded(CARD,dp(c,18)));row.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));}
    public static void header(Activity activity,LinearLayout body,String title,String subtitle){
        LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text(activity,"‹",34,false);back.setGravity(Gravity.CENTER);back.setContentDescription("返回上一页");back.setBackground(rounded(CARD,dp(activity,16)));bar.addView(back,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));back.setOnClickListener(v->activity.finish());
        TextView heading=text(activity,title,25,true);heading.setPadding(dp(activity,14),0,0,0);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));body.addView(bar);
        TextView sub=text(activity,subtitle,13,false);sub.setTextColor(MUTED);sub.setPadding(dp(activity,2),dp(activity,14),dp(activity,2),dp(activity,18));body.addView(sub);
    }
    public static LinearLayout toggle(Context c,String title,String summary,boolean checked,boolean available,java.util.function.Consumer<Boolean> change){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);cardRow(c,row);LinearLayout labels=new LinearLayout(c);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(text(c,title,17,true));
        if(!summary.isEmpty()){TextView description=text(c,summary,12,false);description.setTextColor(MUTED);description.setPadding(0,dp(c,5),dp(c,12),0);labels.addView(description);}
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));android.widget.Switch control=new android.widget.Switch(c);control.setContentDescription(title);control.setChecked(checked);control.setEnabled(available);control.setOnCheckedChangeListener((v,on)->change.accept(on));row.addView(control);row.setOnClickListener(v->{if(control.isEnabled())control.toggle();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,8);row.setLayoutParams(p);return row;
    }
    public static TextView note(Context c,String value){TextView v=text(c,value,13,false);v.setTextColor(MUTED);v.setPadding(dp(c,4),dp(c,12),dp(c,4),dp(c,8));return v;}
}
