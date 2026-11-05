package dev.netlab.automation;

import com.google.gson.Gson;
import dev.netlab.*;
import dev.netlab.core.Device;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** One automation application per physical bay, persisted with the installed device. */
public final class AutomationNode {
    private static final Gson JSON=new Gson();
    private static final Map<DeviceEntity,Map<Integer,AutomationNode>> CACHE=new WeakHashMap<>();
    public transient DeviceEntity host;
    public transient Device device;
    public transient int slot;
    public UUID identity=UUID.randomUUID(),owner;
    public Direction target=Direction.DOWN;
    public String manager="";
    public boolean extract=true,insert=true,control=false,requireVpn=false,running=false;
    public int signal;
    public List<AutomationEntity.Rule> rules=new ArrayList<>();
    public String passwordHash="";
    public boolean publicReceiver;
    public long receiverVersion;
    public List<dev.netlab.automation.core.AutomationProgram.Stock> stocks=new ArrayList<>();
    public List<dev.netlab.automation.core.AutomationProgram.Pattern> patterns=new ArrayList<>();
    public transient Deque<String> captures=new ArrayDeque<>();
    public transient String last="Готово",remoteSnapshot="",capturedTicket="";
    public transient Map<String,String> reports=new LinkedHashMap<>();
    public static AutomationNode of(DeviceEntity host,int slot){
        var d=slot==0?host.device:host.upperDevice;if(d==null||!Set.of("server","endpoint","sniffer").contains(d.kind))return null;
        d.normalize();var map=CACHE.computeIfAbsent(host,k->new HashMap<>());var old=map.get(slot);if(old!=null&&old.device==d)return old;
        var node=d.extensions.containsKey("automation")?JSON.fromJson(d.extensions.get("automation"),AutomationNode.class):new AutomationNode();
        node.host=host;node.device=d;node.slot=slot;
        if(d.hostname.equals("Switch"))d.hostname=d.kind.equals("server")?"ManagementServer":d.kind.equals("endpoint")?"Multiinterface":"PacketAnalyzer";
        dev.netlab.core.Hardware.ensure(d).profile=d.kind.equals("server")?"Automation Server":d.kind.equals("endpoint")?"Multiinterface":"Packet Analyzer";
        if(!d.extensions.containsKey("automation")&&slot==0&&host instanceof AutomationEntity legacy){node.identity=legacy.identity;node.owner=legacy.owner;node.target=legacy.target;node.manager=legacy.manager;node.extract=legacy.extract;node.insert=legacy.insert;node.control=legacy.control;node.requireVpn=legacy.requireVpn;node.running=legacy.running;node.signal=legacy.signal;node.rules.addAll(legacy.rules);}
        map.put(slot,node);return node;
    }
    public static List<AutomationNode> loaded(Level l){var out=new ArrayList<AutomationNode>();for(var host:DeviceEntity.loaded(l))for(int slot=0;slot<2;slot++){var a=of(host,slot);if(a!=null)out.add(a);}return out;}
    public static void clear(){CACHE.clear();}
    public static void invalidate(Device device){for(var nodes:CACHE.values())nodes.entrySet().removeIf(entry->entry.getValue().device==device);}
    public static void applyMemory(Device device,Device previous){
        if(!device.extensions.containsKey("automation"))return;
        var next=com.google.gson.JsonParser.parseString(device.extensions.get("automation")).getAsJsonObject();
        var old=previous.extensions.containsKey("automation")?com.google.gson.JsonParser.parseString(previous.extensions.get("automation")).getAsJsonObject():new com.google.gson.JsonObject();
        for(var nodes:CACHE.values())for(var cached:nodes.values())if(cached.device==device){if(!old.has("identity"))old.addProperty("identity",cached.identity.toString());if(!old.has("owner")&&cached.owner!=null)old.addProperty("owner",cached.owner.toString());}
        for(String key:List.of("owner","identity")){if(old.has(key))next.add(key,old.get(key));else next.remove(key);}
        device.extensions.put("automation",next.toString());invalidate(device);
    }
    public void persist(){device.extensions.put("automation",JSON.toJson(this));}
    public void changed(){persist();host.changed();}
    public Level getLevel(){return host.getLevel();}
    public BlockPos getBlockPos(){return host.getBlockPos();}
    public net.minecraft.world.level.block.state.BlockState getBlockState(){return host.getBlockState();}
    public long id(){return host.slotId(slot);}
    public boolean isRemoved(){return host.isRemoved()||(slot==0?host.device:host.upperDevice)!=device||CACHE.getOrDefault(host,Map.of()).get(slot)!=this;}
    public String ip(){return device.ports.getFirst().ip;}
    public boolean owns(UUID p){return owner==null||owner.equals(p);}
    public boolean managedBy(AutomationNode server){return device.kind.equals("endpoint")&&!manager.isBlank()&&manager.equals(server.ip())&&owner!=null&&owner.equals(server.owner);}
}
