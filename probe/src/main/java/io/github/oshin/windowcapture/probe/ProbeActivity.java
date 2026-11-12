package io.github.oshin.windowcapture.probe;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
public class ProbeActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());private int ticks;private boolean resumed;
 private final Runnable tick=new Runnable(){public void run(){ticks++;try(FileOutputStream out=openFileOutput(getComponentName().getShortClassName()+".txt",MODE_PRIVATE)){out.write(("task="+getTaskId()+" ticks="+ticks+" resumed="+resumed+" secure="+((getWindow().getAttributes().flags&8192)!=0)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(IOException ignored){}getWindow().getDecorView().invalidate();handler.postDelayed(this,500);}};
 @Override public void onCreate(Bundle state){super.onCreate(state);if(getIntent().getBooleanExtra("secure",true))getWindow().addFlags(8192);setContentView(new View(this){final Paint p=new Paint(3);protected void onDraw(Canvas c){c.drawColor(0xffe8f0fe);p.setColor(0xff00c87a);c.drawRect(getWidth()*.12f,getHeight()*.3f,getWidth()*.46f,getHeight()*.65f,p);p.setColor(0xffff5a42);c.drawRect(getWidth()*.54f,getHeight()*.3f,getWidth()*.88f,getHeight()*.65f,p);p.setColor(0xff1a2539);p.setTextSize(24*getResources().getDisplayMetrics().density);c.drawText("FLAG_SECURE TEST",getWidth()*.08f,getHeight()*.2f,p);p.setTextSize(16*getResources().getDisplayMetrics().density);c.drawText(getComponentName().getShortClassName()+"  task="+getTaskId(),getWidth()*.08f,getHeight()*.76f,p);c.drawText("ticks="+ticks+"  resumed="+resumed,getWidth()*.08f,getHeight()*.82f,p);}});handler.post(tick);}
 protected void onResume(){super.onResume();resumed=true;}
 protected void onPause(){resumed=false;super.onPause();}
 protected void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}
}
