package dev.netlab.media.core;
import dev.netlab.core.*;
import java.util.*;

public final class MediaHardware {
    public static final String HEADEND="media_headend",PBX="media_pbx",STB="media_stb",TV="media_tv",PHONE="media_phone",SPLITTER="media_audio_splitter",SPEAKER="media_speaker";
    public static final String VIDEO="NL-MEDIA-VIDEO",SESSIONS="NL-MEDIA-SESSIONS";
    public static final List<String> KINDS=List.of(HEADEND,PBX,STB,TV,PHONE,SPLITTER,SPEAKER),MODULES=List.of(VIDEO,SESSIONS);
    private static boolean initialized;
    public static synchronized void init(){if(initialized)return;initialized=true;
        for(String k:KINDS){boolean network=Set.of(HEADEND,PBX,STB,PHONE).contains(k);DeviceKinds.EXTRA.put(k,new DeviceKinds.Features(network,network,true,Set.of(HEADEND,PBX).contains(k)?12:k.equals(TV)?4:2,false));PortLayout.PROFILES.put(k,MediaHardware::profile);}
        for(String code:MODULES)Hardware.CATALOG.put(code,new Hardware.Module(code,code,"application",0,0,false,0));
        Hardware.EXTRA_SLOTS.put("Media Headend",new LinkedHashMap<>(Map.of("video0",VIDEO)));
        Hardware.EXTRA_SLOTS.put("Media PBX",new LinkedHashMap<>(Map.of("sessions0",SESSIONS)));
        for(String kind:List.of("audio","hdmi")){CableKind.EXTRA_MEDIA.put(kind,kind);CableKind.EXTRA_NAMES.put(kind,kind.equals("audio")?"Аудиокабель 3,5 мм":"Видеокабель HDMI");}
    }
    private static void port(Device d,String name,String medium,int speed,boolean console){var p=new Device.Port(true);p.name=name;p.medium=medium;p.maxMbps=speed;p.bandwidth=Math.max(1,speed)*1000;p.console=console;d.ports.add(p);}
    public static void initialize(Device d){var h=Hardware.ensure(d);h.profile=switch(d.kind){case HEADEND->"Media Headend";case PBX->"Media PBX";case STB->"Media STB";case TV->"Media TV";case PHONE->"Media IP Phone";case SPLITTER->"Media Audio Splitter";default->"Media Speaker";};d.hostname=h.profile.replace(" ","");d.ports.clear();
        if(Set.of(HEADEND,PBX,STB,PHONE).contains(d.kind)){port(d,"GigabitEthernet0/0","copper",1000,false);port(d,"Console","console",0,true);}
        if(d.kind.equals(STB)){port(d,"HDMIOut","hdmi",1,false);port(d,"AudioOut","audio",1,false);}
        if(d.kind.equals(TV)){port(d,"HDMIIn","hdmi",1,false);port(d,"AudioOut","audio",1,false);}
        if(d.kind.equals(SPLITTER)){port(d,"AudioIn","audio",1,false);for(int i=1;i<=4;i++)port(d,"AudioOut"+i,"audio",1,false);}
        if(d.kind.equals(SPEAKER))port(d,"AudioIn","audio",1,false);
    }
    public static PortLayout.Profile profile(Device d){var j=new ArrayList<PortLayout.Jack>();var labels=new LinkedHashMap<Integer,String>();for(int i=0;i<d.ports.size();i++){var p=d.ports.get(i);double x=d.ports.size()==1?.5:.10+.8*i/Math.max(1,d.ports.size()-1);double y=d.kind.equals(TV)?.12:d.kind.equals(PHONE)?.08:d.kind.equals(SPEAKER)?.08:.065;j.add(new PortLayout.Jack(i,x,y,d.kind.equals(TV)?.055:.075,.07,.065,p.console,p.medium));labels.put(i,p.console?"CON":p.name.replace("GigabitEthernet0/0","LAN").replace("Audio","A").replace("HDMI","HDMI "));}return new PortLayout.Profile(d.kind.equals(HEADEND)?"IPTV":d.kind.equals(PBX)?"IP PBX":"",.05,d.kind.equals(TV)?.94:.22,.06,.12,List.copyOf(j),Map.copyOf(labels));}
}
