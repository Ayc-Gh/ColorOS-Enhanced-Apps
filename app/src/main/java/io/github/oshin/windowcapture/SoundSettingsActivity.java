// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.widget.*;

public final class SoundSettingsActivity extends Activity {
    private boolean saving;
    private TextView runtime;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){if(runtime!=null)runtime.setText(SoundController.state(SoundSettingsActivity.this));handler.postDelayed(this,2000);}};
    private final Runnable observer=()->{if(!saving&&!isFinishing()&&!isDestroyed())render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override protected void onResume(){super.onResume();handler.post(poll);}
    @Override protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int p=UiKit.dp(this,20);body.setPadding(p,UiKit.dp(this,16),p,p);scroll.addView(body);
        UiKit.header(this,body,"声音分轨","扩展原生声音分轨的应用范围");body.addView(UiKit.status(this,SettingsBridge.status));
        body.addView(UiKit.section(this,"应用支持"));
        body.addView(UiKit.toggle(this,"解除声音分轨应用限制","允许更多应用使用系统原生人声与伴奏调节，需要本应用的 Root 权限。",SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(SoundPolicy.KEY,false),SettingsBridge.prefs!=null&&!saving,on->{saving=true;SettingsBridge.save(e->e.putBoolean(SoundPolicy.KEY,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查配置连接",Toast.LENGTH_SHORT).show();else if(!on)SoundController.stop(this);if(!isFinishing()&&!isDestroyed())render();});}));
        body.addView(UiKit.section(this,"运行状态"));runtime=UiKit.note(this,SoundController.state(this));body.addView(runtime);
        if(!SettingsBridge.scope.contains(SoundPolicy.HOST)&&!SettingsBridge.scope.contains("com.oplus.atlas"))body.addView(UiKit.note(this,"首次使用请在 LSPosed 勾选“音效”和“Atlas 服务”，然后重启手机。"));
        body.addView(UiKit.section(this,"系统声音设置"));Button button=UiKit.button(this,"打开系统声音设置",false);button.setOnClickListener(v->{try{startActivity(new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS));}catch(Exception e){Toast.makeText(this,"系统没有提供此入口",Toast.LENGTH_SHORT).show();}});body.addView(button);
        body.addView(UiKit.section(this,"使用与恢复"));body.addView(UiKit.note(this,"生效后重新打开系统声音分轨面板。分轨效果和可用输出设备仍由系统判断；原生空间音效冲突检查保留。"));
        body.addView(UiKit.note(this,"关闭开关会恢复原生应用范围，并停止新增应用的分轨处理。停用模块后重启声音组件或手机即可恢复；不会修改系统音频配置文件。"));
        setContentView(scroll);UiKit.applyBars(this,scroll);
    }
}
