package dev.netlab.mobile;

import com.google.gson.*;
import dev.netlab.core.*;
import dev.netlab.mobile.core.*;

/** Only configuration belongs on CISCO memory. Station inventory/pending cards are separate. */
public final class MobileState {
    public static final String KEY="netlabmobile";
    public static final Gson JSON=new Gson();
    public String owner="",operatorName="NetLab Mobile",plmn="00101",coreAddress="",adminVerifier="",stationKey="",stationSalt="",stationCore="",subscriberGateway="10.64.0.1",apn="netlab";
    public boolean radioEnabled=true,clientEnabled,keepLoaded=true;
    public String wiredGateway="";
    public String wifiSsid="";
    public int wiredPort;
    public int band=20,channel=6300,range=512;
    public double azimuth=-1,tilt=6,power;
    public SimCredentials sim;
    public SubscriberRegistry subscribers=new SubscriberRegistry();
    public static MobileState get(Device d){return d.extensions.containsKey(KEY)?JSON.fromJson(d.extensions.get(KEY),MobileState.class):new MobileState();}
    public void save(Device d){d.extensions.put(KEY,JSON.toJson(this));}
    public boolean owns(java.util.UUID player){return owner.isEmpty()||owner.equals(player.toString());}
    public static void physical(Device next,Device previous){if(!previous.extensions.containsKey(KEY))return;var old=get(previous);var state=get(next);state.owner=old.owner;state.save(next);}
    public static void redact(Device d){if(!d.extensions.containsKey(KEY))return;var s=get(d);var out=new JsonObject();out.addProperty("owner",s.owner);out.addProperty("operatorName",s.operatorName);out.addProperty("plmn",s.plmn);d.extensions.put(KEY,out.toString());}
}
