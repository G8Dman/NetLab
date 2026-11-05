package dev.netlab.core;

import dev.netlab.automation.core.Claim;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual payload forwarding, VPN primitives/policy, and ticket settlement; no game launch. */
public final class AutomationTest {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    private static void rejected(Runnable action,String name){try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){checks++;return;}throw new AssertionError(name);}
    private static void wire(Network n,long a,int ap,long b,int bp){n.wires.add(new Network.Wire(new Network.Endpoint(a,ap),new Network.Endpoint(b,bp),3));}
    private static void ip(Device d,int port,String ip){d.ports.get(port).ip=ip;d.ports.get(port).up=true;}
    public static int run(){
        var claim=new Claim(64);check(claim.begin(),"first bearer claim starts");check(!claim.begin(),"reentrant claim blocked");
        claim.finish(13);check(claim.remaining()==51,"partial insertion preserves escrow remainder");
        check(claim.begin(),"remainder can be claimed");claim.finish(0);check(claim.remaining()==51,"full target cannot consume escrow");
        claim.begin();claim.abort();check(claim.begin(),"aborted operation releases busy flag");claim.finish(51);
        check(claim.remaining()==0&&!claim.begin(),"replayed redeemed ticket cannot create items");
        rejected(()->claim.finish(1),"settlement without claim rejected");rejected(()->new Claim(-1),"negative escrow rejected");
        var c=new Claim(2);c.begin();rejected(()->c.finish(3),"overclaim rejected");check(c.remaining()==2,"invalid settlement cannot alter count");
        var key=CryptoChannel.derive("CaseSensitive-Secret-9347",new byte[]{1,2,3});var channel=new CryptoChannel(key);
        byte[] body="{\"ticket\":\"00000000-0000-4000-8000-000000000001\",\"item\":\"minecraft:diamond\",\"count\":64}".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted=channel.seal(body);check(!new String(encrypted,StandardCharsets.UTF_8).contains("minecraft:diamond"),"ciphertext hides manifest");
        var tampered=encrypted.clone();tampered[tampered.length-1]^=1;rejected(()->channel.open(tampered),"modified authentication tag rejected");
        var forgedSequence=encrypted.clone();forgedSequence[7]=100;rejected(()->channel.open(forgedSequence),"sequence number authenticated");
        check(Arrays.equals(body,channel.open(encrypted)),"failed authentication does not advance replay window");
        rejected(()->channel.open(encrypted),"ciphertext replay rejected");
        rejected(()->new CryptoChannel(CryptoChannel.derive("wrong-secret",new byte[0])).open(encrypted),"wrong key rejected");
        rejected(()->channel.open(new byte[20]),"truncated encrypted envelope rejected");
        var early=channel.seal(body);var later=channel.seal(body);check(Arrays.equals(body,channel.open(later))&&Arrays.equals(body,channel.open(early)),"out-of-order authenticated packets allowed");
        var old=channel.seal(body);for(int i=0;i<65;i++)channel.open(channel.seal(body));rejected(()->channel.open(old),"packet outside replay window rejected");
        var n=new Network();var server=new Device("server");var r1=new Device("router");var r2=new Device("router");var target=new Device("endpoint");var sw=new Device("switch");var analyzer=new Device("sniffer");
        n.devices.put(1L,server);n.devices.put(2L,r1);n.devices.put(3L,r2);n.devices.put(4L,target);n.devices.put(5L,sw);n.devices.put(6L,analyzer);
        ip(server,0,"192.168.1.2");server.gateway="192.168.1.1";ip(r1,0,"192.168.1.1");ip(r1,1,"10.0.0.1");ip(r2,0,"10.0.0.2");ip(r2,1,"192.168.2.1");ip(target,0,"192.168.2.2");target.gateway="192.168.2.1";
        r1.routes.add(new Device.Route("192.168.2.0","255.255.255.0","10.0.0.2",1,""));r2.routes.add(new Device.Route("192.168.1.0","255.255.255.0","10.0.0.1",1,""));
        wire(n,1,0,2,0);wire(n,2,1,5,0);wire(n,5,1,3,0);wire(n,3,1,4,0);wire(n,5,2,6,0);
        sw.os().spanSources.put(1,sw.portName(0));sw.os().spanDestinations.put(1,sw.portName(2));
        var plain=n.transmit(1,"192.168.2.2","tcp",5420,body);check(plain.result().success()&&plain.destination()==4&&Arrays.equals(body,plain.payload()),"addon endpoint payload traverses routers and switch");
        check(n.traffic.stream().anyMatch(t->t.protocol().equals("SPAN/TCP")&&t.to().device()==6),"actual application frames mirrored to analyzer");
        var peers=new VpnPeers();n.vpnCommand=(id,line)->peers.configure(id,line);var cli=new Console();cli.execute(r1,n,2,"enable");cli.execute(r1,n,2,"conf t");
        String command="crypto netlab-vpn peer 10.0.0.2 secret CaseSensitive-Secret-9347";
        check(cli.execute(r1,n,2,command).equals("VPN peer updated"),"VPN command reaches server policy");check(!cli.echo(command).contains("CaseSensitive"),"VPN secret hidden in console echo");
        var path=plain.result().path();check(peers.hasPolicy(n,path)&&peers.tunnel(n,path)==null,"one-sided VPN policy cannot establish tunnel");
        peers.configure(3,"crypto netlab-vpn peer 10.0.0.1 secret DifferentSecret-9274");check(peers.tunnel(n,path)==null,"mismatched router secrets rejected");
        peers.configure(3,"crypto netlab-vpn peer 10.0.0.1 secret CaseSensitive-Secret-9347");var tunnel=peers.tunnel(n,path);check(tunnel!=null&&tunnel.from()==2&&tunnel.to()==3,"matching router policies establish path tunnel");
        byte[] wire=tunnel.channel().seal(body);var delivery=n.transmit(1,"192.168.2.2","udp",4500,wire);
        check(delivery.result().success()&&Arrays.equals(wire,delivery.payload()),"network forwards ciphertext byte for byte");check(Arrays.equals(body,tunnel.channel().open(delivery.payload())),"destination decrypts authenticated transmitted manifest");
        check(peers.tunnel(n,path).channel()==tunnel.channel(),"reused channel retains replay window");
        var reverse=n.probe(4,"192.168.1.2","udp",4500);check(reverse.success()&&peers.tunnel(n,reverse.path())!=null,"reverse response has VPN path");
        var restored=new VpnPeers();peers.savedKeys().forEach(restored::restore);check(restored.tunnel(n,path)!=null,"persistent peer configuration restores");
        var oldBoot=tunnel.channel().seal(body);rejected(()->restored.tunnel(n,path).channel().open(oldBoot),"old boot ciphertext cannot replay after restart");
        String show=peers.configure(2,"show crypto netlab-vpn");check(show.contains("10.0.0.2")&&!show.contains("CaseSensitive")&&!show.contains(peers.savedKeys().values().iterator().next()),"show VPN excludes key material");
        peers.configure(2,"no crypto netlab-vpn peer 10.0.0.2");check(peers.tunnel(n,path)==null&&peers.hasPolicy(n,path),"removal invalidates tunnel while remote policy remains");
        peers.configure(3,"no crypto netlab-vpn peer 10.0.0.1");check(!peers.hasPolicy(n,path),"all peer policies removed");
        r2.ports.get(1).up=false;check(!n.transmit(1,"192.168.2.2","tcp",5420,body).result().success(),"shutdown prevents application delivery");
        rejected(()->n.transmit(1,"192.168.2.2","tcp",5420,new byte[65537]),"oversized application packet rejected");
        return checks;
    }
}
