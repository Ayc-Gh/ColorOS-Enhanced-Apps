import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import java.lang.reflect.*;
public final class DeviceProbe {
 public static void main(String[] args)throws Exception {
  if(args[0].equals("clean")) {
   Class<?> c=Class.forName("android.app.ActivityTaskManager");Object atm=c.getMethod("getService").invoke(null);
   Object slice=atm.getClass().getMethod("getRecentTasks",int.class,int.class,int.class).invoke(atm,100,0,0);
   java.util.List<?> tasks=(java.util.List<?>)slice.getClass().getMethod("getList").invoke(slice);
   for(Object info:tasks){Intent base=(Intent)info.getClass().getField("baseIntent").get(info);if(base.getComponent()!=null&&base.getComponent().getPackageName().equals("io.github.oshin.windowcapture.probe")){int id=info.getClass().getField("taskId").getInt(info);System.out.println("removed "+id+" "+atm.getClass().getMethod("removeTask",int.class).invoke(atm,id));}}return;
  }
  if(args[0].equals("float-task")) {
   Class<?> c=Class.forName("com.oplus.flexiblewindow.FlexibleWindowManager");Object instance=c.getMethod("getInstance").invoke(null);
   System.out.println(c.getMethod("startFlexibleWindowForRecentTasks",int.class,Rect.class).invoke(instance,Integer.parseInt(args[1]),new Rect(180,350,1100,1700)));return;
  }
  if(args[0].equals("flex")) {
   Class<?> c=Class.forName("com.oplus.flexiblewindow.FlexibleWindowManager");Object instance=c.getMethod("getInstance").invoke(null);
   Intent intent=new Intent().setClassName("io.github.oshin.windowcapture.probe","io.github.oshin.windowcapture.probe.Probe"+args[1]).addFlags(0x18000000).putExtra("secure",false);
   Bundle options=ActivityOptions.makeBasic().toBundle();options.putInt("androidx.activity.LaunchScenario",1);options.putBoolean("androidx.activity.LaunchEmbedded",true);options.putInt("android.activity.windowingMode",100);options.putBoolean("androidx.activity.HasCaption",true);options.putBoolean("key_flexible_not_need_animation",true);
   System.out.println(c.getMethod("startAnyActivity",Intent.class,Bundle.class).invoke(instance,intent,options));return;
  }
  Class<?> c=Class.forName("com.oplus.splitscreen.OplusSplitScreenManager");Object instance=c.getMethod("getInstance").invoke(null);
  if(args[0].equals("split")){System.out.println(c.getMethod("splitScreenForRecentTasks",int.class).invoke(instance,Integer.parseInt(args[1])));return;}
  if(args[0].equals("check")){Intent i=new Intent().setClassName("io.github.oshin.windowcapture.probe","io.github.oshin.windowcapture.probe.Probe"+args[1]);System.out.println(c.getMethod("getSplitScreenState",Intent.class).invoke(instance,i));return;}
  if(args[0].equals("dismiss")){System.out.println(c.getMethod("dismissSplitScreenMode",int.class).invoke(instance,0));return;}
 }
}
