package dev.netlab;

import com.google.gson.Gson;
import dev.netlab.core.Network;
import dev.netlab.core.TrafficMark;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Study {
    public record Event(long from,int fromPort,long to,int toPort,String protocol,int vlan,boolean dropped,long start,long finish,int bytes,String phase,String item,int count) {}
    public record Batch(long now,java.util.List<Event> events){}
    private static final Gson GSON=new Gson();
    public static void publish(Network network,ServerLevel level){
        publish(network,level,TrafficMark.NONE,false);
    }
    public static void publish(Network network,ServerLevel level,TrafficMark mark,boolean tunnel){
        if(network.traffic.isEmpty())return;
        var events=new java.util.ArrayList<Event>();
        for(var t:network.traffic){
            if(network.deferCounters)Simulation.counters(level,network,t);
            var a=BlockPos.of(t.from().device());var b=BlockPos.of(t.to().device());int ap=t.from().port(),bp=t.to().port();
            if(a.getY()<level.getMinBuildHeight()){a=a.above(1024);ap+=128;}
            if(b.getY()<level.getMinBuildHeight()){b=b.above(1024);bp+=128;}
            var actual=network.wirePayloads.get(t);
            var hint=actual!=null&&actual.encrypted()?new TrafficMark(t.protocol().startsWith("LTE-AIR/")?"RADIO_ENCRYPTED":"VPN","",0):mark.hop(t.protocol(),tunnel);
            events.add(new Event(a.asLong(),ap,b.asLong(),bp,t.protocol(),t.vlan(),t.dropped(),t.start(),t.finish(),t.bytes(),hint.phase(),hint.item(),hint.count()));
        }
        for(var p:level.players())if(p.getItemBySlot(EquipmentSlot.HEAD).is(NetLab.GOGGLES.get())){
            var visible=events.stream().filter(e->p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.of(e.from())))<128*128||p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.of(e.to())))<128*128).toList();
            for(int offset=0;offset<visible.size();offset+=256)PacketDistributor.sendToPlayer(p,new Packets.Trace(GSON.toJson(new Batch(Simulation.now(level),visible.subList(offset,Math.min(visible.size(),offset+256))))));
        }
        network.traffic.clear();
        network.wirePayloads.clear();
    }
}
