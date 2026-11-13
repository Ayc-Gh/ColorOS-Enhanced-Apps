// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class GestureSettingsActivity extends Activity {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean busy;
    private LinearLayout body;
    private ScrollView scroll;
    private String result="";
    private final Runnable observer=()->{if(!busy&&!isFinishing()&&!isDestroyed())render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean bold){TextView t=UiKit.text(this,value,size,bold);t.setPadding(0,dp(6),0,dp(10));return t;}
    private boolean ready(){return !busy&&SettingsBridge.prefs!=null;}
    private void button(String label,boolean available,Runnable action){Button b=UiKit.button(this,label,label.contains("应用并重启"));b.setEnabled(available);b.setOnClickListener(v->action.run());body.addView(b);}
    private void render(){
        int y=scroll==null?0:scroll.getScrollY();scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(body);
        button("‹  返回主设置",true,this::finish);TextView overline=text("AIR GESTURE",11,true);overline.setTextColor(Color.rgb(179,105,22));overline.setPadding(dp(2),dp(20),0,0);body.addView(overline);body.addView(text("隔空手势",25,true));
        body.addView(text("解锁系统已有的隔空滑动和暂停／播放设置，保留手机原生识别方式。",15,false));
        body.addView(UiKit.status(this,SettingsBridge.status+"\n手势体感"+(SettingsBridge.scope.contains(AirGesturePolicy.PACKAGE)?"已勾选":"未勾选，请在框架中勾选后重启组件")));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);UiKit.cardRow(this,row);LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(10);TextView label=text("解锁隔空手势设置",18,true);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));Switch toggle=new Switch(this);toggle.setContentDescription("解锁隔空手势设置");toggle.setChecked(SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(Config.AIR_GESTURE,false));toggle.setEnabled(ready());toggle.setOnCheckedChangeListener((v,on)->{busy=true;SettingsBridge.save(e->e.putBoolean(Config.AIR_GESTURE,on),ok->{busy=false;result=ok?"设置已保存，请重启手势组件使其生效。":"保存失败，请检查框架连接。";if(!isFinishing()&&!isDestroyed())render();});});row.addView(toggle);row.setOnClickListener(v->{if(toggle.isEnabled())toggle.toggle();});body.addView(row,rowParams);
        body.addView(text("修改后点下方按钮，重启手势体感与 AONService 并打开系统设置。此操作需要 Root；也可在系统应用管理中强制停止这两个组件，再重新打开隔空手势设置。",13,false));
        button(busy?"正在处理…":"应用并重启两个组件",ready(),this::restart);
        if(!result.isEmpty())body.addView(text(result,13,true));
        button("打开系统隔空手势设置",!busy,()->open(null));
        body.addView(text("系统手势学习",18,true));
        body.addView(text("通过系统原有学习页面检查识别。请按提示在前置摄像头前 20–40 厘米处操作；学习成功与具体应用中的兼容性分别验证。",13,false));
        button("学习隔空接听来电",!busy,()->open(393220));button("学习隔空滑动",!busy,()->open(393222));button("学习隔空暂停／播放",!busy,()->open(393224));
        body.addView(text("本机适配：Find X7 Ultra / PHY110，ColorOS 17.0.0.100，AONService 15.0.73、手势体感 8.6.0。\n\n首次使用请关闭其他模块中同类的引擎强制开关，再重启上述两个组件。原有隔空接听、静音和应用开关保持由系统管理。开启额外入口不代表所有应用均支持手势，请在系统的“支持的应用”中确认。\n\n关闭本页开关并重启组件，可恢复系统原有设置入口。AON 与 AOD（息屏显示）分别设置。",13,false));
        setContentView(scroll);UiKit.applyBars(this,scroll);ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
    }
    private void open(Integer course){
        Intent intent=new Intent(course==null?"com.oplus.action.AONGestureMenuActivity":"com.oplus.action.AONLearnActivity").setPackage(AirGesturePolicy.PACKAGE);
        if(course!=null)intent.putExtra("learn_type",course);
        try{startActivity(intent);}catch(Exception e){Toast.makeText(this,"系统未提供此手势页面",Toast.LENGTH_LONG).show();}
    }
    private void restart(){
        busy=true;result="正在重启手势组件；如有 Root 授权提示，请在手机上处理。";render();
        worker.execute(()->{
            String message;
            Process process=null;
            try{
                // Fixed allowlist; no app name, path or shell fragment comes from UI input.
                String command="am force-stop --user current com.oplus.gesture; am force-stop --user current com.aiunit.aon; am start --user current -a com.oplus.action.AONGestureMenuActivity -p com.oplus.gesture -f 0x14000000";
                process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
                if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Root 授权或组件重启超时，请手动重启两个组件。");}
                String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                if(process.exitValue()!=0||output.contains("Error:")||output.contains("Exception")||output.contains("Permission denied"))throw new IllegalStateException("未能重启，请检查 Root 授权或手动重启两个组件。");
                message="已请求重启两个组件，请在系统设置和学习页面确认识别。";
            }catch(Exception e){message=e instanceof IllegalStateException?e.getMessage():"无法取得 Root，请手动重启手势体感与 AONService。";}
            finally{if(process!=null)process.destroy();}
            String finalMessage=message;runOnUiThread(()->{busy=false;result=finalMessage;if(!isFinishing()&&!isDestroyed())render();});
        });
    }
}
