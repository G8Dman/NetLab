package dev.netlab.sputnik.client;

import dev.netlab.sputnik.*;
import dev.netlab.sputnik.core.*;
import de.keksuccino.rinku.Rinku;
import org.cef.*;
import org.cef.browser.*;
import org.cef.handler.*;
import org.cef.network.*;
import org.cef.callback.*;
import org.cef.misc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.*;

/** Remains alive when Screen closes; personal cookies stay in this client-only request context. */
public final class BrowserSession implements AutoCloseable {
    public static BrowserSession current;public final BlockPos pc;public String gateway="";public volatile String status="";public volatile WebBrowser browser;public volatile boolean mute;
    private final Set<Integer> streamIds=ConcurrentHashMap.newKeySet();
    private SocksProxy proxy;private CefClient cef;private CefRequestContext context;private int ticks,audioSequence;private final Map<Integer,CompletableFuture<Void>> opens=new ConcurrentHashMap<>();private final Map<Integer,ConcurrentLinkedQueue<CompletableFuture<Void>>> writes=new ConcurrentHashMap<>();private volatile boolean closed,ready;private volatile String pendingUrl="about:blank";
    public BrowserSession(BlockPos pc){this.pc=pc.immutable();}
    private void send(int id,String op,String host,int port,byte[] bytes){var mc=Minecraft.getInstance();mc.execute(()->{if(mc.getConnection()!=null&&!closed)PacketDistributor.sendToServer(new SputnikPackets.Tunnel(pc,id,op,gateway,host,port,bytes));});}
    public void start(){if(browser!=null)return;if(!Rinku.isInitialized())throw new IllegalStateException("Chromium ещё не готов. Дождись загрузки Rinku при запуске игры");try{
        proxy=new SocksProxy(new SocksProxy.Transport(){public CompletableFuture<Void> open(int id,String host,int port){var f=new CompletableFuture<Void>();streamIds.add(id);opens.put(id,f);BrowserSession.this.send(id,"OPEN",host,port,new byte[0]);return f;}public CompletableFuture<Void> send(int id,byte[] bytes){var f=new CompletableFuture<Void>();writes.computeIfAbsent(id,k->new ConcurrentLinkedQueue<>()).add(f);BrowserSession.this.send(id,"DATA","",0,bytes);return f;}public void close(int id){streamIds.remove(id);BrowserSession.this.send(id,"CLOSE","",0,new byte[0]);var a=opens.remove(id);if(a!=null)a.completeExceptionally(new java.io.IOException("Closed"));var b=writes.remove(id);if(b!=null)for(var f:b)f.completeExceptionally(new java.io.IOException("Closed"));}});
        cef=CefApp.getInstance().createClient();context=CefRequestContext.createContext(new CefRequestContextSettings(),new CefRequestContextHandlerAdapter(){});
        cef.addRequestHandler(new CefRequestHandlerAdapter(){@Override public boolean onBeforeBrowse(CefBrowser b,CefFrame f,CefRequest q,boolean gesture,boolean redirect){return !WebBrowser.allowed(q.getURL())||!ready&&!q.getURL().equals("about:blank");}@Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b,CefFrame f,CefRequest q,boolean navigation,boolean download,String initiator,BoolRef disable){return new CefResourceRequestHandlerAdapter(){@Override public boolean onBeforeResourceLoad(CefBrowser bb,CefFrame ff,CefRequest request){return !WebBrowser.allowed(request.getURL())||!ready&&!request.getURL().equals("about:blank");}};}});
        cef.addLifeSpanHandler(new CefLifeSpanHandlerAdapter(){@Override public void onAfterCreated(CefBrowser b){configureProxy(b);}@Override public boolean onBeforePopup(CefBrowser b,CefFrame f,String target,String name){if(WebBrowser.allowed(target))b.loadURL(target);return true;}});
        cef.addDisplayHandler(new CefDisplayHandlerAdapter(){@Override public void onAddressChange(CefBrowser b,CefFrame f,String url){if(f.isMain()&&browser!=null)browser.address=url;}@Override public void onTitleChange(CefBrowser b,String title){if(browser!=null)browser.title=title;}});
        cef.addLoadHandler(new CefLoadHandlerAdapter(){@Override public void onLoadError(CefBrowser b,CefFrame f,ErrorCode code,String text,String failed){if(f.isMain()&&browser!=null)browser.error=text;}});
        cef.addAudioHandler(new CefAudioHandlerAdapter(){@Override public boolean getAudioParameters(CefBrowser b,CefAudioParameters p){p.sampleRate=24000;p.framesPerBuffer=2400;p.channelLayout=CefChannelLayout.CEF_CHANNEL_LAYOUT_MONO;return true;}@Override public void onAudioStreamPacket(CefBrowser b,DataPointer data,int frames,long pts){if(mute||closed||frames<1||frames>6000)return;try{var channel=data.getData(0).forCapacity(frames*4);byte[] pcm=new byte[frames*2];for(int i=0;i<frames;i++){float f=channel.getFloat(i);short sample=(short)Math.round(Math.clamp(Float.isFinite(f)?f:0,-1,1)*32767);pcm[2*i]=(byte)sample;pcm[2*i+1]=(byte)(sample>>8);}int seq=audioSequence++;Minecraft.getInstance().execute(()->{if(!closed&&Minecraft.getInstance().getConnection()!=null){if(pc.getY()==-2048&&Minecraft.getInstance().player!=null)SpeakerAudio.local(Minecraft.getInstance().player.getUUID(),seq,pcm);else PacketDistributor.sendToServer(new SputnikPackets.Audio(pc,seq,pcm));}});}catch(RuntimeException e){status="Ошибка звука Chromium: "+e.getMessage();}}});
        browser=new WebBrowser(cef,context);browser.resize(1280,720);browser.createImmediately();status="Интернет через спутниковый шлюз";
    }catch(Exception e){closeResources();throw new IllegalStateException(e.getMessage(),e);}}
    /** CEF SetPreference explicitly requires TID_UI; this callback runs on that native thread. */
    private void configureProxy(CefBrowser nativeBrowser){try{
        if(closed||proxy==null)return;
        String error=context.setPreference("proxy",Map.of("mode","fixed_servers","server","socks5://127.0.0.1:"+proxy.port(),"bypass_list","<-loopback>"));
        if(error!=null&&!error.isEmpty())throw new IllegalStateException("Прокси Chromium: "+error);
        String rtc=context.setPreference("webrtc.ip_handling_policy","disable_non_proxied_udp");if(rtc!=null&&!rtc.isEmpty())throw new IllegalStateException("WebRTC: "+rtc);
        context.setPreference("quic_allowed",false);context.setPreference("dns_over_https.mode","off");context.setPreference("net.network_prediction_options",2);
        if(nativeBrowser instanceof WebBrowser view)view.readyView();nativeBrowser.setWindowVisibility(true);nativeBrowser.setWindowlessFrameRate(20);nativeBrowser.invalidate(CefPaintElementType.PET_VIEW);ready=true;if(!pendingUrl.equals("about:blank"))nativeBrowser.loadURL(pendingUrl);
    }catch(RuntimeException e){status="Не удалось подключить Chromium: "+e.getMessage();if(browser!=null)browser.error=status;}}
    public void navigate(String url){if(browser==null)start();String target=url.strip();if(!target.contains(":"))target="https://"+target;if(!WebBrowser.allowed(target))throw new IllegalArgumentException("Адрес HTTP или HTTPS");browser.error="";pendingUrl=target;if(ready)browser.loadURL(target);}
    public static void receive(SputnikPackets.Reply q){var s=current;if(s==null||s.closed||!s.streamIds.contains(q.id()))return;switch(q.op()){case "OPEN"->{var f=s.opens.remove(q.id());if(f!=null)f.complete(null);}case "ACK"->{var queue=s.writes.get(q.id());var f=queue==null?null:queue.poll();if(f!=null)f.complete(null);}case "DATA"->{if(s.proxy!=null)s.proxy.receive(q.id(),q.data(),()->s.send(q.id(),"ACK","",0,new byte[0]));}case "EOF"->{if(s.proxy!=null)s.proxy.eof(q.id());}case "ERROR"->{s.status=q.status();var error=new java.io.IOException(q.status());var f=s.opens.remove(q.id());if(f!=null)f.completeExceptionally(error);var queue=s.writes.remove(q.id());if(queue!=null)for(var pending:queue)pending.completeExceptionally(error);if(s.proxy!=null)s.proxy.fail(q.id(),q.status());}}}
    public void tick(){if(++ticks%20==0&&Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new SputnikPackets.Action(pc,0,"heartbeat",""));}
    private void closeResources(){if(proxy!=null){proxy.close();proxy=null;}if(browser!=null){browser.disposeBrowser();browser=null;}if(cef!=null){cef.dispose();cef=null;}if(context!=null){context.dispose();context=null;}}
    @Override public void close(){if(closed)return;closed=true;if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new SputnikPackets.Action(pc,0,"browserClose",""));closeResources();for(var f:opens.values())f.completeExceptionally(new java.io.IOException("Closed"));for(var queue:writes.values())for(var f:queue)f.completeExceptionally(new java.io.IOException("Closed"));opens.clear();writes.clear();streamIds.clear();}
}
