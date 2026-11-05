package dev.netlab.sputnik.core;

import dev.netlab.core.*;
import java.util.*;

public final class SputnikHardware {
    public static final String HUB="sputnik_hub",GATEWAY="sputnik_gateway",ODU="sputnik_odu",DISH="sputnik_dish",VSAT="sputnik_vsat_dish",MODEM="sputnik_modem",SPEAKER="sputnik_speaker";
    public static final String CONTROL="NL-SAT-CONTROL",TX="NL-SAT-TX",RX="NL-SAT-RX";
    public static final List<String> KINDS=List.of(HUB,GATEWAY,ODU,DISH,VSAT,MODEM,SPEAKER),MODULES=List.of(CONTROL,TX,RX);
    private static boolean initialized;
    public static synchronized void init(){if(initialized)return;initialized=true;
        for(String k:KINDS)DeviceKinds.EXTRA.put(k,new DeviceKinds.Features(!Set.of(DISH,VSAT,SPEAKER).contains(k),!Set.of(DISH,VSAT,SPEAKER).contains(k),true,k.equals(HUB)?16:k.equals(GATEWAY)?8:k.equals(MODEM)?6:k.equals(ODU)?8:k.equals(SPEAKER)?1:0,Set.of(HUB,MODEM).contains(k)));
        for(String id:MODULES)Hardware.CATALOG.put(id,new Hardware.Module(id,id,"application",0,0,false,0));
        Hardware.CREATIVE_ONLY.addAll(MODULES);var slots=new LinkedHashMap<String,String>();slots.put("control0",CONTROL);slots.put("tx0",TX);slots.put("rx0",RX);Hardware.EXTRA_SLOTS.put("Sputnik HUB",slots);
        for(String m:List.of("coax_tx","coax_rx","sat_rf_tx","sat_rf_rx","audio","satellite")){CableKind.EXTRA_MEDIA.put(m,m);CableKind.EXTRA_NAMES.put(m,switch(m){case "coax_tx"->"Коаксиальный кабель TX";case "coax_rx"->"Коаксиальный кабель RX";case "sat_rf_tx"->"Радиокабель BUC → антенна";case "sat_rf_rx"->"Радиокабель антенна → LNB";case "audio"->"Аудиокабель 3,5 мм";default->"Спутниковый канал";});}
        for(String k:KINDS)PortLayout.PROFILES.put(k,SputnikHardware::profile);
    }
    private static void port(Device d,String name,String medium,int rate,boolean console){var p=new Device.Port(true);p.name=name;p.medium=medium;p.maxMbps=rate;p.bandwidth=Math.max(1,rate)*1000;p.console=console;d.ports.add(p);}
    public static void initialize(Device d){var h=Hardware.ensure(d);h.profile=d.kind.equals(HUB)?"Sputnik HUB":d.kind.equals(GATEWAY)?"Sputnik Internet Gateway":d.kind.equals(MODEM)?"Sputnik VSAT Modem":d.kind.equals(ODU)?"Sputnik Radio Unit":d.kind.equals(SPEAKER)?"Sputnik Speaker":"Sputnik Antenna";d.hostname=h.profile.replace(" ","");d.ports.clear();
        if(d.kind.equals(SPEAKER)){port(d,"AudioIn","audio",1,false);return;}
        if(Set.of(DISH,VSAT).contains(d.kind)){port(d,"TX",d.kind.equals(DISH)?"sat_rf_tx":"coax_tx",1000,false);port(d,"RX",d.kind.equals(DISH)?"sat_rf_rx":"coax_rx",1000,false);return;}
        if(d.kind.equals(ODU)){port(d,"TX-IF","coax_tx",1000,false);port(d,"RX-IF","coax_rx",1000,false);port(d,"BUC-TX","sat_rf_tx",1000,false);port(d,"LNB-RX","sat_rf_rx",1000,false);port(d,"Console","console",0,true);return;}
        port(d,"GigabitEthernet0/0","copper",1000,false);port(d,"Console","console",0,true);
        if(!d.kind.equals(GATEWAY)){port(d,"TX-IF","coax_tx",1000,false);port(d,"RX-IF","coax_rx",1000,false);port(d,"Satellite0/0","satellite",25,false);d.ports.get(4).ip=d.kind.equals(HUB)?"10.254.0.1":"";}
    }
    public static boolean hubReady(Device d){return d.kind.equals(HUB)&&Hardware.powered(d)&&Hardware.ensure(d).modules.values().containsAll(MODULES);}
    public static void audio(Device d){if(d.kind.equals("pc")&&d.ports.stream().noneMatch(p->p.name.equals("AudioOut")))port(d,"AudioOut","audio",1,false);}
    public static PortLayout.Profile profile(Device d){var j=new ArrayList<PortLayout.Jack>();var labels=new HashMap<Integer,String>();var physical=new ArrayList<Integer>();for(int i=0;i<d.ports.size();i++)if(!d.ports.get(i).medium.equals("satellite")&&!d.ports.get(i).hidden)physical.add(i);
        double height=Set.of(DISH,VSAT).contains(d.kind)?.09:d.kind.equals(SPEAKER)?.12:d.kind.equals(ODU)?.16:.10;
        for(int i=0;i<physical.size();i++){int p=physical.get(i);boolean antenna=Set.of(DISH,VSAT).contains(d.kind);double x=d.kind.equals(SPEAKER)?.5:(antenna?.22:.12)+i*((antenna?.56:.76)/Math.max(1,physical.size()-1));j.add(new PortLayout.Jack(p,x,height,.075,.11,.09,d.ports.get(p).console,d.ports.get(p).medium));labels.put(p,d.ports.get(p).console?"CON":d.portName(p).replace("GigabitEthernet0/0","LAN").replace("-IF",""));}
        return new PortLayout.Profile(d.kind.equals(HUB)?"SAT HUB":d.kind.equals(GATEWAY)?"INTERNET":d.kind.equals(MODEM)?"VSAT":d.kind.equals(ODU)?"BUC / LNB":"",.075,d.kind.equals(ODU)?.5:.24,d.kind.equals(SPEAKER)?.23:d.kind.equals(ODU)?.12:.05,.21,List.copyOf(j),Map.copyOf(labels));
    }
}
