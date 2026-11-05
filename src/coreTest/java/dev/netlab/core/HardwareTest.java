package dev.netlab.core;

import java.util.*;

public final class HardwareTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static void rejects(Runnable action,String label){try{action.run();}catch(IllegalArgumentException e){check(true,label);return;}throw new AssertionError(label);}
    private static Device router(){var d=new Device("router");Hardware.ensure(d).powered=false;return d;}
    private static int modulePort(Device d,String slot){return Hardware.portsIn(d,slot).stream().filter(i->!d.ports.get(i).hidden).findFirst().orElseThrow();}
    private static void config(Console c,Device d,Network n,long id,String command){String result=c.execute(d,n,id,command);if(result.startsWith("%")&&!result.equals("% Interface enabled"))throw new AssertionError(command+": "+result);}
    public static int run(){checks=0;
        var a=router();var h=Hardware.ensure(a);
        h.powered=true;rejects(()->Hardware.install(a,"hwic0","HWIC-2T"),"powered module insertion rejected");h.powered=false;
        rejects(()->Hardware.install(a,"hwic0","NIM-2T"),"NIM cannot fit HWIC");
        Hardware.install(a,"hwic0","WIC-2T");int serial=modulePort(a,"hwic0");
        check(Hardware.portsIn(a,"hwic0").size()==2&&a.portName(serial).startsWith("Serial"),"WIC-2T adds two serial interfaces");
        rejects(()->Hardware.install(a,"hwic0","WIC-1T"),"occupied slot rejects replacement");
        Hardware.install(a,"nm0","NM-1E2W");check(Hardware.slots(a).get("nm0/wic0").equals("WIC"),"carrier exposes WIC child slot");
        Hardware.install(a,"nm0/wic0","WIC-1T");rejects(()->Hardware.remove(a,"nm0"),"carrier removal requires empty children");
        Hardware.remove(a,"nm0/wic0");Hardware.remove(a,"nm0");check(!Hardware.slots(a).containsKey("nm0/wic0"),"carrier removal removes child slots");
        Hardware.remove(a,"hwic0");check(a.ports.get(serial).hidden,"removed module ports hidden");
        check(a.findInterface(a.portName(serial),true)<0,"CLI cannot resurrect removed hardware");
        Hardware.install(a,"hwic0","WIC-2T");check(modulePort(a,"hwic0")==serial,"same module reuses permanent port index");
        var b=router();Hardware.install(b,"hwic0","HWIC-2T");int bs=modulePort(b,"hwic0");
        check(CableKind.compatible("serial_dce",a,serial,b,bs),"Serial cable matches serial ports");
        check(!CableKind.compatible("copper",a,serial,b,bs),"copper cable rejected on serial");
        check(!CableKind.compatible("serial_dce",a,0,b,bs),"serial cable rejected on RJ45");
        var n=new Network();n.devices.put(1L,a);n.devices.put(2L,b);n.wires.add(new Network.Wire(new Network.Endpoint(1,serial),new Network.Endpoint(2,bs),5,"serial_dte"));
        CoreTest.address(a,serial,"10.0.0.1");CoreTest.address(b,bs,"10.0.0.2");
        check(!n.linkUp(1,serial),"power off cuts physical link");h.powered=true;b.hardware.powered=true;
        check(n.linkUp(1,serial)&&n.ping(1,"10.0.0.2").success(),"powered serial module carries routed IP");
        check(n.serialRole(1,serial).equals("DTE")&&n.serialRole(2,bs).equals("DCE"),"DTE cable reverses clock role");
        var cli=new Console();config(cli,a,n,1,"enable");config(cli,a,n,1,"configure terminal");config(cli,a,n,1,"interface "+a.portName(serial));
        check(cli.execute(a,n,1,"clock rate 128000").startsWith("%"),"clock rate rejected on DTE");
        h.powered=false;Hardware.install(a,"hwic1","HWIC-1GE-SFP");int sfp=modulePort(a,"hwic1");
        check(!CableKind.compatible("fiber",a,sfp,b,0),"empty SFP cage is not fiber interface");
        Hardware.install(a,"hwic1/sfp0","GLC-GE-100FX");check(a.ports.get(sfp).medium.equals("fiber")&&a.ports.get(sfp).maxMbps==100,"100FX transceiver sets fiber and 100 Mbps");
        var fiber=router();Hardware.install(fiber,"nm0","NM-1FE-FX");int fp=modulePort(fiber,"nm0");
        check(CableKind.compatible("fiber",a,sfp,fiber,fp),"fiber cable between optical ports");
        rejects(()->Hardware.install(a,"hwic1/sfp0","GLC-T"),"occupied SFP cannot be replaced without removal");
        Hardware.remove(a,"hwic1/sfp0");check(a.ports.get(sfp).medium.equals("sfp"),"removing SFP leaves empty cage");Hardware.install(a,"hwic1/sfp0","GLC-T");
        check(a.ports.get(sfp).medium.equals("copper")&&a.ports.get(sfp).maxMbps==1000,"copper SFP changes connector medium");
        String saved=ConfigStore.snapshot(a);var restored=ConfigStore.restore(saved);check(restored.hardware.modules.equals(a.hardware.modules)&&restored.ports.get(sfp).medium.equals("copper"),"hardware configuration survives snapshot");
        Hardware.remove(a,"hwic0");ConfigStore.replace(a,restored);check(a.ports.get(serial).hidden&&!a.hardware.modules.containsKey("hwic0"),"startup cannot restore removed physical module");
        check(a.ports.get(sfp).medium.equals("copper")&&a.hardware.modules.containsKey("hwic1/sfp0"),"reload retains installed transceiver");
        var fixed=new Device("switch");Hardware.ensure(fixed).powered=false;Hardware.changeProfile(fixed,"2960-24TT");
        check(Hardware.slots(fixed).isEmpty()&&fixed.physicalPortCount()==27,"2960 has 24FE +2GE +Console and no module slots");
        rejects(()->Hardware.install(fixed,"pt0","PT-SWITCH-NM-1FGE"),"fixed switch cannot accept PT module");
        var esw=router();Hardware.install(esw,"hwic0","HWIC-4ESW");var ps=Hardware.portsIn(esw,"hwic0");
        check(ps.size()==4&&ps.stream().allMatch(p->esw.ports.get(p).l2),"HWIC-4ESW adds four switching ports");
        var net=new Network();net.devices.put(10L,esw);var pc1=new Device("pc");var pc2=new Device("pc");net.devices.put(11L,pc1);net.devices.put(12L,pc2);
        CoreTest.address(pc1,0,"192.168.5.2");CoreTest.address(pc2,0,"192.168.5.3");CoreTest.wire(net,11,0,10,ps.get(0));CoreTest.wire(net,12,0,10,ps.get(1));esw.hardware.powered=true;
        check(net.ping(11,"192.168.5.3").success(),"EtherSwitch module actually bridges attached PCs");
        var ec=new Console();config(ec,esw,net,10,"enable");config(ec,esw,net,10,"configure terminal");config(ec,esw,net,10,"interface "+esw.portName(ps.get(0)));
        check(ec.execute(esw,net,10,"ip address 192.168.5.1 255.255.255.0").startsWith("%"),"EtherSwitch physical port rejects L3 address");
        config(ec,esw,net,10,"exit");config(ec,esw,net,10,"interface vlan 1");config(ec,esw,net,10,"ip address 192.168.5.1 255.255.255.0");config(ec,esw,net,10,"no shutdown");
        check(net.ping(11,"192.168.5.1").success(),"router EtherSwitch SVI supplies reachable gateway");
        var consolePc=new Device("pc");check(CableKind.compatible("console",consolePc,1,esw,2)&&!CableKind.compatible("copper",consolePc,1,esw,2),"only console cable matches RS232-Console");
        check(Hardware.CATALOG.size()>=45&&Hardware.CATALOG.containsKey("HWIC-AP-AG-B"),"all documented modules registered");
        return checks;
    }
}
