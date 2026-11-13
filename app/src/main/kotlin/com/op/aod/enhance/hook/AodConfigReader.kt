package com.op.aod.enhance.hook

import android.content.Context
import android.content.SharedPreferences
import com.op.aod.enhance.data.AodConfig
import com.op.aod.enhance.data.AodConfigCodec
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArraySet

internal object AodConfigReader {
    private val DEFAULT_CONFIG=AodConfig()
    private val prefsRef=AtomicReference<SharedPreferences?>(null)
    private val cachedRef=AtomicReference<AodConfig?>(null)
    private val refreshing=AtomicBoolean(false)
    private val lastRefreshNs=AtomicLong(0L)
    private val lastFailureNs=AtomicLong(Long.MIN_VALUE)
    private val observers=CopyOnWriteArraySet<()->Unit>()
    @Volatile var readState="unknown"
        private set
    private val preferenceListener=SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        cachedRef.set(null);lastRefreshNs.set(0L);lastFailureNs.set(Long.MIN_VALUE)
        notifyObservers()
    }

    fun observe(observer:()->Unit){observers.add(observer)}
    private fun notifyObservers(){observers.forEach { observer -> runCatching { observer() } }}

    fun bindPrefs(prefs:SharedPreferences,hostPackage:String){
        prefsRef.getAndSet(prefs)?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        cachedRef.set(null);lastRefreshNs.set(0L);lastFailureNs.set(Long.MIN_VALUE)
        AodLog.i("CONFIG_BRIDGE","bound host=$hostPackage remote=true")
    }

    fun read(@Suppress("UNUSED_PARAMETER") context:Context?):AodConfig{
        val now=System.nanoTime(); val cached=cachedRef.get()
        val stale=cached==null||now-lastRefreshNs.get()>=CACHE_TTL_NS
        val failed=lastFailureNs.get(); val retryAllowed=failed==Long.MIN_VALUE||now-failed>=FAILURE_BACKOFF_NS
        if(stale&&retryAllowed) refresh(now)
        return cachedRef.get()?:DEFAULT_CONFIG
    }

    private fun refresh(now:Long){
        if(!refreshing.compareAndSet(false,true)) return
        try{
            val prefs=prefsRef.get() ?: run { failSafe(now); return }
            val all=runCatching{prefs.all}.onFailure{AodLog.e("CONFIG_PREFS_READ","read failed",it)}.getOrNull()
            if(all==null){failSafe(now);return}
            val parsed=AodConfigCodec.decode(all)
            cachedRef.set(parsed);lastRefreshNs.set(now);lastFailureNs.set(Long.MIN_VALUE)
            val recovered=readState=="failed";readState="ok";if(recovered)notifyObservers()
            AodLog.d("CONFIG_REFRESH","keys=${all.size} durationMode=${parsed.aodDurationMode} customMinutes=${parsed.aodDurationCustomMinutes} blockSingle=${parsed.blockSingleClick} blockLowLight=${parsed.blockLowLightHide}")
        }finally{refreshing.set(false)}
    }

    private fun failSafe(now:Long){
        // The manager retains saved edits for retry; a host must not keep applying
        // an old enhancement while its current configuration cannot be read.
        cachedRef.set(DEFAULT_CONFIG);lastRefreshNs.set(now);lastFailureNs.set(now)
        val changed=readState!="failed";readState="failed";if(changed)notifyObservers()
    }

    private const val CACHE_TTL_NS=500_000_000L
    private const val FAILURE_BACKOFF_NS=5_000_000_000L
}
