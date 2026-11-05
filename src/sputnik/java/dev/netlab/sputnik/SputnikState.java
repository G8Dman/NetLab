package dev.netlab.sputnik;

import com.google.gson.*;
import dev.netlab.core.*;
import java.util.*;

/** Config is private CISCO-card data; equipment identity/ownership remain physical. */
public final class SputnikState {
    public static final String KEY="netlabsputnik";
    public static final Gson JSON=new Gson();
    public String owner="",network="sputnik",satellite="GEO-1",polarization="H",key="",hubAddress="";
    public double azimuth=180,elevation=45,download=25,upload=3,volume=.7;
    public int txMhz=14000,rxMhz=12200;
    public boolean enabled=true,internet,keepLoaded=true;
    public Map<String,String> subscribers=new LinkedHashMap<>();
    public static SputnikState get(Device d){var s=d.extensions.containsKey(KEY)?JSON.fromJson(d.extensions.get(KEY),SputnikState.class):initial(d);if(s.subscribers==null)s.subscribers=new LinkedHashMap<>();return s;}
    public static SputnikState initial(Device d){var s=new SputnikState();if(d.kind.equals(dev.netlab.sputnik.core.SputnikHardware.MODEM)){s.txMhz=14200;s.rxMhz=12000;}return s;}
    public void save(Device d){d.extensions.put(KEY,JSON.toJson(this));}
    public boolean owns(UUID id){return owner.isEmpty()||owner.equals(id.toString());}
    public static void physical(Device next,Device previous){if(previous.extensions.containsKey(KEY)){var s=get(next);s.owner=get(previous).owner;s.save(next);}}
    public static void redact(Device d){if(d.extensions.containsKey(KEY)){var s=get(d);s.key="";s.subscribers.clear();s.save(d);}}
}
