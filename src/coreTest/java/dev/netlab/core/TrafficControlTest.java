package dev.netlab.core;

import java.util.*;

/** Timing, LAN identity, delegated authorization and physical-connection history regressions. */
public final class TrafficControlTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void denied(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(message);}
    private static void wire(Network n,long a,int ap,long b,int bp){n.wires.add(new Network.Wire(new Network.Endpoint(a,ap),new Network.Endpoint(b,bp),8));}
    private static void address(Device d,String ip){d.ports.getFirst().ip=ip;d.ports.getFirst().up=true;}
    public static int run(){
        var clock=new PacketClock();var a=new Network.Endpoint(1,0);var b=new Network.Endpoint(2,0);
        var first=clock.send(a,0,64,100,8,1,1);check(first.start()==0&&first.finish()==1_005_160,"real link time includes 64-byte serialization, 8m propagation and configured latency");
        var queued=clock.send(a,0,64,100,8,1,1);check(queued.start()==5120,"same egress is serialized rather than overlapping packets");
        check(clock.send(b,0,64,100,8,1,1).start()==0,"opposite direction has its own full-duplex queue");
        var slow=new PacketClock().send(a,0,64,100,8,1,.001);check(slow.finish()==first.finish()*1000,"controller factor scales physical transmission time");
        check(new PacketClock().send(a,0,1500,100,8,1,1).finish()>first.finish(),"larger payload takes longer");
        denied(()->new PacketClock().send(a,0,64,100,8,1,0),"zero speed cannot create an infinite queue");
        denied(()->new PacketClock().send(a,0,60000,.001,8,1,.000001),"excessive duration is bounded");
        var queue=new TimedQueue();var delivered=new ArrayList<Integer>();var late=queue.submit(0,100,()->{delivered.add(2);return 2;});var early=queue.submit(0,50,()->{delivered.add(1);return 1;});
        queue.advance(49,10);check(!late.isDone()&&!early.isDone()&&delivered.isEmpty(),"queued requests have no application effect before packet arrival");
        queue.advance(50,10);check(early.isDone()&&!late.isDone()&&delivered.equals(List.of(1)),"first due message completes independently of later traffic");
        queue.advance(100,10);check(late.isDone()&&delivered.equals(List.of(1,2)),"delayed payload completes only after its simulated deadline");
        var failed=queue.submit(100,101,()->{throw new IllegalArgumentException("link removed");});queue.advance(101,10);check(failed.isCompletedExceptionally(),"invalidated delivery reports failure without stopping the queue");
        var n=new Network();var pc1=new Device("pc");var pc2=new Device("pc");var sw=new Device("switch");
        n.devices.put(1L,pc1);n.devices.put(2L,sw);n.devices.put(3L,pc2);address(pc1,"192.168.1.2");address(pc2,"192.168.1.3");wire(n,1,0,2,0);wire(n,2,1,3,0);
        check(n.routePath(1,"192.168.1.3").success(),"application route can be planned without synthetic TCP/UDP probes");
        check(n.cursor==0&&n.traffic.isEmpty()&&pc1.os().arp.isEmpty(),"planning cannot learn ARP or bypass protocol-specific timing");
        n.speedPolicy=(from,to,vlan,protocol)->protocol.equals("ARP")?.001:1;
        check(n.ping(1,"192.168.1.3").success(),"first ping resolves ARP through slowed frames");long firstRtt=n.cursor;
        check(firstRtt>4_000_000_000L,"ARP slowdown delays actual request/reply completion, not just a visual marker");
        check(n.traffic.stream().anyMatch(t->t.protocol().equals("ARP")&&t.finish()-t.start()>1_000_000_000L),"ARP traces carry authoritative duration");
        check(n.traffic.stream().filter(t->t.protocol().equals("ICMP")).allMatch(t->t.finish()-t.start()<2_000_000L),"ARP filter does not slow ICMP");
        n.traffic.clear();long before=n.cursor;n.ping(1,"192.168.1.3");check(n.cursor-before<10_000_000L&&n.traffic.stream().noneMatch(t->t.protocol().equals("ARP")),"cached ARP does not invent extra ARP traffic or its delay");
        sw.os().vlans.put(10,"USERS");sw.os().vlans.put(20,"OTHER");sw.ports.get(0).accessVlan=10;sw.ports.get(1).accessVlan=10;n.speedPolicy=(from,to,vlan,protocol)->vlan==10?.01:1;
        n.traffic.clear();before=n.cursor;check(n.ping(1,"192.168.1.3").success()&&n.cursor-before>400_000_000L,"VLAN 10 rule slows untagged access traffic");
        check(n.traffic.stream().allMatch(t->t.vlan()==10),"native/access VLAN is kept even without an 802.1Q tag");
        sw.ports.get(0).accessVlan=20;sw.ports.get(1).accessVlan=20;n.traffic.clear();before=n.cursor;n.ping(1,"192.168.1.3");check(n.cursor-before<10_000_000L&&n.traffic.stream().allMatch(t->t.vlan()==20),"another VLAN stays at normal speed");
        var dhcp=new Network();var dhcpHost=new Device("pc");var dhcpServer=new Device("router");dhcp.devices.put(10L,dhcpHost);dhcp.devices.put(11L,dhcpServer);address(dhcpServer,"192.168.8.1");wire(dhcp,10,0,11,0);
        var pool=new RouterState.Pool();pool.network="192.168.8.0";pool.gateway="192.168.8.1";dhcpServer.os().pools.put("LAN",pool);dhcp.speedPolicy=(from,to,vlan,protocol)->TrafficAuthority.matches("dhcp",protocol)?.001:1;
        check(dhcp.renewDhcp(10,0).startsWith("DHCP address:")&&dhcp.cursor>2_000_000_000L,"DHCP completion waits for slowed DISCOVER and OFFER arrivals");
        check(dhcp.traffic.stream().allMatch(t->t.bytes()==300&&t.finish()-t.start()>1_000_000_000L),"DHCP protocol frames carry their own size and schedule");
        var radioNet=new Network();var ap=new Device("ap");var radioSwitch=new Device("switch");radioSwitch.os().vlans.put(10,"RADIO");radioSwitch.ports.get(0).accessVlan=10;radioNet.devices.put(2L,ap);radioNet.devices.put(3L,radioSwitch);wire(radioNet,2,0,3,0);
        for(long id:List.of(1L,4L)){var host=new Device("pc");Hardware.ensure(host).powered=false;Hardware.install(host,"radio0","WPC300N");host.hardware.powered=true;Wireless.join(host,"NetLab","open","");radioNet.devices.put(id,host);}
        Wireless.rebuild(radioNet,Map.of(1L,new Wireless.Position(0,0,0),2L,new Wireless.Position(2,0,0),3L,new Wireless.Position(4,0,0),4L,new Wireless.Position(1,0,1)));radioNet.speedPolicy=(from,to,vlan,protocol)->vlan==10?.001:1;
        for(long id:List.of(1L,4L)){var port=radioNet.devices.get(id).ports.get(2);port.ip="192.168.10."+id;port.dhcp=false;}
        check(radioNet.ping(1,"192.168.10.4").success()&&radioNet.cursor>4_000_000_000L,"VLAN 10 controller also slows two clients bridged by its AP");
        check(radioNet.traffic.stream().allMatch(t->t.vlan()==10),"untagged radio frames inherit the wired access VLAN");
        var ctrl=new Device("traffic");var ctrl2=new Device("traffic");n.devices.put(4L,ctrl);n.devices.put(5L,ctrl2);wire(n,4,0,2,2);wire(n,5,0,2,3);sw.ports.get(2).accessVlan=10;sw.ports.get(3).accessVlan=20;
        var fabric=new LanFabric(n);check(fabric.root(new Network.Endpoint(4,0)).equals(fabric.root(new Network.Endpoint(5,0))),"LAN authority spans switched fabric while VLANs remain separate scopes");
        check(fabric.attachedVlan(4)==10&&fabric.attachedVlan(5)==20,"controllers get the VLAN of their actual access port");
        var router=new Device("router");var far=new Device("switch");
        // Router boundary tested with its own wired fabric, not a Console link.
        var routed=new Network();routed.devices.put(1L,sw);routed.devices.put(2L,router);routed.devices.put(3L,far);wire(routed,1,0,2,0);wire(routed,2,1,3,0);
        var separated=new LanFabric(routed);check(!separated.root(new Network.Endpoint(2,0)).equals(separated.root(new Network.Endpoint(2,1))),"router does not extend LAN authority to its other network");
        var auth=new TrafficAuthority();String admin="Admin-secret-239874",user="Student-vlan-secret-2468";
        String hash=TrafficAuthority.hash(admin);check(!hash.contains(admin)&&!hash.equals(TrafficAuthority.hash(admin)),"passwords are salted PBKDF2 hashes");
        var members=fabric.anchors(new Network.Endpoint(4,0));var domain=auth.resolve(members,true);var root=auth.setup(domain,ctrl.identity,hash);
        check(auth.valid(root)&&root.vlan==0,"first controller becomes administrator after setup");
        check(auth.resolve(members,true)==domain,"reconnecting to a registered LAN preserves authority");
        denied(()->auth.setup(domain,"rogue",TrafficAuthority.hash("Another-secret-234")),"another controller cannot replace the existing LAN password");
        denied(()->auth.authenticate(domain,"rogue",0,"Wrong-password-123"),"another LAN controller must know the same admin password");
        var second=auth.authenticate(domain,ctrl2.identity,0,admin);check(auth.valid(second),"same plaintext password works despite randomized salt");
        denied(()->auth.grantPassword(root,10,admin),"delegated password must differ from LAN administrator password");
        auth.grant(root,10,TrafficAuthority.hash(user));var delegated=auth.authenticate(domain,"student-controller",10,user);check(auth.valid(delegated)&&delegated.vlan==10,"delegated password grants VLAN 10 only");
        denied(()->auth.authenticate(domain,ctrl.identity,10,user),"VLAN login cannot downgrade an admin controller to disable global policy");
        denied(()->auth.speed(delegated,0,"all",.1),"VLAN user cannot throttle the parent LAN");
        denied(()->auth.speed(delegated,20,"arp",.1),"VLAN user cannot throttle a neighboring VLAN");
        denied(()->auth.grant(delegated,20,hash),"VLAN user cannot grant privileges");
        denied(()->auth.clear(delegated,0),"VLAN user cannot clear admin rules");
        auth.speed(root,0,"all",.5);auth.speed(delegated,10,"arp",.001);var active=new HashSet<>(List.of(ctrl.identity,"student-controller"));
        check(auth.factor(domain.id,10,"ARP",active)==.001&&auth.factor(domain.id,20,"ARP",active)==.5,"VLAN/protocol rule is confined beneath the LAN policy");
        auth.speed(delegated,10,"all",1);check(auth.factor(domain.id,10,"TCP",active)==.5,"student cannot accelerate above the admin's global restriction");
        check(auth.factor(domain.id,10,"ARP",Set.of(ctrl.identity))==.5,"unplugged controller no longer applies its scoped rules");
        auth.speed(root,10,"tcp",.1);auth.clear(delegated,10);check(auth.factor(domain.id,10,"TCP",active)==.1,"student clear cannot remove administrator VLAN rules");
        auth.revoke(root,10);check(!auth.valid(delegated),"revocation invalidates an already authenticated VLAN controller");
        auth.grant(root,10,TrafficAuthority.hash(user));check(!auth.valid(delegated),"regrant does not resurrect old authorization");
        var newRoot=auth.changeAdminPassword(root,TrafficAuthority.hash("New-master-secret-123"));check(auth.valid(newRoot)&&!auth.valid(root)&&!auth.valid(second),"master password rotation invalidates old admin controllers");
        var other=auth.resolve(Set.of("another-switch/0"),true);auth.setup(other,"other",TrafficAuthority.hash("Other-master-98765"));var merged=new HashSet<>(members);merged.add("another-switch/0");
        denied(()->auth.resolve(merged,true),"conflicting registered LANs cannot silently overwrite each other's authority");
        check(TrafficAuthority.matches("arp","SPAN/ARP")&&TrafficAuthority.matches("dhcp","DHCP-OFFER")&&!TrafficAuthority.matches("arp","TCP"),"protocol filtering handles SPAN and DHCP subtypes");
        var transcript=new ConsoleTranscript();String token=transcript.token;transcript.appendText("Switch>");transcript.remember("enable",false);transcript.remember("Secret-123456",true);transcript.appendText("Switch#");
        check(transcript.token.equals(token)&&transcript.text.contains("Switch>")&&transcript.history.equals(List.of("enable")),"reopening retains scrollback and safe command history without credentials");
        check(transcript.attachSession("first"),"first live console session is attached");long pending=transcript.beginRequest();
        check(!transcript.attachSession("first")&&transcript.currentRequest(pending),"GUI reopening preserves the in-flight operation");
        check(transcript.attachSession("rebooted")&&!transcript.currentRequest(pending)&&transcript.text.contains("Switch#"),"reboot retains text but late output cannot overwrite the new console session");
        String binding=ConsoleTranscript.binding("switch",1,132,2,4);
        check(binding.equals(ConsoleTranscript.binding("switch",1,132,2,4)),"GUI close leaves the physical binding unchanged");
        check(!binding.equals(ConsoleTranscript.binding("switch",1,132,4,6)),"unplug and replug the same endpoints creates a clean history binding");
        check(!binding.equals(ConsoleTranscript.binding("other-device",1,132,2,4)),"replacement device cannot inherit old transcript");
        transcript.appendText("x".repeat(160000));for(int i=0;i<200;i++)transcript.remember("show "+i,false);
        check(transcript.text.length()==150000&&transcript.history.size()==100,"transcripts are bounded");
        String unicode="История 🔒\n".repeat(20000);var chunks=TextChunks.split(unicode);check(String.join("",chunks).equals(unicode),"large Unicode history survives chunking");
        try{for(String chunk:chunks){var bytes=new java.io.ByteArrayOutputStream();new java.io.DataOutputStream(bytes).writeUTF(chunk);}}catch(java.io.IOException error){throw new AssertionError("NBT-compatible history chunk overflow",error);}check(chunks.stream().allMatch(c->c.length()<=16000),"every saved history chunk fits NBT modified UTF-8");
        check(new Device("traffic").physicalPortCount()==2&&ConsoleLink.compatible(new Device("pc"),1,ctrl,1),"rack controller has one RJ45 and one real Console port");
        return checks;
    }
}
