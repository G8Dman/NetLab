package dev.netlab.media;
import dev.netlab.sputnik.*;
import dev.netlab.core.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.concurrent.*;
/** IPTV video bytes travel gateway -> headend -> receiver, not straight to a client. */
public final class MediaInternet implements InternetBridge.External {
    @Override public boolean allowed(ServerPlayer p,BlockPos pos){return MediaService.watching(p,pos)!=null;}
    @Override public SputnikRef gateway(ServerPlayer p,SputnikRef host,String address){var w=MediaService.watching(p,host.host().getBlockPos());if(w==null)throw new IllegalArgumentException("IPTV отключено");return uplink(w.server());}

    static SputnikRef uplink(MediaRef station){
        var state=station.state();
        var gateway=InternetBridge.gateway(new SputnikRef(station.host(),station.bay(),station.device()),state.gateway);
        var network=station.host().network();
        var outbound=network.routePath(station.id(),gateway.ip());
        var inbound=network.routePath(gateway.id(),station.ip());
        if(!outbound.success()||!inbound.success())throw new IllegalArgumentException("Нет обратного маршрута от шлюза Sputnik");
        if(state.satelliteOnly&&(!usesSatellite(network,outbound)||!usesSatellite(network,inbound)))
            throw new IllegalArgumentException("Маршрут IPTV должен проходить через VSAT и HUB в обе стороны");
        return gateway;
    }

    static boolean usesSatellite(Network network,Network.Result route){
        if(!route.success())return false;
        var path=route.path();
        for(int i=1;i<path.size();i++){
            long a=path.get(i-1),b=path.get(i);
            // Route summaries contain device IDs. A parallel physical link makes this hop ambiguous.
            var parallel=network.wires.stream().filter(w->w.a().device()==a&&w.b().device()==b||w.a().device()==b&&w.b().device()==a).toList();
            if(!parallel.isEmpty()&&parallel.stream().allMatch(w->w.type().equals("satellite")))return true;
        }
        return false;
    }
    @Override public CompletableFuture<byte[]> transfer(ServerPlayer p,SputnikRef television,SputnikRef gateway,boolean incoming,byte[] bytes){var w=MediaService.watching(p,television.host().getBlockPos());if(w==null)return CompletableFuture.failedFuture(new IllegalArgumentException("IPTV: просмотр отключён"));var gw=new MediaRef(gateway.host(),gateway.bay(),gateway.device());
        if(w.gatewayId()!=gateway.id())return CompletableFuture.failedFuture(new IllegalArgumentException("IPTV: интернет-шлюз изменился"));
        if(incoming)return MediaService.transport(gw,w.server(),bytes,1080,"tcp","IPTV").thenCompose(part->MediaService.transport(w.server(),w.receiver(),part,5004,"tcp","IPTV"));
        return MediaService.transport(w.receiver(),w.server(),bytes,5004,"tcp","IPTV").thenCompose(part->MediaService.transport(w.server(),gw,part,1080,"tcp","IPTV"));
    }
}
