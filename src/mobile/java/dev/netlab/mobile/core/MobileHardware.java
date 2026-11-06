package dev.netlab.mobile.core;

import dev.netlab.core.*;
import java.util.*;

/** Pure hardware registration; material recipes live in the Mobile data pack. */
public final class MobileHardware {
    public static final String BBU="mobile_bbu",CORE="mobile_core",RRU="mobile_rru",ANTENNA="mobile_antenna",STATION="mobile_sim_station";
    public static final String MODEM="CISCO-LTE-MODEM",CONTROL="NL-MOB-CONTROL",BASEBAND="NL-MOB-BASEBAND",FRONTHAUL="NL-MOB-FRONTHAUL";
    public static final List<String> MODULES=List.of(CONTROL,BASEBAND,FRONTHAUL,MODEM);
    private static boolean initialized;
    public static synchronized void init(){
        if(initialized)return;initialized=true;
        DeviceKinds.EXTRA.put(BBU,new DeviceKinds.Features(true,true,true,12,false));DeviceKinds.EXTRA.put(CORE,new DeviceKinds.Features(true,true,true,16,true));
        DeviceKinds.EXTRA.put(RRU,new DeviceKinds.Features(true,true,true,8,false));DeviceKinds.EXTRA.put(STATION,new DeviceKinds.Features(true,true,true,4,false));DeviceKinds.EXTRA.put(ANTENNA,new DeviceKinds.Features(false,false,true,0,false));
        Hardware.CATALOG.put(CONTROL,new Hardware.Module(CONTROL,"MOB-CONTROL","application",0,0,false,0));
        Hardware.CATALOG.put(BASEBAND,new Hardware.Module(BASEBAND,"MOB-BASEBAND","application",0,0,false,0));
        Hardware.CATALOG.put(FRONTHAUL,new Hardware.Module(FRONTHAUL,"MOB-FRONTHAUL","fronthaul",3,1000,false,0));
        Hardware.CATALOG.put(MODEM,new Hardware.Module(MODEM,"CELLULAR","cellular",1,10,false,0));
        Hardware.HOST_SLOTS.put("mobile0","CELLULAR");var slots=new LinkedHashMap<String,String>();slots.put("control0","MOB-CONTROL");slots.put("baseband0","MOB-BASEBAND");slots.put("fronthaul0","MOB-FRONTHAUL");Hardware.EXTRA_SLOTS.put("NetLab Mobile BBU",slots);
        CableKind.EXTRA_MEDIA.put("rf","rf");CableKind.EXTRA_NAMES.put("rf","Антенный RF-кабель");CableKind.EXTRA_MEDIA.put("cellular","cellular");
        MobilePorts.register();
    }
    public static void initialize(Device d){
        d.hostname=switch(d.kind){case BBU->"MobileBBU";case CORE->"MobileCore";case RRU->"MobileRRU";case STATION->"SIMStation";default->"SectorAntenna";};
        Hardware.ensure(d).profile=switch(d.kind){case BBU->"NetLab Mobile BBU";case CORE->"NetLab Mobile EPC";case RRU->"NetLab Mobile RRU";case STATION->"SIM Provisioner";default->"Sector Antenna";};
        d.ports.clear();
        if(d.kind.equals(ANTENNA)){port(d,"RF0/0","rf",1000,false);port(d,"RF0/1","rf",1000,false);return;}
        if(d.kind.equals(RRU)){port(d,"Fronthaul0/0","fronthaul",1000,false);port(d,"RF0/0","rf",1000,false);port(d,"RF0/1","rf",1000,false);}
        else port(d,"GigabitEthernet0/0","copper",1000,false);
        port(d,"Console","console",0,true);
        if(d.kind.equals(CORE)){port(d,"CellularGateway0","cellular",10,false);var p=d.ports.getLast();p.ip="10.64.0.1";}
    }
    private static void port(Device d,String name,String medium,int speed,boolean console){var p=new Device.Port(true);p.name=name;p.medium=medium;p.console=console;p.maxMbps=speed;p.bandwidth=Math.max(1,speed)*1000;d.ports.add(p);}
    public static boolean bbuReady(Device d){return d.kind.equals(BBU)&&Hardware.powered(d)&&Hardware.ensure(d).modules.values().containsAll(List.of(CONTROL,BASEBAND,FRONTHAUL));}
    public static int cellular(Device d){for(int p=0;p<d.ports.size();p++)if(!d.ports.get(p).hidden&&d.ports.get(p).medium.equals("cellular"))return p;return -1;}
    public static boolean modem(Device d){return d.kind.equals("pc")&&Hardware.ensure(d).modules.containsValue(MODEM)&&cellular(d)>=0;}
}
