// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

/** Unlocks the native system network options; app selection remains in ColorOS. */
public final class NetworkSettingsActivity extends Activity {
    private boolean saving;
    private ScrollView scroll;
    private final Runnable observer=()->{if(!saving&&!isFinishing()&&!isDestroyed())render();};

    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}

    private void render(){
        int y=scroll==null?0:scroll.getScrollY();scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int p=UiKit.dp(this,20);body.setPadding(p,UiKit.dp(this,16),p,p);scroll.addView(body);
        UiKit.header(this,body,"联网管理","显示系统应用的 WLAN 与移动数据限制选项");body.addView(UiKit.status(this,SettingsBridge.status));
        body.addView(UiKit.section(this,"设置解锁"));
        body.addView(UiKit.toggle(this,"显示全部应用的联网选项","补齐系统隐藏的可管理应用与 WLAN／移动数据开关。关闭后重新打开系统页面恢复原生显示。",SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(NetworkPolicy.KEY,false),SettingsBridge.prefs!=null&&!saving,on->{saving=true;SettingsBridge.save(e->e.putBoolean(NetworkPolicy.KEY,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查配置连接",Toast.LENGTH_SHORT).show();if(!isFinishing()&&!isDestroyed())render();});}));
        if(!SettingsBridge.scope.contains(NetworkPolicy.HOST))body.addView(UiKit.note(this,"请在 LSPosed 为本模块勾选“流量管理”，更新后重启手机一次。"));
        body.addView(UiKit.section(this,"系统设置"));
        Button nativePage=UiKit.button(this,"打开系统流量管理",false);nativePage.setOnClickListener(v->{try{startActivity(new Intent("android.settings.DATA_USAGE_SETTINGS").setPackage(NetworkPolicy.HOST));}catch(Exception e){Toast.makeText(this,"当前系统没有提供该入口",Toast.LENGTH_SHORT).show();}});body.addView(nativePage);
        Button runtime=UiKit.button(this,"查看功能运行状态",false);runtime.setOnClickListener(v->startActivity(new Intent(this,com.op.aod.enhance.ui.RuntimeStatusActivity.class).putExtra("section","network")));body.addView(runtime);
        body.addView(UiKit.section(this,"生效与恢复"));body.addView(UiKit.note(this,"修改开关后退出并重新进入系统联网设置，列表会重新加载。模块只解锁选项，实际联网规则仍由系统保存和执行；关闭模块开关不会清除你已经设置的禁用规则。"));
        body.addView(UiKit.note(this,"仅显示具有联网权限、使用普通应用 UID 的应用；共享 UID 的应用共同使用一份网络规则。Android 核心身份、无联网权限应用与企业管理限制保留原生处理。"));
        setContentView(scroll);UiKit.applyBars(this,scroll);ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
    }
}
