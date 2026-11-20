package dev.netlab.media.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.keksuccino.rinku.Rinku;
import dev.netlab.media.MediaPackets;
import dev.netlab.media.core.MediaSource;
import dev.netlab.sputnik.SputnikPackets;
import dev.netlab.sputnik.client.SpeakerAudio;
import dev.netlab.sputnik.client.WebBrowser;
import dev.netlab.sputnik.core.SocksProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefRequestContextSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefPaintElementType;
import org.cef.browser.CefRequestContext;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.misc.CefAudioParameters;
import org.cef.misc.CefChannelLayout;
import org.cef.misc.DataPointer;
import org.cef.network.CefRequest;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** One player document per nearby television; HTTPS is carried by the Sputnik tunnel. */
public final class MediaVideoSession implements AutoCloseable {
    public record Display(boolean on, int channel, String status) {}
    private record Page(String url, String html, String revision) {}
    public static final Map<Long,MediaVideoSession> SESSIONS = new HashMap<>();
    private static final Map<Long,Display> DISPLAYS = new HashMap<>();
    private static final Map<Integer,MediaVideoSession> STREAM_OWNERS = new ConcurrentHashMap<>();
    private static final int MAX_TVS = 4;
    private static final int WIDTH = 640, HEIGHT = 360;

    private final UUID audioId = UUID.randomUUID();
    public final BlockPos pc;
    public volatile WebBrowser browser;
    public volatile String status = "Загрузка плеера";
    private volatile String gateway = "", playingPosition = "";
    private volatile boolean closed, ready, offline;
    private volatile Page page;
    private JsonObject scene;
    private String sourceKey = "";
    private boolean usingFallback;
    private int ticks, lastProof, audioSequence, lastRendered = -100, startAt;
    private int sourceTick;
    private boolean frameDrawn;
    private SocksProxy proxy;
    private CefClient cef;
    private CefRequestContext context;
    private final Map<Integer,CompletableFuture<Void>> opens = new ConcurrentHashMap<>();
    private final Map<Integer,ConcurrentLinkedQueue<CompletableFuture<Void>>> writes = new ConcurrentHashMap<>();

    private MediaVideoSession(BlockPos pc, JsonObject state) {
        this.pc=pc.immutable();
        update(state);
    }

    private void send(int id,String op,String host,int port,byte[] bytes) {
        var mc=Minecraft.getInstance();
        var connection=mc.getConnection();
        if(connection==null)return;
        mc.execute(()->{
            if(mc.getConnection()==connection && (!closed || op.equals("CLOSE")))
                PacketDistributor.sendToServer(new SputnikPackets.Tunnel(pc,id,op,gateway,host,port,bytes));
        });
    }

