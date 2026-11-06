package dev.netlab.core;

import dev.netlab.mobile.core.MobileHardware;
import java.util.*;

public final class PhoneNetworkTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void refused(Runnable action,String message){try{action.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
    public static int run(){
        checks=0;var config=new HandsetConfiguration();String server=UUID.randomUUID().toString();var binding=new HandsetConfiguration.Binding(server,"192.168.77.10","minecraft:overworld");
        refused(()->config.install("automation",new HandsetConfiguration.Installed("CISCO-POCKET-AUTOMATION",null)),"unbound management module must be refused");
        check(!config.has("automation")&&config.epoch==0,"failed installation never consumes the slot or changes epoch");
        config.install("automation",new HandsetConfiguration.Installed("CISCO-POCKET-AUTOMATION",binding));
        check(config.boundTo(server,"minecraft:overworld"),"bound server identity is accepted");
        check(!config.boundTo(UUID.randomUUID().toString(),"minecraft:overworld"),"same-IP replacement server does not inherit binding");
        check(!config.boundTo(server,"minecraft:the_nether"),"binding cannot cross dimensions");
        config.install("diagnostics",new HandsetConfiguration.Installed("CISCO-POCKET-NET",null));config.install("browser",new HandsetConfiguration.Installed("CISCO-POCKET-WEB",null));
        check(config.modules.size()==3&&config.epoch==3,"three independent optional phone slots coexist");
        refused(()->config.install("browser",new HandsetConfiguration.Installed("duplicate",null)),"duplicate module rejected");
        var removed=config.eject("automation");check(removed.binding().equals(binding)&&!config.boundTo(server,"minecraft:overworld")&&config.epoch==4,"ejection retains module binding and revokes device access");
        refused(()->new HandsetConfiguration.Binding("not-a-uuid","192.168.77.10","minecraft:overworld"),"invalid binding identity rejected");
        refused(()->new HandsetConfiguration.Binding(server,"999.1.1.1","minecraft:overworld"),"invalid binding address rejected");
        routedPhone();var internet=internetNetwork();var web=internet.transmit(1,"172.30.77.2","tcp",1080,new byte[]{1,2,3});check(web.result().success()&&web.result().path().containsAll(List.of(2L,3L,5L,6L)),"phone browser path crosses EPC, router, modem and HUB");check(internet.ping(4,"10.64.0.10").success(),"provider gateway has return route to mobile pool");internet.devices.get(3L).routes.removeIf(r->r.network().equals("10.64.0.0"));check(!internet.ping(4,"10.64.0.10").success(),"missing HUB route to mobile pool blocks replies");return checks;
    }
    public static Network internetNetwork(){
        MobileHardware.init();dev.netlab.sputnik.core.SputnikHardware.init();var n=new Network();
        var phone=new Device("pc");phone.ports.clear();var cell=new Device.Port(true);cell.name="Cellular0";cell.medium="cellular";cell.ip="10.64.0.10";phone.ports.add(cell);phone.gateway="10.64.0.1";
        var modem=SputnikTest.device(dev.netlab.sputnik.core.SputnikHardware.MODEM);modem.ports.get(0).ip="192.168.77.1";modem.ports.get(4).ip="10.254.77.2";modem.gateway="10.254.77.1";modem.routes.add(new Device.Route("10.64.0.0","255.255.255.0","192.168.77.2"));
        var hub=SputnikTest.device(dev.netlab.sputnik.core.SputnikHardware.HUB);hub.ports.get(0).ip="172.30.77.1";hub.ports.get(4).ip="10.254.77.1";hub.routes.add(new Device.Route("10.64.0.0","255.255.255.0","10.254.77.2"));
        var gateway=SputnikTest.device(dev.netlab.sputnik.core.SputnikHardware.GATEWAY);gateway.ports.get(0).ip="172.30.77.2";gateway.gateway="172.30.77.1";
        var epc=new Device(MobileHardware.CORE);MobileHardware.initialize(epc);epc.ports.get(0).ip="192.168.66.2";epc.ports.get(2).ip="10.64.0.1";epc.gateway="192.168.66.1";
        var router=new Device("router");router.ports.get(0).ip="192.168.66.1";router.ports.get(1).ip="192.168.77.2";for(var p:router.ports)p.up=true;router.routes.add(new Device.Route("10.64.0.0","255.255.255.0","192.168.66.2"));router.routes.add(new Device.Route("172.30.77.0","255.255.255.0","192.168.77.1"));
        n.devices.put(1L,phone);n.devices.put(2L,modem);n.devices.put(3L,hub);n.devices.put(4L,gateway);n.devices.put(5L,epc);n.devices.put(6L,router);
        n.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(5,2),100,"cellular"));n.wires.add(new Network.Wire(new Network.Endpoint(5,0),new Network.Endpoint(6,0),2));n.wires.add(new Network.Wire(new Network.Endpoint(6,1),new Network.Endpoint(2,0),2));n.wires.add(new Network.Wire(new Network.Endpoint(2,4),new Network.Endpoint(3,4),0,"satellite"));n.wires.add(new Network.Wire(new Network.Endpoint(3,0),new Network.Endpoint(4,0),2));
        n.linkTransits.put("cellular",(net,w,from,ready,bytes,protocol)->net.clock.send(new Network.Endpoint(5,from.device()==1?1000:1001),ready,bytes,from.device()==1?1:5,100,8,1).finish());
        n.linkTransits.put("satellite",(net,w,from,ready,bytes,protocol)->{
            boolean up=from.device()==2;
            var slot=net.clock.send(new Network.Endpoint(3,up?1000:1001),ready,bytes,up?dev.netlab.sputnik.core.SatelliteModel.UP_MBPS:dev.netlab.sputnik.core.SatelliteModel.DOWN_MBPS,0,dev.netlab.sputnik.core.SatelliteModel.ONE_WAY_MS,1);
            net.traffic.add(new Network.Traffic(from,from.equals(w.a())?w.b():w.a(),"SAT/"+protocol,1,false,slot.start(),slot.finish(),bytes));return slot.finish();
        });
        return n;
    }
    private static void routedPhone(){
        MobileHardware.init();var phone=new Device("pc");phone.ports.clear();var cell=new Device.Port(true);cell.name="Cellular0";cell.medium="cellular";cell.ip="10.64.0.10";phone.ports.add(cell);phone.gateway="10.64.0.1";
        var core=new Device(MobileHardware.CORE);MobileHardware.initialize(core);core.ports.get(0).ip="192.168.77.2";core.ports.get(2).ip="10.64.0.1";
        var server=new Device("server");server.ports.get(0).ip="192.168.77.10";server.gateway="192.168.77.2";var sw=new Device("switch");
        var n=new Network();n.devices.put(1L,phone);n.devices.put(2L,core);n.devices.put(3L,sw);n.devices.put(4L,server);
        n.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(2,2),80,"cellular"));n.wires.add(new Network.Wire(new Network.Endpoint(2,0),new Network.Endpoint(3,0),2));n.wires.add(new Network.Wire(new Network.Endpoint(3,1),new Network.Endpoint(4,0),2));
        var ping=n.ping(1,"192.168.77.10");check(ping.success()&&ping.path().contains(2L),"phone reaches LAN only by routing through its EPC");
        byte[] bytes="actual phone payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(String protocol:List.of("udp","tcp")){var delivery=n.transmit(1,"192.168.77.10",protocol,5420,bytes);check(delivery.result().success()&&delivery.destination()==4&&Arrays.equals(delivery.payload(),bytes),"phone carries unchanged "+protocol+" application payload");}
        check(n.ping(4,"10.64.0.10").success(),"return route from server reaches the phone through EPC");
        n.wires.removeFirst();check(!n.ping(1,"192.168.77.10").success(),"losing cellular bearer stops actual phone packets");
    }
}
