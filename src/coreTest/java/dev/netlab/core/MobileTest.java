package dev.netlab.core;

import dev.netlab.mobile.core.*;
import java.util.*;

/** Pure LTE abstraction checks: no game/client/server/world construction. */
public final class MobileTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static void fails(Runnable action,String name){boolean failed=false;try{action.run();}catch(IllegalArgumentException e){failed=true;}check(failed,name);}
    public static int run(){
        MobileHardware.init();
        var pc=new Device("pc");Hardware.initializeHost(pc,true);var h=Hardware.ensure(pc);h.powered=false;
        Hardware.install(pc,"mobile0",MobileHardware.MODEM);check(MobileHardware.modem(pc),"laptop accepts cellular modem");
        int p=MobileHardware.cellular(pc);check(p>=0&&pc.ports.get(p).name.equals("Cellular0"),"stable cellular interface");
        check(PortLayout.jacks(pc,false,0).stream().noneMatch(j->j.port()==p),"cellular interface has no fake wired socket");
        Hardware.install(pc,"radio0","WPC300N");Hardware.install(pc,"usb0","USB-RS232");Hardware.install(pc,"app0","CISCO-AUTOMATION");check(h.modules.size()==4,"modem coexists with WiFi console and automation");
        var desktop=new Device("pc");Hardware.initializeHost(desktop,false);Hardware.ensure(desktop).powered=false;Hardware.install(desktop,"mobile0",MobileHardware.MODEM);check(MobileHardware.modem(desktop),"same modem accepts desktop slot");
        var bbu=new Device(MobileHardware.BBU);MobileHardware.initialize(bbu);Hardware.ensure(bbu).powered=false;
        check(!MobileHardware.bbuReady(bbu),"BBU cannot operate with missing cards");
        Hardware.install(bbu,"control0",MobileHardware.CONTROL);Hardware.install(bbu,"baseband0",MobileHardware.BASEBAND);Hardware.install(bbu,"fronthaul0",MobileHardware.FRONTHAUL);Hardware.ensure(bbu).powered=true;
        check(MobileHardware.bbuReady(bbu),"complete BBU works");check(bbu.ports.stream().filter(q->q.medium.equals("fronthaul")).count()==3,"three dedicated fronthaul ports");
        var rru=new Device(MobileHardware.RRU);MobileHardware.initialize(rru);var ant=new Device(MobileHardware.ANTENNA);MobileHardware.initialize(ant);
        check(CableKind.compatible("fiber",bbu,2,rru,0),"optical fronthaul connects BBU to RRU");check(!CableKind.compatible("fiber",bbu,0,rru,0),"Ethernet uplink cannot masquerade as fronthaul");
        check(CableKind.compatible("rf",ant,0,rru,1),"RF coax connects antenna and RRU");check(!CableKind.compatible("copper",ant,0,rru,1),"Ethernet cable cannot replace coax");
        check(ConsoleLink.compatible(pc,1,bbu,1),"laptop RS232 can configure BBU");check(ConsoleLink.compatible(pc,1,rru,3),"RRU console supported");
        for(String module:MobileHardware.MODULES)check(!Hardware.CREATIVE_ONLY.contains(module)&&!ModuleFabrication.MODULES.contains(module),"mobile module uses its addon crafting recipe, not the basic assembler "+module);
        var sim=new SimCredentials();sim.issuer="core-A";sim.operator="00101";sim.label="Student";var subscribers=new SubscriberRegistry();
        var sub=subscribers.provision(sim);check(!sub.active,"new SIM is not active before acknowledgement");check(subscribers.provision(sim)==sub&&subscribers.entries.size()==1,"retry cannot duplicate subscriber");
        var clone=new SimCredentials();clone.id=sim.id;fails(()->subscribers.provision(clone),"same SIM identity with different key rejected");
        String nonce=MobileAuth.secret();String proof=MobileAuth.proof(sim.key,nonce,"attach|"+sim.id);
        check(!subscribers.authenticate(sim,sim.issuer,sim.operator,nonce,proof),"inactive SIM rejected");
        fails(()->subscribers.activate(sim.id,"not a proof",nonce),"invalid activation rejected");
        subscribers.activate(sim.id,MobileAuth.proof(sim.key,nonce,"activate|"+sim.id),nonce);
        check(subscribers.authenticate(sim,sim.issuer,sim.operator,nonce,proof),"activated SIM authenticates");
        check(!subscribers.authenticate(sim,"core-B",sim.operator,nonce,proof),"other core cannot use SIM");check(!subscribers.authenticate(sim,sim.issuer,"00202",nonce,proof),"other operator rejected");
        check(!subscribers.authenticate(sim,sim.issuer,sim.operator,MobileAuth.secret(),proof),"challenge replay rejected");
        check(!MobileAuth.verify(sim.key,nonce,"activate|"+sim.id,proof),"attach proof cannot activate SIM");
        String verifier=MobileAuth.credential("operator-password");String key=MobileAuth.derive("operator-password",MobileAuth.salt(verifier));check(key.equals(MobileAuth.verifierKey(verifier)),"PBKDF2 credential verifier");
        check(!key.equals(MobileAuth.derive("other-password",MobileAuth.salt(verifier))),"wrong operator password rejected");fails(()->MobileAuth.credential("123"),"weak operator password rejected");
        String ip=subscribers.address(sub,"10.64.0.1");check(ip.equals("10.64.0.10")&&subscribers.address(sub,"10.64.0.1").equals(ip),"subscriber keeps stable IP");
        var sim2=new SimCredentials();var sub2=subscribers.provision(sim2);check(!subscribers.address(sub2,"10.64.0.1").equals(ip),"subscriber addresses do not collide");sub.active=false;check(!subscribers.authenticate(sim,sim.issuer,sim.operator,nonce,proof),"revocation rejects next authentication");
        var site=new RadioModel.Site("sector",0,24,0,20,6300,0,6,24,512,0);var signal=RadioModel.signal(site,List.of(site),0,2,100,0);
        check(signal.bars()>0&&signal.quality()>0,"elevated station covers front of sector");check(RadioModel.received(site,0,2,100,0)>RadioModel.received(site,0,2,-100,0),"sector front stronger than back");
        check(RadioModel.received(site,0,2,100,20)<signal.rsrp(),"walls attenuate received signal");check(RadioModel.signal(site,List.of(site),0,2,600,0).bars()==0,"range limit enforced");
        var low=new RadioModel.Site("low",0,4,0,20,6300,0,6,4,512,0);check(RadioModel.signal(low,List.of(low),0,2,50,0).bars()==0,"ground-level antenna cannot cover clients");
        var jammer=new RadioModel.Site("second",0,24,5,20,6300,0,6,24,512,0);check(RadioModel.signal(site,List.of(site,jammer),0,2,100,0).sinr()<signal.sinr(),"cochannel interference reduces SINR");
        check(RadioModel.rate(signal,true,2)==RadioModel.rate(signal,true,1)/2,"multiple clients share sector bandwidth");
        var clock=new PacketClock();var queue=new Network.Endpoint(900,1000);var first=clock.send(queue,0,1000,1,100,8,1);var second=clock.send(queue,0,1000,1,100,8,1);check(second.start()>=first.start()+8_000_000,"sector serializes concurrent uplink packets");
        routed(pc,desktop);
        faceplates();
        return checks;
    }
    private static void faceplates(){
        for(String kind:List.of(MobileHardware.ANTENNA,MobileHardware.RRU,MobileHardware.BBU,MobileHardware.CORE,MobileHardware.STATION)){
            var d=new Device(kind);MobileHardware.initialize(d);if(kind.equals(MobileHardware.BBU)){Hardware.ensure(d).powered=false;Hardware.install(d,"fronthaul0",MobileHardware.FRONTHAUL);}
            var profile=PortLayout.profile(d);check(profile!=null,"mobile device has a front-panel profile: "+kind);
            for(boolean rack:new boolean[]{false,true})for(int bay=0;bay<(rack?2:1);bay++){
                var jacks=PortLayout.jacks(d,rack,bay);long count=d.ports.stream().filter(p->!p.hidden&&!p.virtual&&!p.medium.equals("cellular")).count();check(jacks.size()==count,"one socket per physical mobile port, excluding cellular gateway");
                for(var jack:jacks){
                    check(PortLayout.pick(d,rack,bay,jack.x(),jack.y())==jack.port(),"mobile socket center selects correct port");
                    check(profile.labels().containsKey(jack.port()%128),"mobile socket has a visible caption");
                    check(jack.z()<(rack?.02+1.5/16*.9:1.5/16),"mobile socket sits in front of solid front panel");
                    for(double dx:new double[]{-jack.width()*.45,0,jack.width()*.45})for(double dy:new double[]{-jack.height()*.45,0,jack.height()*.45}){
                        double ex=-1.5,ey=1.8,ez=-2,x=jack.x()+dx,y=jack.y()+dy,plane=rack?0:1.5/16,t=(plane-ez)/(jack.z()-ez);
                        check(PortLayout.pickRay(d,rack,bay,ex,ey,ez,ex+(x-ex)*t,ey+(y-ey)*t,plane)==jack.port(),"angled click across visible socket area hits same port");
                    }
                    check(jacks.stream().filter(other->other.port()!=jack.port()).noneMatch(other->other.contains(jack.x(),jack.y())),"neighboring socket targets do not overlap");
                }
            }
            if(kind.equals(MobileHardware.BBU)){int index=d.ports.size()-1;String slot=d.ports.get(index).hardwareSlot;Hardware.remove(d,slot);check(PortLayout.jacks(d,false,0).stream().noneMatch(j->j.medium().equals("fronthaul")),"removed fronthaul card leaves no phantom sockets");}
        }
    }
    private static void routed(Device pc,Device other){
        var core=new Device(MobileHardware.CORE);MobileHardware.initialize(core);var sw=new Device("switch");var server=new Device("pc");var n=new Network();
        var cli=new Console();cli.execute(core,n,2,"enable");cli.execute(core,n,2,"conf t");check(cli.execute(core,n,2,"int gi0/0").isEmpty(),"mobile core accepts abbreviated physical interface command");cli.execute(core,n,2,"ip address 192.168.77.2 255.255.255.0");cli.execute(core,n,2,"no shut");cli.execute(core,n,2,"exit");cli.execute(core,n,2,"ip route 192.168.88.0 255.255.255.0 192.168.77.1");check(core.routes.size()==1&&core.ports.getFirst().ip.equals("192.168.77.2"),"core CLI configures actual L3 address and static route");
        n.devices.put(1L,pc);n.devices.put(2L,core);n.devices.put(3L,sw);n.devices.put(4L,server);n.devices.put(5L,other);
        Hardware.ensure(pc).powered=true;Hardware.ensure(other).powered=true;int p=MobileHardware.cellular(pc),q=MobileHardware.cellular(other),cp=MobileHardware.cellular(core);
        pc.preferredPort=p;pc.ports.get(p).ip="10.64.0.10";pc.gateway="10.64.0.1";other.preferredPort=q;other.ports.get(q).ip="10.64.0.11";other.gateway="10.64.0.1";
        core.ports.get(0).ip="192.168.77.2";server.ports.get(0).ip="192.168.77.10";server.gateway="192.168.77.2";
        n.wires.add(new Network.Wire(new Network.Endpoint(1,p),new Network.Endpoint(2,cp),100,"cellular"));n.wires.add(new Network.Wire(new Network.Endpoint(5,q),new Network.Endpoint(2,cp),120,"cellular"));
        n.wires.add(new Network.Wire(new Network.Endpoint(2,0),new Network.Endpoint(3,0),2,"copper"));n.wires.add(new Network.Wire(new Network.Endpoint(3,1),new Network.Endpoint(4,0),2,"copper"));
        check(n.ping(1,"10.64.0.1").success(),"UE can ping EPC gateway without ARP on radio bearer");check(n.ping(1,"192.168.77.10").success(),"mobile UE reaches wired LAN through EPC");
        check(n.ping(4,"10.64.0.10").success(),"wired return path reaches correct UE");
        check(n.ping(1,"10.64.0.11").success(),"same subnet cellular users route through EPC");
        byte[] payload=new byte[1024];Arrays.fill(payload,(byte)42);var delivery=n.transmit(1,"192.168.77.10","tcp",5420,payload);check(delivery.result().success()&&Arrays.equals(payload,delivery.payload())&&delivery.destination()==4,"application bytes arrive through mobile L3 forwarding");
        n.traffic.clear();n.ping(1,"10.64.0.1");check(n.traffic.stream().noneMatch(t->t.protocol().equals("ARP")),"radio does not emit Ethernet ARP");
        server.gateway="";check(!n.ping(1,"192.168.77.10").success(),"missing return route fails mobile ping");server.gateway="192.168.77.2";
        n.wires.removeIf(w->w.type().equals("cellular")&&(w.a().device()==1||w.b().device()==1));check(!n.ping(1,"192.168.77.10").success(),"detached modem cannot send packets");
    }
}
