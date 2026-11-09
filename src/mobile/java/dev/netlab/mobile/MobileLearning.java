package dev.netlab.mobile;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.mobile.core.*;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
/** Read-only observations; radio/authentication services remain the authority. */
public final class MobileLearning {
    private static final Map<String,UUID> ACTORS=new HashMap<>();
    private record Sample(String cell,String settings,double quality){}
    private static final Map<String,Sample> SAMPLES=new HashMap<>();
    public static void remember(ServerPlayer p,MobileRef r){if(ACTORS.size()>1024)ACTORS.clear();ACTORS.put(r.device().identity,p.getUUID());}
    public static void signal(MobileService.Lease lease,net.minecraft.world.phys.Vec3 point){var host=lease.terminal();UUID actor=host instanceof PortableHosts.Host phone?phone.owner.getUUID():host==null?null:ACTORS.get(host.device.identity);if(actor==null)return;var site=lease.cell.site();String key=actor+":"+lease.simId+":"+Math.round(point.x*100)+":"+Math.round(point.y*100)+":"+Math.round(point.z*100),settings=site.channel()+":"+site.azimuth()+":"+site.tilt()+":"+site.x()+":"+site.y()+":"+site.z();if(SAMPLES.size()>1024)SAMPLES.clear();var before=SAMPLES.put(key,new Sample(site.id(),settings,lease.signal.rsrp()));if(before!=null&&before.cell.equals(site.id())&&!before.settings.equals(settings)&&lease.signal.rsrp()>before.quality+1)LearningEvents.owner(lease.core.level(),actor,"mobile.signal.improved",host,0);}
    public static void clear(){ACTORS.clear();SAMPLES.clear();}
    public static void emit(MobileRef r,String kind,Object... fields){try{LearningEvents.owner(r.level(),UUID.fromString(r.state().owner),kind,r.host(),r.bay(),fields);}catch(IllegalArgumentException ignored){}}
    public static void attached(MobileService.Lease lease){var host=lease.terminal();UUID actor=host instanceof PortableHosts.Host pocket?pocket.owner.getUUID():host==null?null:ACTORS.get(host.device.identity);if(actor==null&&lease.ue!=null)try{actor=UUID.fromString(lease.ue.state().owner);}catch(IllegalArgumentException ignored){}
        LearningEvents.owner(lease.core.level(),actor,"mobile.attach",host,0,"phone",lease.ue==null,"sim",lease.simId,"sector",lease.cell.antenna().device().identity);
        emit(lease.cell.bbu(),"mobile.chain");emit(lease.core,"mobile.core");
    }
    public static void tick(ServerTickEvent.Post event){if(event.getServer().getTickCount()%100!=0||LearningEvents.EVENTS.isEmpty())return;for(var level:event.getServer().getAllLevels()){
        for(var cell:MobileRadio.cells(level)){
            if(cell.site().height()>=12)emit(cell.antenna(),"mobile.antenna");emit(cell.rru(),"mobile.rf");emit(cell.bbu(),"mobile.fronthaul");
        }
        for(var r:MobileRef.loaded(level))if(r.device().kind.equals(MobileHardware.CORE)&&Hardware.powered(r.device())&&!r.ip().isBlank()&&!r.state().adminVerifier.isBlank()&&MemoryCards.present(r.device())&&MemoryCards.card(r.device()).active)emit(r,"mobile.core");
    }}
    private MobileLearning(){}
}
