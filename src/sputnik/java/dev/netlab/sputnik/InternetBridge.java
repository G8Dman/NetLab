package dev.netlab.sputnik;

import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.sputnik.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Socket I/O stays off the game thread. TLS remains opaque bytes end-to-end. */
public final class InternetBridge {
    /** Optional media endpoints have server-owned viewing leases and their own routed path. */
    public interface External {
        boolean allowed(ServerPlayer player,net.minecraft.core.BlockPos address);
        SputnikRef gateway(ServerPlayer player,SputnikRef host,String address);
        CompletableFuture<byte[]> transfer(ServerPlayer player,SputnikRef host,SputnikRef gateway,boolean incoming,byte[] bytes);
    }
    public static External external;
    public static boolean externalAllowed(ServerPlayer p,net.minecraft.core.BlockPos pos){return external!=null&&external.allowed(p,pos);}

    private record Key(UUID player,int stream){}
    private static final Map<Key,Stream> STREAMS=new ConcurrentHashMap<>();
    private static final ExecutorService IO=Executors.newVirtualThreadPerTaskExecutor();
    private static final class Stream {
        final ServerPlayer player;final int id;final SputnikRef pc,gateway;final String pcIdentity,gatewayIdentity;final long pcEpoch,gatewayEpoch;final AtomicBoolean closed=new AtomicBoolean();final AtomicInteger uploads=new AtomicInteger();boolean https;volatile Socket socket;volatile long touched=System.nanoTime();final Queue<CompletableFuture<Void>> acks=new ConcurrentLinkedQueue<>();final Set<CompletableFuture<Void>> pending=ConcurrentHashMap.newKeySet();CompletableFuture<Void> writeTail=CompletableFuture.completedFuture(null);
        Stream(ServerPlayer p,int id,SputnikRef pc,SputnikRef gateway){player=p;this.id=id;this.pc=pc;this.gateway=gateway;pcIdentity=pc.device().identity;gatewayIdentity=gateway.device().identity;pcEpoch=Hardware.ensure(pc.device()).epoch;gatewayEpoch=Hardware.ensure(gateway.device()).epoch;}
        boolean live(){return !closed.get()&&!player.hasDisconnected()&&SputnikPackets.browserAllowed(player,pc.host().getBlockPos())&&pc.live()&&gateway.live()&&pc.device().identity.equals(pcIdentity)&&gateway.device().identity.equals(gatewayIdentity)&&pcEpoch==Hardware.ensure(pc.device()).epoch&&gatewayEpoch==Hardware.ensure(gateway.device()).epoch&&enabled(gateway);}
    }
    public static boolean enabled(SputnikRef gateway){if(!gateway.live()||!gateway.device().kind.equals(SputnikHardware.GATEWAY)||!gateway.state().internet||!gateway.state().enabled)return false;String address=gateway.state().hubAddress;return SatelliteLinks.pairs(gateway.level()).stream().anyMatch(p->p.hub().ip().equals(address)&&p.hub().state().owner.equals(gateway.state().owner)&&SatelliteLinks.valid(p));}
    public static SputnikRef gateway(SputnikRef pc,String address){var n=pc.host().network();var matches=SputnikRef.loaded(pc.level()).stream().filter(r->r.device().kind.equals(SputnikHardware.GATEWAY)&&(address.isBlank()||r.ip().equals(address))&&enabled(r)&&!r.ip().isBlank()&&n.routePath(pc.id(),r.ip()).success()).toList();if(matches.size()!=1)throw new IllegalArgumentException("Нужен один доступный интернет-шлюз с рабочим HUB/VSAT. Укажи его LAN IP и проверь маршруты туда и обратно");return matches.getFirst();}
    public static void receive(ServerPlayer p,SputnikPackets.Tunnel q){var key=new Key(p.getUUID(),q.id());var existing=STREAMS.get(key);
        if(q.op().equals("CLOSE")){close(key,"");return;}
        if(q.op().equals("ACK")){if(existing!=null){existing.touched=System.nanoTime();var ack=existing.acks.poll();if(ack!=null)ack.complete(null);}return;}
        if(q.op().equals("DATA")){if(existing==null)return;if(q.data().length==0||q.data().length>SocksProxy.CHUNK||!existing.live()||existing.socket==null||existing.uploads.incrementAndGet()>16){close(key,"Поток остановлен или занят");return;}existing.touched=System.nanoTime();
            var delivered=transfer(existing,false,q.data());existing.writeTail=existing.writeTail.thenCombine(delivered,(ignored,bytes)->bytes).thenAcceptAsync(bytes->{try{if(existing.closed.get())throw new IOException("Closed");existing.socket.getOutputStream().write(bytes);existing.socket.getOutputStream().flush();}catch(IOException e){throw new CompletionException(e);}},IO);
            existing.writeTail.whenComplete((v,e)->p.server.execute(()->{existing.uploads.decrementAndGet();if(e!=null)close(key,message(e));else reply(p,q.id(),"ACK","",new byte[0]);}));return;}

        if(!q.op().equals("OPEN")||existing!=null)return;
        try{if(STREAMS.size()>=128||STREAMS.keySet().stream().filter(k->k.player.equals(p.getUUID())).count()>=(externalAllowed(p,q.pc())?48:SocksProxy.MAX_STREAMS))throw new IllegalArgumentException("Слишком много соединений браузера");InternetTarget.validate(q.host(),q.port());var host=dev.netlab.api.PortableHosts.resolve(p,q.pc());if(host==null||!SputnikPackets.browserAllowed(p,q.pc())||!host.device.kind.equals("pc")&&!externalAllowed(p,q.pc())||!Hardware.powered(host.device))throw new IllegalArgumentException("Открой браузер рядом с включённым ПК или ноутбуком");
            var pc=new SputnikRef(host,0,host.device);var gw=externalAllowed(p,q.pc())?external.gateway(p,pc,q.gateway()):gateway(pc,q.gateway());var stream=new Stream(p,q.id(),pc,gw);stream.https=q.port()==443;STREAMS.put(key,stream);
            transfer(stream,false,("CONNECT "+q.host()+":"+q.port()).getBytes(StandardCharsets.UTF_8)).whenComplete((bytes,error)->{if(error!=null){close(key,message(error));return;}IO.submit(()->{try{var target=InternetTarget.resolve(q.host(),q.port());var socket=new Socket();stream.socket=socket;socket.connect(target,15000);socket.setSoTimeout(90000);socket.setTcpNoDelay(true);p.server.execute(()->{if(!stream.live()){close(key,"Спутниковый тракт отключён");return;}transfer(stream,true,"CONNECTED".getBytes(StandardCharsets.US_ASCII)).whenComplete((ignored,e)->{if(e!=null)close(key,message(e));else{reply(p,q.id(),"OPEN","",new byte[0]);IO.submit(()->read(key,stream));}});});}catch(Exception e){p.server.execute(()->close(key,message(e)));}});});
        }catch(IllegalArgumentException e){reply(p,q.id(),"ERROR",message(e),new byte[0]);}
    }
    private static CompletableFuture<byte[]> transfer(Stream s,boolean incoming,byte[] bytes){if(externalAllowed(s.player,s.pc.host().getBlockPos()))return external.transfer(s.player,s.pc,s.gateway,incoming,bytes);if(!s.live())return CompletableFuture.failedFuture(new IOException("Спутниковый канал или питание отключены"));var a=incoming?s.gateway:s.pc;var b=incoming?s.pc:s.gateway;
        return ApplicationTransport.sendAsync(a.level(),new ApplicationTransport.Endpoint(a.host(),a.bay()),new ApplicationTransport.Endpoint(b.host(),b.bay()),bytes,false,TrafficMark.NONE,1080).thenApply(receipt->{if(!receipt.success()||!s.live())throw new CompletionException(new IOException(receipt.success()?"Канал отключён":receipt.status()));return receipt.payload();});
    }
    private static void read(Key key,Stream s){var window=new Semaphore(64);try{var buffer=new byte[SocksProxy.CHUNK];int n;while(!s.closed.get()&&(n=s.socket.getInputStream().read(buffer))>=0){if(n==0)continue;if(!window.tryAcquire(60,TimeUnit.SECONDS))throw new IOException("Receive window timeout");byte[] part=Arrays.copyOf(buffer,n);var future=new CompletableFuture<Void>();s.pending.add(future);future.whenComplete((v,e)->{s.pending.remove(future);window.release();if(e!=null)s.player.server.execute(()->close(key,message(e)));});
            s.player.server.execute(()->{if(!s.live()){future.completeExceptionally(new IOException("Канал отключён"));return;}transfer(s,true,part).whenComplete((arrived,e)->{if(e!=null){future.completeExceptionally(e);return;}s.acks.add(future);BrowserLearning.bytes(s.player,s.pc.host().getBlockPos(),arrived.length,s.https);reply(s.player,s.id,"DATA","",arrived);});});s.touched=System.nanoTime();}
            CompletableFuture.allOf(s.pending.toArray(CompletableFuture[]::new)).get(60,TimeUnit.SECONDS);s.player.server.execute(()->{reply(s.player,s.id,"EOF","",new byte[0]);close(key,"");});
        }catch(Exception e){s.player.server.execute(()->close(key,message(e)));}}

