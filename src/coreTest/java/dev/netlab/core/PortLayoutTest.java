package dev.netlab.core;

import java.util.*;

/** Regression for missing Console sockets, high-density chassis and the two rack bays. */
public final class PortLayoutTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void selectable(Device d,boolean rack,int bay){
        var jacks=PortLayout.jacks(d,rack,bay);var selectable=new HashSet<Integer>();
        for(var j:jacks){
            check(selectable.add(j.port()),"one visible jack per physical interface");
            check(PortLayout.pick(d,rack,bay,j.x(),j.y())==j.port(),"visible jack selects its actual interface: "+d.portName(j.port()%128));
            double eyeX=-2,eyeY=2,eyeZ=-2,plane=rack?0:.125,t=(plane-eyeZ)/(j.z()-eyeZ);
            var projected=PortLayout.project(rack,eyeX,eyeY,eyeZ,eyeX+(j.x()-eyeX)*t,eyeY+(j.y()-eyeY)*t,plane);
            check(PortLayout.pick(d,rack,bay,projected.x(),projected.y())==j.port(),"angled click through block/rack selection box hits intended socket");
            check(j.x()-j.width()/2>0&&j.x()+j.width()/2<1&&j.y()-j.height()/2>0&&j.y()+j.height()/2<(rack?1:.25),"socket stays inside device front");
            if(rack)check(j.port()/128==bay&&(bay==0?j.y()<.5:j.y()>.5),"rack socket and encoded port belong to same bay");
        }
        check(jacks.size()==d.physicalPortCount(),"all active physical interfaces visible");
        check(jacks.stream().filter(PortLayout.Jack::console).count()==1,"Console visible exactly once");
        check(PortLayout.pick(d,rack,bay,0,0)==-1,"frame/case cannot select an unrelated port");
    }
    public static int run(){
        var router=new Device("router");var sw=new Device("switch");selectable(router,false,0);selectable(sw,false,0);
        var console=PortLayout.jacks(router,false,0).stream().filter(PortLayout.Jack::console).findFirst().orElseThrow();
        check(router.portName(console.port()).equals("Console")&&console.x()>.88,"router has a dedicated right-hand Console connector");
        Hardware.ensure(sw).powered=false;Hardware.changeProfile(sw,"2960-24TT");selectable(sw,false,0);
        check(PortLayout.jacks(sw,false,0).size()==27,"2960 renders 24FE + 2GE + Console");
        int last=sw.findInterface("FastEthernet0/24",false);check(PortLayout.jacks(sw,false,0).stream().anyMatch(j->j.port()==last),"last access port reachable instead of only first four");
        selectable(sw,true,0);selectable(sw,true,1);
        var lower=PortLayout.jacks(sw,true,0);var upper=PortLayout.jacks(sw,true,1);
        for(int i=0;i<lower.size();i++)check(Math.abs(upper.get(i).y()-lower.get(i).y()-.5)<1e-9&&upper.get(i).port()==lower.get(i).port()+128,"rack transform preserves port alignment and separates bays");
        var saved=ConfigStore.restore(ConfigStore.snapshot(sw));check(PortLayout.jacks(saved,false,0).equals(PortLayout.jacks(sw,false,0)),"layout survives configuration serialization");
        for(String profile:Hardware.profiles("switch")){Hardware.changeProfile(sw,profile);selectable(sw,false,0);}
        Hardware.ensure(router).powered=false;Hardware.install(router,"hwic0","WIC-2T");selectable(router,false,0);
        check(PortLayout.jacks(router,false,0).stream().filter(j->j.medium().equals("serial")).count()==2,"installed serial module adds two selectable visible jacks");
        Hardware.remove(router,"hwic0");check(PortLayout.jacks(router,false,0).size()==3,"removed module sockets disappear");
        var loop=new Device.Port(true);loop.virtual=true;router.ports.add(loop);var radio=new Device.Port(true);radio.medium="wireless";router.ports.add(radio);
        check(PortLayout.jacks(router,false,0).size()==3,"virtual and radio interfaces have no wired socket");
        check(PortLayout.jacks(new Device("rack"),true,1).isEmpty(),"empty rack has no dummy ports");
        return checks;
    }
}
