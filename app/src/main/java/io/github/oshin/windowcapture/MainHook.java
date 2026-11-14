// SPDX-License-Identifier: AGPL-3.0-or-later
// Behavior independently adapted to PHY110 from the installed ColorOS implementation.
package io.github.oshin.windowcapture;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedInterface;

public final class MainHook extends XposedModule {
    private static final String TAG="WindowCapture", WM="com.android.server.wm.";
    private final AtomicBoolean installed=new AtomicBoolean();
    private final AtomicBoolean castInstalled=new AtomicBoolean();
    private CastHooks casting;
    private SharedPreferences prefs;
    private ClassLoader loader;
    private int hooks;
    private final Set<String> reported=ConcurrentHashMap.newKeySet();
    private final CaptionVisibilityTracker<View> hiddenViews=new CaptionVisibilityTracker<>();
    private final Map<Integer,Object> parked=new ConcurrentHashMap<>();
    private final EdgeCountPolicy edgeCountPolicy=new EdgeCountPolicy();
    private final ThreadLocal<Integer> minimizing=ThreadLocal.withInitial(()->0);
    private final ThreadLocal<ArrayDeque<LaunchContext>> launchContext=ThreadLocal.withInitial(ArrayDeque::new);
    private record SoundCaller(String packageName,int targetUid){}
    private final ThreadLocal<SoundCaller> soundCaller=new ThreadLocal<>();
    private String registeringFeature="Window.Framework";
    private final java.util.concurrent.atomic.AtomicInteger pendingLaunchChecks=new java.util.concurrent.atomic.AtomicInteger();
    private record LaunchContext(Object controller,Object target,Object source,Object task,Object existingTask,
                                 Context context,Object edge,LaunchPolicy.Kind kind) {}
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{
        WindowDiagnostics.configurationChanged(k);
        if(k==null||Config.HIDE_BOTTOM.equals(k)||Config.HIDE_TOP.equals(k))restoreViews();
        // Restore parked task surfaces before returning to the normal exit path.
        if((k==null||Config.FOREGROUND.equals(k))&&!bool(Config.FOREGROUND)) {
            new Handler(Looper.getMainLooper()).post(()->{
                for(Object task:new ArrayList<>(parked.values())) restoreTask(task);
            });
        }
    };
    @Override public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        if(!installed.compareAndSet(false,true))return;
        loader=param.getClassLoader();
        try {prefs=getRemotePreferences(Config.PREFS);prefs.registerOnSharedPreferenceChangeListener(listener);}
        catch(Throwable t){log(Log.ERROR,TAG,"Cannot read configuration; all features stay disabled",t);return;}
        boolean supported=CompatibilityPolicy.rom(android.os.Build.MODEL,android.os.Build.VERSION.SDK_INT,android.os.Build.DISPLAY);WindowDiagnostics.compatibility(supported);
        if(!supported){installDiagnostics();return;}
        installFeature("Window.Capture",this::installCapture);installFeature("Window.Layout",this::installWindows);
        installFeature("Window.Split",this::installSplit);installFeature("Window.Launch",this::installLaunch);
        installFeature("Window.Caption",this::installCaption);installFeature("Window.Edge",this::installEdge);
        installFeature("Sound.Startup",this::installSoundStartup);
        installDiagnostics();
        log(Log.INFO,TAG,"Installed "+hooks+" hooks; device="+android.os.Build.MODEL+" API="+android.os.Build.VERSION.SDK_INT);
    }
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if(!"com.oplus.cast".equals(param.getPackageName())||!castInstalled.compareAndSet(false,true))return;
        casting=new CastHooks(this,param.getClassLoader());
        casting.install();
    }
    private Class<?> cls(String name){try{return Class.forName(name,false,loader);}catch(Throwable t){return null;}}
    private interface Installer { XposedInterface.Hooker create(Method m); }
    private void installFeature(String feature,Runnable installer){registeringFeature=feature;try{installer.run();}catch(Throwable error){WindowDiagnostics.fail(feature);log(Log.WARN,TAG,"Feature disabled after incomplete installation: "+feature,error);}finally{WindowDiagnostics.seal(feature);}}
    private void hook(String className,String name,java.util.function.Predicate<Method> shape,Installer factory) {
        Class<?> c=cls(className);int found=0;String feature=registeringFeature,path=className+"#"+name;
        if(c!=null)for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&shape.test(m)) {
            try {m.setAccessible(true);XposedInterface.Hooker delegate=factory.create(m);hook(m).intercept(chain->{if(!WindowDiagnostics.ready(feature))return chain.proceed();WindowDiagnostics.trigger(feature);return delegate.intercept(chain);});hooks++;found++;WindowDiagnostics.register(feature,path,true);info("hook "+m);}
            catch(Throwable t){log(Log.WARN,TAG,"Hook failed "+m,t);}
        }
        if(found==0){WindowDiagnostics.register(feature,path,false);info("Unavailable: "+path);}
    }
    private void hook(String c,String n,Installer f){hook(c,n,m->true,f);}
    private boolean bool(String k){try{return prefs!=null&&prefs.getBoolean(k,false);}catch(Throwable t){return false;}}
    private int number(String k){try{return prefs.getInt(k,-1);}catch(Throwable t){return -1;}}
    private float ratio(){try{return prefs.getFloat(Config.RATIO,-1f);}catch(Throwable t){return -1f;}}
    private int count(){int n=number(Config.COUNT);return n>=1?Math.min(n,Config.MAX_COUNT):-1;}
    private void info(String s){if(bool(Config.LOG))log(Log.INFO,TAG,s);}
    private void hit(String key){if(bool(Config.LOG)&&reported.add(key))log(Log.INFO,TAG,"Applied: "+key);}
    private void installSoundStartup(){
        String manager="com.android.server.am.OplusAppStartupManager";
        Class<?> owner=cls(manager);
        if(owner==null)throw new IllegalStateException("Startup manager unavailable");
        final Class<?> strategy;
        try{strategy=owner.getDeclaredField("mOplusStartupStrategy").getType();}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        hook(manager,"isAllowStartFromProvider",m->m.getReturnType()==boolean.class&&Arrays.equals(
                Arrays.stream(m.getParameterTypes()).map(Class::getName).toArray(String[]::new),
                new String[]{"com.android.server.am.ProcessRecord","com.android.server.am.ContentProviderRecord","android.content.pm.ApplicationInfo","java.lang.String","int"}),m->chain->{
            SoundCaller previous=soundCaller.get(),current=null;
            if(bool(SoundPolicy.KEY)){
                Object process=chain.getArg(0),record=chain.getArg(1),value=chain.getArg(2);
                Object name=Reflect.field(record,"name");
                if(value instanceof ApplicationInfo info&&name instanceof android.content.ComponentName component&&SoundStartupPolicy.APP.equals(component.getPackageName())){
                    String caller=(String)chain.getArg(3);
                    if(SoundStartupPolicy.caller(true,(Integer)chain.getArg(4),Reflect.integer(process,"uid",-1),
                            (String)Reflect.field(process,"processName"),caller,info.packageName,component.getClassName(),info.uid,
                            (info.flags&(ApplicationInfo.FLAG_STOPPED|ApplicationInfo.FLAG_SUSPENDED))!=0))current=new SoundCaller(caller,info.uid);
                }
            }
            if(current==null)soundCaller.remove();else soundCaller.set(current);
            try{return chain.proceed();}finally{if(previous==null)soundCaller.remove();else soundCaller.set(previous);}
        });
        // Override only the final association list decision. The native provider
        // path still applies thermal, force-stop, suspension and enterprise checks.
        hook(strategy.getName(),"isAllowAssociateByList",m->m.getReturnType()==boolean.class&&Arrays.equals(
                m.getParameterTypes(),new Class<?>[]{int.class,String.class,String.class,String.class,String.class,String.class,int.class,int.class,int.class,int.class,int.class}),m->chain->{
            Object nativeValue=chain.proceed();SoundCaller caller=soundCaller.get();
            if(Boolean.FALSE.equals(nativeValue)&&caller!=null&&SoundStartupPolicy.association(bool(SoundPolicy.KEY),
                    (Integer)chain.getArg(0),(String)chain.getArg(1),(String)chain.getArg(2),(String)chain.getArg(3),
                    (String)chain.getArg(5),(Integer)chain.getArg(8),(Integer)chain.getArg(10),caller.packageName(),caller.targetUid())){
                hit("sound provider association");return true;
            }
            return nativeValue;
        });
        for(Method method:owner.getDeclaredMethods())if(Set.of("shouldPreventStartProvider","isAllowStartFromProvider").contains(method.getName()))deoptimize(method);
        for(Method method:strategy.getDeclaredMethods())if(method.getName().equals("isAllowAssociateByList"))deoptimize(method);
    }
    private void flag(String c,String n,String key,boolean value) {
        hook(c,n,m->m.getReturnType()==boolean.class,m->chain->{if(!bool(key))return chain.proceed();hit(n);return value;});
    }
    private void installCapture() {
        // The live hook dump confirms WindowState#isSecureLocked().
        flag(WM+"WindowState","isSecureLocked",Config.CAPTURE,false);
        // OEM capture clients also inspect LayoutParams directly. Work on a private copy,
        // preserving all other window attributes and the caller's FLAG_SECURE request.
        for(String name:List.of("addWindow","relayoutWindow"))hook(WM+"WindowManagerService",name,m->chain->{
            if(!bool(Config.CAPTURE))return chain.proceed();
            Object[] args=chain.getArgs().toArray();boolean changed=false;
            for(int i=0;i<args.length;i++)if(args[i] instanceof WindowManager.LayoutParams p&&(p.flags&WindowManager.LayoutParams.FLAG_SECURE)!=0) {
                WindowManager.LayoutParams copy=new WindowManager.LayoutParams();copy.copyFrom(p);
                copy.flags&=~WindowManager.LayoutParams.FLAG_SECURE;args[i]=copy;changed=true;
            }
            if(changed){hit("strip FLAG_SECURE on "+name);return chain.proceed(args);}
            return chain.proceed();
        });
        for(String[] entry:new String[][]{{"WindowSurfaceController","setSecure"},{"WindowState","setSecureLocked"},{"Task","setSecure"}})
            hook(WM+entry[0],entry[1],m->m.getParameterCount()==1&&m.getParameterTypes()[0]==boolean.class,m->chain->{
                if(!bool(Config.CAPTURE))return chain.proceed();
                hit(entry[0]+"."+entry[1]);return chain.proceed(new Object[]{false});
            });
        // Defeat boot-image inlining at real callers instead of depending on debug builds.
        for(String c:List.of("WindowStateAnimator","WindowManagerService","WindowSurfaceController","TaskSnapshotController"))deopt(WM+c,
                "createSurfaceLocked","relayoutWindow","setSecure","createTaskSnapshot","isScreenCaptureAllowed");
    }
    private void installWindows() {
        String u=WM+"FlexibleWindowUtils", f=WM+"FlexibleWindowManagerService";
        for(String n:List.of("isUnSupportCallerFlexibleWindow","isInFlexibleWindowBlackList","isInMultiWindowFlexibleBlackList","isFlexibleTaskInPSBlackList","isDisableCustomZoomWindow"))flag(u,n,Config.UNLOCK,false);
        flag(u,"isSupportFlexibleWindow",Config.UNLOCK,true);
        hook(u,"getUnSupportRatiosInFlexibleTask",m->m.getReturnType()==String.class,m->chain->{if(!bool(Config.UNLOCK))return chain.proceed();return "";});
        flag(u,"isFlexibleTaskInSupportFreeRatiosList",Config.FREE,true);
        hook(u,"getUnSupportRatiosSetInFlexibleTask",m->Set.class.isAssignableFrom(m.getReturnType()),m->chain->{if(!bool(Config.FREE))return chain.proceed();return new HashSet<Float>();});
        for(String n:List.of("isSupportMultiMode"))hook(u,n,m->m.getReturnType()==boolean.class,m->chain->{
            if(bool(Config.MULTI)&&flexibleCaller()){hit(n);return true;}return chain.proceed();
        });
        hook(WM+"FlexibleTaskInfo","getRatio",m->m.getReturnType()==float.class&&m.getParameterCount()==0,m->chain->{
            float v=ratio();if(!Float.isFinite(v)||v<0.25f||v>3f)return chain.proceed();hit("custom ratio");return v;
        });
        hook(f,"getMaxWinNum",m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{int.class})&&m.getReturnType()==int.class,m->chain->{
            int n=count();if(n<1||((Integer)chain.getArg(0))!=1)return chain.proceed();hit("count "+n);return n;
        });
        hook(f,"getTasksNumber",m->m.getParameterCount()==0&&m.getReturnType()==int.class,m->chain->{Object r=chain.proceed();int n=count();return n<1?r:Math.max((Integer)r,n+3);});
        for(String n:List.of("getMaxWinNum","getMaxFlexibleTaskNum"))hook(u,n,m->m.getParameterCount()==0&&m.getReturnType()==int.class,m->chain->{
            int c=count();if(c<1)return chain.proceed();hit("panorama "+n+"="+c);return n.equals("getMaxFlexibleTaskNum")?c:Math.max((Integer)chain.proceed(),c+3);
        });
        for(String[] entry:new String[][]{{"getCornerRadius",Config.CORNER},{"getShadowRadiusFocused",Config.SHADOW_FOCUS},{"getShadowRadiusUnfocused",Config.SHADOW_BLUR}}) {
            hook(f,entry[0],m->m.getReturnType()==int.class,m->chain->{int v=number(entry[1]);if(v<0)return chain.proceed();return Math.min(v,300);});
        }
        hook(WM+"OplusFlexibleTaskLayoutPolicy","avoidOverlappingAreas",m->m.getParameterCount()==5,m->chain->{
            Object result=chain.proceed();if(count()<2)return result;
            try {cascade(chain.getThisObject(),chain.getArgs());}catch(Throwable t){info("cascade skipped: "+t);}
            return result;
        });
        deopt(WM+"FlexibleTaskController","updateTaskMaxNumWhenFocusChangedInPanoramic","adjustOptionsForSmartMultiWindow","isSupportMultiMode");
    }
    private boolean flexibleCaller(){
        boolean flexible=false;StackTraceElement[] frames=Thread.currentThread().getStackTrace();
        for(int i=0;i<Math.min(frames.length,18);i++) {
            String c=frames[i].getClassName();
            if(c.equals(WM+"OplusSplitScreenManagerService")||c.equals(WM+"TaskExtImpl"))return false;
            if(c.equals(WM+"FlexibleWindowManagerService")||c.equals(WM+"FlexibleTaskController")||c.equals(WM+"PhoneCanvasTaskLayoutPolicy"))flexible=true;
        }return flexible;
    }
    private void cascade(Object owner,List<Object> a) {
        if(!(a.get(0) instanceof Rect r)||!(a.get(1) instanceof List<?> occupied)||!(a.get(3) instanceof Float scale)||!Float.isFinite(scale)||scale<=0)return;
        if(!(Reflect.field(owner,"mWindowBounds") instanceof Rect screen)||r.isEmpty())return;
        Rect status=(Rect)Reflect.field(owner,"mStatusBarFrame"),nav=(Rect)Reflect.field(owner,"mNaviBarFrame");
        Context context=(Context)Reflect.field(owner,"mContext");float density=context==null?android.content.res.Resources.getSystem().getDisplayMetrics().density:context.getResources().getDisplayMetrics().density;
        int gap=Math.max(1,Math.round(32*density)),min=Math.max(screen.top,status==null?screen.top:status.bottom),bottom=screen.bottom;
        if(nav!=null&&!nav.isEmpty()&&nav.width()>screen.width()/2&&nav.bottom>=screen.bottom)bottom=Math.min(bottom,nav.top);
        int max=bottom-Math.round(r.height()*scale),top=r.top;
        for(int attempt=0;attempt<=occupied.size()&&top<=max;attempt++) {
            int next=top;for(Object item:occupied)if(item instanceof Rect other&&r.left<other.right&&r.left+Math.round(r.width()*scale)>other.left&&Math.abs((long)other.top-top)<gap)next=Math.max(next,other.top+gap);
            if(next==top)break;top=next;
        }
        if(top>=min&&top<=max)r.offsetTo(r.left,top);
    }
    private void installSplit() {
        // Restrict to standard app tasks. Keep home, recents, assistant and lockscreen native.
        for(String c:List.of("ActivityRecord","Task"))for(String method:List.of("supportsSplitScreenWindowingMode","supportsSplitScreenWindowingModeInDisplayArea","supportsSplitScreenWindowingModeInner"))
            hook(WM+c,method,m->m.getReturnType()==boolean.class,m->chain->{
                if(!bool(Config.SPLIT)||!standard(chain.getThisObject()))return chain.proceed();hit("split "+c);return true;
            });
        hook(WM+"OplusSplitScreenManagerService","supportsSplitScreenWindowingMode",m->m.getReturnType()==boolean.class,m->chain->{
            if(bool(Config.SPLIT)&&!chain.getArgs().isEmpty()&&standard(chain.getArg(0))){hit("split vendor task");return true;}return chain.proceed();
        });
        hook(WM+"OplusSplitScreenManagerService","supportsSplitScreenByVendorPolicy",m->m.getReturnType()==boolean.class,m->chain->{
            if(!bool(Config.SPLIT)||chain.getArgs().isEmpty())return chain.proceed();Object target=chain.getArg(0);
            if(standard(target)||target instanceof String pkg&&!Set.of("android","com.android.systemui","com.android.launcher").contains(pkg)){hit("split vendor policy");return true;}return chain.proceed();
        });
        hook(WM+"TaskDisplayArea","supportsNonResizableMultiWindow",m->m.getReturnType()==boolean.class,m->chain->{if(!bool(Config.SPLIT))return chain.proceed();hit("non-resizable split");return true;});
        hook(WM+"TaskDisplayArea","supportsActivityMinWidthHeightMultiWindow",m->m.getReturnType()==boolean.class,m->chain->{if(!bool(Config.SPLIT))return chain.proceed();return true;});
        deopt(WM+"ActivityRecord","supportsMultiWindowInDisplayArea","supportsSplitScreenWindowingMode");
        deopt(WM+"Task","supportsMultiWindowInDisplayArea","supportsSplitScreenWindowingMode");
    }
    private boolean standard(Object task){Object type=Reflect.call(task,"getActivityType");return type instanceof Integer&&((Integer)type)==1;}
    private void deopt(String c,String...names){Class<?> type=cls(c);if(type==null)return;Set<String> set=new HashSet<>(Arrays.asList(names));for(Method m:type.getDeclaredMethods())if(set.contains(m.getName()))try{deoptimize(m);}catch(Throwable ignored){}}
    private void installLaunch() {
        String controller=WM+"FlexibleTaskController";
        hook(controller,"adjustOptionsForSmartMultiWindow",m->m.getParameterCount()==6,m->chain->{
            if(!bool(Config.JUMP)&&!bool(Config.NOTIFICATION)&&!bool(Config.LAUNCHER)&&!bool(Config.REMEMBER))return chain.proceed();
            Object owner=chain.getThisObject(),target=chain.getArg(0),source=chain.getArg(3),request=chain.getArg(5);
            Object task=source==null?Reflect.call(owner,"getLaunchFromTask",target,source,request):Reflect.call(source,"getTask");
            Object value=Reflect.field(owner,"mContext");Context context=value instanceof Context c?c:null;
            String targetPackage=(String)Reflect.field(target,"packageName"),caller=launchCaller(owner,request,source);
            String sourcePackage=(String)Reflect.field(source,"packageName");
            if(source==null&&targetPackage!=null&&targetPackage.equals(caller))sourcePackage=(String)Reflect.call(task,"getBasePackageName");
            Object edge=null;
            if(bool(Config.REMEMBER)&&targetPackage!=null){
                Object handle=Reflect.call(cls(WM+"FloatHandleController"),"getInstance");
                edge=Reflect.call(handle,"getFloatInfoByPkgAndUid",targetPackage,Reflect.integer(target,"mUserId",0));
            }
            boolean home=Integer.valueOf(2).equals(Reflect.call(task,"getActivityType"));
            LaunchPolicy.Kind kind=targetPackage!=null&&context!=null&&allowPackage(targetPackage,context)?
                    LaunchPolicy.choose(bool(Config.JUMP),bool(Config.NOTIFICATION),bool(Config.LAUNCHER),bool(Config.REMEMBER),
                        new LaunchPolicy.Origin(caller,home,standard(task),source!=null,LaunchPolicy.sameAppNavigation(targetPackage,sourcePackage,caller,home),edge!=null)):
                    LaunchPolicy.Kind.NATIVE;
            LaunchContext current=new LaunchContext(owner,target,source,task,chain.getArg(4),context,edge,kind);
            ArrayDeque<LaunchContext> stack=launchContext.get();stack.push(current);
            try {
                Object result=chain.proceed();
                if(kind==LaunchPolicy.Kind.NATIVE)return result;
                ActivityOptions options=result instanceof ActivityOptions o?o:null;
                boolean alreadyFloating=Integer.valueOf(100).equals(Reflect.call(options,"getLaunchWindowingMode"));
                // The native smart-open path can return before its source-task check.
                // Only repair the requested desktop/edge path, preserving native locks.
                if(!alreadyFloating){
                    if(kind!=LaunchPolicy.Kind.LAUNCHER&&kind!=LaunchPolicy.Kind.REMEMBER)return result;
                    Object mode=Reflect.call(options,"getLaunchWindowingMode");
                    if(mode instanceof Integer n&&n!=0&&n!=1)return result;
                    if(!mayRepairLaunch(current,targetPackage))return result;
                    if(options==null)options=ActivityOptions.makeBasic();
                    Reflect.call(options,"setLaunchWindowingMode",100);
                    if(!Integer.valueOf(100).equals(Reflect.call(options,"getLaunchWindowingMode")))return result;
                }
                Object extra=Reflect.call(options,"getExtraBundle");Bundle bundle=extra instanceof Bundle b?b:new Bundle();
                if(!(extra instanceof Bundle))Reflect.call(options,"setExtraBundle",bundle);
                bundle.putInt("androidx.activity.LaunchScenario",1);bundle.putBoolean("androidx.activity.HasCaption",true);
                boolean across=kind==LaunchPolicy.Kind.JUMP||kind==LaunchPolicy.Kind.NOTIFICATION;
                bundle.putBoolean("key_flexible_smart_across_task_jump",across);
                if(!across)bundle.putBoolean("key_flexible_smart_inner_task_jump",false);
                Reflect.call(Reflect.field(options,"mActivityOptionsExt"),"setZoomLaunchFlags",kind==LaunchPolicy.Kind.NOTIFICATION?5:24);
                if(kind==LaunchPolicy.Kind.REMEMBER)options=restoreEdgeOptions(current,options);
                hit(kind.name().toLowerCase(java.util.Locale.ROOT)+" floating");observeLaunch(current);
                return options;
            } finally {stack.pop();if(stack.isEmpty())launchContext.remove();}
        });
        hook(WM+"OplusSplitScreenAppReader","isSupportLightOpen",m->m.getReturnType()==boolean.class&&m.getParameterCount()==2,m->chain->{
            ArrayDeque<LaunchContext> stack=launchContext.get();
            if(stack.isEmpty()||!qualifiesLaunch(stack.peek()))return chain.proceed();
            // The argument is the SOURCE package (often the desktop), not the target.
            hit("light open target policy");return true;
        });
        hook(WM+"OplusSplitScreenAppReader","inInnerTaskJumpList",m->m.getReturnType()==boolean.class,m->chain->{ArrayDeque<LaunchContext> stack=launchContext.get();if(stack.isEmpty()||stack.peek().kind()!=LaunchPolicy.Kind.JUMP)return chain.proceed();return false;});
        hook(controller,"isFlexibleAcrossTaskJump",m->m.getReturnType()==boolean.class,m->chain->{
            Object result=chain.proceed();ArrayDeque<LaunchContext> stack=launchContext.get();
            if(!stack.isEmpty()&&qualifiesLaunch(stack.peek()))return true;return result;
        });
        hook(controller,"getReusableOptionsForFreeForm",m->m.getReturnType()==ActivityOptions.class&&m.getParameterCount()==7,m->chain->{
            Object target=chain.getArg(0),task=chain.getArg(3),owner=chain.getThisObject();int id=Reflect.integer(task,"mTaskId",-1);
            if(bool(Config.REMEMBER)&&parked.containsKey(id)&&chain.getArg(2) instanceof ActivityOptions options){
                Object extra=Reflect.call(options,"getExtraBundle");
                if(extra instanceof Bundle bundle&&id==bundle.getInt("key_start_float_handle_view_task_id",-1)&&"coloros-enhance-reopen".equals(bundle.getString("key_start_float_handle_view_reason"))){
                    Object value=Reflect.field(owner,"mContext");String pkg=(String)Reflect.field(target,"packageName");
                    Object handle=Reflect.call(cls(WM+"FloatHandleController"),"getInstance"),edge=Reflect.call(handle,"getFloatHandleInfo",id);
                    if(value instanceof Context context&&edge!=null&&pkg!=null&&pkg.equals(Reflect.call(task,"getBasePackageName"))&&allowPackage(pkg,context)){
                        LaunchContext current=new LaunchContext(owner,target,null,null,task,context,edge,LaunchPolicy.Kind.REMEMBER);
                        if(mayRepairLaunch(current,pkg)){
                            // Parking retains the mini task. Restore the native saved
                            // state as well as bounds/scale before options are reused.
                            Object saved=Reflect.field(edge,"flexibleTaskInfo"),state=Reflect.call(saved,"getFlexibleState");
                            if(state instanceof Integer n&&(n==1||n==2))Reflect.call(Reflect.call(owner,"getFlexibleInfoManager",task),"setTaskState",n);
                            hit("keep parked window on reopen");return restoreEdgeOptions(current,options);
                        }
                    }
                }
            }
            return chain.proceed();
        });
    }
    private boolean qualifiesLaunch(LaunchContext context){return context.kind()!=LaunchPolicy.Kind.NATIVE;}
    private String launchCaller(Object owner,Object request,Object source){
        String caller=(String)Reflect.field(request,"callingPackage");
        if(source==null){
            // A PendingIntent keeps its creator as callingPackage. Its actual
            // sender identifies a notification click or a desktop shortcut.
            try{Object atms=Reflect.field(owner,"mAtms"),process=Reflect.call(atms,"getProcessController",Reflect.integer(request,"realCallingPid",-1),Reflect.integer(request,"realCallingUid",-1));
                Object value=Reflect.field(process,"mInfo");if(value instanceof ApplicationInfo info&&(LaunchPolicy.isLauncher(info.packageName)||"com.android.systemui".equals(info.packageName)))return info.packageName;
            }catch(Throwable ignored){/* Keep the native caller when its process is unavailable. */}
        }
        return caller;
    }
    private boolean mayRepairLaunch(LaunchContext context,String pkg){
        try{
            if(Boolean.TRUE.equals(Reflect.call(context.controller(),"isKeyGuardShowing")))return false;
            Object locked=Reflect.call(cls(WM+"FlexibleWindowUtils"),"isActivityLock",context.target());
            Object encrypted=Reflect.call(cls(WM+"FlexibleWindowUtils"),"isEncryptPkg",pkg,Reflect.integer(context.target(),"mUserId",0));
            return Boolean.FALSE.equals(locked)&&Boolean.FALSE.equals(encrypted);
        }catch(Throwable t){info("Keep native launch: "+t.getClass().getSimpleName());return false;}
    }
    private ActivityOptions restoreEdgeOptions(LaunchContext context,ActivityOptions options){
        try{
        Object saved=Reflect.field(context.edge(),"flexibleTaskInfo");
        // Use the OEM's float-open metadata, including MaintainTaskState. A task
        // retained in the foreground must not be mistaken for a fullscreen start.
        Object prepared=Reflect.call(cls(WM+"FlexibleWindowUtils"),"setFlexibleTaskOptions",options,saved);
        if(prepared instanceof ActivityOptions value)options=value;
        Object extra=Reflect.call(options,"getExtraBundle");Bundle bundle=extra instanceof Bundle b?b:new Bundle();
        if(!(extra instanceof Bundle))Reflect.call(options,"setExtraBundle",bundle);
        Reflect.call(Reflect.field(options,"mActivityOptionsExt"),"setZoomLaunchFlags",4);
        bundle.putBoolean("key_flexible_smart_across_task_jump",false);bundle.putBoolean("key_flexible_smart_inner_task_jump",false);
        bundle.putString("key_start_float_handle_view_reason","coloros-enhance-reopen");
        Object bounds=Reflect.call(saved,"getLaunchBounds");
        int id=Reflect.integer(context.edge(),"taskId",-1);if(id>=0)bundle.putInt("key_start_float_handle_view_task_id",id);
        if(bounds instanceof Rect rect&&!rect.isEmpty()){
            Rect copy=new Rect(rect);Reflect.call(options,"setLaunchBounds",copy);
            Object scale=Reflect.field(saved,"mScale");if(scale instanceof Float f&&Float.isFinite(f)&&f>0)bundle.putFloat("androidx.activity.LaunchScale",f);
            Object handle=Reflect.call(cls(WM+"FloatHandleController"),"getInstance");Reflect.call(handle,"fillFloatPositionRect",context.edge(),copy);
        }
        }catch(Throwable t){log(Log.WARN,TAG,"Saved edge position unavailable",t);}
        return options;
    }
    private void observeLaunch(LaunchContext context){
        if(pendingLaunchChecks.incrementAndGet()>8){pendingLaunchChecks.decrementAndGet();return;}
        long generation=WindowDiagnostics.generation();WindowDiagnostics.launch(context.kind().name(),"requested",generation);
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            try{Object atms=Reflect.field(context.controller(),"mAtms"),lock=Reflect.field(atms,"mGlobalLock");
                if(lock==null){WindowDiagnostics.launch(context.kind().name(),"unknown",generation);return;}
                synchronized(lock){Object task=Reflect.call(context.target(),"getTask");if(task==null)task=context.existingTask();
                    Object visible=Reflect.call(task,"isVisible"),floating=Reflect.call(context.controller(),"isFlexibleTaskAndHasCaption",task);
                    Object handle=Reflect.call(cls(WM+"FloatHandleController"),"getInstance"),edge=Reflect.call(handle,"isInFloatingList",Reflect.integer(task,"mTaskId",-1));
                    WindowDiagnostics.launch(context.kind().name(),Boolean.TRUE.equals(visible)?(Boolean.TRUE.equals(floating)&&Boolean.FALSE.equals(edge)?"floating":"not_observed"):"unknown",generation);
                }
            }catch(Throwable t){WindowDiagnostics.launch(context.kind().name(),"unknown",generation);}
            finally{pendingLaunchChecks.decrementAndGet();}
        },1200);
    }
    private void installDiagnostics(){
        registeringFeature="Window.Status";
        hook(WM+"WindowManagerService","systemReady",m->m.getParameterCount()==0,m->chain->{Object result=chain.proceed();try{Object value=Reflect.field(chain.getThisObject(),"mContext");if(value instanceof Context context)WindowDiagnostics.attach(context,prefs,getRemotePreferences("aod_runtime_status"));}catch(Throwable t){log(Log.WARN,TAG,"Runtime status receiver unavailable",t);}return result;});
        WindowDiagnostics.seal("Window.Status");
    }
    private boolean allowPackage(String pkg,Context context){
        if(Set.of("android","com.android.systemui","com.android.launcher","com.android.settings",getModuleApplicationInfo().packageName).contains(pkg))return false;
        if(bool(Config.EXCLUDE_SYSTEM))try{ApplicationInfo app=context.getPackageManager().getApplicationInfo(pkg,0);if((app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)return false;}catch(Throwable t){return false;}
        return true;
    }
    private void installCaption(){
        for(String[] item:new String[][]{{"FlexibleTaskCaptionView","initCaptionView"},{"FlexibleCaptionView","updateThreeButtonVisibility"}})hook(WM+item[0],item[1],m->chain->{Object r=chain.proceed();applyCaption(chain.getThisObject());return r;});
    }
    private void applyCaption(Object owner){
        for(String[] entry:new String[][]{{"mBottomHandleView",Config.HIDE_BOTTOM},{"mControlView",Config.HIDE_TOP},{"mControlBackgroundView",Config.HIDE_TOP}}) {
            Object v=Reflect.field(owner,entry[0]);if(!(v instanceof View view))continue;
            if(bool(entry[1])){hiddenViews.track(view,entry[1],view.getVisibility());view.setVisibility(View.INVISIBLE);hit(entry[0]);}
        }
    }
    private void restoreViews(){for(View view:hiddenViews.views())view.post(()->hiddenViews.reconcileOne(view,this::bool,View::setVisibility,View.INVISIBLE));}
    private void installEdge(){
        String handle=WM+"FloatHandleController",controller=WM+"FlexibleTaskController";
        hook(handle,"getLimitMaxFloatNum",m->m.getReturnType()==int.class&&m.getParameterCount()==0,m->chain->{
            int nativeLimit=(Integer)chain.proceed(),value=number(Config.EDGE_COUNT);
            if(!edgeCountPolicy.needsSize(value))return nativeLimit;
            Object size=Reflect.call(chain.getThisObject(),"getFloatInfoSize");
            if(!(size instanceof Integer currentSize))return nativeLimit;
            int limit=edgeCountPolicy.limit(value,nativeLimit,currentSize);
            if(limit!=nativeLimit)hit("edge count "+limit);
            return limit;
        });
        deopt(handle,"updateInfoMapForAdd");
        // PHY110 already grants the native floating-lock permission. Keep its
        // permission result; foreground retention is handled by task parking.
        hook(handle,"shouldForceLaunchIfTaskInFloatHandle",m->m.getReturnType()==boolean.class,m->chain->{
            Object nativeResult=chain.proceed();if(!bool(Config.REMEMBER)&&!bool(Config.FOREGROUND))return nativeResult;
            if(bool(Config.REMEMBER)){hit("remember edge");return false;}
            if(chain.getArgs().isEmpty()||!(chain.getArg(0) instanceof ActivityInfo ai))return nativeResult;
            Object manager=Reflect.call(cls(WM+"FlexibleWindowManagerService"),"getInstance",(Object)null);
            return Boolean.TRUE.equals(Reflect.call(manager,"isSpuerLockTask",ai.packageName))?true:nativeResult;
        });
        hook(controller,"exitFlexibleTask",m->Arrays.equals(Arrays.stream(m.getParameterTypes()).map(Class::getName).toArray(String[]::new),new String[]{WM+"Task","boolean","int","int"}),m->chain->{
            Object task=chain.getArg(0);int id=Reflect.integer(task,"mTaskId",-1),reason=(Integer)chain.getArg(2);
            if(parked.containsKey(id)&&reason!=16)restoreTask(task);
            if(bool(Config.FOREGROUND)&&reason==16&&id>=0) {
                Object manager=Reflect.call(cls(handle),"getInstance");
                if(Boolean.TRUE.equals(Reflect.call(manager,"isInFloatingList",id))&&parkTask(chain.getThisObject(),task)) {hit("park foreground "+id);return null;}
            }
            return chain.proceed();
        });
        hook(controller,"resetFlexibleTaskToFullScreen",m->m.getParameterCount()==1,m->chain->{Object task=chain.getArg(0);restoreTask(task);return chain.proceed();});
        hook(WM+"ExitFlexibleTaskToFloatStrategy","onMinimizeAnimationStartInner",m->chain->{
            int old=minimizing.get();minimizing.set(old+1);try{return chain.proceed();}finally{if(old==0)minimizing.remove();else minimizing.set(old);}
        });
        hook(WM+"TaskFragmentExtImpl","pauseInRecentsAnim",m->m.getReturnType()==void.class,m->chain->{if(bool(Config.FOREGROUND)&&minimizing.get()>0)return null;return chain.proceed();});
        for(String n:List.of("windowsAreFocusable","isFocusable"))hook(WM+"ActivityRecord",n,m->m.getReturnType()==boolean.class,m->chain->{
            Object result=chain.proceed();Object task=Reflect.call(chain.getThisObject(),"getTask");if(parked.containsKey(Reflect.integer(task,"mTaskId",-1)))return false;return result;
        });
        hook(handle,"removeFloatHandle",m->m.getParameterCount()==4,m->chain->{
            if(!chain.getArgs().isEmpty()&&chain.getArg(0) instanceof Integer id){Object task=parked.get(id);if(task!=null)restoreTask(task);}return chain.proceed();
        });
        hook(WM+"Task","removeImmediately",m->chain->{parked.remove(Reflect.integer(chain.getThisObject(),"mTaskId",-1));return chain.proceed();});
    }
    private boolean parkTask(Object controller,Object task){
        try {
            Object atms=Reflect.field(controller,"mAtms");Object lock=Reflect.call(atms,"getGlobalLock");if(lock==null)return false;
            synchronized(lock){
                Object surface=Reflect.call(task,"getSurfaceControl"),parent=Reflect.call(task,"getParentSurfaceControl"),transaction=Reflect.call(task,"getSyncTransaction");
                if(surface==null||parent==null||transaction==null||!Boolean.TRUE.equals(Reflect.call(surface,"isValid"))||!Boolean.TRUE.equals(Reflect.call(parent,"isValid")))return false;
                Reflect.call(transaction,"reparent",surface,parent);Reflect.call(transaction,"setVisibility",surface,false);Reflect.call(transaction,"apply");
                parked.put(Reflect.integer(task,"mTaskId",-1),task);Object wms=Reflect.field(atms,"mWindowManager");Reflect.call(wms,"updateFocusedWindowLocked",0,true);return true;
            }
        }catch(Throwable t){log(Log.WARN,TAG,"Cannot park task",t);return false;}
    }
    private void restoreTask(Object task){
        int id=Reflect.integer(task,"mTaskId",-1);if(!parked.containsKey(id))return;
        try {
            Object atms=Reflect.field(task,"mAtmService"),lock=Reflect.call(atms,"getGlobalLock");if(lock==null)lock=Reflect.field(atms,"mGlobalLock");if(lock==null)return;
            synchronized(lock){Object surface=Reflect.call(task,"getSurfaceControl"),parent=Reflect.call(task,"getParentSurfaceControl"),transaction=Reflect.call(task,"getSyncTransaction");
                if(surface!=null&&parent!=null&&transaction!=null&&Boolean.TRUE.equals(Reflect.call(surface,"isValid"))&&Boolean.TRUE.equals(Reflect.call(parent,"isValid"))){Reflect.call(transaction,"reparent",surface,parent);Reflect.call(transaction,"setVisibility",surface,true);Reflect.call(transaction,"apply");}
                parked.remove(id);Reflect.call(Reflect.field(atms,"mWindowManager"),"updateFocusedWindowLocked",0,true);
            }
        }catch(Throwable t){log(Log.WARN,TAG,"Cannot restore parked task "+id,t);}
    }
}
