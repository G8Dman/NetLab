package dev.netlab.core;

import java.util.*;

public final class SwitchingTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static void wire(Network n,long a,int ap,long b,int bp){CoreTest.wire(n,a,ap,b,bp);}
    private static String cmd(Console c,Device d,Network n,long id,String command){String out=c.execute(d,n,id,command);if(out.startsWith("%"))throw new AssertionError(command+": "+out);return out;}
    private static Network lab(){var n=new Network();n.devices.put(1L,new Device("pc"));n.devices.put(2L,new Device("switch"));n.devices.put(3L,new Device("pc"));CoreTest.address(n.devices.get(1L),0,"192.168.1.2");CoreTest.address(n.devices.get(3L),0,"192.168.1.3");wire(n,1,0,2,0);wire(n,2,1,3,0);return n;}
    public static int run(){checks=0;var n=lab();var sw=n.devices.get(2L);var c=new Console();cmd(c,sw,n,2,"enable");
        check(n.ping(1,"192.168.1.3").success(),"L2 ping and return");
        check(sw.os().learnedMacs.size()==2,"MACs learned from both sources");
        check(n.traffic.stream().anyMatch(e->e.protocol().equals("ARP"))&&n.traffic.stream().anyMatch(e->e.protocol().equals("ICMP")),"trace contains actual ARP and ICMP events");
        check(cmd(c,sw,n,2,"show mac address-table dynamic").contains("DYNAMIC"),"MAC show reads learned table");
        cmd(c,sw,n,2,"clear mac address-table dynamic");check(sw.os().learnedMacs.isEmpty(),"clear dynamic MAC");
        n.lanPeers(1,0);check(sw.os().learnedMacs.isEmpty(),"topology probes do not learn MACs");
        cmd(c,sw,n,2,"configure terminal");cmd(c,sw,n,2,"vlan 10");cmd(c,sw,n,2,"name USERS");cmd(c,sw,n,2,"exit");
        cmd(c,sw,n,2,"interface range gig0/0 - 1");cmd(c,sw,n,2,"switchport access vlan 10");
        check(sw.ports.get(0).accessVlan==10&&sw.ports.get(1).accessVlan==10,"range applies both access VLANs");
        check(n.ping(1,"192.168.1.3").success(),"same access VLAN forwards");
        cmd(c,sw,n,2,"exit");cmd(c,sw,n,2,"interface gig0/1");cmd(c,sw,n,2,"switchport access vlan 20");
        check(!n.ping(1,"192.168.1.3").success(),"different or missing VLAN isolates");
        cmd(c,sw,n,2,"switchport access vlan 10");cmd(c,sw,n,2,"exit");cmd(c,sw,n,2,"no vlan 10");
        check(!n.ping(1,"192.168.1.3").success(),"deleted VLAN deactivates access ports");
        cmd(c,sw,n,2,"vlan 10");cmd(c,sw,n,2,"exit");cmd(c,sw,n,2,"interface gig0/0");cmd(c,sw,n,2,"switchport mode access");cmd(c,sw,n,2,"switchport port-security");cmd(c,sw,n,2,"switchport port-security mac-address sticky");
        check(n.ping(1,"192.168.1.3").success(),"sticky permits first source");
        check(sw.ports.get(0).stickyMacs.contains(Switching.mac(Addressing.mac(1,0))),"sticky learns configuration MAC");
        var newcomer=new Device("pc");CoreTest.address(newcomer,0,"192.168.1.2");n.devices.put(4L,newcomer);n.wires.removeIf(w->w.a().device()==1||w.b().device()==1);wire(n,4,0,2,0);
        check(!n.ping(4,"192.168.1.3").success()&&sw.ports.get(0).errDisabled,"security shutdown blocks changed source");
        check(sw.os().securityViolations.getOrDefault(0,0L)>0,"violation count increments");
        cmd(c,sw,n,2,"shutdown");cmd(c,sw,n,2,"no shutdown");check(!sw.ports.get(0).errDisabled,"admin bounce recovers err-disabled");
        cmd(c,sw,n,2,"switchport port-security violation restrict");check(!n.ping(4,"192.168.1.3").success()&&!sw.ports.get(0).errDisabled,"restrict drops without disabling");
        var saved=ConfigStore.restore(ConfigStore.snapshot(sw));check(!saved.ports.get(0).stickyMacs.isEmpty()&&saved.os().learnedMacs.isEmpty(),"startup keeps sticky, excludes dynamic table");

        var mesh=new Network();for(long id=10;id<=12;id++)mesh.devices.put(id,new Device("switch"));
        wire(mesh,10,0,11,0);wire(mesh,11,1,12,0);wire(mesh,12,1,10,1);
        check(mesh.switching.blocked(1).size()==1,"triangle STP blocks one endpoint");
        mesh.wires.removeLast();check(mesh.switching.blocked(1).isEmpty(),"STP reconverges on link removal");
        var s=mesh.devices.get(10L);s.ports.get(0).bpduGuardMode="enable";mesh.switching.blocked(1);check(s.ports.get(0).errDisabled,"BPDU guard sees attached switch");

        var pair=new Network();var a=new Device("switch");var b=new Device("switch");pair.devices.put(20L,a);pair.devices.put(21L,b);wire(pair,20,0,21,0);wire(pair,20,1,21,1);
        for(int i=0;i<2;i++){a.ports.get(i).channelGroup=1;b.ports.get(i).channelGroup=2;a.ports.get(i).channelMode="active";b.ports.get(i).channelMode="passive";}
        check(pair.switching.channelUp(20,0)&&pair.switching.blocked(1).isEmpty(),"LACP members treated as single STP edge");
        a.ports.get(0).channelMode="passive";check(!pair.switching.channelUp(20,0),"passive-passive does not bundle");
        a.ports.get(0).channelMode="active";b.ports.get(0).accessVlan=10;check(!pair.switching.channelUp(20,0),"incompatible VLAN suspends channel");b.ports.get(0).accessVlan=1;
        a.ports.get(0).channelGroup=0;b.ports.get(0).channelGroup=0;a.ports.get(0).switchMode="dynamic auto";b.ports.get(0).switchMode="dynamic auto";
        check(!pair.switching.trunk(20,0),"DTP auto-auto stays access");b.ports.get(0).switchMode="dynamic desirable";check(pair.switching.trunk(20,0)&&pair.switching.trunk(21,0),"DTP desirable-auto forms trunk");
        var ca=new Console();cmd(ca,a,pair,20,"enable");cmd(ca,a,pair,20,"configure terminal");cmd(ca,a,pair,20,"interface gig0/0");cmd(ca,a,pair,20,"switchport trunk allowed vlan 10,20");cmd(ca,a,pair,20,"switchport trunk allowed vlan add 30");cmd(ca,a,pair,20,"switchport trunk allowed vlan remove 20");check(Switching.vlans(a.ports.get(0).allowedVlans).equals(Set.of(10,30)),"trunk add/remove preserves other VLANs");
        a.os().vtpDomain="LAB";b.os().vtpDomain="LAB";b.os().vtpMode="client";a.os().vlans.put(10,"STAFF");a.os().vtpRevision=1;pair.switching.syncVtp();check(b.os().vlans.containsKey(10),"VTP copies VLAN DB over negotiated trunk");
        a.os().vtpPassword="wrong";a.os().vlans.put(30,"NO");a.os().vtpRevision++;pair.switching.syncVtp();check(!b.os().vlans.containsKey(30),"VTP domain password mismatch blocks update");
        var svi=lab();var sc=new Console();var sd=svi.devices.get(2L);cmd(sc,sd,svi,2,"enable");cmd(sc,sd,svi,2,"configure terminal");cmd(sc,sd,svi,2,"interface vlan 1");cmd(sc,sd,svi,2,"ip address 192.168.1.10 255.255.255.0");cmd(sc,sd,svi,2,"no shutdown");check(svi.ping(1,"192.168.1.10").success(),"SVI address reachable from access VLAN");
        var dhcp=lab();var dhcpSwitch=dhcp.devices.get(2L);var server=new Device("router");CoreTest.address(server,0,"192.168.1.1");dhcp.devices.put(5L,server);wire(dhcp,5,0,2,2);
        var pool=new RouterState.Pool();pool.network="192.168.1.0";pool.gateway="192.168.1.1";server.os().pools.put("LAN",pool);
        dhcpSwitch.os().dhcpSnooping=true;dhcpSwitch.os().dhcpSnoopingVlans="1";
        check(dhcp.renewDhcp(1,0).contains("no usable offer"),"snooping rejects server on untrusted switch port");
        dhcpSwitch.ports.get(2).dhcpTrust=true;
        check(dhcp.renewDhcp(1,0).startsWith("DHCP address"),"trusted DHCP server allowed");
        check(!dhcpSwitch.os().snoopingBindings.isEmpty(),"snooping binding created");
        var mirror=lab();var ms=mirror.devices.get(2L);mirror.devices.put(8L,new Device("pc"));wire(mirror,2,3,8,0);
        ms.os().spanSources.put(1,ms.portName(0));ms.os().spanDestinations.put(1,ms.portName(3));
        check(mirror.ping(1,"192.168.1.3").success(),"SPAN source still forwards normally");
        check(mirror.traffic.stream().anyMatch(t->t.protocol().startsWith("SPAN/")),"SPAN mirrors actual input events");
        check(mirror.lanPeers(1,0).stream().noneMatch(h->h.end().device()==8),"SPAN destination cannot carry ordinary data");
        var staticNet=lab();var staticSw=staticNet.devices.get(2L);staticSw.os().staticMacs.put(Switching.mac(Addressing.mac(3,0))+" vlan 1",staticSw.portName(3));
        check(!staticNet.ping(1,"192.168.1.3").success(),"static MAC controls unicast egress");
        return checks;
    }
}
