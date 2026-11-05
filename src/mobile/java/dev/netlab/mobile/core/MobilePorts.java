package dev.netlab.mobile.core;

import dev.netlab.core.*;
import java.util.*;

/** Front panel is at z=1.5/16; connectors sit at 1.25/16, outside its solid surface. */
public final class MobilePorts {
    public static final double FACE=1.25/16;
    public static void register(){for(String kind:List.of(MobileHardware.ANTENNA,MobileHardware.RRU,MobileHardware.BBU,MobileHardware.CORE,MobileHardware.STATION))PortLayout.PROFILES.put(kind,MobilePorts::profile);}
    private static void socket(Device d,List<PortLayout.Jack> out,Map<Integer,String> labels,String name,String label,double x,double y,double w,double h){
        for(int p=0;p<d.ports.size();p++){var port=d.ports.get(p);if(!port.hidden&&!port.virtual&&d.portName(p).equals(name)){out.add(new PortLayout.Jack(p,x,y,FACE,w,h,port.console,CableKind.medium(d,p)));labels.put(p,label);}}
    }
    public static PortLayout.Profile profile(Device d){
        var ports=new ArrayList<PortLayout.Jack>();var labels=new HashMap<Integer,String>();String title="";double titleY=.245,powerY=.22,powerX=.065;
        switch(d.kind){
            case MobileHardware.ANTENNA->{socket(d,ports,labels,"RF0/0","RF0",.30,.070,.145,.095);socket(d,ports,labels,"RF0/1","RF1",.70,.070,.145,.095);}
            case MobileHardware.RRU->{title="RRU";titleY=.68;powerY=.60;powerX=.12;socket(d,ports,labels,"Fronthaul0/0","FH0",.17,.22,.16,.15);socket(d,ports,labels,"RF0/0","RF0",.39,.22,.16,.15);socket(d,ports,labels,"RF0/1","RF1",.61,.22,.16,.15);socket(d,ports,labels,"Console","CON",.83,.22,.14,.15);}
            case MobileHardware.BBU->{title="BBU";socket(d,ports,labels,"GigabitEthernet0/0","ETH0",.12,.105,.13,.105);for(int i=0;i<3;i++)socket(d,ports,labels,"Fronthaul0/"+i,"FH"+i,.34+i*.18,.105,.13,.105);socket(d,ports,labels,"Console","CON",.90,.105,.12,.105);}
            case MobileHardware.CORE->{title="EPC";socket(d,ports,labels,"GigabitEthernet0/0","ETH0",.32,.105,.19,.12);socket(d,ports,labels,"Console","CON",.72,.105,.17,.12);}
            case MobileHardware.STATION->{title="SIM";titleY=.45;powerY=.42;powerX=.12;socket(d,ports,labels,"GigabitEthernet0/0","ETH0",.30,.14,.19,.14);socket(d,ports,labels,"Console","CON",.72,.14,.17,.14);}
            default->throw new IllegalArgumentException("Unknown mobile enclosure");
        }
        return new PortLayout.Profile(title,FACE,titleY,powerX,powerY,List.copyOf(ports),Map.copyOf(labels));
    }
}
