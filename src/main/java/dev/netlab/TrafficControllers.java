package dev.netlab;

import com.google.gson.Gson;
import dev.netlab.core.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Credentials and controller leases stay in SavedData, never in Device or block update tags. */
public final class TrafficControllers extends SavedData {
    private static final Gson JSON=new Gson();
    public TrafficAuthority authority=new TrafficAuthority();
    public static TrafficControllers get(ServerLevel l){return l.getDataStorage().computeIfAbsent(new Factory<>(TrafficControllers::new,(tag,regs)->{var s=new TrafficControllers();if(tag.contains("authority"))s.authority=JSON.fromJson(StoredJson.get(tag,"authority"),TrafficAuthority.class);return s;}),"netlab_traffic_authority");}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider regs){StoredJson.put(tag,"authority",JSON.toJson(authority));return tag;}
    public static void bind(ServerLevel l,Network n){
        var saved=get(l);var auth=saved.authority;var fabric=new LanFabric(n);var roots=new HashMap<Network.Endpoint,Set<String>>();
        for(var e:n.devices.entrySet())if(e.getValue().kind.equals("traffic")){
            var point=new Network.Endpoint(e.getKey(),0);var root=fabric.root(point);if(root==null)continue;
            var members=fabric.anchors(point);var ids=new HashSet<String>();for(String member:members)if(auth.anchors.containsKey(member))ids.add(auth.anchors.get(member));
            if(ids.size()==1){int before=auth.anchors.size();auth.resolve(members,true);if(before!=auth.anchors.size())saved.setDirty();}roots.put(root,ids);
        }
        var active=new HashSet<String>();for(var e:n.devices.entrySet())if(e.getValue().kind.equals("traffic")&&n.linkUp(e.getKey(),0)){
            var device=e.getValue();if(!device.extensions.containsKey("netlab.trafficLease")&&MemoryCards.present(device)&&MemoryCards.card(device).active)remember(auth,device);
            var role=device.extensions.containsKey("netlab.trafficLease")?auth.leases.get(device.identity):null;var root=fabric.root(new Network.Endpoint(e.getKey(),0));var domains=roots.get(root);
            if(domains!=null&&auth.valid(role)&&domains.contains(role.domain)&&(role.vlan==0||role.vlan==fabric.attachedVlan(e.getKey())))active.add(e.getValue().identity);
        }
        n.speedPolicy=(a,b,v,p)->{var root=fabric.root(a);if(root==null)root=fabric.root(b);var domains=roots.get(root);double speed=1;if(domains!=null)for(String domain:domains)speed=Math.min(speed,auth.factor(domain,v,p,active));return speed;};
    }
    private static TrafficAuthority.Domain domain(ServerLevel l,Network n,long id){
        if(!n.linkUp(id,0))throw new IllegalArgumentException("RJ45 не подключён к работающей LAN. Проверь кабель и порт коммутатора.");
        var store=get(l);var domain=store.authority.resolve(new LanFabric(n).anchors(new Network.Endpoint(id,0)),true);store.setDirty();return domain;
    }
    public static String prompt(dev.netlab.core.Console cli){return TrafficConsole.prompt(cli);}
    public static String open(ServerLevel l,Network n,long id,dev.netlab.core.Console cli){
        try{return TrafficConsole.open(get(l).authority,domain(l,n,id),n,id,cli);}
        catch(IllegalArgumentException e){cli.pending="";return "% "+e.getMessage()+"\nTrafficControl>";}
    }
    public static String execute(ServerLevel l,Network n,long id,dev.netlab.core.Console cli,UUID player,String line){
        var store=get(l);
        try{String answer=TrafficConsole.execute(store.authority,domain(l,n,id),n,id,cli,player,l.getGameTime(),line);var role=TrafficConsole.role(cli);var host=Simulation.entity(l,id);int bay=host!=null&&id==host.slotId(1)?1:0;
            if(store.authority.valid(role)){dev.netlab.api.LearningEvents.owner(l,player,role.vlan==0?"traffic.admin":"traffic.vlan.login",host,bay);String cmd=line.strip().toLowerCase(Locale.ROOT);if(role.vlan>0&&cmd.startsWith("speed lan")&&answer.startsWith("%"))dev.netlab.api.LearningEvents.owner(l,player,"traffic.scope.denied",host,bay);if(role.vlan>0&&cmd.startsWith("speed vlan")&&!answer.startsWith("%"))dev.netlab.api.LearningEvents.owner(l,player,"traffic.vlan.allowed",host,bay);if(role.vlan==0&&cmd.startsWith("revoke vlan")&&!answer.startsWith("%"))dev.netlab.api.LearningEvents.owner(l,player,"traffic.revoke",host,bay);}
            return answer;}
        catch(IllegalArgumentException e){cli.pending="";return "% "+e.getMessage()+"\nTrafficControl>";}
        finally{var device=n.devices.get(id);if(device!=null)remember(store.authority,device);store.setDirty();}
    }
    private static void remember(TrafficAuthority auth,Device device){
        var lease=auth.leases.get(device.identity);if(lease==null)return;var data=new com.google.gson.JsonObject();data.add("lease",JSON.toJsonTree(lease));data.add("rules",JSON.toJsonTree(auth.rules.stream().filter(r->r.controller.equals(device.identity)).toList()));device.extensions.put("netlab.trafficLease",data.toString());
    }
    public static void restore(ServerLevel level,Device device){
        if(!device.extensions.containsKey("netlab.trafficLease"))return;var auth=get(level).authority;var data=com.google.gson.JsonParser.parseString(device.extensions.get("netlab.trafficLease")).getAsJsonObject();var original=JSON.fromJson(data.get("lease"),TrafficAuthority.Lease.class);
        if(!auth.valid(original))throw new IllegalArgumentException("Доступ карты к LAN/VLAN отозван; нужен повторный login");
        var lease=new TrafficAuthority.Lease(original.domain,device.identity,original.vlan,original.revision);auth.leases.put(device.identity,lease);auth.rules.removeIf(r->r.controller.equals(device.identity));
        for(var row:data.getAsJsonArray("rules")){var rule=JSON.fromJson(row,TrafficAuthority.Rule.class);auth.speed(lease,rule.vlan,rule.protocol,rule.speed);}get(level).setDirty();
    }
    public static CommandGrammar.Completion complete(dev.netlab.core.Console cli,String prefix){
        if(!cli.pending.isEmpty())return new CommandGrammar.Completion(prefix,List.of());
        var choices=List.of("login admin","login vlan ","grant vlan ","revoke vlan ","password admin","speed lan ","speed vlan ","clear lan","clear vlan ","show status","show policy","logout","help").stream().filter(s->s.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
        return new CommandGrammar.Completion(choices.size()==1?choices.getFirst():prefix,choices);
    }
}
