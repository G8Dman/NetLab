package dev.netlab.core;

import java.util.*;

public final class WirelessTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static Device laptop(){var d=new Device("pc");Hardware.ensure(d).powered=false;Hardware.install(d,"radio0","WPC300N");d.hardware.powered=true;return d;}
    private static Map<Long,Wireless.Position> positions(){var p=new HashMap<Long,Wireless.Position>();p.put(1L,new Wireless.Position(0,0,0));p.put(2L,new Wireless.Position(10,0,0));p.put(3L,new Wireless.Position(11,0,0));p.put(4L,new Wireless.Position(2,0,0));return p;}
    private static Network topology(){var n=new Network();n.devices.put(1L,laptop());n.devices.put(2L,new Device("ap"));var router=new Device("router");CoreTest.address(router,0,"192.168.8.1");n.devices.put(3L,router);CoreTest.wire(n,2,0,3,0);
        var pool=new RouterState.Pool();pool.network="192.168.8.0";pool.gateway="192.168.8.1";router.os().pools.put("WLAN",pool);return n;}
    public static int run(){checks=0;var n=topology();var pos=positions();var client=n.devices.get(1L);var ap=n.devices.get(2L);
        check(Wireless.scan(n,pos,1).size()==1,"installed adapter finds nearby AP");
        check(Wireless.radio(client)==2&&client.portName(2).equals("Wireless0"),"laptop has separate Wireless0 preserving Ethernet/RS232");
        check(!CableKind.compatible("copper",client,2,ap,1),"radio is not a copper connector");
        Wireless.join(client,"NetLab","open","");Wireless.rebuild(n,pos);
        check(client.wireless.associated==2&&n.linkUp(1,2),"open association creates actual radio link");
        check(n.renewDhcp(1,2).startsWith("DHCP address"),"DHCP crosses AP into wired network");
        check(client.ports.get(2).ip.startsWith("192.168.8.")&&client.gateway.equals("192.168.8.1"),"radio adapter obtains address and gateway");
        check(n.ping(1,"192.168.8.1").success(),"Wi-Fi to wired gateway echo and return");
        check(n.traffic.stream().anyMatch(t->t.from().port()==2&&t.from().device()==1),"wireless transmissions recorded for goggles");
        var pc=new Device("pc");CoreTest.address(pc,0,"192.168.8.20");n.devices.put(5L,pc);CoreTest.wire(n,5,0,3,1);CoreTest.address(n.devices.get(3L),1,"192.168.8.30");
        Wireless.configure(ap,"Secure","wpa2","wireless123",6,32,false);
        Wireless.join(client,"Secure","wpa2","incorrect");Wireless.rebuild(n,pos);
        check(client.wireless.associated==Long.MIN_VALUE&&!n.linkUp(1,2),"wrong WPA2 password denies association");
        Wireless.join(client,"Secure","open","");Wireless.rebuild(n,pos);check(client.wireless.associated==Long.MIN_VALUE,"security mode mismatch denies association");
        Wireless.join(client,"Secure","wpa2","wireless123");Wireless.rebuild(n,pos);check(client.wireless.associated==2,"correct WPA2 credential associates");
        check(!ap.wireless.keyHash.contains("wireless123")&&!client.wireless.clientKey.contains("wireless123"),"credentials not stored as plaintext");
        Wireless.configure(ap,"Secure","wpa2","",11,32,false);Wireless.rebuild(n,pos);check(n.linkUp(1,2)&&ap.wireless.channel==11,"blank AP password retains configured secret");
        pos.put(1L,new Wireless.Position(100,0,0));Wireless.rebuild(n,pos);check(client.wireless.associated==Long.MIN_VALUE&&!n.ping(1,"192.168.8.1").success(),"out of range disconnects actual data path");
        pos.put(1L,new Wireless.Position(0,0,0));Wireless.rebuild(n,pos);check(client.wireless.associated==2,"return into range reconnects saved profile");
        Hardware.ensure(ap).powered=false;Wireless.rebuild(n,pos);check(client.wireless.associated==Long.MIN_VALUE,"powered off AP disconnects clients");ap.hardware.powered=true;
        ap.wireless.enabled=false;Wireless.rebuild(n,pos);check(client.wireless.associated==Long.MIN_VALUE,"disabled radio disconnects clients");ap.wireless.enabled=true;
        Wireless.rebuild(n,pos);n.renewDhcp(1,2);
        var second=laptop();n.devices.put(4L,second);Wireless.join(second,"Secure","wpa2","wireless123");Wireless.rebuild(n,pos);n.renewDhcp(4,2);
        check(!second.ports.get(2).ip.equals(client.ports.get(2).ip)&&!second.ports.get(2).ip.isEmpty(),"multiple clients obtain distinct DHCP leases");
        check(n.ping(1,second.ports.get(2).ip).success(),"AP forwards between two clients on shared radio");
        ap.wireless.isolated=true;check(!n.ping(1,second.ports.get(2).ip).success(),"client isolation blocks wireless peer echo");
        check(n.ping(1,"192.168.8.1").success(),"isolation preserves access to wired gateway");ap.wireless.isolated=false;
        var closer=new Device("ap");Wireless.configure(closer,"Secure","wpa2","wireless123",1,32,false);n.devices.put(6L,closer);pos.put(6L,new Wireless.Position(1,0,0));Wireless.rebuild(n,pos);
        check(client.wireless.associated==6,"saved profile roams to nearer compatible AP");
        Hardware.ensure(client).powered=false;Hardware.remove(client,"radio0");Wireless.rebuild(n,pos);check(!n.linkUp(1,2),"removed wireless adapter cannot transmit");
        var desktop=new Device("pc");Hardware.ensure(desktop).profile="PC-PT";desktop.hardware.powered=false;
        try{Hardware.install(desktop,"radio0","WPC300N");throw new AssertionError("PC rejects PCMCIA");}catch(IllegalArgumentException expected){checks++;}
        Hardware.install(desktop,"radio0","WMP300N");check(Wireless.radio(desktop)>=0,"PCI wireless card installs into desktop");
        var snapshot=ConfigStore.restore(ConfigStore.snapshot(second));check(snapshot.hardware.modules.containsValue("WPC300N")&&snapshot.wireless.clientSsid.equals("Secure"),"adapter and saved Wi-Fi profile survive snapshot");
        var noAdapter=new Device("pc");n.devices.put(7L,noAdapter);pos.put(7L,new Wireless.Position(0,0,0));check(Wireless.scan(n,pos,7).isEmpty(),"PC without adapter cannot scan radio networks");
        var moduleNet=new Network();var host=laptop();var moduleRouter=new Device("router");Hardware.ensure(moduleRouter).powered=false;Hardware.install(moduleRouter,"hwic0","HWIC-AP-AG-B");moduleRouter.hardware.powered=true;
        moduleNet.devices.put(10L,moduleRouter);moduleNet.devices.put(11L,host);Wireless.configure(moduleRouter,"ModuleAP","open","",6,32,false);Wireless.join(host,"ModuleAP","open","");
        var mp=new HashMap<Long,Wireless.Position>();mp.put(10L,new Wireless.Position(0,0,0));mp.put(11L,new Wireless.Position(5,0,0));Wireless.rebuild(moduleNet,mp);
        check(host.wireless.associated==10,"HWIC-AP-AG-B operates as real AP");
        int svi=moduleRouter.findInterface("vlan1",true);CoreTest.address(moduleRouter,svi,"192.168.9.1");CoreTest.address(host,Wireless.radio(host),"192.168.9.2");host.gateway="192.168.9.1";
        check(moduleNet.ping(11,"192.168.9.1").success(),"wireless clients reach router module SVI");
        var remote=new Device("pc");CoreTest.address(remote,0,"10.9.0.2");remote.gateway="10.9.0.1";CoreTest.address(moduleRouter,0,"10.9.0.1");moduleNet.devices.put(12L,remote);CoreTest.wire(moduleNet,10,0,12,0);
        check(moduleNet.ping(11,"10.9.0.2").success(),"HWIC AP routes through SVI into another subnet with return path");
        Wireless.state(host).enabled=false;check(Wireless.scan(moduleNet,mp,11).isEmpty(),"disabled client radio cannot scan");
        var dual=laptop();dual.gateway="10.22.0.1";dual.os().dns.add("10.22.0.53");dual.ports.get(Wireless.radio(dual)).ip="1.2.3.4";
        Wireless.join(dual,"NetLab","open","");check(dual.preferredPort==Wireless.radio(dual)&&dual.ports.get(dual.preferredPort).ip.isEmpty(),"join selects radio and starts fresh DHCP instead of retaining static address");
        Wireless.disconnect(dual);check(dual.preferredPort==0&&dual.gateway.equals("10.22.0.1")&&dual.os().dns.contains("10.22.0.53"),"disconnect restores wired gateway and DNS");
        return checks;
    }
}
