package dev.netlab.sputnik.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Loopback SOCKS5 frontend. Every byte crosses the supplied game transport; no direct connect fallback. */
public final class SocksProxy implements AutoCloseable {
    public static final int CHUNK=16384,MAX_STREAMS=12;
    public interface Transport {CompletableFuture<Void> open(int id,String host,int port);CompletableFuture<Void> send(int id,byte[] bytes);void close(int id);}
    private record Chunk(byte[] data,Runnable delivered){}
    public static final class Pipe {
        final Socket socket;final BlockingQueue<Chunk> input=new ArrayBlockingQueue<>(64);final CompletableFuture<Void> opened=new CompletableFuture<>();final AtomicBoolean closed=new AtomicBoolean();
        Pipe(Socket s){socket=s;}
    }
    private final ServerSocket listener;private final Transport transport;private final Map<Integer,Pipe> streams=new ConcurrentHashMap<>();private static final AtomicInteger ids=new AtomicInteger(1);private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();private volatile boolean closed;
    public SocksProxy(Transport t)throws IOException{transport=t;listener=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));workers.submit(()->{while(!closed)try{Socket socket=listener.accept();if(streams.size()>=MAX_STREAMS){socket.close();continue;}int id=ids.getAndIncrement();var p=new Pipe(socket);streams.put(id,p);workers.submit(()->serve(id,p));}catch(IOException e){if(!closed)close();}});}
    public int port(){return listener.getLocalPort();}
    public void receive(int id,byte[] bytes){receive(id,bytes,()->{});}
    public void receive(int id,byte[] bytes,Runnable delivered){var p=streams.get(id);if(p!=null&&bytes.length<=CHUNK&&!p.input.offer(new Chunk(bytes.clone(),delivered)))fail(id,"Буфер браузера заполнен");}
    public void eof(int id){receive(id,new byte[0]);}
    public void fail(int id,String reason){var p=streams.remove(id);if(p!=null&&p.closed.compareAndSet(false,true)){p.opened.completeExceptionally(new IOException(reason));try{p.socket.close();}catch(IOException ignored){}p.input.offer(new Chunk(new byte[0],()->{}));}}
    private static int octet(InputStream in)throws IOException{int b=in.read();if(b<0)throw new EOFException();return b;}
    public static String[] handshake(InputStream in,OutputStream out)throws IOException{
        if(octet(in)!=5)throw new IOException("SOCKS5 required");int methods=octet(in);if(methods<1||methods>16)throw new IOException("SOCKS methods");byte[] m=in.readNBytes(methods);if(m.length!=methods)throw new EOFException();boolean noAuth=false;for(byte x:m)if(x==0)noAuth=true;out.write(new byte[]{5,(byte)(noAuth?0:255)});out.flush();if(!noAuth)throw new IOException("SOCKS auth method");
        if(octet(in)!=5||octet(in)!=1||octet(in)!=0)throw new IOException("SOCKS CONNECT only");int type=octet(in);String host;
        if(type==3){int n=octet(in);if(n<1)throw new IOException("SOCKS hostname");byte[] text=in.readNBytes(n);if(text.length!=n)throw new EOFException();host=new String(text,StandardCharsets.US_ASCII);}else if(type==1||type==4){int n=type==1?4:16;byte[] bytes=in.readNBytes(n);if(bytes.length!=n)throw new EOFException();host=InetAddress.getByAddress(bytes).getHostAddress();}else throw new IOException("SOCKS address type");
        int port=octet(in)*256+octet(in);InternetTarget.validate(host,port);return new String[]{host,Integer.toString(port)};
    }
    private void serve(int id,Pipe p){try{p.socket.setSoTimeout(30000);p.socket.setTcpNoDelay(true);var input=p.socket.getInputStream();var output=p.socket.getOutputStream();var target=handshake(input,output);transport.open(id,target[0],Integer.parseInt(target[1])).get(60,TimeUnit.SECONDS);if(p.closed.get())throw new IOException("Stream closed");output.write(new byte[]{5,0,0,1,0,0,0,0,0,0});output.flush();p.socket.setSoTimeout(120000);
            workers.submit(()->{try{while(!p.closed.get()){var chunk=p.input.poll(120,TimeUnit.SECONDS);if(chunk==null||chunk.data.length==0)break;output.write(chunk.data);output.flush();chunk.delivered.run();}}catch(Exception ignored){}finally{fail(id,"Поток закрыт");transport.close(id);}});
            var pending=ConcurrentHashMap.<CompletableFuture<Void>>newKeySet();var window=new Semaphore(16);var buffer=new byte[CHUNK];int n;
            while(!p.closed.get()&&(n=input.read(buffer))>=0)if(n>0){if(!window.tryAcquire(60,TimeUnit.SECONDS))throw new IOException("Upload timeout");var f=transport.send(id,Arrays.copyOf(buffer,n));pending.add(f);f.whenComplete((v,e)->{pending.remove(f);window.release();if(e!=null)fail(id,"Канал передачи отключён");});}
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(60,TimeUnit.SECONDS);
        }catch(Exception e){try{p.socket.getOutputStream().write(new byte[]{5,1,0,1,0,0,0,0,0,0});}catch(IOException ignored){}}finally{fail(id,"Браузер закрыл соединение");transport.close(id);}}
    @Override public void close(){if(closed)return;closed=true;try{listener.close();}catch(IOException ignored){}for(int id:List.copyOf(streams.keySet())){fail(id,"Браузер закрыт");transport.close(id);}workers.shutdownNow();}
}
