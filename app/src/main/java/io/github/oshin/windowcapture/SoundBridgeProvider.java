// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Only this app and the system audio process can renew the native policy lease. */
public final class SoundBridgeProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        int uid=Binder.getCallingUid();if(uid!=1000&&uid!=android.os.Process.myUid())throw new SecurityException("System audio caller required");
        if(!"lease".equals(method))throw new IllegalArgumentException("Unknown audio request");
        Context c=getContext();if(c==null)return Bundle.EMPTY;SoundController.pulse(c,Binder.getCallingPid());Bundle r=new Bundle();r.putString("state",SoundController.state(c));return r;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
