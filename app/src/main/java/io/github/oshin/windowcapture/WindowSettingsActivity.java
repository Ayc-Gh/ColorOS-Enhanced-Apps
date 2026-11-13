// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import android.app.*;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

public final class WindowSettingsActivity extends Activity {
    private LinearLayout body;private ScrollView scroll;private boolean saving;
    private final int ink=Color.rgb(26,37,57),muted=Color.rgb(94,106,126),accent=Color.rgb(38,105,239);
    private final Runnable observer=()->{if(!saving&&!isFinishing()&&!isDestroyed())render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,float size,boolean bold){return UiKit.text(this,value,size,bold);}
    private boolean ready(){return SettingsBridge.prefs!=null&&!saving;}
    private void section(String s){body.addView(UiKit.section(this,s));}
    private void render(){
        int y=scroll==null?0:scroll.getScrollY();
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(UiKit.BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(16),dp(20),dp(30));scroll.addView(body);
        Button back=UiKit.button(this,"‹  返回主界面",false);back.setOnClickListener(v->finish());body.addView(back);
        TextView overline=text("WINDOWS",11,true);overline.setTextColor(accent);overline.setPadding(dp(2),dp(20),0,0);body.addView(overline);
        body.addView(text("分屏与小窗",28,true));TextView sub=text("为 Find X7 Ultra · ColorOS 17 适配",14,false);sub.setTextColor(muted);sub.setPadding(0,dp(7),0,dp(18));body.addView(sub);
        body.addView(UiKit.status(this,SettingsBridge.status));
        section("分屏与小窗");
        toggle(Config.UNLOCK,"解除小窗应用限制","放开系统的小窗支持列表与不支持比例限制");
        toggle(Config.SPLIT,"允许不支持的应用分屏","尝试让不可调整大小的普通应用进入分屏；应用布局仍受自身实现影响");
        toggle(Config.MULTI,"强制多窗口模式","在小窗调用链启用多模式，保留分屏自身的系统策略");
        integer(Config.COUNT,"同时开启的小窗数量",1,10,"个");
        toggle(Config.FREE,"自由调整小窗比例","开放自由比例与系统的可调整比例集合");
        decimal(Config.RATIO,"自定义小窗高宽比","高度 ÷ 宽度，0.25–3；2 表示高度为宽度的两倍；留空跟随系统");
        section("小窗外观");
        integer(Config.CORNER,"小窗圆角",0,300,"px");
        integer(Config.SHADOW_FOCUS,"聚焦时阴影",0,300,"px");
        integer(Config.SHADOW_BLUR,"未聚焦时阴影",0,300,"px");
        toggle(Config.HIDE_BOTTOM,"隐藏底部操作条","隐藏底部把手，关闭后恢复；不改变系统触摸区域");
        toggle(Config.HIDE_TOP,"隐藏顶部三个点","隐藏顶部控制视图，关闭后恢复");
        section("优先以小窗打开");
        toggle(Config.JUMP,"应用跳转优先小窗","应用之间跳转时优先小窗，不改变同一应用内部页面跳转");
        toggle(Config.NOTIFICATION,"通知启动优先小窗","从系统通知打开应用时使用小窗");
        toggle(Config.LAUNCHER,"桌面启动优先小窗","从桌面图标启动应用时使用小窗");
        toggle(Config.EXCLUDE_SYSTEM,"排除系统应用","优先小窗规则跳过系统应用；桌面与系统界面始终保留原行为");
        toggle(Config.FILTER,"启用应用过滤","过滤规则应用到所有优先小窗入口");
        toggle(Config.BLACKLIST,"使用黑名单模式","开启：所选应用不强制小窗；关闭：只对所选应用生效");
        Button apps=UiKit.button(this,"选择应用 · 已选 "+(SettingsBridge.prefs==null?0:SettingsBridge.prefs.getStringSet(Config.APPS,Set.of()).size())+" 个",false);apps.setEnabled(ready());apps.setOnClickListener(v->pickApps());body.addView(apps);
        section("贴边小窗");
        integer(Config.EDGE_COUNT,"贴边应用数量",1,Config.MAX_EDGE_COUNT,"个");
        TextView edgeNote=text("独立设置 1–5 个贴边应用，留空跟随系统。降低数量或恢复默认后，已有的多余贴边应用可继续保留；展开或关闭后按新上限管理。",12,false);edgeNote.setTextColor(muted);edgeNote.setPadding(0,dp(4),0,dp(10));body.addView(edgeNote);
        toggle(Config.REMEMBER,"记住贴边小窗","重新打开已贴边应用时，按系统保存的位置恢复为小窗");
        toggle(Config.FOREGROUND,"贴边后前台保活","贴边时保留应用任务与运行状态；部分应用仍可能主动暂停");
        section("诊断与恢复");
        Button runtime=UiKit.button(this,"查看功能运行状态",true);runtime.setOnClickListener(v->startActivity(new android.content.Intent(this,com.op.aod.enhance.ui.RuntimeStatusActivity.class).putExtra("section","windows")));body.addView(runtime);
        toggle(Config.LOG,"详细日志","记录接口适配与首次生效事件，可在框架日志中查看");
        TextView note=text("安装或升级后重启手机一次。配置保存与宿主加载可分别在功能运行状态中查看；比例、圆角、阴影与捕获状态改变后，请重新打开目标应用。\n\n请停用其他同类模块，避免重复修改。所有功能默认关闭，数值留空表示跟随系统。高宽比不改变旧版保存的数值含义，最终尺寸仍受屏幕和系统布局限制。",13,false);note.setTextColor(muted);note.setPadding(0,dp(16),0,dp(12));body.addView(note);
        Button reset=UiKit.button(this,"恢复分屏与小窗默认设置",false);reset.setEnabled(ready());reset.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("恢复默认设置").setMessage("恢复分屏与小窗设置，清空对应数值和应用列表。OPPO 互联、截图录屏和 AOD 设置保留。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->save(e->{for(String key:Config.WINDOW_KEYS)e.remove(key);})).show());body.addView(reset);
        TextView footer=text("v"+BuildConfig.VERSION_NAME+" · AGPL-3.0",12,false);footer.setPadding(0,dp(16),0,0);footer.setTextColor(muted);body.addView(footer);
        setContentView(scroll);UiKit.applyBars(this,scroll);ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
    }
    private void toggle(String key,String title,String description){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);UiKit.cardRow(this,row);LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(8);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(text(title,17,true));TextView detail=text(description,12,false);detail.setTextColor(muted);detail.setPadding(0,dp(4),dp(12),0);labels.addView(detail);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Switch control=new Switch(this);control.setContentDescription(title);control.setChecked(SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(key,false));control.setEnabled(ready());control.setOnCheckedChangeListener((v,checked)->save(e->e.putBoolean(key,checked)));row.addView(control);row.setOnClickListener(v->{if(control.isEnabled())control.toggle();});body.addView(row,rowParams);
    }
    private void integer(String key,String title,int min,int max,String unit){
        int value=SettingsBridge.prefs==null?-1:SettingsBridge.prefs.getInt(key,-1);Button button=UiKit.button(this,title+"  ·  "+(value<0?"跟随系统":value+" "+unit),false);button.setContentDescription(title);button.setEnabled(ready());
        button.setOnClickListener(v->{EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setHint("留空跟随系统；"+min+"–"+max);if(value>=0)input.setText(String.valueOf(value));
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("取消",null).setNeutralButton("系统默认",(d,w)->save(e->e.remove(key))).setPositiveButton("保存",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(b->{String s=input.getText().toString().trim();try{int n=s.isEmpty()?-1:Integer.parseInt(s);if(n!=-1&&(n<min||n>max))throw new NumberFormatException();dialog.dismiss();save(e->{if(n<0)e.remove(key);else e.putInt(key,n);});}catch(NumberFormatException x){input.setError("请输入 "+min+"–"+max+"，或留空");}}));dialog.show();});body.addView(button);
    }
    private void decimal(String key,String title,String hint){
        float value=SettingsBridge.prefs==null?-1:SettingsBridge.prefs.getFloat(key,-1);Button button=UiKit.button(this,title+"  ·  "+(value<0?"跟随系统":value),false);button.setEnabled(ready());button.setOnClickListener(v->{EditText input=new EditText(this);input.setSingleLine();input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setHint(hint);if(value>0)input.setText(String.valueOf(value));AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("取消",null).setNeutralButton("系统默认",(d,w)->save(e->e.remove(key))).setPositiveButton("保存",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(b->{String s=input.getText().toString().trim();try{float n=s.isEmpty()?-1:Float.parseFloat(s);if(n!=-1&&(!Float.isFinite(n)||n<.25||n>3))throw new NumberFormatException();dialog.dismiss();save(e->{if(n<0)e.remove(key);else e.putFloat(key,n);});}catch(NumberFormatException x){input.setError("请输入 0.25–3，或留空");}}));dialog.show();});body.addView(button);
    }
    private void pickApps(){
        List<ApplicationInfo> apps=new ArrayList<>(getPackageManager().getInstalledApplications(0));apps.removeIf(a->a.packageName.equals(getPackageName()));apps.sort(Comparator.comparing(a->a.loadLabel(getPackageManager()).toString(),java.text.Collator.getInstance()));
        Set<String> selected=new HashSet<>(SettingsBridge.prefs.getStringSet(Config.APPS,Set.of()));String[] labels=new String[apps.size()];boolean[] checked=new boolean[apps.size()];for(int i=0;i<apps.size();i++){ApplicationInfo a=apps.get(i);labels[i]=a.loadLabel(getPackageManager())+"\n"+a.packageName;checked[i]=selected.contains(a.packageName);}
        new AlertDialog.Builder(this).setTitle("选择应用").setMultiChoiceItems(labels,checked,(d,index,value)->{String pkg=apps.get(index).packageName;if(value)selected.add(pkg);else selected.remove(pkg);}).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->save(e->e.putStringSet(Config.APPS,selected))).show();
    }
    private void save(Consumer<SharedPreferences.Editor> edit){saving=true;SettingsBridge.save(edit,ok->{saving=false;if(isFinishing()||isDestroyed())return;if(!ok)Toast.makeText(this,"保存失败，请重新连接 LSPosed",Toast.LENGTH_LONG).show();render();});}
}
