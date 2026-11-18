// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;

/** Camera-local resource adapter. LUT bytes live only in read-only anonymous memory. */
final class CameraFilterAdapter {
    private static final String TAG="ColorOSEnhance-Filters";
    private final Map<String,String> paths=new HashMap<>();
    private final List<ParcelFileDescriptor> descriptors=new ArrayList<>();
    private final Set<String> notes=new HashSet<>();
    private final XposedModule module;
    private final BooleanSupplier enabled;
    private volatile boolean prepared;
    private volatile int photoFilterCount;
    private boolean preparationFailed;
    private volatile boolean active;
    private volatile Boolean sessionEnabled;
    private synchronized boolean enabled(){if(!active)return false;if(sessionEnabled==null)sessionEnabled=enabled.getAsBoolean();return sessionEnabled;}
    private static final String[][] FILTERS={
        {"portra400_normal.bin","camera_filter_oplus_he_guang"},
        {"positive_normal.bin","camera_filter_oplus_nong_yu"},
        {"blackandwhite_HC_normal.bin","camera_filter_fanchaheibai"}
    };
    private static final String[][] NATIVE_FILTERS={
        {"gt-3dcity.cube.rgb.bin","camera_filter_gt_street_mode_three_dimen_city"},
        {"gt-glazed-tile.cube.rgb.bin","camera_filter_gt_street_mode_glazed_tile"},
        {"gt-desert.cube.rgb.bin","camera_filter_gt_street_mode_desert"},
        {"gt-crisp-fall.cube.rgb.bin","camera_filter_oplus_basic_desertlegend"},
        {"gt-mountainous.cube.rgb.bin","camera_filter_gt_street_mode_mountainous"},
        {"gt-after-glow.cube.rgb.bin","camera_filter_gt_street_mode_after_glow"},
        {"gt-coco-grove.cube.rgb.bin","camera_filter_gt_street_mode_coco_grove"},
        {"gt-steaming.cube.rgb.bin","camera_filter_gt_street_mode_steaming"},
        {"gt-24hour-city.cube.rgb.bin","camera_filter_gt_street_mode_night_city"},
        {"gt-old-times.cube.rgb.bin","camera_filter_gt_street_mode_old_time"},
        {"gt-azure-water.cube.rgb.bin","camera_filter_gt_street_mode_azure_water"},
        {"gt-blue-water.cube.rgb.bin","camera_filter_gt_street_mode_blue_water"},
        {"gt-quietly-ele.cube.rgb.bin","camera_filter_gt_street_mode_simple_elegant"},
        {"gt-beach.cube.rgb.bin","camera_filter_gt_street_mode_beach"},
        {"tone-of-light.cube.rgb.bin","tol_filter_type"},
        {"fuji_cc.bin","camera_filter_oplus_qing_xin"},
        {"fuji-nc.bin","camera_filter_oplus_fu_gu"},
        {"fuji-proNegHi.bin","camera_filter_oplus_tong_tou"},
        {"mountains.cube.rgb.bin","camera_filter_oplus_shan_ye"},
        {"island.cube.rgb.bin","camera_filter_oplus_hai_dao"},
        {"city.cube.rgb.bin","camera_filter_oplus_cheng_shi"},
        {"fuji-eterna-v2.cube.rgb.bin","camera_filter_oplus_basic_eterna"},
        {"meicam.child.bin","camera_filter_oplus_tong_hua_new"},
        {"Serenity.cube.rgb.bin","camera_filter_oplus_ren_wen"},
        {"natural.cube.rgb.bin","camera_filter_ziran"},
        {"gourmet.cube.rgb.bin","camera_filter_meiwei"},
        {"vivid-cool.cube.rgb.bin","camera_filter_lengdiao"},
        {"kodak.cube.rgb.bin","camera_filter_feilin"},
        {"morandi.cube.rgb.bin","camera_filter_oplus_style_morandi"},
        {"blackandwhite.cube.rgb.bin","camera_filter_heibai"},
        {"mono.cube.rgb.bin","camera_video_filter_danse"},
        {"cyberpunk-kitsch-3.1.CUBE.rgb.bin","night_filter_cyberpunk_oplus_r"},
        {"vivid-warm.cube.rgb.bin","camera_filter_oplus_basic_vividwarm"},
        {"fuji.cube.rgb.bin","camera_fuji_film"},
        {"vivid-lut.cube.rgb.bin","camera_filter_huidiao"},
        {"DL_B_Inception.bin","camera_filter_oplus_basic_b_inception"},
        {"DL_C_R3_80.bin","camera_filter_oplus_basic_c_r"},
        {"DL_A_MoneyBall.bin","camera_filter_oplus_basic_a_moneyball"},
        {"Delta400.3dl.rgb.bin","camera_filter_oplus_soft"},
        {"oplus-black-gold.cube.rgb.bin","camera_filter_oplus_night_black_gold"},
        {"oplus-yellow-blue.cube.rgb.bin","camera_filter_oplus_night_teal_orange"},
        {"oplus-cyberpunk.cube.rgb.bin","camera_filter_oplus_night_cyberpunk"},
        {"oplus-nightcity.cube.rgb.bin","night_filter_night_city"},
        {"ccd_base_cold.bin","camera_filter_oplus_flash_cold"},
        {"ccd_base_warm.bin","camera_filter_oplus_flash_warm"},
        {"jzk-movie.cube.rgb.bin","camera_filter_type_director_joint_filter_JZK"}
    };
    CameraFilterAdapter(XposedModule module,BooleanSupplier enabled){this.module=module;this.enabled=enabled;}
    private synchronized void note(String value){if(notes.add(value))module.log(Log.INFO,TAG,value);}
    private synchronized boolean prepare(){
        if(prepared)return true;if(preparationFailed)return false;
        try(ZipFile zip=new ZipFile(module.getModuleApplicationInfo().sourceDir)){
            Enumeration<? extends java.util.zip.ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                var entry=entries.nextElement();String prefix="assets/camera-filters/";
                if(!entry.getName().startsWith(prefix)||entry.isDirectory())continue;
                String name=entry.getName().substring(prefix.length());
                if(!CameraFilterResources.SHA256.containsKey(name))continue;
                if(entry.getSize()<1||entry.getSize()>2_000_000)throw new IllegalStateException("Invalid LUT size");
                byte[] bytes;try(var input=zip.getInputStream(entry)){bytes=input.readAllBytes();}
                if(!CameraFilterResources.matches(name,bytes))throw new IllegalStateException("LUT integrity mismatch: "+name);
                // Android SharedMemory can use ashmem, which is not readable by stdio.
                // A sealed memfd supports the OEM file reader without any disk entry.
                var fd=Os.memfd_create("cea-filter-"+name,OsConstants.MFD_CLOEXEC|2 /* MFD_ALLOW_SEALING */);
                ParcelFileDescriptor descriptor;
                try{
                    int offset=0;while(offset<bytes.length){int written=Os.write(fd,bytes,offset,bytes.length-offset);if(written<=0)throw new IllegalStateException("Incomplete LUT write");offset+=written;}
                    Os.lseek(fd,0,OsConstants.SEEK_SET);
                    Os.fcntlInt(fd,1033 /* F_ADD_SEALS */,15 /* SEAL|SHRINK|GROW|WRITE */);
                    descriptor=ParcelFileDescriptor.dup(fd);
                }finally{Os.close(fd);}
                descriptors.add(descriptor);paths.put(name,"/proc/self/fd/"+descriptor.getFd());
            }
            if(paths.size()!=CameraFilterResources.SHA256.size())throw new IllegalStateException("Incomplete resources");
            prepared=true;note("Anonymous LUT resources ready: "+paths.size());return true;
        }catch(Throwable error){preparationFailed=true;for(var descriptor:descriptors)try{descriptor.close();}catch(Exception ignored){}descriptors.clear();paths.clear();module.log(Log.ERROR,TAG,"Cannot prepare filter resources",error);return false;}
    }
    private String remap(String value,boolean relative){
        if(value==null||!enabled()||!prepare())return value;
        String[] names=value.split(",",-1);boolean changed=false;
        for(int i=0;i<names.length;i++){
            String path=paths.get(names[i]);
            if(path!=null){names[i]=relative?"../../../.."+path:path;changed=true;}
        }
        return changed?String.join(",",names):value;
    }
    private String contrastPair(String name,int strength,boolean relative){
        String[] pair=CameraFilterPolicy.contrastLuts(name,strength,paths.keySet());
        if(pair==null)return null;
        String prefix=relative?"../../../..":"";
        return prefix+paths.get(pair[0])+","+prefix+paths.get(pair[1]);
    }
    org.json.JSONObject diagnostics()throws org.json.JSONException{
        return new org.json.JSONObject().put("installed",active).put("sessionEnabled",Boolean.TRUE.equals(sessionEnabled)).put("resourcesReady",prepared).put("resourceFailure",preparationFailed).put("photoFilterCount",photoFilterCount);
    }
    void install(ClassLoader loader)throws Exception{
        Class<?> manager=Class.forName("com.oplus.camera.filter.FilterGroupManager",false,loader);
        Method init=manager.getDeclaredMethod("initFilterGroupFromIpu",Context.class,String.class,List.class);
        module.hook(init).intercept(chain->{
            String group=(String)chain.getArg(1);Object[] args=chain.getArgs().toArray();
            if(enabled()&&Set.of("photo","portrait","commonVideo").contains(group.split("-")[0])&&prepare()){
                Context context=(Context)chain.getArg(0);List<?> original=(List<?>)chain.getArg(2);
                List<Object> rows=new ArrayList<>(original);Set<String> existing=new HashSet<>();
                for(Object row:original)if(row instanceof Map<?,?> map)existing.add(String.valueOf(map.get("drawing_item_filter_type")));
                List<String[]> candidates=new ArrayList<>(Arrays.asList(FILTERS));
                if(group.startsWith("photo-"))for(String[] candidate:NATIVE_FILTERS)if(!candidate[0].startsWith("ccd_")||group.endsWith("-back"))candidates.add(candidate);
                for(String[] filter:candidates)if(!existing.contains(filter[0])&&(paths.containsKey(filter[0])||new java.io.File("/odm/etc/camera/meishe_lut/"+filter[0]).isFile())){
                    int id=context.getResources().getIdentifier(filter[1],"string","com.oplus.camera");
                    if(id==0)continue;
                    Map<String,Object> row=new HashMap<>();row.put("drawing_item_filter_type",filter[0]);row.put("drawing_item_filter_name",id);rows.add(row);
                }
                args[2]=rows;if(group.equals("photo-back"))photoFilterCount=rows.size();note("Group "+group+" original="+original.size()+" extended="+rows.size());if(group.equals("photo-back"))for(Object item:rows){Map<?,?> row=(Map<?,?>)item;note("Catalog "+row.get("drawing_item_filter_type")+" = "+context.getString((Integer)row.get("drawing_item_filter_name")));}
            }
            return chain.proceed(args);
        });
        Class<?> render=Class.forName("com.meicam.effect.oppo.MeisheRender",false,loader);
        module.hook(render.getDeclaredMethod("addPrefixAndJoin",String.class,String.class)).intercept(chain->{
            String value=(String)chain.getArg(0),mapped=remap(value,false);
            if(Objects.equals(value,mapped))return chain.proceed();
            String prefix=(String)chain.getArg(1);String[] parts=value.split(",",-1);
            for(int i=0;i<parts.length;i++)parts[i]=paths.getOrDefault(parts[i],prefix+parts[i]);
            note("Preview resource "+value);return String.join(",",parts);
        });
        Class<?> effect=Class.forName("com.meicam.effect.sdk.MsEffectSdkContextUtils",false,loader);
        Class<?> paramType=Class.forName("com.meicam.effect.sdk.SMsEffectParam",false,loader);
        var lut=paramType.getField("lutFilePath");var intensity=paramType.getField("intensity");
        var enableIntp=paramType.getField("intpFactorEnable");var intp=paramType.getField("intpFactor");
        var second=paramType.getField("secondLutFilePath");
        if(intensity.getType()!=float.class||intp.getType()!=float.class||enableIntp.getType()!=boolean.class)throw new NoSuchFieldException("Filter parameter types changed");
        for(Method method:effect.getDeclaredMethods())if(method.getName().startsWith("RenderEffectWith")&&method.getParameterTypes()[0]==paramType){
            module.hook(method).intercept(chain->{
                Object param=chain.getArg(0);
                if(param==null||!enabled())return chain.proceed();
                String path=(String)lut.get(param), name=null;
                for(var entry:paths.entrySet())if(entry.getValue().equals(path)){name=entry.getKey();break;}
                String pair=contrastPair(name,Math.round(intensity.getFloat(param)),false);
                if(pair==null)return chain.proceed();
                float oldIntensity=intensity.getFloat(param);boolean oldEnable=enableIntp.getBoolean(param);
                float oldFactor=intp.getFloat(param);String oldSecond=(String)second.get(param);
                try{
                    lut.set(param,pair);intensity.setFloat(param,100f);enableIntp.setBoolean(param,true);
                    intp.setFloat(param,CameraFilterPolicy.factor(Math.round(oldIntensity)));second.set(param,"");
                    note("Contrast preview " + name + " strength="+oldIntensity);
                    return chain.proceed();
                }finally{lut.set(param,path);intensity.setFloat(param,oldIntensity);enableIntp.setBoolean(param,oldEnable);intp.setFloat(param,oldFactor);second.set(param,oldSecond);}
            });
        }
        Class<?> params=Class.forName("com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsParameters",false,loader);
        module.hook(params.getDeclaredMethod("getParameters")).intercept(chain->{
            String[] original=(String[])chain.proceed();
            if(original==null||!enabled())return original;
            Map<String,String> values=new LinkedHashMap<>();
            for(int i=0;i+1<original.length;i+=2)values.put(original[i],original[i+1]);
            String value=values.get("filter_type"),mapped=remap(value,true);
            if(Objects.equals(value,mapped))return original;
            int strength=100;try{strength=Integer.parseInt(values.getOrDefault("filter_intensity","100"));}catch(NumberFormatException ignored){}
            String pair=contrastPair(value,strength,true);
            if(pair!=null){mapped=pair;values.put("filter_intensity","100");values.put("filter_lut_intp_factor",Float.toString(CameraFilterPolicy.factor(strength)));}
            values.put("filter_type",mapped);
            note("Capture mapping "+value+" strength="+strength+" pair="+(pair!=null));
            String[] result=new String[values.size()*2];int i=0;
            for(var entry:values.entrySet()){result[i++]=entry.getKey();result[i++]=entry.getValue();}
            return result;
        });
        active=true;note("Filter adapter installed");
    }
}
