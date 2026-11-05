package dev.netlab.core;

import java.util.*;

/** Reported VLAN/help/SFP regressions, including preservation of an existing wired configuration. */
public final class CliRegressionTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void command(Console c,Device d,Network n,String line){String r=c.execute(d,n,1,line);if(r.startsWith("%"))throw new AssertionError(line+": "+r);}
    private static int port(Device d,String slot){return Hardware.portsIn(d,slot).stream().filter(i->!d.ports.get(i).hidden).findFirst().orElseThrow();}
    public static int run(){checks=0;
        var sw=new Device("switch");var n=new Network();n.devices.put(1L,sw);var c=new Console();
        command(c,sw,n,"enable");command(c,sw,n,"conf t");
        String help=c.execute(sw,n,1,"vlan ?");
        check(help.contains("<1-4094>")&&!help.contains("vlan 10"),"VLAN help describes the accepted ID range, not known VLAN examples");
        check(c.mode==2&&c.prompt(sw).equals("Switch(config)#"),"asking VLAN help leaves global configuration active");
        check(c.execute(sw,n,1,"vlan?").equals("vlan"),"partial keyword help differs from argument help");
        check(Set.of(c.execute(sw,n,1,"do ?").split("\n")).equals(Set.of("do ping","do show")),"do help lists only the next command level");
        String showHelp=c.execute(sw,n,1,"do sh ?");
        check(showHelp.contains("do show ip")&&!showHelp.contains("do show ip interface brief"),"abbreviated context help does not dump entire command paths");
        check(c.execute(sw,n,1,"do show running-config ?").equals("<cr>"),"complete command help shows end of command");
        check(c.execute(sw,n,1,"vlan 10 ?").equals("<cr>")&&!sw.os().vlans.containsKey(10),"argument help never creates a VLAN");
        command(c,sw,n,"vlan 10");check(c.mode==8&&c.prompt(sw).equals("Switch(config-vlan)#"),"VLAN creation enters the correctly labelled VLAN mode");
        command(c,sw,n,"name STUDENTS");check(sw.os().vlans.get(10).equals("STUDENTS"),"VLAN name applies inside VLAN mode");
        command(c,sw,n,"do show vlan brief");check(c.mode==8&&c.prompt(sw).contains("config-vlan"),"do show keeps VLAN mode");
        command(c,sw,n,"exit");check(c.mode==2&&c.prompt(sw).contains("(config)#"),"exit returns from VLAN mode to global config");
        command(c,sw,n,"vlan 20");command(c,sw,n,"end");check(c.mode==1&&c.prompt(sw).equals("Switch#"),"end explicitly returns to privileged EXEC");
        command(c,sw,n,"conf t");String before=c.context;check(c.execute(sw,n,1,"vlan 4095").startsWith("%")&&c.mode==2&&c.context.equals(before),"invalid VLAN cannot change configuration context");
        check(c.complete(sw,"vlan 2").choices().contains("vlan 20 "),"Tab still completes concrete existing VLAN IDs");
        var r=new Device("router");Hardware.ensure(r).powered=false;
        Hardware.install(r,"hwic0","HWIC-1GE-SFP");int p=port(r,"hwic0"),count=r.ports.size();var cage=r.ports.get(p);
        cage.ip="192.168.44.1";cage.description="Existing uplink";cage.up=true;
        check(r.portName(p).equals("GigabitEthernet0/0/0"),"HWIC0 uses slot/subslot/port with subslot zero");
        Hardware.install(r,"hwic0/sfp0","GLC-GE-100FX");
        check(r.ports.size()==count&&r.ports.get(p)==cage&&r.portName(p).equals("GigabitEthernet0/0/0"),"reported 100FX transceiver keeps the same single logical interface");
        check(cage.maxMbps==100&&cage.medium.equals("fiber")&&cage.ip.equals("192.168.44.1")&&cage.up,"100FX sets fiber/100Mbps without wiping IP or admin state");
        check(r.ports.stream().noneMatch(x->x.name.startsWith("FastEthernet")&&!x.hidden),"100FX does not manufacture a second FastEthernet interface");
        check(r.findInterface("gi0/1",false)==1,"the router's separate built-in RJ45 remains present");
        Hardware.remove(r,"hwic0/sfp0");check(r.ports.get(p)==cage&&!cage.hidden&&cage.medium.equals("sfp"),"removing a transceiver leaves its empty cage and interface");
        Hardware.install(r,"hwic0/sfp0","GLC-T");check(r.ports.size()==count&&cage.medium.equals("copper"),"changing optical SFP to copper does not duplicate the logical port");
        Hardware.install(r,"hwic1","HWIC-1GE-SFP");check(r.portName(port(r,"hwic1")).equals("GigabitEthernet0/1/0"),"HWIC1 legitimately uses subslot one");
        // Simulate the exact old saved port name while retaining its physical array index.
        String old="GigabitEthernet0/1/0";cage.name=old;r.ports.get(port(r,"hwic1")).name="GigabitEthernet0/2/0";r.os().staticMacs.put("001122334455 vlan 10",old);
        var ospf=new RouterState.Routing("ospf","1");ospf.passive.add(old);r.os().routing.put("ospf 1",ospf);
        r.os().nat.add(new RouterState.NatRule("pat","","","1","",old,-1,-1));
        r.routes.add(new Device.Route("0.0.0.0","0.0.0.0","",1,old));
        var legacy=ConfigStore.restore(ConfigStore.snapshot(r));
        check(legacy.ports.get(p).name.equals("GigabitEthernet0/0/0")&&legacy.ports.get(p).ip.equals("192.168.44.1"),"legacy snapshot migrates in place without changing cable indices or IP");
        check(legacy.os().staticMacs.values().contains("GigabitEthernet0/0/0")&&legacy.os().routing.get("ospf 1").passive.contains("GigabitEthernet0/0/0"),"static MAC and passive-interface references migrate");
        check(legacy.os().nat.getFirst().iface().equals("GigabitEthernet0/0/0")&&legacy.routes.getFirst().iface().equals("GigabitEthernet0/0/0"),"NAT and route interface references migrate");
        legacy.normalize();check(legacy.portName(p).equals("GigabitEthernet0/0/0"),"name migration is idempotent");
        var fixed=new Device("switch");Hardware.ensure(fixed).powered=false;Hardware.changeProfile(fixed,"3650-24PS");
        check(fixed.physicalPortCount()==29,"3650 has 24 downlinks, four fixed uplink cages and Console");
        int sfp=fixed.findInterface("gi1/1/1",false),total=fixed.ports.size();check(sfp>=0&&fixed.ports.get(sfp).medium.equals("sfp"),"3650 uplink interface exists before transceiver insertion");
        Hardware.install(fixed,"sfp0","GLC-GE-100FX");check(fixed.ports.size()==total&&port(fixed,"sfp0")==sfp&&fixed.portName(sfp).equals("GigabitEthernet1/1/1"),"fixed switch 100FX uses its existing numbered cage");
        Hardware.remove(fixed,"sfp0");check(!fixed.ports.get(sfp).hidden&&fixed.ports.get(sfp).medium.equals("sfp"),"fixed cage stays visible after SFP removal");
        Hardware.changeProfile(fixed,"2960-24TT");check(fixed.findInterface("gi1/1/1",false)<0,"changing chassis hides unsupported cages");
        Hardware.changeProfile(fixed,"3650-24PS");check(fixed.findInterface("gi1/1/1",true)==sfp,"returning to the chassis reuses the fixed cage index");
        var isr=new Device("router");Hardware.ensure(isr).powered=false;Hardware.changeProfile(isr,"ISR4331");Hardware.install(isr,"nim0","NIM-ES2-4");
        check(isr.portName(port(isr,"nim0")).equals("GigabitEthernet0/1/0"),"ISR NIM0 follows the built-in subslot zero");
        return checks;
    }
}