    public static void tick(){for(var entry:STREAMS.entrySet())if(!entry.getValue().live()||System.nanoTime()-entry.getValue().touched>120_000_000_000L)close(entry.getKey(),"Канал отключён или истекло время ожидания");}
    private static String message(Throwable e){while(e.getCause()!=null)e=e.getCause();String s=e.getMessage();return s==null?"Ошибка интернет-соединения":s.substring(0,Math.min(s.length(),400));}
    private static void reply(ServerPlayer p,int id,String op,String text,byte[] bytes){if(!p.hasDisconnected())net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new SputnikPackets.Reply(id,op,text,bytes));}
    private static void close(Key key,String reason){var s=STREAMS.remove(key);if(s==null||!s.closed.compareAndSet(false,true))return;for(var f:s.pending)f.completeExceptionally(new IOException("Closed"));for(var f:s.acks)f.completeExceptionally(new IOException("Closed"));s.acks.clear();if(s.socket!=null)IO.submit(()->{try{s.socket.close();}catch(IOException ignored){}});if(!reason.isEmpty())reply(s.player,s.id,"ERROR",reason,new byte[0]);}
    public static void closePlayer(ServerPlayer player){for(var key:List.copyOf(STREAMS.keySet()))if(key.player.equals(player.getUUID()))close(key,"");}
    public static void clear(){BrowserLearning.clear();for(var key:List.copyOf(STREAMS.keySet()))close(key,"");SatelliteLinks.clear();}
}
