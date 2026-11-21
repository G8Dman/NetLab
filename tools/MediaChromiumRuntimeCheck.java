import org.cef.*;
import org.cef.browser.*;
import org.cef.handler.*;
import org.cef.network.*;
import org.cef.misc.*;
import org.cef.callback.*;
import dev.netlab.media.client.*;
import java.awt.Rectangle;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
/** Actual bundled Chromium: local IPTV player HTML, decoded video/audio, shared-clock seek. No Minecraft. */
public final class MediaChromiumRuntimeCheck {
    private static final String FIRST="https://netlab.media/player?rev=first";
    private static final String NEXT="https://netlab.media/player?rev=next";
    private static final String YOUTUBE="https://netlab.media/player?rev=youtube";
    private static final String VIDEO="https://netlab.media/check.webm";
    private static final String YOUTUBE_API="""
            window.YT={Player:class {
              constructor(element,options){
                this.loads=[];this.id=options.videoId;this.time=options.playerVars.start;
                window.fixturePlayer=this;
                setTimeout(()=>options.events.onReady({target:this}),0);
              }
              playVideo(){this.started=true}
              loadVideoById(request){this.loads.push(request);this.id=request.videoId;this.time=request.startSeconds}
              getPlayerState(){return 1}
              getVideoUrl(){return 'https://www.youtube.com/watch?v='+this.id}
              getCurrentTime(){return this.time}
              seekTo(time){this.time=time}
            }};
            window.onYouTubeIframeAPIReady();
            """;
    static CefResourceHandler resource(byte[] body,String type){return new CefResourceHandlerAdapter(){int offset;public boolean processRequest(CefRequest request,CefCallback callback){callback.Continue();return true;}public void getResponseHeaders(CefResponse response,IntRef length,StringRef redirect){response.setStatus(200);response.setMimeType(type);length.set(body.length);}public boolean readResponse(byte[] data,int max,IntRef read,CefCallback callback){int n=Math.min(max,body.length-offset);if(n<=0){read.set(0);return false;}System.arraycopy(body,offset,data,0,n);offset+=n;read.set(n);return true;}};}
    public static void main(String[] args)throws Exception{
        System.setProperty("jcef.path",args[0]);System.setProperty("jcef.external_message_pump","true");dev.netlab.sputnik.client.NativeBrowserLibraries.prepare();String[] switches={"--disable-gpu","--disable-background-networking","--autoplay-policy=no-user-gesture-required"};if(!CefApp.startup(switches))throw new AssertionError("CEF startup failed");var settings=new CefSettings();settings.windowless_rendering_enabled=true;settings.log_severity=CefSettings.LogSeverity.LOGSEVERITY_ERROR;settings.log_file="/tmp/netlab-media-cef.log";var app=CefApp.getInstance(switches,settings);var client=app.createClient();long deadline=System.nanoTime()+30_000_000_000L;while(CefApp.getState()!=CefApp.CefAppState.INITIALIZED&&System.nanoTime()<deadline){app.N_DoMessageLoopWork();Thread.sleep(10);}if(CefApp.getState()!=CefApp.CefAppState.INITIALIZED)throw new AssertionError("CEF init");var context=CefRequestContext.createContext(new CefRequestContextSettings(),new CefRequestContextHandlerAdapter(){});byte[] video=Files.readAllBytes(Path.of(args[1]));var audio=new AtomicBoolean();var paint=new AtomicBoolean();var seek=new AtomicBoolean();var failure=new AtomicReference<String>();
        var created=new AtomicInteger();var committed=new AtomicReference<String>();var nextFrame=new AtomicBoolean();var nextAudio=new AtomicBoolean();var youtubeSwitch=new AtomicBoolean();
        client.addRequestHandler(new CefRequestHandlerAdapter(){
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b,CefFrame f,CefRequest q,boolean navigation,boolean download,String initiator,BoolRef disable){
                return new CefResourceRequestHandlerAdapter(){public CefResourceHandler getResourceHandler(CefBrowser b,CefFrame f,CefRequest q){
                    return switch(q.getURL()){
                        case FIRST -> new HtmlResource(PlayerPage.html(VIDEO,2));
                        case NEXT -> new HtmlResource(PlayerPage.html(VIDEO+"?next",1));
                        case YOUTUBE -> new HtmlResource(PlayerPage.html("https://youtu.be/M7lc1UVf-VE",5));
                        case "https://www.youtube.com/iframe_api" -> resource(YOUTUBE_API.getBytes(java.nio.charset.StandardCharsets.UTF_8),"application/javascript");
                        case VIDEO, VIDEO+"?next" -> resource(video,"video/webm");
                        default -> resource(new byte[0],"text/plain");
                    };
                }};
            }
        });
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter(){public void onAfterCreated(CefBrowser b){created.incrementAndGet();b.setWindowVisibility(true);b.setWindowlessFrameRate(20);b.loadURL(FIRST);}});
        client.addAudioHandler(new CefAudioHandlerAdapter(){public boolean getAudioParameters(CefBrowser b,CefAudioParameters p){p.sampleRate=24000;p.framesPerBuffer=2400;p.channelLayout=CefChannelLayout.CEF_CHANNEL_LAYOUT_MONO;return true;}public void onAudioStreamPacket(CefBrowser b,DataPointer p,int frames,long pts){if(frames>0){if(NEXT.equals(committed.get()))nextAudio.set(true);else audio.set(true);}}});
        client.addDisplayHandler(new CefDisplayHandlerAdapter(){
            @Override public void onAddressChange(CefBrowser b,CefFrame f,String url){if(f.isMain())committed.set(url);}
            public boolean onConsoleMessage(CefBrowser b,CefSettings.LogSeverity severity,String message,String source,int line){if(message.equals("MEDIA_SEEK_OK"))seek.set(true);if(message.equals("MEDIA_NEXT_OK"))nextFrame.set(true);if(message.equals("MEDIA_YOUTUBE_SWITCH_OK"))youtubeSwitch.set(true);if(message.startsWith("MEDIA_ERROR"))failure.set(message);return true;}
        });
        var browser=new CefBrowserOsr(client,"about:blank",false,context){public Rectangle getViewRect(CefBrowser b){return new Rectangle(0,0,640,360);}public void onPaint(CefBrowser b,boolean popup,Rectangle[] dirty,ByteBuffer bytes,int w,int h){if(w>0&&h>0)paint.set(true);}};browser.createImmediately();long last=0;
        while(System.nanoTime()<deadline&&!(audio.get()&&paint.get()&&seek.get())){app.N_DoMessageLoopWork();long now=System.nanoTime();if(FIRST.equals(committed.get())&&now-last>400_000_000L){browser.executeJavaScript("(()=>{let v=document.querySelector('video');if(v&&Number.isFinite(v.duration)&&v.readyState>=2){if(!window.__asked){window.netlabSeek(6);window.__asked=true;}else if(v.currentTime>=5)console.log('MEDIA_SEEK_OK');if(v.error)console.log('MEDIA_ERROR:'+v.error.code)}})()",FIRST,0);last=now;}Thread.sleep(10);}
        if(FIRST.equals(committed.get())&&audio.get()&&paint.get()&&seek.get()){
            browser.loadURL(NEXT);
            long nextDeadline=System.nanoTime()+10_000_000_000L;
            while(System.nanoTime()<nextDeadline&&!(nextFrame.get()&&nextAudio.get())){
                app.N_DoMessageLoopWork();
                long now=System.nanoTime();
                if(NEXT.equals(committed.get())&&now-last>200_000_000L){
                    browser.executeJavaScript("(()=>{const v=document.querySelector('video');if(v?.currentSrc.endsWith('?next') && v.readyState>=2 && v.currentTime>=1 && v.currentTime<5)console.log('MEDIA_NEXT_OK')})()",NEXT,0);
                    last=now;
                }
                Thread.sleep(10);
            }
        }
        if(nextFrame.get()&&nextAudio.get()){
            browser.loadURL(YOUTUBE);
            long youtubeDeadline=System.nanoTime()+10_000_000_000L;
            while(System.nanoTime()<youtubeDeadline&&!youtubeSwitch.get()&&failure.get()==null){
                app.N_DoMessageLoopWork();
                long now=System.nanoTime();
                if(YOUTUBE.equals(committed.get())&&now-last>200_000_000L){
                    browser.executeJavaScript("""
                            (()=>{
                              const player=window.fixturePlayer;
                              if(!player?.started||window.tested)return;
                              window.tested=true;
                              const next='dQw4w9WgXcQ';
                              window.netlabYouTube(next,2.5,'next');
                              window.netlabYouTube(next,2.6,'next');
                              if(player.loads.length!==1||player.loads[0].videoId!==next||player.loads[0].startSeconds!==2.5
                                  ||!document.title.startsWith('NETLAB_FRAME:next:')){
                                console.log('MEDIA_ERROR: second clip or schedule refresh');return;
                              }
                              window.netlabYouTube(next,0,'loop');
                              if(window.fixturePlayer!==player||player.loads.length!==2||player.loads[1].startSeconds!==0){
                                console.log('MEDIA_ERROR: restart same clip in playlist loop');return;
                              }
                              console.log('MEDIA_YOUTUBE_SWITCH_OK');
                            })()
                            """,YOUTUBE,0);
                    last=now;
                }
                Thread.sleep(10);
            }
        }
        boolean ok=created.get()==1&&audio.get()&&paint.get()&&seek.get()&&nextFrame.get()&&nextAudio.get()&&youtubeSwitch.get()&&failure.get()==null;
        browser.close(true);long until=System.nanoTime()+800_000_000L;while(System.nanoTime()<until){app.N_DoMessageLoopWork();Thread.sleep(10);}client.dispose();context.dispose();app.dispose();
        String result="Standalone Media Chromium: paint="+paint+", decoded PCM="+audio+", schedule seek="+seek+", browser instances="+created+", next video="+nextFrame+", next audio="+nextAudio+", YouTube API fixture switch="+youtubeSwitch+", error="+failure.get();
        System.out.println(result+". No Minecraft, server, microphone or world launched.");if(!ok)throw new AssertionError(result);
    }
}
