package dev.netlab;

import dev.netlab.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Server-authoritative physical console access, checked for every command. */
public final class ConsoleAccess {
    public record Target(DeviceEntity entity, Device device, long id, Console cli) {}
    public record Check(Target target,String problem) {}
    private static final Map<DeviceEntity,Map<UUID,Session>> sessions=new WeakHashMap<>();
    private static final Map<DeviceEntity,Set<UUID>> active=new WeakHashMap<>();
    private record Session(long position,int port,Device device,Console cli,long epoch,long pcEpoch,int sourcePort,long localWire,long remoteWire) {}
    public static void close(DeviceEntity pc,UUID player){var set=active.get(pc);if(set!=null)set.remove(player);}
    public static boolean active(DeviceEntity pc,UUID player){return active.getOrDefault(pc,Set.of()).contains(player);}
    public static void connectionChanged(DeviceEntity pc,int port){var map=sessions.get(pc);if(map!=null){var players=new HashSet<UUID>();map.forEach((id,s)->{if(s.sourcePort==port)players.add(id);});for(var id:players){map.remove(id);close(pc,id);}}}
    public static void traffic(DeviceEntity pc,Target target){
        for(var entry:pc.links.entrySet())if(entry.getKey()<pc.device.ports.size()&&pc.device.ports.get(entry.getKey()).console
            &&entry.getValue().pos()==target.entity.getBlockPos().asLong()&&target.entity.portDevice(entry.getValue().port())==target.device){
            var source=pc.device.ports.get(entry.getKey());var remote=target.device.ports.get(entry.getValue().port()%128);
            source.sent++;source.received++;remote.sent++;remote.received++;pc.changed();target.entity.changed();return;
        }
    }
    public static Target target(DeviceEntity pc,UUID player,boolean open){
        return inspect(pc,player,open).target();
    }
    public static Check inspect(DeviceEntity pc,UUID player,boolean open){
        if(open)close(pc,player);
        if(!pc.device.kind.equals("pc")||pc.getLevel()==null)return new Check(null,"Terminal доступен только на ПК или ноутбуке в загруженном мире.");
        if(!Hardware.powered(pc.device))return new Check(null,"ПК/ноутбук выключен. Включи питание на физической панели.");
        boolean present=pc.device.ports.stream().anyMatch(p->p.console&&!p.hidden);
        String problem=present?"RS232 не подключён. Соедини его консольным кабелем с Console устройства.":"Нет доступного RS232. Установи USB-RS232 в слот usb0 ноутбука.";
        for(int i=0;i<pc.device.ports.size();i++){
            if(!pc.device.ports.get(i).console||pc.device.ports.get(i).hidden)continue;
            var peer=pc.links.get(i);if(peer==null)continue;
            var pos=BlockPos.of(peer.pos());
            if(!pc.getLevel().hasChunkAt(pos)){problem="Устройство на другом конце кабеля находится в незагруженном чанке.";continue;}
            if(!(pc.getLevel().getBlockEntity(pos) instanceof DeviceEntity other)){problem="На другом конце кабеля нет сетевого устройства. Переподключи кабель.";continue;}
            var d=other.portDevice(peer.port());int p=peer.port()%128;
            var back=other.links.get(peer.port());
            String physical=ConsoleLink.problem(pc.device,i,d,p,peer.cable(),back!=null&&back.pos()==pc.getBlockPos().asLong()&&back.port()==i);
            if(!physical.isBlank()){problem=physical;continue;}
            var map=sessions.computeIfAbsent(pc,k->new HashMap<>());var session=map.get(player);
            long epoch=d.hardware==null?0:d.hardware.epoch;
            long pcEpoch=pc.device.hardware==null?0:pc.device.hardware.epoch;
            boolean same=session!=null&&session.position()==peer.pos()&&session.port()==peer.port()&&session.device()==d&&session.epoch()==epoch&&session.pcEpoch()==pcEpoch&&session.sourcePort==i&&session.localWire==pc.portEpoch(i)&&session.remoteWire==other.portEpoch(peer.port());
            if(open){if(!same){session=new Session(peer.pos(),peer.port(),d,new Console(),epoch,pcEpoch,i,pc.portEpoch(i),other.portEpoch(peer.port()));map.put(player,session);}active.computeIfAbsent(pc,k->new HashSet<>()).add(player);same=true;}
            if(!same){problem="Консольная сессия закрыта или устройство перезапущено. Снова открой Terminal из Desktop.";continue;}
            return new Check(new Target(other,d,other.slotId(peer.port()/128),session.cli()),"");
        }
        return new Check(null,problem);
    }
}
