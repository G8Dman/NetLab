package dev.netlab.core;

import dev.netlab.automation.core.Claim;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real routing/link deadlines with the same escrow settlement boundary used by automation. */
public final class ItemPacketTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void address(Device d,int port,String ip){d.ports.get(port).ip=ip;d.ports.get(port).up=true;}
    private static void wire(Network n,long a,int ap,long b,int bp){n.wires.add(new Network.Wire(new Network.Endpoint(a,ap),new Network.Endpoint(b,bp),8));}
    public static int run(){
        checks=0;var n=new Network();var source=new Device("endpoint");var target=new Device("endpoint");var sw=new Device("switch");
        n.devices.put(1L,source);n.devices.put(2L,sw);n.devices.put(3L,target);address(source,0,"10.3.0.11");address(target,0,"10.3.0.12");wire(n,1,0,2,0);wire(n,2,1,3,0);
        check(n.ping(1,"10.3.0.12").success(),"item topology resolves bidirectional ARP");
        n.clock=new PacketClock();n.cursor=0;n.traffic.clear();
        n.speedPolicy=(a,b,v,p)->p.endsWith("TCP")&&(a.device()==1||b.device()==3)?.0001:1;
        var ack=n.transmit(3,"10.3.0.11","tcp",5420,"ACCEPT".getBytes(StandardCharsets.UTF_8));long acknowledged=n.cursor;
        check(ack.result().success()&&acknowledged<10_000_000,"reverse acknowledgement is fast on this asymmetric link");
        n.traffic.clear();byte[] manifest="{\"op\":\"ITEM_DATA\",\"item\":\"minecraft:coal\",\"count\":16}".getBytes(StandardCharsets.UTF_8);
        var delivery=n.transmit(1,"10.3.0.12","tcp",5420,manifest);long arrival=n.cursor;
        check(delivery.result().success()&&delivery.result().path().equals(List.of(1L,2L,3L))&&Arrays.equals(delivery.payload(),manifest),"item data follows the actual source-switch-receiver route");
        var frames=n.traffic.stream().filter(t->t.protocol().equals("TCP")).toList();
        check(frames.size()==2&&frames.get(1).start()>=frames.getFirst().finish(),"successive item hops use store-and-forward link deadlines");
        check(frames.getLast().finish()==arrival&&arrival-acknowledged>20_000_000_000L,"slow forward data cannot be replaced by the already arrived reverse ACK");
        check(frames.stream().allMatch(t->t.bytes()==manifest.length+64),"item packet size is the transmitted manifest plus frame overhead");
        var escrow=new Claim(16);check(escrow.begin(),"ticket locked during item flight");var queue=new TimedQueue();
        var settled=queue.submit(acknowledged,arrival,()->{escrow.finish(16);return true;});
        queue.advance(arrival-1,10);check(!settled.isDone()&&escrow.remaining()==16&&!escrow.begin(),"no payout and no second redemption before final item hop arrives");
        queue.advance(arrival,10);check(settled.join()&&escrow.remaining()==0&&!escrow.begin(),"payout happens once at the same deadline as the final trace hop");
        var mark=new TrafficMark("ITEM_DATA","minecraft:coal",16);
        check(mark.hop("TCP",false).equals(mark)&&mark.hop("SPAN/TCP",false).equals(mark),"clear LAN and SPAN traces retain item hints");
        for(String protocol:List.of("ARP","NDP","DHCP-OFFER"))check(mark.hop(protocol,true).equals(TrafficMark.NONE),"address/service frames do not inherit item icons: "+protocol);
        check(mark.hop("UDP",true).equals(new TrafficMark("VPN","",0))&&mark.hop("SPAN/UDP",true).item().isEmpty(),"encrypted WAN and its mirrors never publish item ID or quantity");
        var liquid=new TrafficMark("FLUID_DATA","minecraft:water",16000);check(liquid.hop("TCP",false).count()==16000,"fluid traces carry mB volumes above item stack limit");check(liquid.hop("UDP",true).item().isEmpty()&&liquid.hop("UDP",true).count()==0,"VPN hides fluid identity and mB volume");
        boolean rejected=false;try{new TrafficMark("ITEM_DATA","minecraft:coal",1000);}catch(IllegalArgumentException error){rejected=true;}check(rejected,"fluid support does not relax item stack limits");

        n=new Network();source=new Device("endpoint");target=new Device("endpoint");var r1=new Device("router");var r2=new Device("router");
        n.devices.put(1L,source);n.devices.put(2L,r1);n.devices.put(3L,r2);n.devices.put(4L,target);
        address(source,0,"10.1.0.2");source.gateway="10.1.0.1";address(r1,0,"10.1.0.1");address(r1,1,"172.16.0.1");address(r2,0,"172.16.0.2");address(r2,1,"10.2.0.1");address(target,0,"10.2.0.2");target.gateway="10.2.0.1";
        r1.routes.add(new Device.Route("10.2.0.0","255.255.255.0","172.16.0.2"));r2.routes.add(new Device.Route("10.1.0.0","255.255.255.0","172.16.0.1"));wire(n,1,0,2,0);wire(n,2,1,3,0);wire(n,3,1,4,0);
        var tunnel=new RouterTunnel(new VpnPeers.Tunnel(2,3,new CryptoChannel(CryptoChannel.derive("item-flight-test",new byte[0]))));n.routerPayload=tunnel;
        n.speedPolicy=(a,b,v,p)->p.endsWith("UDP")?.001:1;
        delivery=n.transmit(1,"10.2.0.2","tcp",5420,manifest);
        check(delivery.result().success()&&tunnel.completed()&&Arrays.equals(delivery.payload(),manifest),"router VPN restores item manifest before receiver delivery");
        var wan=n.traffic.stream().filter(t->t.protocol().equals("UDP")).toList();
        check(wan.size()==1&&wan.getFirst().from().device()==2&&wan.getFirst().to().device()==3&&wan.getFirst().finish()-wan.getFirst().start()>1_000_000_000L,"encrypted item frame uses WAN UDP timing and UDP slowdown");
        var wirePayload=n.wirePayloads.get(wan.getFirst());check(wirePayload.encrypted()&&!Arrays.equals(wirePayload.bytes(),manifest)&&Arrays.equals(wirePayload.bytes(),tunnel.envelope()),"WAN capture records actual encrypted bytes");
        var payloads=n.wirePayloads;check(n.traffic.stream().filter(t->t.protocol().equals("TCP")).allMatch(t->!payloads.get(t).encrypted()&&Arrays.equals(payloads.get(t).bytes(),manifest)),"LAN captures remain plaintext before and after VPN routers");
        check(n.traffic.stream().filter(t->t.protocol().equals("TCP")).allMatch(t->mark.hop(t.protocol(),true).equals(mark)),"plaintext item icons resume on both LAN portions of routed transfer");
        n.traffic.clear();n.wirePayloads.clear();var datagramTunnel=new RouterTunnel(new VpnPeers.Tunnel(2,3,new CryptoChannel(CryptoChannel.derive("mobile-backhaul-test",new byte[0]))));n.routerPayload=datagramTunnel;
        var datagram=n.transmit(1,"10.2.0.2","udp",2152,manifest);check(datagram.result().success()&&datagramTunnel.completed()&&Arrays.equals(datagram.payload(),manifest),"UDP mobile bearer survives Cisco router VPN");
        var datagrams=n.traffic.stream().filter(t->t.protocol().equals("UDP")).toList();check(datagrams.size()==3&&datagrams.stream().filter(t->payloads.get(t).encrypted()).count()==1,"UDP origin does not hide which part of backhaul is encrypted");
        target.ports.get(0).up=false;check(!n.routePath(1,"10.2.0.2").success(),"broken destination link prevents an item route");
        var visual=new StudyClock();visual.reset(100,1_000_000_000L);
        check(Math.abs(visual.advance(100,1_025_000_000L,false,true,false,1)-5.025)<1e-9,"live trace interpolates the authoritative game tick");
        check(Math.abs(visual.advance(100,2_000_000_000L,false,true,false,1)-5.05)<1e-9,"wall-clock stall cannot animate an item seconds ahead of delivery");
        check(Math.abs(visual.advance(101,2_010_000_000L,false,true,false,1)-5.05)<1e-9,"next tick continues at the real server timestamp");
        check(Math.abs(visual.advance(101,2_030_000_000L,true,true,false,1)-5.05)<1e-9,"paused world does not advance an item marker");
        check(Math.abs(visual.advance(101,2_130_000_000L,false,false,false,.1)-5.06)<1e-9,"optional slow playback remains explicitly separate from network time");
        check(Math.abs(visual.advance(101,2_140_000_000L,false,false,true,1)-5.06)<1e-9,"playback pause freezes only trace time");
        check(Math.abs(visual.advance(110,2_150_000_000L,false,true,false,1)-5.5)<1e-9,"returning to live time follows current delivery clock");
        return checks;
    }
}
