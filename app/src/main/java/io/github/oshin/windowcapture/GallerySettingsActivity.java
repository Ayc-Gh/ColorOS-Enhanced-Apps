// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

public final class GallerySettingsActivity extends Activity {
    private boolean saving;
    private ScrollView scroll;
    private final Runnable observer=()->{if(!saving&&!isFinishing()&&!isDestroyed())render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);SettingsBridge.observe(observer);render();}
    @Override public void onDestroy(){SettingsBridge.unobserve(observer);super.onDestroy();}
    private void render(){
        int y=scroll==null?0:scroll.getScrollY();scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int p=UiKit.dp(this,20);body.setPadding(p,UiKit.dp(this,16),p,p);scroll.addView(body);
        UiKit.header(this,body,"相机与相册","使用原生拍摄、AI 修图与保存流程");body.addView(UiKit.status(this,SettingsBridge.status));
        body.addView(UiKit.section(this,"AI 修图师"));
        body.addView(UiKit.toggle(this,"解锁 AI 修图师","在照片编辑的 AI 功能中显示 AI 修图师。系统已经支持时保持原生处理。",SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(GalleryPolicy.KEY,false),SettingsBridge.prefs!=null&&!saving,on->{saving=true;SettingsBridge.save(e->e.putBoolean(GalleryPolicy.KEY,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查配置连接",Toast.LENGTH_SHORT).show();if(!isFinishing()&&!isDestroyed())render();});}));
        body.addView(UiKit.section(this,"云端灵感推荐"));
        body.addView(UiKit.toggle(this,"解锁相机灵感推荐","恢复相机的云端灵感推荐入口；推荐图片、联网请求和参考／跟拍引导由相机原生流程处理。",SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(CameraInspirationPolicy.KEY,false),SettingsBridge.prefs!=null&&!saving,on->{saving=true;SettingsBridge.save(e->e.putBoolean(CameraInspirationPolicy.KEY,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查配置连接",Toast.LENGTH_SHORT).show();if(!isFinishing()&&!isDestroyed())render();});}));
        body.addView(UiKit.section(this,"专业视频"));
        body.addView(UiKit.toggle(this,"解锁 Log 视频","在“更多 → 专业视频 → 格式”中增加 Log。沿用相机原生录制与色彩处理，不改变曲线版本。",SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(Config.CAMERA_LOG,false),SettingsBridge.prefs!=null&&!saving,on->{saving=true;SettingsBridge.save(e->e.putBoolean(Config.CAMERA_LOG,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查配置连接",Toast.LENGTH_SHORT).show();if(!isFinishing()&&!isDestroyed())render();});}));
        if(!SettingsBridge.scope.contains(GalleryPolicy.HOST))body.addView(UiKit.note(this,"请在 LSPosed 为本模块勾选“相册”，然后重新打开相册。"));
        if(!SettingsBridge.scope.contains(CameraInspirationPolicy.HOST))body.addView(UiKit.note(this,"相机增强需要在 LSPosed 勾选“相机”。"));
        body.addView(UiKit.section(this,"系统相册"));
        Button gallery=UiKit.button(this,"打开相册",false);gallery.setOnClickListener(v->{try{Intent intent=getPackageManager().getLaunchIntentForPackage(GalleryPolicy.HOST);if(intent==null)throw new IllegalStateException();startActivity(intent);}catch(Exception error){Toast.makeText(this,"当前系统没有提供相册入口",Toast.LENGTH_SHORT).show();}});body.addView(gallery);
        Button camera=UiKit.button(this,"打开相机灵感推荐",false);camera.setOnClickListener(v->{try{Intent intent=getPackageManager().getLaunchIntentForPackage(CameraInspirationPolicy.HOST);if(intent==null)throw new IllegalStateException();startActivity(intent);}catch(Exception error){Toast.makeText(this,"当前系统没有提供相机入口",Toast.LENGTH_SHORT).show();}});body.addView(camera);
        Button status=UiKit.button(this,"查看功能运行状态",false);status.setOnClickListener(v->startActivity(new Intent(this,com.op.aod.enhance.ui.RuntimeStatusActivity.class).putExtra("section","gallery")));body.addView(status);
        body.addView(UiKit.section(this,"使用与恢复"));
        body.addView(UiKit.note(this,"修改开关后退出照片编辑并重新进入；首次安装适配后需重新打开相册。关闭后恢复原生入口，已经保存的图片保留。"));
        body.addView(UiKit.note(this,"进入相册选择照片，点击“编辑 → AI → AI 修图师”。按页面提示输入修图要求；联网授权、账号、服务额度及图片条件仍由系统检查。"));
        body.addView(UiKit.note(this,"灵感推荐使用相机的云端入口，不等同于本地实时灵感构图。成功列表和推荐图已在本机验证；并非所有场景都能返回推荐。"));
        body.addView(UiKit.note(this,"相机开关在相机进程启动时读取。修改后请重启相机进程或手机；关闭后会恢复原生功能表。Log 选择只在当前相机进程有效，重启后需重新选择。RAW、RAW MAX、专业视频与长曝光已由本机原生提供。"));
        setContentView(scroll);UiKit.applyBars(this,scroll);ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
    }
}
