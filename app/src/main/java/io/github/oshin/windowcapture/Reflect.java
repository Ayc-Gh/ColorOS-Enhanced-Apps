// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;
import java.lang.reflect.*;
final class Reflect {
    private Reflect(){}
    static Object field(Object target,String name) {
        if(target==null)return null;
        for(Class<?> c=target instanceof Class<?>?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target); }
            catch(ReflectiveOperationException ignored){}
        }
        return null;
    }
    static Object call(Object target,String name,Object...args) {
        if(target==null)return null;
        for(Class<?> c=target instanceof Class<?>?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass()) {
            for(Method m:c.getDeclaredMethods()) if(m.getName().equals(name)&&matches(m.getParameterTypes(),args)) {
                try { m.setAccessible(true);return m.invoke(target instanceof Class<?>?null:target,args); }
                catch(ReflectiveOperationException e){throw new IllegalStateException(name,e);}
            }
        }
        return null;
    }
    static boolean matches(Class<?>[] types,Object[] values) {
        if(types.length!=values.length)return false;
        for(int i=0;i<types.length;i++) {
            Class<?> t=types[i];Object v=values[i];
            if(v==null){if(t.isPrimitive())return false;continue;}
            if(t.isPrimitive()) t=t==int.class?Integer.class:t==boolean.class?Boolean.class:t==float.class?Float.class:t==long.class?Long.class:t==double.class?Double.class:t;
            if(!t.isInstance(v))return false;
        }
        return true;
    }
    static int integer(Object target,String field,int fallback) {Object v=field(target,field);return v instanceof Number?((Number)v).intValue():fallback;}
}
