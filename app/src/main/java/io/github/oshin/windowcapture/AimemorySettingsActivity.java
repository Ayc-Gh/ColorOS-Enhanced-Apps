// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

/** Settings for the native Xiaobu Memory auto-billing pipeline. */
public final class AimemorySettingsActivity extends Activity {
    private ScrollView scroll;
    private boolean saving;
    private final Runnable observer=()->{if(!saving&&!isFinishing()&&!isDestroyed())render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private int dp(int value){return UiKit.dp(this,value);}
    private void render(){
        int y=scroll==null?0:scroll.getScrollY();scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(body);
        UiKit.header(this,body,"小布记忆","恢复微信自动记账的系统入口");
        body.addView(UiKit.status(this,SettingsBridge.status));
        body.addView(UiKit.section(this,"自动记账"));
        body.addView(UiKit.toggle(this,"恢复微信自动记账支持","补齐缺失的微信配置；还需在系统“自动记账”中开启微信，识别结果由系统原生引擎处理。",SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(Config.AIMEMORY_WECHAT_BILL,false),SettingsBridge.prefs!=null&&!saving,on->{saving=true;SettingsBridge.save(e->e.putBoolean(Config.AIMEMORY_WECHAT_BILL,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查框架连接",Toast.LENGTH_SHORT).show();if(!isFinishing()&&!isDestroyed())render();});}));
        if(!SettingsBridge.scope.contains(AimemoryPolicy.HOST))body.addView(UiKit.note(this,"请在 LSPosed 为本模块勾选“小布记忆（com.oplus.gleanerservice）”，更新后重启手机一次。"));
        body.addView(UiKit.section(this,"系统设置"));
        body.addView(UiKit.note(this,"从桌面打开小布记忆，进入设置 → 自动记账，开启系统总开关和微信。"));
        android.widget.Button runtime=UiKit.button(this,"查看功能运行状态",false);runtime.setOnClickListener(v->startActivity(new Intent(this,com.op.aod.enhance.ui.RuntimeStatusActivity.class).putExtra("section","memory")));body.addView(runtime);
        body.addView(UiKit.section(this,"说明"));
        body.addView(UiKit.note(this,"修改后退出并重新进入系统自动记账设置；若列表未更新，重启手机后再进入。关闭后恢复原生列表，不会删除系统已保存的账单或应用开关。"));
        body.addView(UiKit.note(this,"账单详情沿用原生网页识别，轻应用支付结果页使用系统自带的文字识别。成功页需停留片刻；是否生成账单以系统实际保存结果为准。"));
        setContentView(scroll);UiKit.applyBars(this,scroll);ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
    }
}
