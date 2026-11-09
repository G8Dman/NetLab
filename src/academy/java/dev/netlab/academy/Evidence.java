package dev.netlab.academy;
import com.google.gson.*;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.academy.core.Course;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import java.util.*;
/** Server-side evidence: explicit actor actions, actual CLI state and completed packet paths. */
public final class Evidence {
    private static final Gson JSON=new Gson();
    static Device device(LearningEvents.Event e){return e.host()==null?null:e.bay()==0?e.host().device:e.host().upperDevice;}
    static String fingerprint(Device d){var j=new JsonObject();j.addProperty("name",d.hostname);j.addProperty("gateway",d.gateway);var ports=new JsonArray();for(var x:d.ports)ports.add(x.name+":"+x.ip+":"+x.mask+":"+x.up+":"+x.accessVlan+":"+x.trunk+":"+x.allowedVlans);j.add("ports",ports);for(String key:List.of("automation","netlabmobile","netlabsputnik","netlabmedia"))if(d.extensions.containsKey(key))j.addProperty(key,d.extensions.get(key));try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static void touch(Course.Progress p,Device d){if(d==null)return;if(!p.values.containsKey("device:"+d.identity)&&p.values.keySet().stream().filter(k->k.startsWith("device:")).count()>=256){var it=p.values.keySet().iterator();while(it.hasNext())if(it.next().startsWith("device:")){it.remove();break;}}p.values.put("device:"+d.identity,d.kind);}
    static boolean ownsAction(Course.Progress p,Device d){return d!=null&&p.values.containsKey("device:"+d.identity);}
    static void yes(Course.Progress p,boolean condition,String... ids){if(condition)for(String id:ids)p.prove(id);}
    static int number(Map<String,String> d,String key){try{return Integer.parseInt(d.getOrDefault(key,"0"));}catch(NumberFormatException e){return 0;}}
    static boolean flag(Map<String,String> d,String key){return Boolean.parseBoolean(d.getOrDefault(key,"false"));}
    static JsonObject extension(Device d,String key){try{return d!=null&&d.extensions.containsKey(key)?JsonParser.parseString(d.extensions.get(key)).getAsJsonObject():new JsonObject();}catch(RuntimeException e){return new JsonObject();}}
    static String str(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonPrimitive()?j.get(key).getAsString():"";}
    static boolean bool(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonPrimitive()&&j.get(key).getAsBoolean();}
    public static void crafted(ServerPlayer actor,String item){var p=Academy.progress(actor.serverLevel(),actor.getUUID());
        yes(p,item.equals("netlab:basic_circuit_board"),"B04");yes(p,item.equals("netlab:advanced_circuit_board"),"B13");
        var module=item.startsWith("netlabmobile:");if(module)p.fact("mobile.craft:"+item.substring(item.indexOf(':')+1),true);
        var sat=item.startsWith("netlabsputnik:");if(sat)p.fact("sat.craft:"+item.substring(item.indexOf(':')+1),true);
        yes(p,List.of("sector_antenna","radio_unit","baseband_unit","mobile_core","sim_station","blank_sim").stream().allMatch(x->p.fact("mobile.craft:"+x)),"M01");
        yes(p,List.of("hub","gateway","odu","dish","modem","vsat_dish").stream().allMatch(x->p.fact("sat.craft:"+x)),"S01");
    }
    public static void event(LearningEvents.Event e){var p=Academy.progress(e.level(),e.actor());var d=device(e);touch(p,d);String id=d==null?"":d.identity;var m=e.data();
        switch(e.kind()){
            case "carryon.rack.picked"->{p.values.put("carry:"+id,m.getOrDefault("fingerprint",""));p.values.put("carry.position:"+id,m.getOrDefault("position",""));}
            case "console.tab"->p.values.put("tab:"+id,m.getOrDefault("value",""));
            case "module.craft"->p.prove("B12");
            case "rack.install"->{p.prove("P01");yes(p,e.host().upperDevice!=null,"P02");yes(p,p.values.getOrDefault("removed:"+id,"").equals(JSON.toJson(Hardware.ensure(d).modules)),"P15");}
            case "rack.remove"->p.values.put("removed:"+id,JSON.toJson(Hardware.ensure(d).modules));
            case "placed"->{yes(p,d!=null&&LaptopBattery.laptop(d)&&p.values.getOrDefault("laptop:"+id,"").equals(fingerprint(d)),"P17");}
            case "console.open"->{p.prove("C01");yes(p,flag(m,"portable")&&p.values.getOrDefault("portable:"+id,"").equals(fingerprint(d)),"C12");}
            case "panel.install"->{p.fact("module:"+id,number(m,"portsAdded")>0);if(m.getOrDefault("module","").matches("WPC300N|WMP300N"))p.fact("wifi.module:"+id,true);}
            case "panel.memoryInsert"->p.fact("card:"+id,true);
            case "panel.memoryRead"->yes(p,MemoryCards.present(d)&&MemoryCards.card(d).active,"C10");
            case "panel.power"->{if(!Hardware.ensure(d).powered){p.values.put("off:"+id,fingerprint(d));}else{yes(p,p.fact("card:"+id)&&MemoryCards.present(d)&&MemoryCards.card(d).active,"P08");yes(p,p.fact("module:"+id)&&d.physicalPortCount()>0,"P13");yes(p,p.fact("wifi.module:"+id)&&Wireless.radio(d)>=0,"W02");yes(p,MemoryCards.present(d)&&MemoryCards.card(d).active&&p.values.getOrDefault("saved:"+id,"").equals(fingerprint(d))&&p.values.getOrDefault("off:"+id,"").equals(fingerprint(d)),"C11");if(p.values.getOrDefault("saved:"+id,"").equals(fingerprint(d))&&p.values.getOrDefault("off:"+id,"").equals(fingerprint(d))&&MemoryCards.present(d)&&MemoryCards.card(d).active){p.fact("cycle:"+d.kind,true);p.fact("cycle.id:"+id,true);yes(p,d.kind.equals("media_headend"),"T16");}}}
            case "cable.connect"->{boolean power=flag(m,"power");if(power){p.fact("power:"+id,true);p.prove("P07");}else{int port=number(m,"port")%128;if(port<d.ports.size()){var portD=d.ports.get(port);yes(p,number(m,"anchors")>0&&Set.of("copper","fiber","sfp").contains(portD.medium)&&e.host().network().linkUp(e.host().slotId(e.bay()),port),"P09");yes(p,portD.medium.equals("fiber")&&portD.up,"P14");if(portD.console)p.prove("P07");}if(flag(m,"consumed")){var link=e.host().links.get(number(m,"port"));if(link!=null)p.fact("paid:"+link.circuit(),true);}}}
            case "cable.refund"->yes(p,p.fact("paid:"+m.getOrDefault("circuit","")),"P12");
            case "cable.color"->p.prove("P10");case "cable.name"->{p.fact("named.cable",true);p.fact("named:"+m.getOrDefault("circuit",""),true);}
            case "goggles.cable"->yes(p,p.fact("named.cable"),"P11");
            case "study.packet"->{if(flag(m,"items"))p.fact("cargo.visible",true);}
            case "study.speed"->p.prove("D03");
            default->AddonEvidence.event(e,p,d);
        }
        Academy.combine(p);
    }
    public static void command(LearningEvents.Command c){var e=c.event();var p=Academy.progress(e.level(),e.actor());var a=c.after();var b=c.before();touch(p,a);String cmd=c.canonical().strip(),id=a.identity;boolean ok=!c.result().stripLeading().startsWith("%")&&!c.result().contains("Invalid input")&&!c.result().contains("Incomplete command")&&!c.result().contains("Ambiguous command");
        if(ok){
            yes(p,b.kind.matches("router|switch")&&c.beforeMode()==0&&c.afterMode()==1,"C02");
            if(c.beforeMode()<2&&c.afterMode()==2)p.fact("config.enter:"+id,true);
            yes(p,p.fact("config.enter:"+id)&&cmd.equals("end")&&c.afterMode()==1,"C03");
            boolean name=!a.hostname.equals(b.hostname),ip=false;
            for(int i=0;i<Math.min(a.ports.size(),b.ports.size());i++){var x=a.ports.get(i);var old=b.ports.get(i);if(!x.ip.isEmpty()&&(!x.ip.equals(old.ip)||!x.mask.equals(old.mask)))ip=true;}
            boolean setter=!(cmd.startsWith("show")||cmd.startsWith("do show")||cmd.startsWith("ping")||cmd.startsWith("trace")||cmd.startsWith("ipconfig")||cmd.endsWith("?")||cmd.startsWith("write")||cmd.startsWith("memory")||cmd.startsWith("copy")||cmd.equals("exit")||cmd.equals("end")||cmd.startsWith("terminal"));
            if(name||ip||setter&&!ConfigStore.snapshot(a,false).equals(ConfigStore.snapshot(b,false))){p.fact("configured:"+id,true);if(c.portable()&&!c.phone())p.values.put("portable:"+id,fingerprint(a));if(c.phone())p.prove("M18");}
            yes(p,name,"C04");if(ip)p.fact("ip:"+id,true);
            if(cmd.contains("?"))p.fact("help",true);if(c.phone()&&!c.input().isBlank()){p.fact("phone.cli:"+id,true);yes(p,p.facts.stream().filter(k->k.startsWith("phone.cli:")).count()>=3,"M19");}
            if(!c.input().isBlank()&&!cmd.equals(c.input().strip())&&!cmd.contains("?"))p.prove("C08");
            if(!p.values.getOrDefault("tab:"+id,"").isBlank()&&c.input().strip().startsWith(p.values.get("tab:"+id).strip())&&!c.input().isBlank())p.prove("C07");
            if((cmd.startsWith("show ip interface brief")||cmd.startsWith("do show ip interface brief"))&&p.fact("ip:"+id)&&a.ports.stream().anyMatch(x->x.up&&!x.ip.isBlank()&&!x.console))p.prove("C06");
            if(cmd.startsWith("ipconfig")&&LaptopBattery.laptop(a)&&a.ports.stream().anyMatch(x->x.up&&!x.ip.isBlank()))p.values.put("laptop:"+id,fingerprint(a));
            if((ConfigurationCommands.isSave(cmd)||cmd.equals("memory write"))&&p.fact("configured:"+id)&&(!MemoryCards.required(a)||MemoryCards.present(a)&&MemoryCards.card(a).active)&&!a.os().startup.isBlank()){p.values.put("saved:"+id,fingerprint(a));p.prove("C09");}
            yes(p,cmd.equals("memory read")&&MemoryCards.present(a)&&MemoryCards.card(a).active,"C10");
            yes(p,(cmd.startsWith("show running-config")||cmd.startsWith("do show running-config"))&&p.fact("configured:"+id)&&c.result().contains(a.hostname),"C13");
            yes(p,cmd.contains("show mac address-table")&&!a.os().learnedMacs.isEmpty(),"L04");
            yes(p,cmd.startsWith("show cdp neighbors")&&c.network().devices.entrySet().stream().filter(x->x.getValue().identity.equals(a.identity)).anyMatch(x->c.network().cdp(x.getKey(),false).lines().count()>1),"R16");
            yes(p,c.remote()&&cmd.startsWith("show "),"R15");
            if(cmd.startsWith("nslookup")&&c.result().contains("Address")&&!c.result().toLowerCase(Locale.ROOT).contains("not found"))p.prove("R07");
            if(cmd.startsWith("traceroute")&&c.result().contains("Destination reached")&&c.network().devices.values().stream().anyMatch(x->x.kind.equals("router")&&c.result().contains("  "+x.hostname)))p.prove("R17");
            if(a.wireless!=null&&a.wireless.associated!=Long.MIN_VALUE){p.prove("W04");}
            if(Wireless.accessPoint(a)&&a.wireless!=null&&a.wireless.security.equals("wpa2")&&!a.wireless.keyHash.isBlank()&&p.fact("configured:"+id))p.prove("W03");
        }
        for(var op:c.operations())if(op.kind().equals("ping")){ping(p,e,c.network(),op);var actor=e.level().getServer().getPlayerList().getPlayer(e.actor());if(op.result().success()&&op.result().path().size()>1&&actor!=null&&actor.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(NetLab.GOGGLES.get())){p.prove("D01");yes(p,c.traffic().stream().anyMatch(t->t.protocol().contains("ARP"))&&c.traffic().stream().anyMatch(t->t.protocol().contains("ICMP")),"D02");}if(op.result().success())for(var t:c.traffic())if(!t.dropped()&&c.network().speedPolicy.speed(t.from(),t.to(),t.vlan(),t.protocol())<1&&t.finish()>t.start()){p.prove("D06");yes(p,p.fact("traffic.specific")&&c.network().speedPolicy.speed(t.from(),t.to(),t.vlan(),t.protocol())<c.network().speedPolicy.speed(t.from(),t.to(),t.vlan(),t.protocol().contains("ICMP")?"UDP":"ICMP"),"D07");}}
        if(ok&&c.after().kind.equals("traffic")&&c.input().matches("(?i)speed (lan|vlan [0-9]+) (arp|icmp|tcp|udp|dhcp|ndp) .*"))p.fact("traffic.specific",true);
        for(var op:c.operations())if(op.kind().equals("dhcp-snooping-drop"))p.fact("dhcp.rogue.denied",true);
        if(ok){for(var node:c.network().devices.entrySet()){var d=node.getValue();if(!ownsAction(p,d))continue;
            if(d.kind.equals("switch")&&!d.os().vtpDomain.isBlank()&&c.network().devices.values().stream().anyMatch(x->x!=d&&x.kind.equals("switch")&&x.os().vtpDomain.equals(d.os().vtpDomain)&&x.os().vtpRevision==d.os().vtpRevision&&!d.os().vlans.isEmpty()&&x.os().vlans.equals(d.os().vlans)))p.prove("L19");
            if(d.ports.stream().anyMatch(x->x.dhcp&&!x.ip.isBlank())&&c.network().devices.values().stream().anyMatch(x->x.os().leases.values().stream().anyMatch(l->d.ports.stream().anyMatch(port->port.ip.equals(l.address()))))){p.prove("R05");yes(p,p.fact("dhcp.rogue.denied")&&c.network().devices.values().stream().anyMatch(x->!x.os().snoopingBindings.isEmpty()),"L17");yes(p,c.network().devices.values().stream().anyMatch(x->x.ports.stream().anyMatch(port->!port.helper.isBlank())),"R06");}
        }}
        yes(p,c.operations().stream().anyMatch(op->op.result().success())&&c.traffic().stream().anyMatch(t->t.protocol().startsWith("SPAN/")&&!t.dropped()),"L18");
        AddonEvidence.command(c,p,ok);Academy.combine(p);Academy.changed(e.level(),e.actor());
    }
    private static void ping(Course.Progress p,LearningEvents.Event e,Network n,Network.Operation op){
        var result=op.result();var src=n.devices.get(op.source());if(src==null)return;String key=src.identity+":"+op.destination();
        if(!result.success()){
            String reason=result.text().toLowerCase(Locale.ROOT);String cause=reason.contains("no active source")?"source":reason.contains("no route")||reason.contains("gateway")||reason.contains("return path")?"route":reason.contains("acl")?"acl":reason.contains("vlan")?"vlan":reason.contains("down")||reason.contains("link")?"link":"other";
            if(src.wireless!=null&&src.wireless.associated!=Long.MIN_VALUE){var ap=n.devices.get(src.wireless.associated);var dst=n.devices.get(n.findAddress(op.destination()));if(ap!=null&&ap.wireless!=null&&ap.wireless.isolated&&dst!=null&&dst.wireless!=null&&dst.wireless.associated==src.wireless.associated)p.fact("isolation:"+src.identity,true);}
            p.values.put("failure:"+key,cause);
            var target=n.devices.get(n.findAddress(op.destination()));if(target!=null&&src.kind.equals("pc")&&target.kind.equals("pc")){int a=new LanFabric(n).attachedVlan(op.source()),b=new LanFabric(n).attachedVlan(n.findAddress(op.destination()));yes(p,a>0&&b>0&&a!=b&&result.path().stream().noneMatch(x->n.devices.get(x).kind.equals("router")),"L08");}
            for(var node:n.devices.values())if(ownsAction(p,node)&&node.kind.equals("switch")&&!node.os().securityViolations.isEmpty()){p.prove("L15");p.fact("security:"+node.identity,true);}
            if(cause.equals("acl"))for(long node:result.path()){var dev=n.devices.get(node);if(dev!=null&&dev.ports.stream().anyMatch(x->!x.aclIn.isBlank()||!x.aclOut.isBlank()))p.fact("acl.denied:"+dev.identity,true);}
            int sourceVlan=new LanFabric(n).attachedVlan(op.source());for(var dev:n.devices.values())if(ownsAction(p,dev)&&dev.kind.equals("switch")&&dev.ports.stream().anyMatch(x->x.up&&x.trunk&&!Switching.allowsVlan(x.allowedVlans,sourceVlan)))p.fact("trunk.filtered",true);return;
        }
        if(result.path().size()<2)return;var path=result.path().stream().map(n.devices::get).filter(Objects::nonNull).toList();var end=path.getLast();long switches=path.stream().filter(d->d.kind.equals("switch")).count(),routers=path.stream().filter(d->d.kind.equals("router")).count();boolean clients=src.kind.equals("pc")&&end.kind.equals("pc");
        yes(p,p.fact("isolation:"+src.identity)&&end.wireless==null&&src.wireless!=null&&src.wireless.associated!=Long.MIN_VALUE,"W09");
        if(clients&&switches>0){p.prove("L03");yes(p,switches>1,"L05");}
        if(routers>0){yes(p,path.stream().anyMatch(d->p.fact("acl.denied:"+d.identity)&&d.ports.stream().anyMatch(x->!x.aclIn.isBlank()||!x.aclOut.isBlank())),"R11");yes(p,src.kind.equals("pc")&&end.kind.equals("router"),"R01");yes(p,clients&&!src.gateway.isBlank()&&!end.gateway.isBlank(),"R02");yes(p,clients&&routers>1&&result.path().stream().anyMatch(node->n.devices.get(node).kind.equals("router")&&n.forwardingProtocol(node,op.destination()).equals("static")),"R03");}
        yes(p,op.protocol().equals("icmpv6")&&clients,"R13");yes(p,op.protocol().equals("icmpv6")&&clients&&routers>0,"R14");
        boolean mobile=path.stream().anyMatch(d->d.kind.startsWith("mobile_"));boolean satellite=path.stream().anyMatch(d->d.kind.startsWith("sputnik_"));
        yes(p,mobile&&src.extensions.containsKey("netlab.phone"),"M11");yes(p,mobile&&routers>0,"M13");yes(p,satellite&&end.kind.contains("gateway"),"S11");
        for(var d:path){if(d.wireless!=null&&d.wireless.associated!=Long.MIN_VALUE){p.prove("W05");yes(p,d.ports.stream().anyMatch(x->x.dhcp&&!x.ip.isBlank()),"W06");}if(!d.os().translations.isEmpty())p.prove("R12");}
        for(long dev:result.path()){
            var d=n.devices.get(dev);if(d==null)continue;
            if(d.kind.equals("switch")){yes(p,p.fact("security:"+d.identity)&&d.ports.stream().noneMatch(x->x.errDisabled),"L16");
                int vlan=new LanFabric(n).attachedVlan(op.source());var blocked=n.switching.blocked(Math.max(1,vlan));if(!blocked.isEmpty()){p.prove("L12");p.fact("stp:"+src.identity+":"+op.destination(),true);}else if(p.fact("stp:"+src.identity+":"+op.destination()))p.prove("L13");
                yes(p,d.ports.stream().filter(x->x.up&&!x.console&&x.switchMode.equals("access")&&x.accessVlan>1).count()>=2&&!d.os().vlans.isEmpty(),"L07");
                yes(p,d.ports.stream().anyMatch(x->x.trunk&&x.sent+x.received>0)&&switches>1,"L09");yes(p,p.fact("trunk.filtered")&&d.ports.stream().anyMatch(x->x.trunk&&!x.allowedVlans.equals("all")&&Switching.allowsVlan(x.allowedVlans,new LanFabric(n).attachedVlan(op.source()))),"L10");
                for(int port=0;port<d.ports.size();port++)if(d.ports.get(port).channelGroup>0&&n.switching.channelUp(dev,port))p.prove("L14");
                yes(p,d.ports.stream().anyMatch(x->x.virtual&&x.name.startsWith("Vlan")&&x.ip.equals(op.destination())),"L11");
            }
            if(d.kind.equals("router")){String protocol=n.forwardingProtocol(dev,op.destination());yes(p,protocol.equals("rip"),"R08");yes(p,protocol.equals("ospf"),"R09");yes(p,protocol.equals("eigrp"),"R10");}
        }
        String failure=p.values.remove("failure:"+key);if(failure!=null&&!failure.equals("other")){p.prove("D11");switch(failure){case "source"->p.prove("D11");case "route"->{p.prove("R04");if(satellite)p.prove("S15");}case "vlan"->{}default->{}}}
        yes(p,clients&&routers>0&&switches>0&&path.stream().allMatch(Hardware::powered)&&path.stream().filter(d->d.kind.equals("router")||d.kind.equals("switch")).allMatch(d->MemoryCards.present(d)&&MemoryCards.card(d).active),"F02");
        for(var wire:n.wires)if(result.path().contains(wire.a().device())&&result.path().contains(wire.b().device()))yes(p,clients&&routers>1&&wire.type().startsWith("serial"),"R18");
    }
    public static void scan(MinecraftServer server){
        for(var level:server.getAllLevels())for(var actor:level.players()){
            var p=Academy.progress(level,actor.getUUID());for(var h:DeviceEntity.loaded(level))for(int bay=0;bay<2;bay++){
                final int slot=bay;var d=bay==0?h.device:h.upperDevice;if(!ownsAction(p,d))continue;String id=d.identity;
                yes(p,d.kind.equals("energy")&&h.energy>0,"P04");yes(p,h.rackSupply&&h.rackPowered,"P05");yes(p,d.kind.matches("router|switch")&&Hardware.powered(d)&&h.powerLinks.containsKey(512+bay),"P06");
                if(LaptopBattery.laptop(d)){int charge=LaptopBattery.stored(d);String old=p.values.put("charge:"+id,""+charge);yes(p,old!=null&&charge>Integer.parseInt(old),"P16");}
                if(d.kind.equals("switch")&&Hardware.powered(d)){
                    var clients=new ArrayList<Device>();for(var link:h.links.entrySet())if(link.getKey()/128==bay&&level.hasChunkAt(net.minecraft.core.BlockPos.of(link.getValue().pos()))&&level.getBlockEntity(net.minecraft.core.BlockPos.of(link.getValue().pos())) instanceof DeviceEntity remote){var other=remote.portDevice(link.getValue().port());int port=link.getKey()%128;if(other!=null&&other.kind.equals("pc")&&Hardware.powered(other)&&d.ports.get(port).up&&other.ports.get(link.getValue().port()%128).up&&!d.ports.get(port).console)clients.add(other);}
                    yes(p,clients.size()>=2,"L01");if(clients.size()>=2)for(var a:clients)for(var b:clients)if(a!=b)for(var x:a.ports)for(var y:b.ports)yes(p,!x.ip.isBlank()&&!y.ip.isBlank()&&!x.ip.equals(y.ip)&&x.mask.equals(y.mask)&&Device.same(x.ip,y.ip,x.mask),"L02");
                    yes(p,p.fact("configured:"+id)&&d.os().vlans.entrySet().stream().anyMatch(v->v.getKey()>1&&!v.getValue().isBlank()&&d.ports.stream().filter(x->x.up&&x.accessVlan==v.getKey()&&x.switchMode.equals("access")).count()>=2),"L06");
                }
                if(Wireless.accessPoint(d))yes(p,Hardware.powered(d)&&h.links.entrySet().stream().anyMatch(x->x.getKey()/128==slot&&!d.ports.get(x.getKey()%128).console),"W01");
                if(d.wireless!=null&&!d.wireless.clientSsid.isBlank()){
                    var network=h.network();var positions=WirelessWorld.positions(level);boolean active=d.wireless.associated!=Long.MIN_VALUE;
                    String was=p.values.put("association:"+id,Boolean.toString(active));
                    if(active){p.prove("W04");yes(p,p.fact("wifi.badkey:"+id),"W07");yes(p,p.fact("wifi.out:"+id),"W08");}
                    else {var point=positions.get(h.slotId(bay));boolean inRange=false;
                        for(var ap:network.devices.entrySet())if(Wireless.accessPoint(ap.getValue())&&ap.getValue().wireless!=null&&ap.getValue().wireless.ssid.equals(d.wireless.clientSsid)&&Wireless.active(ap.getValue())){var location=positions.get(ap.getKey());if(point!=null&&location!=null&&point.distance(location)<=ap.getValue().wireless.range){inRange=true;if(!Wireless.authenticated(d,ap.getValue()))p.fact("wifi.badkey:"+id,true);}}
                        if(!inRange&&"true".equals(was))p.fact("wifi.out:"+id,true);
                    }
                }
                if(bay==0&&p.values.getOrDefault("carry:"+id,"").equals(dev.netlab.compat.RackCarry.fingerprint(h))&&!p.values.getOrDefault("carry.position:"+id,"").equals(""+h.getBlockPos().asLong())&&dev.netlab.compat.RackCarry.rack(h))p.prove("E01");
                AddonEvidence.scan(level,actor,p,h,bay,d);
            }
            Academy.changed(level,actor.getUUID());
        }
    }
    private Evidence(){}
}
