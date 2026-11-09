package dev.netlab.sputnik;
import dev.netlab.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
/** Client decoding evidence is accepted only for a recent, server-delivered authenticated browser stream. */
public final class BrowserLearning {
    private record Key(UUID player,long pos){}
    private static final class Seen {long time;int bytes;boolean tls;}
    private static final Map<Key,Seen> SEEN=new HashMap<>();
    public static void bytes(ServerPlayer p,BlockPos pos,int count,boolean tls){var s=SEEN.computeIfAbsent(new Key(p.getUUID(),pos.asLong()),k->new Seen());s.time=p.serverLevel().getGameTime();s.bytes=Math.min(1000000,s.bytes+count);s.tls|=tls;}
    public static boolean received(ServerPlayer p,BlockPos pos){var s=SEEN.get(new Key(p.getUUID(),pos.asLong()));return s!=null&&s.tls&&s.bytes>=256&&p.serverLevel().getGameTime()-s.time<2400&&SputnikPackets.browserAllowed(p,pos);}
    public static void loaded(ServerPlayer p,BlockPos pos){if(!received(p,pos)||InternetBridge.externalAllowed(p,pos))return;var host=PortableHosts.resolve(p,pos);LearningEvents.emit(p,"browser.loaded",host,0,"mobile",host instanceof PortableHosts.Host);}
    public static void clear(){SEEN.clear();}
    private BrowserLearning(){}
}
