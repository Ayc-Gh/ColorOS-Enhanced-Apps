// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.util.*;

/** Registers the existing Master Video Log presenter without changing its native controls. */
final class CameraLogRegistryPatch {
    private static final String FEATURE="com.oplus.camera.feature.logvideo";
    private static Object call(Object o,String m,Class<?>[] t,Object... a)throws Exception{return o.getClass().getMethod(m,t).invoke(o,a);}
    private static Object build(Object b)throws Exception{return call(b,"build",new Class<?>[0]);}
    static Object apply(Object table,ClassLoader loader)throws Exception{
        String prefix="com.oplus.ocs.camera.configure.ProtobufFeatureConfig$";
        Class<?> fc=Class.forName(prefix+"Feature",false,loader),tc=Class.forName(prefix+"CameraTypeFeatureTable",false,loader),mc=Class.forName(prefix+"ModeFeatureTable",false,loader),cc=Class.forName(prefix+"CameraFeatureTable",false,loader);
        Object builder=call(table,"toBuilder",new Class<?>[0]),camera=call(table,"getCameraFeatureTable",new Class<?>[0]);List<String> pool=new ArrayList<>((List<String>)call(table,"getStrPoolList",new Class<?>[0]));
        Map<?,?> modes=(Map<?,?>)call(camera,"getModeFeatureTablesMap",new Class<?>[0]);Object mode=modes.get("master_video_mode");if(mode==null)throw new IllegalStateException("master_video_mode missing");Object mb=call(mode,"toBuilder",new Class<?>[0]);Map<?,?> types=(Map<?,?>)call(mode,"getCameraTypeFeatureTablesMap",new Class<?>[0]);int changed=0;
        for(var entry:types.entrySet()){
            int id=((Number)entry.getKey()).intValue();String name=pool.get(id);if(!name.equals("common")&&!name.startsWith("rear_"))continue;
            List<?> features=(List<?>)call(entry.getValue(),"getFeatureListList",new Class<?>[0]);Set<String> keys=new HashSet<>();
            for(Object f:features){int key=(Integer)call(f,"getFeatureKeyNameIndex",new Class<?>[0]);keys.add(pool.get(key));}
            // A root feature row is required by hasConfigFeature; option rows alone
            // expose the label without satisfying the native support check.
            if(keys.contains(FEATURE))continue;
            Object tb=call(entry.getValue(),"toBuilder",new Class<?>[0]);
            for(String[] values:new String[][]{{FEATURE,FEATURE,"","","main_menu","string"},{FEATURE,"pref_log_video_key","[on,off]","off","main_menu","string"},{FEATURE,"pref_master_video_format_key","[sdr,log]","sdr","main_menu","string"}}){
                if(keys.contains(values[1]))continue;Object f=fc.getMethod("newBuilder").invoke(null);String[] fields={"FeatureName","FeatureKeyName","FeatureValueRange","FeatureDefaultValue","EntryType","FeatureValueType"};
                for(int i=0;i<values.length;i++){int index=pool.indexOf(values[i]);if(index<0){index=pool.size();pool.add(values[i]);call(builder,"addStrPool",new Class<?>[]{String.class},values[i]);}call(f,"set"+fields[i]+"Index",new Class<?>[]{int.class},index);}
                call(tb,"addFeatureList",new Class<?>[]{fc},build(f));
            }
            call(mb,"putCameraTypeFeatureTables",new Class<?>[]{int.class,tc},id,build(tb));changed++;
        }
        if(changed==0)return table;Object cb=call(camera,"toBuilder",new Class<?>[0]);call(cb,"putModeFeatureTables",new Class<?>[]{String.class,mc},"master_video_mode",build(mb));call(builder,"setCameraFeatureTable",new Class<?>[]{cc},build(cb));return build(builder);
    }
}
