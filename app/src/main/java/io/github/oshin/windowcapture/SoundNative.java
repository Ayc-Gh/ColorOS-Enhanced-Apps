// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.os.IBinder;
import android.os.Parcel;
import java.nio.charset.StandardCharsets;

/** PHY110 native Binder contracts, validated before a runtime list is loaded. */
final class SoundNative {
    private static IBinder service(String name,String descriptor)throws Exception{
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
        if(binder==null||!descriptor.equals(binder.getInterfaceDescriptor()))throw new IllegalStateException("Native audio interface changed");return binder;
    }
    private static void cstring(Parcel p,String s){byte[] bytes=(s+"\0").getBytes(StandardCharsets.UTF_8),padded=java.util.Arrays.copyOf(bytes,(bytes.length+3)&~3);Parcel raw=Parcel.obtain();try{raw.unmarshall(padded,0,padded.length);p.appendFrom(raw,0,padded.length);}finally{raw.recycle();}}
    static String attribute(String pkg)throws Exception{
        IBinder binder=service("MMListService","IMMListService");Parcel p=Parcel.obtain(),r=Parcel.obtain();
        try{p.writeInterfaceToken("IMMListService");cstring(p,"mss-whitelist");cstring(p,pkg);if(!binder.transact(6,p,r,0))throw new IllegalStateException("MSS lookup unavailable");String value=r.readString();boolean found=r.readInt()!=0;return found?value:null;}finally{p.recycle();r.recycle();}
    }
    static void reload()throws Exception{IBinder binder=service("MMListService","IMMListService");Parcel p=Parcel.obtain(),r=Parcel.obtain();try{p.writeInterfaceToken("IMMListService");if(!binder.transact(14,p,r,0)||r.dataSize()!=0)throw new IllegalStateException("MSS reload unavailable");}finally{p.recycle();r.recycle();}}
    static boolean enabled(String pkg)throws Exception{return request(43,pkg,null)!=0;}
    static int enable(String pkg,boolean on)throws Exception{return request(42,pkg,on);}
    private static int request(int code,String pkg,Boolean on)throws Exception{
        IBinder binder=service("SpecailizerPLService","oplus.spservice.ISpecailizerPLService");Parcel p=Parcel.obtain(),r=Parcel.obtain();
        try{p.writeInterfaceToken("oplus.spservice.ISpecailizerPLService");p.writeString(pkg);if(on!=null)p.writeInt(on?1:0);if(!binder.transact(code,p,r,0))throw new IllegalStateException("MSS control unavailable");r.readException();return r.readInt();}finally{p.recycle();r.recycle();}
    }
    private SoundNative(){}
}
