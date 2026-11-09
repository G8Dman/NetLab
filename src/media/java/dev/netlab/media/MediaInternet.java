package dev.netlab.media;
import dev.netlab.sputnik.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.concurrent.*;
/** IPTV video bytes travel gateway -> headend -> receiver, not straight to a client. */
public final class MediaInternet implements InternetBridge.External {
    @Override public boolean allowed(ServerPlayer p,BlockPos pos){return MediaService.watching(p,pos)!=null;}
    @Override public SputnikRef gateway(ServerPlayer p,SputnikRef host,String address){var w=MediaService.watching(p,host.host().getBlockPos());if(w==null)throw new IllegalArgumentException("IPTV отключено");return InternetBridge.gateway(new SputnikRef(w.server().host(),w.server().bay(),w.server().device()),w.server().state().gateway);}
    @Override public CompletableFuture<byte[]> transfer(ServerPlayer p,SputnikRef television,SputnikRef gateway,boolean incoming,byte[] bytes){var w=MediaService.watching(p,television.host().getBlockPos());if(w==null)return CompletableFuture.failedFuture(new IllegalArgumentException("IPTV: просмотр отключён"));var gw=new MediaRef(gateway.host(),gateway.bay(),gateway.device());
        if(incoming)return MediaService.transport(gw,w.server(),bytes,1080,"tcp","IPTV").thenCompose(part->MediaService.transport(w.server(),w.receiver(),part,5004,"tcp","IPTV"));
        return MediaService.transport(w.receiver(),w.server(),bytes,5004,"tcp","IPTV").thenCompose(part->MediaService.transport(w.server(),gw,part,1080,"tcp","IPTV"));
    }
}
