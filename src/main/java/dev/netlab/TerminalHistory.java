package dev.netlab;

import com.google.gson.Gson;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Per-player transcripts belong to the laptop; no text or credentials enter public BE updates. */
public final class TerminalHistory extends SavedData {
    private static final Gson JSON=new Gson();
    public static final class Entry extends dev.netlab.core.ConsoleTranscript {
        public String binding,pcIdentity;public UUID player;
        public long pc,remote;public int sourcePort,remotePort;
        public boolean busy;
    }
    private Map<String,Entry> entries=new LinkedHashMap<>();
    public static TerminalHistory get(ServerLevel l){return l.getDataStorage().computeIfAbsent(new Factory<>(TerminalHistory::new,(tag,regs)->{var s=new TerminalHistory();if(tag.contains("entries")){var all=JSON.fromJson(StoredJson.get(tag,"entries"),Entry[].class);for(var e:all){e.busy=false;s.entries.put(e.pcIdentity+"|"+e.player,e);}}return s;}),"netlab_terminal_history");}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider regs){StoredJson.put(tag,"entries",JSON.toJson(entries.values()));return tag;}
    private static int sourcePort(DeviceEntity pc,ConsoleAccess.Target target){for(var e:pc.links.entrySet())if(e.getKey()<pc.device.ports.size()&&pc.device.ports.get(e.getKey()).console&&e.getValue().pos()==target.entity().getBlockPos().asLong()&&target.entity().portDevice(e.getValue().port())==target.device())return e.getKey();throw new IllegalArgumentException("Консольный кабель изменён");}
    public Entry entry(DeviceEntity pc,ConsoleAccess.Target target,UUID player){
        int port=sourcePort(pc,target);var peer=pc.links.get(port);String key=pc.device.identity+"|"+player;
        String binding=dev.netlab.core.ConsoleTranscript.binding(target.device().identity,port,peer.port(),pc.portEpoch(port),target.entity().portEpoch(peer.port()));
        var e=entries.get(key);if(e==null||!binding.equals(e.binding)){
            if(entries.size()>=256&&!entries.containsKey(key))throw new IllegalArgumentException("Лимит сохранённых терминалов достигнут");
            e=new Entry();e.binding=binding;e.pcIdentity=pc.device.identity;e.pc=pc.getBlockPos().asLong();e.remote=peer.pos();e.sourcePort=port;e.remotePort=peer.port();e.player=player;entries.put(key,e);setDirty();
        }return e;
    }
    public void append(Entry e,String text){e.appendText(text);setDirty();}
    public boolean current(Entry e){return entries.get(e.pcIdentity+"|"+e.player)==e;}
    public void command(Entry e,String command,boolean secret){e.remember(command,secret);setDirty();}
    public static void send(ServerPlayer p,DeviceEntity pc,Entry e,String text,boolean open,boolean replace,boolean connected){
        PacketDistributor.sendToPlayer(p,new TerminalPackets.Data(pc.getBlockPos(),e.token,replace?e.text:text,JSON.toJson(e.history),open,replace,e.busy,connected));
    }
    public static void error(ServerPlayer p,DeviceEntity pc,String message,boolean open){PacketDistributor.sendToPlayer(p,new TerminalPackets.Data(pc.getBlockPos(),"",message,"[]",open,true,false,false));}
    public static void unavailable(ServerPlayer p,DeviceEntity pc,String message){
        var store=get(p.serverLevel());var e=store.entries.get(pc.device.identity+"|"+p.getUUID());
        if(e!=null){var wire=pc.links.get(e.sourcePort);if(wire!=null&&wire.pos()==e.remote&&wire.port()==e.remotePort){e.busy=false;e.beginRequest();store.append(e,message);send(p,pc,e,"",true,true,false);return;}}
        error(p,pc,message,true);
    }
    public static void cut(DeviceEntity device,int port){
        if(!(device.getLevel() instanceof ServerLevel l))return;var store=get(l);long pos=device.getBlockPos().asLong();
        for(var it=store.entries.values().iterator();it.hasNext();){var e=it.next();if(e.pc==pos&&e.sourcePort==port||e.remote==pos&&e.remotePort==port){
            var player=l.getServer().getPlayerList().getPlayer(e.player);if(player!=null)PacketDistributor.sendToPlayer(player,new TerminalPackets.Data(net.minecraft.core.BlockPos.of(e.pc),e.token,"Консольный кабель отключён. История очищена.","[]",false,true,false,false));it.remove();store.setDirty();
        }}
    }
}
