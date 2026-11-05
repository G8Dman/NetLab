import dev.netlab.core.*;
import dev.netlab.sputnik.core.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real HTTPS + production SOCKS frontend + pure NetLab packet queues. No game classes are instantiated. */
public class SputnikWebRuntimeCheck {
    static final class Broker implements SocksProxy.Transport,AutoCloseable {
        final ExecutorService io=Executors.newVirtualThreadPerTaskExecutor();final ScheduledExecutorService simulation=Executors.newSingleThreadScheduledExecutor();final Map<Integer,Socket> sockets=new ConcurrentHashMap<>();final Network network;final long start=System.nanoTime();final AtomicInteger crossings=new AtomicInteger();final AtomicLong bytes=new AtomicLong();volatile SocksProxy proxy;volatile boolean cut;
        Broker(){SputnikHardware.init();network=SputnikTest.network();}
        CompletableFuture<byte[]> cross(boolean incoming,byte[] data){var result=new CompletableFuture<byte[]>();simulation.execute(()->{try{network.cursor=System.nanoTime()-start;network.traffic.clear();var delivery=network.transmit(incoming?4:1,incoming?"192.168.77.100":"172.30.77.2","tcp",1080,data);if(cut||!delivery.result().success())throw new IOException("Satellite link down");if(!delivery.result().path().containsAll(List.of(2L,3L)))throw new IOException("Satellite path bypassed");crossings.incrementAndGet();bytes.addAndGet(data.length);long due=network.cursor-(System.nanoTime()-start);simulation.schedule(()->{if(cut)result.completeExceptionally(new IOException("Satellite link down"));else result.complete(delivery.payload());},Math.max(0,due),TimeUnit.NANOSECONDS);}catch(Exception e){result.completeExceptionally(e);}});return result;}
        public CompletableFuture<Void> open(int id,String host,int port){return cross(false,("CONNECT "+host).getBytes(StandardCharsets.US_ASCII)).thenAcceptAsync(v->{try{var s=new Socket();s.connect(InternetTarget.resolve(host,port),15000);s.setSoTimeout(30000);sockets.put(id,s);io.submit(()->read(id,s));}catch(Exception e){throw new CompletionException(e);}},io);}
        public CompletableFuture<Void> send(int id,byte[] data){return cross(false,data).thenAcceptAsync(bytes->{try{sockets.get(id).getOutputStream().write(bytes);sockets.get(id).getOutputStream().flush();}catch(Exception e){throw new CompletionException(e);}},io);}
        void read(int id,Socket socket){try{byte[] buffer=new byte[SocksProxy.CHUNK];int n;while((n=socket.getInputStream().read(buffer))>=0)if(n>0){byte[] bytes=cross(true,Arrays.copyOf(buffer,n)).get(40,TimeUnit.SECONDS);proxy.receive(id,bytes);}proxy.eof(id);}catch(Exception e){proxy.fail(id,"Remote stream closed");}}
        public void close(int id){var s=sockets.remove(id);if(s!=null)try{s.close();}catch(IOException ignored){}}
        public void close(){for(int id:List.copyOf(sockets.keySet()))close(id);io.shutdownNow();simulation.shutdownNow();}
    }
    public static void main(String[] args)throws Exception{
        try(var broker=new Broker();var proxy=new SocksProxy(broker)){broker.proxy=proxy;var socket=new Socket(new Proxy(Proxy.Type.SOCKS,new InetSocketAddress("127.0.0.1",proxy.port())));socket.connect(InetSocketAddress.createUnresolved("example.com",443),20000);socket.setSoTimeout(40000);
            var ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket,"example.com",443,true);var params=ssl.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(params);ssl.startHandshake();ssl.getOutputStream().write("GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));ssl.getOutputStream().flush();byte[] body=ssl.getInputStream().readAllBytes();String response=new String(body,StandardCharsets.UTF_8);if(!response.contains("200")||!response.contains("Example Domain"))throw new AssertionError("Real HTTPS response was not received");ssl.close();if(broker.crossings.get()<4||broker.bytes.get()<body.length)throw new AssertionError("Traffic did not cross packet queues");
            broker.cut=true;broker.simulation.submit(()->broker.network.wires.remove(1)).get();boolean refused=false;try{broker.cross(false,new byte[]{1,2,3}).get(3,TimeUnit.SECONDS);}catch(ExecutionException expected){refused=true;}if(!refused)throw new AssertionError("Broken satellite bearer still sent bytes");
            System.out.println("Real verified HTTPS example.com: "+body.length+" response bytes; "+broker.crossings+" queued satellite crossings / "+broker.bytes+" opaque bytes. Removing the bearer blocks delivery. No client, server or world launched.");
        }
    }
}
