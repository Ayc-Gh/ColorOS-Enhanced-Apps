// SPDX-License-Identifier: AGPL-3.0-or-later
// Behavior researched from installed OShin 16.14.1912; independently adapted to PHY110.
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
    private final Map<View,Integer> hiddenViews=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Integer,Object> parked=new ConcurrentHashMap<>();
    private final ThreadLocal<Integer> minimizing=ThreadLocal.withInitial(()->0);
    private final ThreadLocal<ArrayDeque<Object[]>> launchContext=ThreadLocal.withInitial(ArrayDeque::new);
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{
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
        installCapture();installWindows();installSplit();installLaunch();installCaption();installEdge();
        log(Log.INFO,TAG,"Installed "+hooks+" hooks; device="+android.os.Build.MODEL+" API="+android.os.Build.VERSION.SDK_INT);
    }
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if(!"com.oplus.cast".equals(param.getPackageName())||!castInstalled.compareAndSet(false,true))return;
        casting=new CastHooks(this,param.getClassLoader());
        casting.install();
    }
    private Class<?> cls(String name){try{return Class.forName(name,false,loader);}catch(Throwable t){return null;}}
    private interface Installer { XposedInterface.Hooker create(Method m); }
    private void hook(String className,String name,java.util.function.Predicate<Method> shape,Installer factory) {
        Class<?> c=cls(className);int found=0;
        if(c!=null)for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&shape.test(m)) {
            try {m.setAccessible(true);hook(m).intercept(factory.create(m));hooks++;found++;info("hook "+m);}
            catch(Throwable t){log(Log.WARN,TAG,"Hook failed "+m,t);}
        }
        if(found==0)info("Unavailable: "+className+"#"+name);
    }
    private void hook(String c,String n,Installer f){hook(c,n,m->true,f);}
    private boolean bool(String k){try{return prefs!=null&&prefs.getBoolean(k,false);}catch(Throwable t){return false;}}
    private int number(String k){try{return prefs.getInt(k,-1);}catch(Throwable t){return -1;}}
    private float ratio(){try{return prefs.getFloat(Config.RATIO,-1f);}catch(Throwable t){return -1f;}}
    private int count(){int n=number(Config.COUNT);return n>=1?Math.min(n,Config.MAX_COUNT):-1;}
    private void info(String s){if(bool(Config.LOG))log(Log.INFO,TAG,s);}
    private void hit(String key){if(bool(Config.LOG)&&reported.add(key))log(Log.INFO,TAG,"Applied: "+key);}
    private void flag(String c,String n,String key,boolean value) {
        hook(c,n,m->m.getReturnType()==boolean.class,m->chain->{if(!bool(key))return chain.proceed();hit(n);return value;});
    }
    private void installCapture() {
        // Original OShin's live hook dump confirms WindowState#isSecureLocked().
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
        for(String n:List.of("isSupportMultiMode","isPsPlusEnable"))hook(u,n,m->m.getReturnType()==boolean.class,m->chain->{
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
            if(!bool(Config.JUMP)&&!bool(Config.NOTIFICATION)&&!bool(Config.LAUNCHER))return chain.proceed();
            Object target=chain.getArg(0),source=chain.getArg(3);Object task=source==null?null:Reflect.call(source,"getTask");
            ArrayDeque<Object[]> stack=launchContext.get();stack.push(new Object[]{target,source,task,Reflect.field(chain.getThisObject(),"mContext")});
            try {
                Object result=chain.proceed();
                if(!(result instanceof ActivityOptions options))return result;
                Object request=chain.getArg(5);String caller=(String)Reflect.field(request,"callingPackage");
                String targetPackage=(String)Reflect.field(target,"packageName");
                String sourcePackage=task==null?null:(String)Reflect.call(task,"getBasePackageName");
                boolean isHome=task!=null&&!standard(task);
                if(targetPackage!=null&&targetPackage.equals(sourcePackage)&&!isHome)return chain.getArg(1);
                boolean launch=bool(Config.LAUNCHER)&&(isHome||Set.of("com.android.launcher","com.android.launcher3","com.oplus.launcher").contains(caller==null?"":caller));
                boolean notification=bool(Config.NOTIFICATION)&&source==null&&"com.android.systemui".equals(caller);
                if(Integer.valueOf(100).equals(Reflect.call(options,"getLaunchWindowingMode"))&&(launch||notification)&&qualifiesLaunch(stack.peek())) {
                    Object ext=Reflect.field(options,"mActivityOptionsExt");
                    Reflect.call(ext,"setZoomLaunchFlags",launch?24:5);
                    Object extra=Reflect.call(options,"getExtraBundle");if(launch&&extra instanceof Bundle b)b.putBoolean("key_flexible_smart_across_task_jump",true);
                    hit(launch?"launcher floating":"notification floating");
                }
                return result;
            } finally {stack.pop();if(stack.isEmpty())launchContext.remove();}
        });
        hook(WM+"OplusSplitScreenAppReader","isSupportLightOpen",m->m.getReturnType()==boolean.class&&m.getParameterCount()==2,m->chain->{
            ArrayDeque<Object[]> stack=launchContext.get();
            if(stack.isEmpty()||!qualifiesLaunch(stack.peek()))return chain.proceed();
            String pkg=chain.getArg(1) instanceof String s?s:null;Context context=chain.getArg(0) instanceof Context c?c:null;
            if(pkg==null||context==null||!allowPackage(pkg,context))return chain.proceed();hit("light open "+pkg);return true;
        });
        hook(WM+"OplusSplitScreenAppReader","inInnerTaskJumpList",m->m.getReturnType()==boolean.class,m->chain->{if(!bool(Config.JUMP))return chain.proceed();return false;});
        hook(controller,"isFlexibleAcrossTaskJump",m->m.getReturnType()==boolean.class,m->chain->{
            Object result=chain.proceed();ArrayDeque<Object[]> stack=launchContext.get();
            if(!stack.isEmpty()&&qualifiesLaunch(stack.peek()))return true;return result;
        });
    }
    private boolean qualifiesLaunch(Object[] context){
        String pkg=(String)Reflect.field(context[0],"packageName");
        if(!(context[3] instanceof Context c)||pkg==null||!allowPackage(pkg,c))return false;
        if(bool(Config.JUMP))return true;Object source=context[1],task=context[2];
        return bool(Config.NOTIFICATION)&&source==null||bool(Config.LAUNCHER)&&task!=null&&!standard(task);
    }
    private boolean allowPackage(String pkg,Context context){
        if(Set.of("android","com.android.systemui","com.android.launcher","com.android.settings",getModuleApplicationInfo().packageName).contains(pkg))return false;
        if(bool(Config.EXCLUDE_SYSTEM))try{ApplicationInfo app=context.getPackageManager().getApplicationInfo(pkg,0);if((app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)return false;}catch(Throwable t){return false;}
        if(!bool(Config.FILTER))return true;
        Set<String> selected;try{selected=prefs.getStringSet(Config.APPS,Set.of());}catch(Throwable t){return false;}
        return bool(Config.BLACKLIST)?!selected.contains(pkg):selected.contains(pkg);
    }
    private void installCaption(){
        for(String[] item:new String[][]{{"FlexibleTaskCaptionView","initCaptionView"},{"FlexibleCaptionView","updateThreeButtonVisibility"}})hook(WM+item[0],item[1],m->chain->{Object r=chain.proceed();applyCaption(chain.getThisObject());return r;});
    }
    private void applyCaption(Object owner){
        for(String[] entry:new String[][]{{"mBottomHandleView",Config.HIDE_BOTTOM},{"mControlView",Config.HIDE_TOP},{"mControlBackgroundView",Config.HIDE_TOP}}) {
            Object v=Reflect.field(owner,entry[0]);if(!(v instanceof View view))continue;
            if(bool(entry[1])){synchronized(hiddenViews){hiddenViews.putIfAbsent(view,view.getVisibility());}view.setVisibility(View.INVISIBLE);hit(entry[0]);}
        }
    }
    private void restoreViews(){synchronized(hiddenViews){hiddenViews.forEach((view,visibility)->view.post(()->view.setVisibility(visibility)));hiddenViews.clear();}}
    private void installEdge(){
        String handle=WM+"FloatHandleController",controller=WM+"FlexibleTaskController";
        // OShin's spelling includes the historical OEM typo "Alloaw".
        hook(handle,"isAlloawLockFloatPhone",m->m.getReturnType()==boolean.class,m->chain->{if(bool(Config.FOREGROUND))return true;return chain.proceed();});
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
