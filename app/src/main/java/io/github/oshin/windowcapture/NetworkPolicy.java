// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

final class NetworkPolicy {
    static final String HOST="com.oplus.trafficmonitor", KEY="network_options";
    static final class ScreenScope {
        private final ThreadLocal<Boolean> appClassification=new ThreadLocal<>();
        interface Call { Object run() throws Throwable; }
        boolean options(){return appClassification.get()!=null;}
        boolean dataApps(){return Boolean.TRUE.equals(appClassification.get());}
        Object during(boolean classifiesApps,Call call)throws Throwable{
            Boolean previous=appClassification.get();
            appClassification.set(classifiesApps||Boolean.TRUE.equals(previous));
            try{return call.run();}
            finally{if(previous==null)appClassification.remove();else appClassification.set(previous);}
        }
    }
    static boolean eligible(boolean enabled,int uid,boolean internet){int appId=uid%100000;return enabled&&uid>=0&&appId>=10000&&appId<=19999&&internet;}
    private NetworkPolicy(){}
}