    private void start() throws IOException {
        proxy=new SocksProxy(new SocksProxy.Transport(){
            public CompletableFuture<Void> open(int id,String host,int port){
                var future=new CompletableFuture<Void>();
                opens.put(id,future);STREAM_OWNERS.put(id,MediaVideoSession.this);
                MediaVideoSession.this.send(id,"OPEN",host,port,new byte[0]);
                return future;
            }
            public CompletableFuture<Void> send(int id,byte[] bytes){
                var future=new CompletableFuture<Void>();
                writes.computeIfAbsent(id,k->new ConcurrentLinkedQueue<>()).add(future);
                MediaVideoSession.this.send(id,"DATA","",0,bytes);
                return future;
            }
            public void close(int id){
                MediaVideoSession.this.send(id,"CLOSE","",0,new byte[0]);
                STREAM_OWNERS.remove(id,MediaVideoSession.this);
                failPending(id,new IOException("Соединение закрыто"));
            }
        });
        cef=CefApp.getInstance().createClient();
        context=CefRequestContext.createContext(new CefRequestContextSettings(),new CefRequestContextHandlerAdapter(){});
        cef.addRequestHandler(new CefRequestHandlerAdapter(){
            @Override public boolean onBeforeBrowse(CefBrowser b,CefFrame f,CefRequest request,boolean gesture,boolean redirect){
                String url = request.getURL();
                if(url.equals("about:blank"))return false;
                return !ready || !WebBrowser.allowed(url) || (f.isMain() && !url.equals(page.url()));
            }
            @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b,CefFrame f,CefRequest request,
                    boolean navigation,boolean download,String initiator,BoolRef disable){
                return new CefResourceRequestHandlerAdapter(){
                    @Override public CefResourceHandler getResourceHandler(CefBrowser bb,CefFrame ff,CefRequest q){
                        Page current=page;
                        return q.getURL().equals(current.url())?new HtmlResource(current.html()):null;
                    }
                    @Override public boolean onBeforeResourceLoad(CefBrowser bb,CefFrame ff,CefRequest q){
                        return !WebBrowser.allowed(q.getURL()) || (!ready && !q.getURL().equals("about:blank"));
                    }
                };
            }
        });
        cef.addLifeSpanHandler(new CefLifeSpanHandlerAdapter(){
            @Override public void onAfterCreated(CefBrowser b){configureProxy(b);}
            @Override public boolean onBeforePopup(CefBrowser b,CefFrame f,String target,String name){return true;}
        });
        cef.addDisplayHandler(new CefDisplayHandlerAdapter(){
            @Override public void onTitleChange(CefBrowser b,String title){
                Page current=page;
                if(closed || b!=browser || !b.getURL().equals(current.url()))return;
                String framePrefix="NETLAB_FRAME:"+current.revision()+":";
                String statusPrefix="NETLAB_STATUS:"+current.revision()+":";
                if(title.startsWith(framePrefix)){
                    playingPosition=title.substring(framePrefix.length());
                    status="";
                }else if(title.startsWith(statusPrefix)){
                    String message=title.substring(statusPrefix.length());
                    offline=message.equals("TWITCH_OFFLINE");
                    status=offline?"Twitch: трансляция офлайн":message;
                    playingPosition="";
                }
            }
        });
        cef.addLoadHandler(new CefLoadHandlerAdapter(){
            @Override public void onLoadError(CefBrowser b,CefFrame f,ErrorCode code,String text,String failed){
                if(f.isMain() && b==browser && failed.equals(page.url()))status="Плеер: "+text;
            }
        });
        cef.addAudioHandler(new CefAudioHandlerAdapter(){
            @Override public boolean getAudioParameters(CefBrowser b,CefAudioParameters p){
                p.sampleRate=24000;p.framesPerBuffer=2400;p.channelLayout=CefChannelLayout.CEF_CHANNEL_LAYOUT_MONO;return true;
            }
            @Override public void onAudioStreamPacket(CefBrowser b,DataPointer data,int frames,long pts){
                if(closed||frames<1||frames>6000)return;
                receiveAudio(b,data,frames);
            }
        });
        browser=new WebBrowser(cef,context);
        browser.resize(WIDTH,HEIGHT);
        browser.createImmediately();
    }

    private void receiveAudio(CefBrowser source,DataPointer data,int frames) {
        try{
            var channel=data.getData(0).forCapacity(frames*4);
            byte[] pcm=new byte[frames*2];
            for(int i=0;i<frames;i++){
                float value=channel.getFloat(i);
                short sample=(short)Math.round(Math.clamp(Float.isFinite(value)?value:0,-1,1)*32767);
                pcm[2*i]=(byte)sample;pcm[2*i+1]=(byte)(sample>>8);
            }
            int sequence=audioSequence++;
            Minecraft.getInstance().execute(()->{
                if(closed||source!=browser||!recentlyRendered()||!scene.has("outputs"))return;
                if(Minecraft.getInstance().getConnection()!=null)
                    PacketDistributor.sendToServer(new dev.netlab.Packets.LearningHint(pc,"media.audio",audioId.toString()));
                for(var element:scene.getAsJsonArray("outputs")){
                    var output=element.getAsJsonObject();
                    SpeakerAudio.receive(new SputnikPackets.Sound(audioId,BlockPos.of(output.get("pos").getAsLong()),sequence,output.get("volume").getAsFloat(),pcm));
                }
            });
        }catch(RuntimeException error){status="Ошибка вывода звука";}
    }

    /** CEF preferences are set from its UI thread before requesting the local player document. */
    private void configureProxy(CefBrowser nativeBrowser) {
        try{
            if(closed||proxy==null||nativeBrowser!=browser)return;
            String error=context.setPreference("proxy",Map.of("mode","fixed_servers","server","socks5://127.0.0.1:"+proxy.port(),"bypass_list","<-loopback>"));
            if(error!=null&&!error.isEmpty())throw new IllegalStateException(error);
            String rtc=context.setPreference("webrtc.ip_handling_policy","disable_non_proxied_udp");
            if(rtc!=null&&!rtc.isEmpty())throw new IllegalStateException(rtc);
            context.setPreference("quic_allowed",false);
            context.setPreference("dns_over_https.mode","off");
            context.setPreference("net.network_prediction_options",2);
            if(nativeBrowser instanceof WebBrowser view)view.readyView();
            nativeBrowser.setWindowVisibility(true);
            nativeBrowser.setWindowlessFrameRate(20);
            nativeBrowser.invalidate(CefPaintElementType.PET_VIEW);
            ready=true;
            nativeBrowser.loadURL(page.url());
        }catch(RuntimeException error){status="Не удалось подключить плеер к Sputnik";}
    }

    private void failPending(int id,IOException error) {
        var pending=opens.remove(id);
        if(pending!=null)pending.completeExceptionally(error);
        var queue=writes.remove(id);
        if(queue!=null)for(var future:queue)future.completeExceptionally(error);
    }

    public static void receive(SputnikPackets.Reply reply) {
        var session=STREAM_OWNERS.get(reply.id());
        if(session==null||session.closed)return;
        switch(reply.op()){
            case "OPEN"->{var future=session.opens.remove(reply.id());if(future!=null)future.complete(null);}
            case "ACK"->{var queue=session.writes.get(reply.id());var future=queue==null?null:queue.poll();if(future!=null)future.complete(null);}
            case "DATA"->{if(session.proxy!=null)session.proxy.receive(reply.id(),reply.data(),()->session.send(reply.id(),"ACK","",0,new byte[0]));}
            case "EOF"->{if(session.proxy!=null)session.proxy.eof(reply.id());}
            case "ERROR"->{
                session.status=reply.status();
                session.failPending(reply.id(),new IOException(reply.status()));
                if(session.proxy!=null)session.proxy.fail(reply.id(),reply.status());
            }
        }
    }

    private boolean recentlyRendered(){return ticks-lastRendered<=10;}
    public WebBrowser.ResourceLocationHandle frame(){
        lastRendered=ticks;
        if(browser==null)return null;
        var result=browser.frameHandle();
        frameDrawn=result!=null;
        return result;
    }
    public boolean click(double u,double v){
        if(browser==null||!ready)return false;
        int x=Math.clamp((int)(u*WIDTH),0,WIDTH-1), y=Math.clamp((int)(v*HEIGHT),0,HEIGHT-1);
        browser.move(x,y);browser.mouse(x,y,0,true);browser.mouse(x,y,0,false);
        return true;
    }

    public void tick(){
        ticks++;
        if(!recentlyRendered()){
            if(browser!=null)closeResources();
            return;
        }
        if(offline && !usingFallback && scene.has("fallback")){
            usingFallback=true;
            setPage(scene.get("fallback").getAsString());
        }
        if(browser==null){
            if(ticks<startAt)return;
            startAt=ticks+40;
            if(!Rinku.isInitialized()){status="Ожидание Chromium";return;}
            try{start();}catch(RuntimeException|IOException error){closeResources();status="Не удалось запустить плеер";}
            return;
        }
        if(ready && ticks%20==0){
            double target=offset();
            browser.executeJavaScript("window.netlabSeek&&window.netlabSeek("+target+")",page.url(),0);
        }
        if(ticks-lastProof>=40&&frameDrawn&&!playingPosition.isBlank()&&Minecraft.getInstance().getConnection()!=null){
            lastProof=ticks;
            PacketDistributor.sendToServer(new MediaPackets.Action(pc,0,"frame",playingPosition));
        }
    }

    private double offset(){return scene.get("seconds").getAsDouble()+Math.max(0,ticks-sourceTick)/20.0;}

    private void setPage(String url){
        playingPosition="";offline=false;status="Загрузка плеера";
        String revision=UUID.randomUUID().toString();
        page=new Page(MediaSource.PLAYER_URL+"?rev="+revision,PlayerPage.html(url,offset(),revision),revision);
        if(browser!=null&&ready)browser.loadURL(page.url());
    }

    private void update(JsonObject state){
        scene=state;sourceTick=ticks;
        String nextGateway=state.get("gateway").getAsString();
        if(!gateway.equals(nextGateway)&&browser!=null)closeResources();
        gateway=nextGateway;
        String next=state.get("source").getAsString()+":"+state.get("channel")+":"+state.get("url")+":"
                +state.get("entry")+":"+state.get("cycle")+":"+state.get("started")+":"+state.get("fallback");
        if(!next.equals(sourceKey)){
            sourceKey=next;usingFallback=false;
            if(browser!=null)closeResources();
            setPage(state.get("url").getAsString());
        }
    }

    public static Display display(BlockPos pos){return DISPLAYS.get(pos.asLong());}
    public static void scene(JsonArray states){
        var seen=new HashSet<Long>();
        DISPLAYS.clear();
        for(var e:states){
            var row=e.getAsJsonObject();long key=row.get("pos").getAsLong();
            DISPLAYS.put(key,new Display(row.get("on").getAsBoolean(),row.has("channel")?row.get("channel").getAsInt():0,row.get("status").getAsString()));
            if(!row.has("url")||seen.size()>=MAX_TVS)continue;
            seen.add(key);
            var session=SESSIONS.get(key);
            if(session==null)SESSIONS.put(key,new MediaVideoSession(BlockPos.of(key),row));
            else session.update(row);
        }
        for(long key:List.copyOf(SESSIONS.keySet()))if(!seen.contains(key))SESSIONS.remove(key).close();
    }

    public static void clear(){
        for(var session:SESSIONS.values())session.close();
        SESSIONS.clear();DISPLAYS.clear();
    }

    private void closeResources(){
        ready=false;playingPosition="";frameDrawn=false;
        for(var entry:STREAM_OWNERS.entrySet())if(entry.getValue()==this){
            send(entry.getKey(),"CLOSE","",0,new byte[0]);
            STREAM_OWNERS.remove(entry.getKey(),this);
        }
        if(proxy!=null){proxy.close();proxy=null;}
        if(browser!=null){browser.disposeBrowser();browser=null;}
        if(cef!=null){cef.dispose();cef=null;}
        if(context!=null){context.dispose();context=null;}
        IOException error=new IOException("Плеер остановлен");
        for(int id:List.copyOf(opens.keySet()))failPending(id,error);
        for(int id:List.copyOf(writes.keySet()))failPending(id,error);
    }

    @Override public void close(){if(!closed){closed=true;closeResources();}}
}
