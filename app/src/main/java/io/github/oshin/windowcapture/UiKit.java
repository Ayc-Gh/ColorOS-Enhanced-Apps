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
final class UiKit {
    static final int BG=Color.rgb(244,247,252), CARD=Color.WHITE, INK=Color.rgb(25,35,53), MUTED=Color.rgb(94,106,126), BLUE=Color.rgb(38,105,239);
    private UiKit() {}
    static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static GradientDrawable outlined(int color,int stroke,int strokeColor,int radius){GradientDrawable d=rounded(color,radius);d.setStroke(stroke,strokeColor);return d;}
    static TextView text(Context c,String value,float size,boolean bold){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(INK);v.setLineSpacing(dp(c,3),1);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    static TextView section(Context c,String value){TextView v=text(c,value,13,true);v.setTextColor(BLUE);v.setLetterSpacing(.04f);v.setPadding(0,dp(c,24),0,dp(c,7));return v;}
    static TextView status(Context c,String value){TextView v=text(c,value,13,false);v.setTextColor(MUTED);v.setPadding(dp(c,15),dp(c,13),dp(c,15),dp(c,13));v.setBackground(outlined(CARD,dp(c,1),Color.rgb(224,230,240),dp(c,15)));return v;}
    static Button button(Context c,String label,boolean primary){Button b=new Button(c);styleButton(c,b,label,primary);return b;}
    static void styleButton(Context c,Button b,String label,boolean primary){b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:INK);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(c,46));b.setMinimumHeight(dp(c,46));b.setPadding(dp(c,16),dp(c,2),dp(c,16),dp(c,2));b.setStateListAnimator(null);b.setBackground(rounded(primary?BLUE:CARD,dp(c,14)));b.setElevation(dp(c,primary?2:0));}
    static void applyBars(Activity activity,View root){activity.getWindow().setStatusBarColor(BG);activity.getWindow().setNavigationBarColor(BG);WindowInsetsController controller=activity.getWindow().getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});root.requestApplyInsets();}
    static void cardRow(Context c,LinearLayout row){row.setBackground(outlined(CARD,dp(c,1),Color.rgb(233,237,245),dp(c,16)));row.setElevation(dp(c,1));row.setPadding(dp(c,16),dp(c,10),dp(c,12),dp(c,10));}
}
