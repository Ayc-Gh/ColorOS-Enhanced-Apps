package com.op.aod.enhance.hook

import java.lang.reflect.Field
import java.util.WeakHashMap

/** Preserve native values across toggles and native re-initialisation. */
internal class NativeBooleanOverride(private val names:List<String>) {
    private val originals=WeakHashMap<Any,Map<Field,Boolean>>()
    @Synchronized fun beforeNative(instance:Any){originals[instance]?.forEach { (field,value)->field.setBoolean(instance,value) }}
    @Synchronized fun afterNative(instance:Any,enabled:Boolean):Int {
        val values=names.mapNotNull { name -> findField(instance.javaClass,name)?.let { it to it.getBoolean(instance) } }.toMap()
        originals[instance]=values;apply(instance,enabled);return values.size
    }
    @Synchronized fun apply(instance:Any,enabled:Boolean):Boolean? {
        val values=originals[instance]?:return null
        values.forEach { (field,value)->field.setBoolean(instance,enabled||value) }
        return values.entries.firstOrNull { it.key.name==names.first() }?.let { (field,_)->field.getBoolean(instance) }
    }
    @Synchronized fun nativeValue(instance:Any):Boolean?=originals[instance]?.entries?.firstOrNull { it.key.name==names.first() }?.value
    @Synchronized fun instances():List<Any> = originals.keys.toList()
    private fun findField(type:Class<*>,name:String):Field? {
        var current:Class<*>?=type
        while(current!=null){
            val owner=current
            val field=runCatching{owner.getDeclaredField(name)}.getOrNull()
            if(field!=null&&field.type==Boolean::class.javaPrimitiveType)return field.apply{isAccessible=true}
            current=current.superclass
        }
        return null
    }
}
