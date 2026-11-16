// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;

/** Resolve appearance at the task getters used by native rendering, without changing caches. */
final class WindowAppearanceHooks {
    private static final String WM="com.android.server.wm.";
    private final XposedModule module;
    private final ClassLoader loader;
    private final SharedPreferences prefs;
    WindowAppearanceHooks(XposedModule module,ClassLoader loader,SharedPreferences prefs){this.module=module;this.loader=loader;this.prefs=prefs;}
    void install(){
        for(String[] item:new String[][]{{"Window.Corner","getCornerRadius",Config.CORNER},{"Window.ShadowFocused","getShadowRadiusFocused",Config.SHADOW_FOCUS},{"Window.ShadowUnfocused","getShadowRadiusUnfocused",Config.SHADOW_BLUR}}){
            String feature=item[0],getter=item[1],key=item[2];
            try{
                // Task caches stay native, so removing an override restores the same task.
                installGetter(feature,WM+"TaskExtImpl",getter,key);
            }finally{WindowDiagnostics.seal(feature);}
        }
        for(String owner:List.of("FlexibleWindowManagerService","FlexibleTaskController","TaskExtImpl"))try{
            for(Method method:Class.forName(WM+owner,false,loader).getDeclaredMethods())if(Set.of("prepareSurfaces","updateTaskSurface","resetTaskTransformWhenDestoryIfNeed","collectFlexibleTaskInfo","getTaskInfo","resetFlexibleTaskSurfaceTransform").contains(method.getName()))module.deoptimize(method);
        }catch(Throwable error){module.log(Log.WARN,"WindowCapture-Appearance","Native appearance caller deoptimization incomplete",error);}
    }
    private void installGetter(String feature,String owner,String name,String key){
        String path=owner+"#"+name;
        try{
            Class<?> extensionType=Class.forName(owner,false,loader);
            Method method=extensionType.getDeclaredMethod(name);
            if(method.getReturnType()!=int.class)throw new NoSuchMethodException(path);
            method.setAccessible(true);
            Field taskField=field(extensionType,"mTask"),scenario=field(extensionType,"mScenario"),embedded=field(extensionType,"mEmbedded"),caption=field(extensionType,"mHasCaption");
            Method activityType=taskField.getType().getMethod("getActivityType");activityType.setAccessible(true);
            module.hook(method).intercept(chain->{
                Object original=chain.proceed();
                if(!WindowDiagnostics.ready(feature)||!(original instanceof Integer nativeValue))return original;
                int requested;try{requested=prefs.getInt(key,-1);}catch(Throwable ignored){return original;}
                if(requested<0)return original;
                // Resolve members once at installation, not on every frame/getter call.
                try{
                    Object extension=chain.getThisObject(),task=taskField.get(extension);
                    if(task==null||!WindowAppearancePolicy.applies(scenario.getInt(extension),number(activityType.invoke(task)),embedded.getBoolean(extension),caption.getBoolean(extension)))return original;
                }catch(ReflectiveOperationException|IllegalArgumentException error){return original;}
                int value=WindowAppearancePolicy.radius(requested,nativeValue);
                WindowDiagnostics.value(feature,path,nativeValue,value);
                return value;
            });
            WindowDiagnostics.register(feature,path,true);
        }catch(Throwable error){WindowDiagnostics.register(feature,path,false);module.log(Log.WARN,"WindowCapture-Appearance","Appearance interface unavailable: "+path,error);}
    }
    private static Field field(Class<?> type,String name)throws NoSuchFieldException{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static int number(Object value){return value instanceof Integer n?n:-1;}
}
