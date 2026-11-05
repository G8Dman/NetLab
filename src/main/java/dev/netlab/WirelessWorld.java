package dev.netlab;

import dev.netlab.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Wired and wireless topology assembled only from already loaded devices. */
public final class WirelessWorld {
    public static Network network(Level level){
        return network(level,true);
    }
    public static Network network(Level level,boolean extensions){
        var n=new Network();n.mbps=NetLab.BANDWIDTH.get();n.latencyMs=NetLab.LATENCY.get();
        if(level instanceof net.minecraft.server.level.ServerLevel server)n.vpnCommand=(id,line)->dev.netlab.api.VpnStore.get(server).configure(n,id,line);
        var positions=new HashMap<Long,Wireless.Position>();var entities=new HashMap<Long,DeviceEntity>();
        for(var e:DeviceEntity.loaded(level)){
            if(n.devices.size()>=1022)break;long id=e.getBlockPos().asLong();entities.put(id,e);n.devices.put(id,e.device);
            var pos=e.getBlockPos();var point=new Wireless.Position(pos.getX(),pos.getY(),pos.getZ());positions.put(id,point);
            if(e.upperDevice!=null){n.devices.put(e.slotId(1),e.upperDevice);positions.put(e.slotId(1),point);}
        }
        for(var e:entities.values())for(var entry:e.links.entrySet()){
            long id=e.getBlockPos().asLong();var peer=entry.getValue();var other=entities.get(peer.pos());if(other==null)continue;
            var back=other.links.get(peer.port());if(back==null||back.pos()!=id||back.port()!=entry.getKey())continue;
            if(id<peer.pos()||id==peer.pos()&&entry.getKey()<peer.port())
                n.wires.add(new Network.Wire(new Network.Endpoint(e.slotId(entry.getKey()/128),entry.getKey()%128),new Network.Endpoint(other.slotId(peer.port()/128),peer.port()%128),dev.netlab.core.WallCablePath.length(CableGeometry.route(e,entry.getKey(),other,peer.port(),peer.anchors())),peer.cable()));
        }
        Wireless.rebuild(n,positions);if(level instanceof net.minecraft.server.level.ServerLevel server)Simulation.bind(server,n);if(extensions)for(var hook:dev.netlab.api.TopologyExtensions.BUILD)hook.accept(level,n);return n;
    }
    public static Map<Long,Wireless.Position> positions(Level level){var out=new HashMap<Long,Wireless.Position>();for(var e:DeviceEntity.loaded(level)){
        var p=e.getBlockPos();var point=new Wireless.Position(p.getX(),p.getY(),p.getZ());out.put(p.asLong(),point);if(e.upperDevice!=null)out.put(e.slotId(1),point);
    }return out;}
    public static void changed(Level level,Network n){for(long id:n.changed){var pos=BlockPos.of(id);if(pos.getY()<level.getMinBuildHeight())pos=pos.above(1024);if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof DeviceEntity e)e.changed();}}
}
