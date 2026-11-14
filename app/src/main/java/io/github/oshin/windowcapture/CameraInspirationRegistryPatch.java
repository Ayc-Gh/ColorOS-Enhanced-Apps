// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import java.lang.reflect.Method;
import java.util.*;

/** Adds only the missing inspiration row to the in-memory camera feature table. */
final class CameraInspirationRegistryPatch {
    private static Object call(Object object,String method,Class<?>[] types,Object... args)throws Exception{return object.getClass().getMethod(method,types).invoke(object,args);}
    private static Object build(Object builder)throws Exception{return call(builder,"build",new Class<?>[0]);}
    static Object apply(Object table,ClassLoader loader)throws Exception{
        String base="com.oplus.ocs.camera.configure.ProtobufFeatureConfig$";
        Class<?> featureClass=Class.forName(base+"Feature",false,loader),typeClass=Class.forName(base+"CameraTypeFeatureTable",false,loader),modeClass=Class.forName(base+"ModeFeatureTable",false,loader),cameraClass=Class.forName(base+"CameraFeatureTable",false,loader);
        Object builder=call(table,"toBuilder",new Class<?>[0]);
        List<String> pool=new ArrayList<>((List<String>)call(table,"getStrPoolList",new Class<?>[0]));
        Object cameras=call(table,"getCameraFeatureTable",new Class<?>[0]);Map<?,?> modes=(Map<?,?>)call(cameras,"getModeFeatureTablesMap",new Class<?>[0]);Object photo=modes.get("photo_mode");if(photo==null)throw new IllegalStateException("photo_mode missing");
        Map<?,?> types=(Map<?,?>)call(photo,"getCameraTypeFeatureTablesMap",new Class<?>[0]);Object modeBuilder=call(photo,"toBuilder",new Class<?>[0]);int added=0;
        for(String typeName:new String[]{"rear_sat","rear_main"}){
            int typeId=pool.indexOf(typeName);Object type=types.get(typeId);if(type==null)continue;List<?> features=(List<?>)call(type,"getFeatureListList",new Class<?>[0]);boolean exists=false;
            for(Object feature:features){int name=(Integer)call(feature,"getFeatureNameIndex",new Class<?>[0]);if(name>=0&&name<pool.size()&&pool.get(name).equals("com.oplus.camera.feature.ai_composition")){exists=true;break;}}
            if(exists)continue;
            Object feature=featureClass.getMethod("newBuilder").invoke(null);String[] values={"com.oplus.camera.feature.ai_composition","key_ai_composition","[on,off]","off","main_menu","string"};String[] fields={"FeatureName","FeatureKeyName","FeatureValueRange","FeatureDefaultValue","EntryType","FeatureValueType"};
            for(int i=0;i<values.length;i++){int id=pool.indexOf(values[i]);if(id<0){id=pool.size();pool.add(values[i]);call(builder,"addStrPool",new Class<?>[]{String.class},values[i]);}call(feature,"set"+fields[i]+"Index",new Class<?>[]{int.class},id);}
            Object typeBuilder=call(type,"toBuilder",new Class<?>[0]);call(typeBuilder,"addFeatureList",new Class<?>[]{featureClass},build(feature));call(modeBuilder,"putCameraTypeFeatureTables",new Class<?>[]{int.class,typeClass},typeId,build(typeBuilder));added++;
        }
        if(added==0)return table;Object cameraBuilder=call(cameras,"toBuilder",new Class<?>[0]);call(cameraBuilder,"putModeFeatureTables",new Class<?>[]{String.class,modeClass},"photo_mode",build(modeBuilder));call(builder,"setCameraFeatureTable",new Class<?>[]{cameraClass},build(cameraBuilder));return build(builder);
    }
}
