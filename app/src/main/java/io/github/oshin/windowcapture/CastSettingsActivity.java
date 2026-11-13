// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.content.SharedPreferences;
import java.util.function.Consumer;

public final class CastSettingsActivity extends Activity {
    private ScrollView scroll;private LinearLayout body;private boolean saving;
    private final Runnable observer=()->{if(!saving&&!isFinishing()&&!isDestroyed())render();};
    private static final String[] KEYS={Config.CAST,Config.CAST_QUALITY,Config.CAST_BITRATE,Config.CAST_FPS,Config.CAST_RESOLUTION,Config.CAST_DENSITY,Config.CAST_LOW_BANDWIDTH,Config.CAST_MODEL,Config.CAST_HARDWARE,Config.CAST_GAME};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private boolean ready(){return SettingsBridge.prefs!=null&&!saving;}
    private int number(String key,int fallback){return SettingsBridge.prefs==null?fallback:SettingsBridge.prefs.getInt(key,fallback);}
    private TextView text(String value,int size,boolean bold){return UiKit.text(this,value,size,bold);}
    private void section(String name){body.addView(UiKit.section(this,name));}
    private void render(){
        int y=scroll==null?0:scroll.getScrollY();scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(body);
        Button back=UiKit.button(this,"‹  返回主设置",false);back.setOnClickListener(v->finish());body.addView(back);
        TextView overline=text("OPPO CONNECT",11,true);overline.setTextColor(Color.rgb(0,140,131));overline.setPadding(dp(2),dp(20),0,0);body.addView(overline);
        body.addView(text("OPPO 互联",25,true));TextView context=text("电脑投屏与截图录屏 · Find X7 Ultra",13,false);context.setTextColor(UiKit.MUTED);context.setPadding(0,dp(7),0,dp(14));body.addView(context);body.addView(UiKit.status(this,SettingsBridge.status));
        section("截图与录屏");
        toggle(Config.CAPTURE,"解除截图录屏限制","用于手机截图、录屏和电脑投屏；移除普通窗口的安全标记限制。独立于下方投屏优化开关，修改后重新打开目标应用。");
        section("电脑投屏");
        toggle(Config.CAST,"启用电脑投屏优化","作用于 OPPO 互联使用的屏幕投屏组件；改变设置后停止并重新发起手机投屏");
        Button recommended=UiKit.button(this,"应用本机建议设置",true);recommended.setEnabled(ready());recommended.setOnClickListener(v->save(e->{e.putBoolean(Config.CAST,true);e.putInt(Config.CAST_QUALITY,1);e.remove(Config.CAST_BITRATE);e.putInt(Config.CAST_FPS,60);e.putInt(Config.CAST_RESOLUTION,0);e.putBoolean(Config.CAST_DENSITY,true);for(String k:new String[]{Config.CAST_LOW_BANDWIDTH,Config.CAST_MODEL,Config.CAST_HARDWARE,Config.CAST_GAME})e.putBoolean(k,false);}));body.addView(recommended);
        TextView advice=text("建议：保留当前原生分辨率，60 帧，码率范围提高到 8–16 Mbps，DPI 跟随手机。已按当前电脑链路确认：原生 1440×3168，硬件 H.264，约 12 Mbps。",13,false);advice.setPadding(0,dp(9),0,0);body.addView(advice);
        section("画质与码率");
        choice(Config.CAST_QUALITY,"码率档位",new String[]{"跟随系统","均衡：至少 8–16 Mbps","高画质：至少 20–32 Mbps"},new int[]{0,1,2});
        bitrate();
        toggle(Config.CAST_LOW_BANDWIDTH,"解除低带宽档位限制","将低码率表提高至至少 8–16 Mbps；弱网络时可能增加等待，建议先保持关闭");
        section("画面与帧率");
        choice(Config.CAST_FPS,"请求帧率",new String[]{"跟随系统","60 帧","90 帧","120 帧"},new int[]{0,60,90,120});
        choice(Config.CAST_RESOLUTION,"分辨率上限",new String[]{"跟随协商","1080 档 · 等比","1440 档 · 等比","本机物理分辨率 · 等比"},new int[]{0,1,2,3});
        toggle(Config.CAST_DENSITY,"DPI 跟随手机当前设置","读取当前有效 DPI，兼容本机 560 的自定义显示设置；不写死为 640");
        TextView fps=text("帧率请求受本机硬件、电脑接收能力、网络和画面变化共同影响。分辨率只做等比限制，不拉伸、不强制放大；本机 1440 档对应 1440×3168。",13,false);fps.setPadding(0,dp(8),0,0);body.addView(fps);
        section("高级选项");
        toggle(Config.CAST_MODEL,"阻止按机型降低能力","跳过已核对的机型降级分支；本机当前没有命中降级，无需默认开启");
        toggle(Config.CAST_HARDWARE,"采用硬件实际枚举能力","保留 Android 查询到的硬件编码能力，避免后续配置将其降低；不伪造编解码器支持");
        toggle(Config.CAST_GAME,"游戏场景增强","仅在投屏组件明确报告游戏场景时提高码率并请求高帧率；本机普通电脑镜像不触发");
        TextView scope=text("当前电脑镜像链路：OPPO 互联 → 屏幕投屏 → 硬件编码 → 电脑。独立的“屏幕共享”未参与此链路，本版本未加入其通话、音频或远程协助设置。\n\n升级模块后先重启手机。后续调整此页设置只需重连投屏；新会话恢复原生协商值，关闭总开关即可回到系统策略。",13,false);scope.setPadding(0,dp(22),0,dp(14));body.addView(scope);
        Button reset=UiKit.button(this,"仅恢复投屏默认设置",false);reset.setEnabled(ready());reset.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("恢复投屏默认设置").setMessage("关闭本页增强并清除投屏参数。分屏、小窗和截图设置保留。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->save(e->{for(String k:KEYS)e.remove(k);})).show());body.addView(reset);
        setContentView(scroll);UiKit.applyBars(this,scroll);ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
    }
    private void toggle(String key,String title,String detail){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);UiKit.cardRow(this,row);LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(8);LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(text(title,17,true));TextView description=text(detail,12,false);description.setTextColor(UiKit.MUTED);description.setPadding(0,dp(5),dp(12),0);labels.addView(description);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));Switch control=new Switch(this);control.setContentDescription(title);control.setChecked(SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(key,false));control.setEnabled(ready());control.setOnCheckedChangeListener((v,checked)->save(e->e.putBoolean(key,checked)));row.addView(control);row.setOnClickListener(v->{if(control.isEnabled())control.toggle();});body.addView(row,rowParams);}
    private void choice(String key,String label,String[] labels,int[] values){int current=number(key,0),selected=0;for(int i=0;i<values.length;i++)if(values[i]==current)selected=i;Button b=UiKit.button(this,label+"  ·  "+labels[selected],false);b.setContentDescription(label);b.setEnabled(ready());final int initial=selected;b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(labels,initial,(d,index)->{d.dismiss();save(e->e.putInt(key,values[index]));}).setNegativeButton("取消",null).show());body.addView(b);}
    private void bitrate(){int value=number(Config.CAST_BITRATE,-1);Button b=UiKit.button(this,"自定义码率  ·  "+(value<2?"关闭":value+" Mbps"),false);b.setEnabled(ready());b.setOnClickListener(v->{EditText input=new EditText(this);input.setSingleLine();input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setSelectAllOnFocus(true);input.setHint("2–40 Mbps；留空关闭");if(value>=2)input.setText(String.valueOf(value));AlertDialog d=new AlertDialog.Builder(this).setTitle("自定义码率范围").setMessage("设置后将最小与最大码率表设为同一个值，优先于档位；并非始终实际达到该速率。").setView(input).setNegativeButton("取消",null).setNeutralButton("关闭",(dialog,w)->save(e->e.remove(Config.CAST_BITRATE))).setPositiveButton("保存",null).create();d.setOnShowListener(dialog->{input.requestFocus();d.getButton(-1).setOnClickListener(view->{String s=input.getText().toString().trim();try{int n=s.isEmpty()?-1:Integer.parseInt(s);if(n!=-1&&(n<2||n>40))throw new NumberFormatException();d.dismiss();save(e->{if(n<2)e.remove(Config.CAST_BITRATE);else e.putInt(Config.CAST_BITRATE,n);});}catch(NumberFormatException x){input.setError("请输入 2–40，或留空关闭");}});});d.show();});body.addView(b);}
    private void save(Consumer<SharedPreferences.Editor> edit){saving=true;SettingsBridge.save(edit,ok->{saving=false;if(isFinishing()||isDestroyed())return;if(!ok)Toast.makeText(this,"保存失败，请检查框架连接",Toast.LENGTH_LONG).show();render();});}
}
