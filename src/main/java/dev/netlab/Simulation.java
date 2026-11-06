package dev.netlab;

import dev.netlab.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Main-thread completion queue: no sleeps, worker-thread world access, or fixed visual delays. */
public final class Simulation {
    private static final class State {
        final PacketClock clock=new PacketClock();final TimedQueue queue=new TimedQueue();
    }
    private static final Map<ServerLevel,State> STATES=new WeakHashMap<>();
    private static State state(ServerLevel l){return STATES.computeIfAbsent(l,k->new State());}
    public static long now(ServerLevel l){return l.getGameTime()*50_000_000L;}
    public static void bind(ServerLevel l,Network n){n.clock=state(l).clock;n.cursor=now(l);n.deferCounters=true;TrafficControllers.bind(l,n);}
    public static <T> CompletableFuture<T> after(ServerLevel level,long due,Supplier<T> action){
        return state(level).queue.submit(now(level),due,action);
    }
    public static DeviceEntity entity(ServerLevel l,long id){var portable=dev.netlab.api.PortableHosts.find(l,id);if(portable!=null)return portable;var p=BlockPos.of(id);if(p.getY()<l.getMinBuildHeight())p=p.above(1024);return l.hasChunkAt(p)&&l.getBlockEntity(p) instanceof DeviceEntity e?e:null;}
    public static int encoded(ServerLevel l,Network.Endpoint e){return e.port()+(dev.netlab.api.PortableHosts.find(l,e.device())==null&&BlockPos.of(e.device()).getY()<l.getMinBuildHeight()?128:0);}
    public static BooleanSupplier witness(ServerLevel l,Network n,Network.Traffic t){
        for(var hook:dev.netlab.api.TopologyExtensions.WITNESSES){var custom=hook.get(l,t);if(custom!=null)return custom;}
        var a=entity(l,t.from().device());var b=entity(l,t.to().device());if(a==null||b==null)return ()->false;
        int ap=encoded(l,t.from()),bp=encoded(l,t.to());var da=a.portDevice(ap);var db=b.portDevice(bp);
        if(da==null||db==null||ap%128>=da.ports.size()||bp%128>=db.ports.size())return ()->false;
        long ae=a.portEpoch(ap),be=b.portEpoch(bp),pa=da==null?0:Hardware.ensure(da).epoch,pb=db==null?0:Hardware.ensure(db).epoch;
        boolean wireless=da!=null&&"wireless".equals(da.ports.get(ap%128).medium);
        return ()->{
            if(a.isRemoved()||b.isRemoved()||entity(l,t.from().device())!=a||entity(l,t.to().device())!=b||a.portDevice(ap)!=da||b.portDevice(bp)!=db||!Hardware.powered(da)||!Hardware.powered(db)||Hardware.ensure(da).epoch!=pa||Hardware.ensure(db).epoch!=pb)return false;
            if(ap%128>=da.ports.size()||bp%128>=db.ports.size()||da.ports.get(ap%128).hidden||db.ports.get(bp%128).hidden||!da.ports.get(ap%128).up||!db.ports.get(bp%128).up)return false;
            if(wireless)return Wireless.authenticated(da.kind.equals("pc")?da:db,da.kind.equals("pc")?db:da);
            var forward=a.links.get(ap);var back=b.links.get(bp);
            return a.portEpoch(ap)==ae&&b.portEpoch(bp)==be&&forward!=null&&back!=null&&forward.pos()==b.getBlockPos().asLong()&&forward.port()==bp&&back.pos()==a.getBlockPos().asLong()&&back.port()==ap;
        };
    }
    public static void counters(ServerLevel l,Network n,Network.Traffic t){
        var valid=witness(l,n,t);after(l,t.start(),()->{if(valid.getAsBoolean()){var e=entity(l,t.from().device());e.portDevice(encoded(l,t.from())).ports.get(t.from().port()).sent++;e.changed();}return true;});
        after(l,t.finish(),()->{if(valid.getAsBoolean()){var e=entity(l,t.to().device());e.portDevice(encoded(l,t.to())).ports.get(t.to().port()).received++;e.changed();}return true;});
    }
    public static BooleanSupplier guard(ServerLevel l,Network n){
        var witnesses=n.traffic.stream().filter(t->!t.dropped()&&!t.protocol().startsWith("SPAN/")&&!t.protocol().equals("ARP")&&!t.protocol().equals("NDP")&&n.linkUp(t.from().device(),t.from().port())&&n.linkUp(t.to().device(),t.to().port())).map(t->witness(l,n,t)).toList();
        return ()->witnesses.stream().allMatch(BooleanSupplier::getAsBoolean)&&n.deliveryGuards.stream().allMatch(BooleanSupplier::getAsBoolean);
    }
    public static void tick(ServerTickEvent.Post event){for(var l:event.getServer().getAllLevels()){var s=STATES.get(l);if(s!=null)s.queue.advance(now(l),2048);}}
    public static void clear(net.neoforged.neoforge.event.server.ServerStoppedEvent event){STATES.clear();dev.netlab.api.PortableHosts.clear();}
}
