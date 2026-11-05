package dev.netlab.core;

import java.util.*;

/** Adapter lifecycle and the same local/world transform used by models and cable selection. */
public final class OrientationAndHostTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void rejects(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(message);}
    private static boolean near(double a,double b){return Math.abs(a-b)<1e-9;}
    private static void orientations(Device d,boolean rack,int bay){
        for(int turns=0;turns<4;turns++)for(var jack:PortLayout.jacks(d,rack,bay)){
            double plane=rack?0:d.kind.equals("pc")?.0625:.125,eyeX=-2,eyeY=2,eyeZ=-2,t=(plane-eyeZ)/(jack.z()-eyeZ);
            var eye=DeviceOrientation.rotate(eyeX,eyeY,eyeZ,turns);
            var hit=DeviceOrientation.rotate(eyeX+(jack.x()-eyeX)*t,eyeY+(jack.y()-eyeY)*t,plane,turns);
            var localEye=DeviceOrientation.inverse(eye.x(),eye.y(),eye.z(),turns);var localHit=DeviceOrientation.inverse(hit.x(),hit.y(),hit.z(),turns);
            check(PortLayout.pickRay(d,rack,bay,localEye.x(),localEye.y(),localEye.z(),localHit.x(),localHit.y(),localHit.z())==jack.port(),"rotated visible jack remains selectable, turn="+turns+", port="+jack.port());
            var end=DeviceOrientation.rotate(jack.x(),jack.y(),jack.z(),turns);var localEnd=DeviceOrientation.inverse(end.x(),end.y(),end.z(),turns);
            check(near(localEnd.x(),jack.x())&&near(localEnd.y(),jack.y())&&near(localEnd.z(),jack.z()),"rotated cable endpoint matches its rendered jack");
        }
    }
    public static int run(){
        var laptop=new Device("pc");Hardware.initializeHost(laptop,true);var router=new Device("router");int rs232=1;
        check(Hardware.slots(laptop).get("usb0").equals("USB"),"laptop exposes a dedicated USB adapter slot");
        check(laptop.ports.get(rs232).hidden&&PortLayout.jacks(laptop,false,0).size()==1,"new laptop has Ethernet but no phantom RS232");
        check(!ConsoleLink.compatible(laptop,rs232,router,2),"missing adapter cannot provide console access");
        rejects(()->Hardware.install(laptop,"usb0","USB-RS232"),"adapter installation requires power off");
        laptop.hardware.powered=false;rejects(()->Hardware.install(laptop,"radio0","USB-RS232"),"USB adapter cannot occupy PCMCIA radio slot");
        Hardware.install(laptop,"usb0","USB-RS232");check(laptop.ports.size()==2&&!laptop.ports.get(rs232).hidden&&laptop.ports.get(rs232).console,"adapter reuses stable RS232 index and exposes its socket");
        check(PortLayout.jacks(laptop,false,0).size()==2&&ConsoleLink.compatible(laptop,rs232,router,2),"visible adapter socket works with Console cable");
        orientations(laptop,false,0);Hardware.install(laptop,"radio0","WPC300N");
        check(PortLayout.jacks(laptop,false,0).size()==2,"radio module is not drawn as a cable jack");
        var saved=ConfigStore.restore(ConfigStore.snapshot(laptop));
        check(saved.hardware.modules.get("usb0").equals("USB-RS232")&&saved.hardware.hostConsoleVersion==1&&PortLayout.jacks(saved,false,0).size()==2,"installed adapter survives saved configuration");
        Hardware.remove(laptop,"usb0");laptop.normalize();check(laptop.ports.get(rs232).hidden&&!laptop.ports.get(rs232).up,"removed adapter remains hidden and disabled after normalization");
        check(!ConsoleLink.compatible(laptop,rs232,router,2)&&PortLayout.jacks(laptop,false,0).size()==1,"unplugged adapter removes jack and console compatibility");
        check(!Hardware.migrateHostConsole(laptop)&&laptop.ports.get(rs232).hidden,"migration cannot resurrect a deliberately removed adapter");
        check(laptop.hardware.modules.containsKey("radio0"),"removing USB adapter preserves Wi-Fi");
        Hardware.install(laptop,"usb0","USB-RS232");check(laptop.ports.size()==3&&Hardware.portsIn(laptop,"usb0").equals(List.of(rs232)),"reinstall preserves RS232 index and avoids duplicate ports");
        var restored=ConfigStore.restore(ConfigStore.snapshot(laptop));Hardware.remove(restored,"usb0");restored=ConfigStore.restore(ConfigStore.snapshot(restored));
        check(!Hardware.migrateHostConsole(restored)&&restored.ports.get(rs232).hidden,"removed adapter stays absent after save/load");
        var old=new Device("pc");Hardware.ensure(old).profile="Laptop-PT";
        check(Hardware.migrateHostConsole(old)&&old.hardware.modules.get("usb0").equals("USB-RS232"),"legacy laptop gets its existing RS232 mapped to an installed adapter");
        check(old.ports.size()==2&&old.ports.get(rs232).hardwareSlot.equals("usb0")&&!old.ports.get(rs232).hidden,"legacy migration preserves cable index and visible console");
        check(!Hardware.migrateHostConsole(old),"legacy migration is idempotent");
        var pc=new Device("pc");Hardware.initializeHost(pc,false);check(!pc.ports.get(rs232).hidden&&!Hardware.slots(pc).containsKey("usb0"),"desktop retains built-in RS232");orientations(pc,false,0);
        var sw=new Device("switch");Hardware.ensure(sw).powered=false;Hardware.changeProfile(sw,"2960-24TT");
        orientations(router,false,0);orientations(sw,false,0);orientations(sw,true,0);orientations(sw,true,1);
        double[][] fronts={{.5,.1},{.9,.5},{.5,.9},{.1,.5}};
        for(int turns=0;turns<4;turns++){var p=DeviceOrientation.rotate(.5,.2,.1,turns);check(near(p.x(),fronts[turns][0])&&near(p.z(),fronts[turns][1])&&near(p.y(),.2),"north/east/south/west front centres are correct");}
        orientations(new Device("endpoint"),false,0);
        var wrap=DeviceOrientation.rotate(.2,.3,.4,4);check(near(wrap.x(),.2)&&near(wrap.z(),.4),"full turn restores placement");
        return checks;
    }
}
