package dev.netlab.sputnik;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.sputnik.core.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
public final class SputnikLearning {
    public static void emit(SputnikRef r,String kind,Object... fields){try{LearningEvents.owner(r.level(),UUID.fromString(r.state().owner),kind,r.host(),r.bay(),fields);}catch(IllegalArgumentException ignored){}}
    public static void tick(ServerTickEvent.Post e){if(e.getServer().getTickCount()%100!=0||LearningEvents.EVENTS.isEmpty())return;for(var level:e.getServer().getAllLevels()){
        for(var r:SputnikRef.loaded(level)){
            if(!r.live()||!Hardware.powered(r.device()))continue;String k=r.device().kind;
            if(k.equals(SputnikHardware.HUB)&&SputnikHardware.hubReady(r.device())&&MemoryCards.present(r.device())&&MemoryCards.card(r.device()).active)emit(r,"sputnik.hub");
            if(k.equals(SputnikHardware.MODEM)){var tx=SatelliteLinks.peer(r,2,"coax_tx");var rx=SatelliteLinks.peer(r,3,"coax_rx");if(tx!=null&&rx!=null&&tx.id()==rx.id())emit(r,"sputnik.subscriber.rf");}
            if(k.equals(SputnikHardware.ODU)&&r.state().enabled&&r.device().ports.stream().filter(p->!p.console).allMatch(p->p.up))emit(r,"sputnik.radio");
            if(k.equals(SputnikHardware.GATEWAY)&&InternetBridge.enabled(r))emit(r,"sputnik.gateway");
        }
        for(var pair:SatelliteLinks.pairs(level))if(SatelliteLinks.valid(pair)){
            for(var r:List.of(pair.hub(),pair.modem())){emit(r,"sputnik.provider.rf");emit(r,"sputnik.aligned");emit(r,"sputnik.parameters");emit(r,"sputnik.subscriber");emit(r,"sputnik.lock");}
        }
    }}
    private SputnikLearning(){}
}
