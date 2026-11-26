// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
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
        body.addView(UiKit.section(this,"相机滤镜"));
        body.addView(UiKit.toggle(this,"补齐相机滤镜","补齐和光、浓郁、反差黑白，以及照片模式中缺失的原生风格。修改后重启相机进程生效。",SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(Config.CAMERA_FILTERS,false),SettingsBridge.prefs!=null&&!saving,on->{saving=true;SettingsBridge.save(e->e.putBoolean(Config.CAMERA_FILTERS,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查配置连接",Toast.LENGTH_SHORT).show();if(!isFinishing()&&!isDestroyed())render();});}));
        body.addView(UiKit.section(this,"专业视频"));
        body.addView(UiKit.toggle(this,"解锁 Log 视频","在“更多 → 专业视频 → 格式”中增加 Log。沿用相机原生录制与色彩处理，不改变曲线版本。",SettingsBridge.prefs!=null&&SettingsBridge.prefs.getBoolean(Config.CAMERA_LOG,false),SettingsBridge.prefs!=null&&!saving,on->{saving=true;SettingsBridge.save(e->e.putBoolean(Config.CAMERA_LOG,on),ok->{saving=false;if(!ok)Toast.makeText(this,"保存失败，请检查配置连接",Toast.LENGTH_SHORT).show();if(!isFinishing()&&!isDestroyed())render();});}));
        addX7uCameraSettings(body);
        if(!SettingsBridge.scope.contains(GalleryPolicy.HOST))body.addView(UiKit.note(this,"请在 LSPosed 为本模块勾选“相册”，然后重新打开相册。"));
        if(!SettingsBridge.scope.contains(CameraInspirationPolicy.HOST))body.addView(UiKit.note(this,"相机增强需要在 LSPosed 勾选“相机”。"));
        body.addView(UiKit.section(this,"打开系统应用"));
        Button gallery=UiKit.button(this,"打开相册",false);gallery.setOnClickListener(v->{try{Intent intent=getPackageManager().getLaunchIntentForPackage(GalleryPolicy.HOST);if(intent==null)throw new IllegalStateException();startActivity(intent);}catch(Exception error){Toast.makeText(this,"当前系统没有提供相册入口",Toast.LENGTH_SHORT).show();}});body.addView(gallery);
        Button camera=UiKit.button(this,"打开系统相机",false);camera.setOnClickListener(v->{try{Intent intent=getPackageManager().getLaunchIntentForPackage(CameraInspirationPolicy.HOST);if(intent==null)throw new IllegalStateException();startActivity(intent);}catch(Exception error){Toast.makeText(this,"当前系统没有提供相机入口",Toast.LENGTH_SHORT).show();}});body.addView(camera);
        Button status=UiKit.button(this,"查看功能运行状态",false);status.setOnClickListener(v->startActivity(new Intent(this,com.op.aod.enhance.ui.RuntimeStatusActivity.class).putExtra("section","gallery")));body.addView(status);
        body.addView(UiKit.section(this,"使用与恢复"));
        body.addView(UiKit.note(this,"修改开关后退出照片编辑并重新进入；首次安装适配后需重新打开相册。关闭后恢复原生入口，已经保存的图片保留。"));
        body.addView(UiKit.note(this,"进入相册选择照片，点击“编辑 → AI → AI 修图师”。按页面提示输入修图要求；联网授权、账号、服务额度及图片条件仍由系统检查。"));
        body.addView(UiKit.note(this,"灵感推荐使用相机的云端入口，不等同于本地实时灵感构图。成功列表和推荐图已在本机验证；并非所有场景都能返回推荐。"));
        body.addView(UiKit.note(this,"修改相机开关后，请强行停止并重新打开相机。首次升级后若运行状态仍显示旧版，请重启手机。Log 格式只在当前相机会话生效，重启相机后需重新选择。RAW、RAW MAX、专业视频与长曝光已由本机原生提供。"));
        setContentView(scroll);UiKit.applyBars(this,scroll);ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
    }

    private void addX7uCameraSettings(LinearLayout body) {
        SharedPreferences prefs = SettingsBridge.prefs;
        boolean requested = prefs != null && prefs.getBoolean(Config.CAMERA_X7U_ENHANCEMENTS, false);
        boolean supported = supportsX7uCameraSettings();
        body.addView(UiKit.section(this, "X7 Ultra 相机增强"));
        body.addView(UiKit.toggle(this, "启用 X7 Ultra 相机增强",
                "补齐大师胶片、夜景 28/35 mm、专业视频 24 帧、片门全开、LUT 与波形监看、4K 延时、视频高码率、录像中心指示和全局曝光。",
                requested, prefs != null && !saving && (supported || requested), enabled -> {
                    saving = true;
                    SettingsBridge.save(editor -> editor.putBoolean(Config.CAMERA_X7U_ENHANCEMENTS, enabled), saved -> {
                        saving = false;
                        if (!saved) Toast.makeText(this, "保存失败，请检查配置连接", Toast.LENGTH_SHORT).show();
                        if (!isFinishing() && !isDestroyed()) render();
                    });
                }));
        if (supported) {
            body.addView(UiKit.note(this, "迁移前先在 LSPosed 停用独立的「X7 Ultra 相机增强」，再开启本开关。强行停止并重新打开相机后，可在下方查看功能运行状态。"));
            body.addView(UiKit.note(this, "专业视频的「片门全开」为 4:3（1920×1440），支持后摄 SDR 30 帧。切换到 Log 时会回到 1080p。"));
            body.addView(UiKit.note(this, "使用自定义 LUT 时，同时开启上方的「解锁 Log 视频」。在专业视频的后摄 1080p、30 帧 Log 中导入 3D .cube，边长 2～65、输入范围 0～1、单文件最多 16 MiB。重启相机后需重新导入。"));
            body.addView(UiKit.note(this, "在 1080p、30 帧 Log 下，使用 LUT 仅监看时仍录制原生 10 位 Log。GPU 录制效果直出会将 LUT 写入成片，后置主摄实测输出 8 位 HEVC。"));
        } else {
            body.addView(UiKit.note(this, "适配范围为 PHY110_17.0.0.100(CN01) 和相机 7.006.191。当前系统或相机版本超出适配范围。"));
        }
    }

    private boolean supportsX7uCameraSettings() {
        if (!CompatibilityPolicy.rom(Build.MODEL, Build.VERSION.SDK_INT, Build.DISPLAY)) return false;
        String host = CameraInspirationPolicy.HOST;
        try {
            var info = getPackageManager().getPackageInfo(host, PackageManager.PackageInfoFlags.of(0));
            return CompatibilityPolicy.host(host, info.getLongVersionCode()) && "7.006.191".equals(info.versionName);
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            return false;
        }
    }
}
