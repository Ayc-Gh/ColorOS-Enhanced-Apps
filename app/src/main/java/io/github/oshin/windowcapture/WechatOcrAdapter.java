// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.*;
import java.lang.ref.WeakReference;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Binds verification to the originating native coroutine and its visible WeChat page. */
final class WechatOcrAdapter {
    private record Session(long generation,ComponentName component,boolean owned){}
    private record VerifiedReceipt(Session session,PaymentEvidence.Proof proof){}
    private record ObservedIdentity(WeakReference<Object> nativeIdentity,PaymentEvidence.Proof proof){}
    private final XposedModule module;
    private final ClassLoader loader;
    private final BooleanSupplier enabled;
    private final Predicate<Object> owned;
    private final AimemoryTrace trace;
    private final AtomicLong generation=new AtomicLong();
    private final ThreadLocal<Session> scope=new ThreadLocal<>();
    private final Map<Object,Session> coroutines=Collections.synchronizedMap(new WeakHashMap<>());
    private final ArrayDeque<ObservedIdentity> identities=new ArrayDeque<>();
    private volatile Session active;
    private volatile VerifiedReceipt receipt;
    private Method visible;
    private Constructor<?> failedResult;
    private Object failure;
    WechatOcrAdapter(XposedModule module,ClassLoader loader,BooleanSupplier enabled,Predicate<Object> owned,AimemoryTrace trace){this.module=module;this.loader=loader;this.enabled=enabled;this.owned=owned;this.trace=trace;}
    void invalidate(){generation.incrementAndGet();receipt=null;synchronized(identities){identities.clear();}}
    private PaymentEvidence.Proof exactProof(Object nativeIdentity){
        if(nativeIdentity!=null)for(ObservedIdentity item:identities)
            if(item.nativeIdentity().get()==nativeIdentity)return item.proof();
        return null;
    }
    private Class<?> cls(String name)throws ClassNotFoundException{return Class.forName(name,false,loader);}
    private static Object call(Object value,String method)throws ReflectiveOperationException{return value.getClass().getMethod(method).invoke(value);}
    private boolean current(Session session){
        if(session==null||!enabled.getAsBoolean()||session.generation()!=generation.get())return false;
        try{return session.component().equals(visible.invoke(null,session.component()));}catch(Exception ignored){return false;}
    }
    private boolean wechat(ComponentName component){return component!=null&&AimemoryPolicy.WECHAT.equals(component.getPackageName())&&AimemoryPolicy.paymentPage(component.getClassName());}
    void install()throws ReflectiveOperationException {
        String base="com.oplus.gleanerservice.feature.flashnotes.autonotes.parse.payment.";
        Class<?> config=cls("com.oplus.gleanerservice.config.AutoFlashNotes"),handler=cls(base+"PaymentHandler"),collector=cls(base+"PaymentPollingCollector"),coroutine=cls(base+"PaymentOcrExtractor$extract$2"),ocr=cls(base+"PaymentOcrExtractor"),identify=cls(base+"f");
        visible=cls("com.coloros.commons.utils.OplusActivityManagerUtils").getDeclaredMethod("a",ComponentName.class);visible.setAccessible(true);
        Class<?> status=cls(base+"OcrExtractStatus");
        @SuppressWarnings({"unchecked","rawtypes"}) Object fail=Enum.valueOf((Class<? extends Enum>)status,"BILL_EXTRACT_FAILED");failure=fail;
        failedResult=cls(base+"c").getConstructor(Map.class,status);
        Field component=collector.getDeclaredField("b");component.setAccessible(true);
        Method start=handler.getDeclaredMethod("k",Context.class,config),stop=handler.getDeclaredMethod("l",Context.class),parse=ocr.getDeclaredMethod("b",String.class);
        Method invoke=coroutine.getDeclaredMethod("invokeSuspend",Object.class);
        Constructor<?> ctor=Arrays.stream(coroutine.getDeclaredConstructors()).filter(c->c.getParameterCount()==2&&c.getParameterTypes()[0]==Context.class).findFirst().orElseThrow();ctor.setAccessible(true);
        Method compare=identify.getDeclaredMethod("c",identify);
        Class<?> info=cls(base+"PaymentInfoHandler");Method makeIdentity=info.getDeclaredMethod("h",Map.class,String.class);
        // Resolve every required member before installing behavior-changing hooks.
        for(Method method:new Method[]{start,stop,parse,invoke,compare,makeIdentity}){method.setAccessible(true);module.deoptimize(method);}
        for(Method method:ocr.getDeclaredMethods())if(method.getName().equals("a"))module.deoptimize(method);
        module.deoptimize(ctor);
        module.hook(start).intercept(chain->{Object rule=chain.getArg(1);String pkg=(String)call(rule,"getPackageName"),page=(String)call(rule,"getActivityName");long token=generation.incrementAndGet();receipt=null;active=AimemoryPolicy.WECHAT.equals(pkg)?new Session(token,new ComponentName(pkg,page),owned.test(rule)):null;return chain.proceed();});
        module.hook(stop).intercept(chain->{generation.incrementAndGet();active=null;receipt=null;return chain.proceed();});
        for(Method method:collector.getDeclaredMethods())if(method.getName().equals("k")&&method.getParameterCount()==1){
            module.deoptimize(method);module.hook(method).intercept(chain->{Session before=scope.get(),session=active;ComponentName target=(ComponentName)component.get(chain.getThisObject());
                if(session!=null&&wechat(target)&&session.component().equals(target)&&(enabled.getAsBoolean()||session.owned()))scope.set(session);else scope.remove();
                try{return chain.proceed();}finally{if(before==null)scope.remove();else scope.set(before);}
            });
        }
        module.hook(ctor).intercept(chain->{Object result=chain.proceed();Session session=scope.get();if(session!=null)coroutines.put(chain.getThisObject(),session);return result;});
        for(Method method:coroutine.getDeclaredMethods())if(method.getName().equals("create")&&method.getParameterCount()==2){
            module.deoptimize(method);
            module.hook(method).intercept(chain->{Session before=scope.get(),session=coroutines.get(chain.getThisObject());if(session!=null)scope.set(session);
                try{Object result=chain.proceed();if(session!=null)coroutines.put(result,session);return result;}finally{if(before==null)scope.remove();else scope.set(before);}
            });
        }
        module.hook(invoke).intercept(chain->{Session session=coroutines.get(chain.getThisObject());if(session==null)return chain.proceed();
            if(!current(session)){trace.validation("PAGE_NO_LONGER_ACTIVE",false);return failedResult.newInstance(Map.of(),failure);}
            Session previous=scope.get();scope.set(session);try{Object result=chain.proceed();if(result!=null&&result.getClass()==failedResult.getDeclaringClass())trace.ocrResult(String.valueOf(call(result,"b")));return result;}finally{if(previous==null)scope.remove();else scope.set(previous);}
        });
        module.hook(parse).intercept(chain->{Object original=chain.proceed();Session session=scope.get();if(session==null||!(original instanceof Map<?,?> map))return original;
            if(!current(session)){trace.validation("PAGE_NO_LONGER_ACTIVE",false);return Map.of();}
            PaymentEvidence.Result result=PaymentEvidence.verify((String)chain.getArg(0),map);
            receipt=result.proof()==null?null:new VerifiedReceipt(session,result.proof());
            trace.validation(result.state(),result.corrected());return result.values();
        });
        module.hook(makeIdentity).intercept(chain->{
            Object result=chain.proceed();String page=(String)chain.getArg(1);
            VerifiedReceipt seen=receipt;
            boolean matches=result!=null&&seen!=null&&seen.session()==active&&seen.session().generation()==generation.get()
                    &&seen.session().component().getClassName().equals(page)&&enabled.getAsBoolean()
                    &&seen.proof().matches((Map<?,?>)chain.getArg(0));
            synchronized(identities){
                identities.removeIf(item->{Object previous=item.nativeIdentity().get();return previous==null||previous==result;});
                if(matches){
                    if(identities.size()>=32)identities.removeFirst();
                    identities.addLast(new ObservedIdentity(new WeakReference<>(result),seen.proof()));
                }
            }
            return result;
        });
        module.hook(compare).intercept(chain->{Object nativeResult=chain.proceed();if(!enabled.getAsBoolean())return nativeResult;
            PaymentEvidence.Proof fresh,old;
            synchronized(identities){fresh=exactProof(chain.getThisObject());old=exactProof(chain.getArg(0));}
            PaymentEvidence.Proof.Relation relation=fresh==null?PaymentEvidence.Proof.Relation.UNKNOWN:fresh.compare(old);
            if(relation==PaymentEvidence.Proof.Relation.DIFFERENT&&Boolean.TRUE.equals(nativeResult)){trace.preciseDedup();return false;}
            if(relation==PaymentEvidence.Proof.Relation.SAME)return true;
            if(Boolean.TRUE.equals(nativeResult)&&fresh!=null)trace.duplicate();return nativeResult;
        });
        // The archive-wide duplicate check does not identify its matching record. Preserve that native result.
        Class<?> engine=cls("com.oplus.gleanerservice.flashnotes.runtime.engine.FlashNotesEngine");
        for(Method method:engine.getDeclaredMethods())if(method.getName().equals("R")&&method.getParameterCount()==3){
            module.hook(method).intercept(chain->{Object trigger=chain.getArg(1);Object extras=call(trigger,"getExtras");boolean target=extras instanceof Map<?,?> data&&AimemoryPolicy.WECHAT.equals(data.get("packageName"))&&"AUTO_PAYMENT".equals(String.valueOf(call(trigger,"getType")));
                Session session=active;if(target&&session!=null&&session.owned()&&!enabled.getAsBoolean())return false;
                return chain.proceed();
            });
        }
    }
}
