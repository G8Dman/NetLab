package dev.netlab.media.client;
import dev.netlab.sputnik.client.*;
import dev.netlab.media.*;
import com.google.gson.*;

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
public final class MediaVideoSession implements AutoCloseable {
    public static final Map<Long,MediaVideoSession> SESSIONS=new HashMap<>();
    private static final Map<Integer,MediaVideoSession> STREAM_OWNERS=new ConcurrentHashMap<>();
    private final UUID audioId=UUID.randomUUID();
    private JsonObject scene;private String html="",sourceKey="";
public final BlockPos pc;public String gateway="";public volatile String status="";public volatile WebBrowser browser;public volatile boolean mute;private volatile String playingPosition="";private int lastProof;
    private SocksProxy proxy;private CefClient cef;private CefRequestContext context;private int ticks,audioSequence;private final Map<Integer,CompletableFuture<Void>> opens=new ConcurrentHashMap<>();private final Map<Integer,ConcurrentLinkedQueue<CompletableFuture<Void>>> writes=new ConcurrentHashMap<>();private volatile boolean closed,ready;private volatile String pendingUrl="about:blank";
    public MediaVideoSession(BlockPos pc,JsonObject state){this.pc=pc.immutable();update(state);}
    private void send(int id,String op,String host,int port,byte[] bytes){var mc=Minecraft.getInstance();mc.execute(()->{if(mc.getConnection()!=null&&!closed)PacketDistributor.sendToServer(new SputnikPackets.Tunnel(pc,id,op,gateway,host,port,bytes));});}
    public void start(){if(browser!=null)return;if(!Rinku.isInitialized())throw new IllegalStateException("Chromium ещё не готов. Дождись загрузки Rinku при запуске игры");try{
        proxy=new SocksProxy(new SocksProxy.Transport(){public CompletableFuture<Void> open(int id,String host,int port){var f=new CompletableFuture<Void>();opens.put(id,f);STREAM_OWNERS.put(id,MediaVideoSession.this);MediaVideoSession.this.send(id,"OPEN",host,port,new byte[0]);return f;}public CompletableFuture<Void> send(int id,byte[] bytes){var f=new CompletableFuture<Void>();writes.computeIfAbsent(id,k->new ConcurrentLinkedQueue<>()).add(f);MediaVideoSession.this.send(id,"DATA","",0,bytes);return f;}public void close(int id){MediaVideoSession.this.send(id,"CLOSE","",0,new byte[0]);STREAM_OWNERS.remove(id);var a=opens.remove(id);if(a!=null)a.completeExceptionally(new java.io.IOException("Closed"));var b=writes.remove(id);if(b!=null)for(var f:b)f.completeExceptionally(new java.io.IOException("Closed"));}});
        cef=CefApp.getInstance().createClient();context=CefRequestContext.createContext(new CefRequestContextSettings(),new CefRequestContextHandlerAdapter(){});
        cef.addRequestHandler(new CefRequestHandlerAdapter(){@Override public boolean onBeforeBrowse(CefBrowser b,CefFrame f,CefRequest q,boolean gesture,boolean redirect){return !WebBrowser.allowed(q.getURL())||!ready&&!q.getURL().equals("about:blank");}@Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b,CefFrame f,CefRequest q,boolean navigation,boolean download,String initiator,BoolRef disable){return new CefResourceRequestHandlerAdapter(){@Override public CefResourceHandler getResourceHandler(CefBrowser bb,CefFrame ff,CefRequest request){return request.getURL().equals("https://netlab.media/player")?new HtmlResource(html):null;}@Override public boolean onBeforeResourceLoad(CefBrowser bb,CefFrame ff,CefRequest request){return !WebBrowser.allowed(request.getURL())||!ready&&!request.getURL().equals("about:blank");}};}});
        cef.addLifeSpanHandler(new CefLifeSpanHandlerAdapter(){@Override public void onAfterCreated(CefBrowser b){configureProxy(b);}@Override public boolean onBeforePopup(CefBrowser b,CefFrame f,String target,String name){return true;}});
        cef.addDisplayHandler(new CefDisplayHandlerAdapter(){@Override public void onAddressChange(CefBrowser b,CefFrame f,String url){if(f.isMain()&&browser!=null)browser.address=url;}@Override public void onTitleChange(CefBrowser b,String title){if(browser!=null)browser.title=title;if(title.startsWith("NETLAB_FRAME:"))playingPosition=title.substring(13);}});
        cef.addLoadHandler(new CefLoadHandlerAdapter(){@Override public void onLoadError(CefBrowser b,CefFrame f,ErrorCode code,String text,String failed){if(f.isMain()&&browser!=null)browser.error=text;}});
        cef.addAudioHandler(new CefAudioHandlerAdapter(){@Override public boolean getAudioParameters(CefBrowser b,CefAudioParameters p){p.sampleRate=24000;p.framesPerBuffer=2400;p.channelLayout=CefChannelLayout.CEF_CHANNEL_LAYOUT_MONO;return true;}@Override public void onAudioStreamPacket(CefBrowser b,DataPointer data,int frames,long pts){if(mute||closed||frames<1||frames>6000)return;try{var channel=data.getData(0).forCapacity(frames*4);byte[] pcm=new byte[frames*2];for(int i=0;i<frames;i++){float f=channel.getFloat(i);short sample=(short)Math.round(Math.clamp(Float.isFinite(f)?f:0,-1,1)*32767);pcm[2*i]=(byte)sample;pcm[2*i+1]=(byte)(sample>>8);}int seq=audioSequence++;Minecraft.getInstance().execute(()->{if(!closed&&scene!=null&&scene.has("outputs")){var source=audioId;if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new dev.netlab.Packets.LearningHint(pc,"media.audio",audioId.toString()));for(var o:scene.getAsJsonArray("outputs")){var output=o.getAsJsonObject();SpeakerAudio.receive(new SputnikPackets.Sound(source,BlockPos.of(output.get("pos").getAsLong()),seq,output.get("volume").getAsFloat(),pcm));}}});}catch(RuntimeException e){status="Ошибка звука Chromium: "+e.getMessage();}}});
        browser=new WebBrowser(cef,context);browser.resize(640,360);browser.createImmediately();status="IPTV";
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
    private void navigate(String url){if(browser==null)start();String target=url.strip();if(!target.contains(":"))target="https://"+target;if(!WebBrowser.allowed(target))throw new IllegalArgumentException("Адрес HTTP или HTTPS");browser.error="";pendingUrl=target;if(ready)browser.loadURL(target);}
    public static void receive(SputnikPackets.Reply q){var s=STREAM_OWNERS.get(q.id());if(s==null||s.closed)return;switch(q.op()){case "OPEN"->{var f=s.opens.remove(q.id());if(f!=null)f.complete(null);}case "ACK"->{var queue=s.writes.get(q.id());var f=queue==null?null:queue.poll();if(f!=null)f.complete(null);}case "DATA"->{if(s.proxy!=null)s.proxy.receive(q.id(),q.data(),()->s.send(q.id(),"ACK","",0,new byte[0]));}case "EOF"->{if(s.proxy!=null)s.proxy.eof(q.id());}case "ERROR"->{s.status=q.status();var error=new java.io.IOException(q.status());var f=s.opens.remove(q.id());if(f!=null)f.completeExceptionally(error);var queue=s.writes.remove(q.id());if(queue!=null)for(var pending:queue)pending.completeExceptionally(error);if(s.proxy!=null)s.proxy.fail(q.id(),q.status());}}}
    public void tick(){ticks++;if(ticks-lastProof>=40&&!playingPosition.isBlank()&&browser!=null&&browser.frameHandle()!=null&&Minecraft.getInstance().getConnection()!=null){lastProof=ticks;PacketDistributor.sendToServer(new MediaPackets.Action(pc,0,"frame",playingPosition));}if(browser==null){try{start();navigate("https://netlab.media/player");}catch(RuntimeException e){status=e.getMessage();}return;}if(ticks%20==0&&scene!=null){double target=scene.get("seconds").getAsDouble();browser.executeJavaScript("window.netlabSeek&&window.netlabSeek("+target+")","https://netlab.media/player",0);}}

    public void update(JsonObject state){scene=state;gateway=state.has("gateway")?state.get("gateway").getAsString():"";String next=state.get("url").getAsString()+":"+state.get("entry")+":"+state.get("cycle")+":"+state.get("started");if(!next.equals(sourceKey)){sourceKey=next;html=PlayerPage.html(state.get("url").getAsString(),state.get("seconds").getAsDouble());if(browser!=null)browser.reloadIgnoreCache();}}
    public static void scene(JsonArray states){var seen=new HashSet<Long>();int count=0;for(var e:states){var row=e.getAsJsonObject();long key=row.get("pos").getAsLong();if(!row.has("url")||count++>=4)continue;seen.add(key);var session=SESSIONS.get(key);if(session==null){session=new MediaVideoSession(BlockPos.of(key),row);SESSIONS.put(key,session);}else session.update(row);}for(long key:List.copyOf(SESSIONS.keySet()))if(!seen.contains(key)){SESSIONS.remove(key).close();}}
    public static void clear(){for(var session:SESSIONS.values())session.close();SESSIONS.clear();STREAM_OWNERS.clear();}
    private void closeResources(){if(proxy!=null){proxy.close();proxy=null;}if(browser!=null){browser.disposeBrowser();browser=null;}if(cef!=null){cef.dispose();cef=null;}if(context!=null){context.dispose();context=null;}}
    @Override public void close(){if(closed)return;closed=true;STREAM_OWNERS.entrySet().removeIf(e->e.getValue()==this);closeResources();for(var f:opens.values())f.completeExceptionally(new java.io.IOException("Closed"));for(var queue:writes.values())for(var f:queue)f.completeExceptionally(new java.io.IOException("Closed"));opens.clear();writes.clear();}
}
