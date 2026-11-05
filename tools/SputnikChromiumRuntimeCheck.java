import org.cef.*;
import org.cef.browser.*;
import org.cef.handler.*;
import org.cef.network.*;
import org.cef.misc.*;
import dev.netlab.sputnik.core.SocksProxy;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Standalone off-screen Chromium only. No Minecraft classes, window, server, Level or GameTest. */
public class SputnikChromiumRuntimeCheck {
    public static void main(String[] args)throws Exception{
        System.setProperty("jcef.path",args[0]);System.setProperty("jcef.external_message_pump","true");
        String[] switches={"--disable-gpu","--disable-background-networking","--autoplay-policy=no-user-gesture-required"};
        System.loadLibrary("jawt");
        if(!CefApp.startup(switches))throw new AssertionError("CEF startup failed");var settings=new CefSettings();settings.windowless_rendering_enabled=true;settings.log_severity=CefSettings.LogSeverity.LOGSEVERITY_ERROR;settings.log_file="/tmp/netlab-sputnik-cef.log";
        var app=CefApp.getInstance(switches,settings);var client=app.createClient();long deadline=System.nanoTime()+40_000_000_000L;
        while(CefApp.getState()!=CefApp.CefAppState.INITIALIZED&&System.nanoTime()<deadline){app.N_DoMessageLoopWork();Thread.sleep(10);}
        if(CefApp.getState()!=CefApp.CefAppState.INITIALIZED)throw new AssertionError("CEF did not initialize");
        try(var broker=new SputnikWebRuntimeCheck.Broker();var proxy=new SocksProxy(broker)){broker.proxy=proxy;var context=CefRequestContext.createContext(new CefRequestContextSettings(),new CefRequestContextHandlerAdapter(){});AtomicBoolean secure=new AtomicBoolean(),audio=new AtomicBoolean(),paint=new AtomicBoolean();var failure=new AtomicReference<String>();
            client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter(){@Override public void onAfterCreated(CefBrowser b){String error=context.setPreference("proxy",Map.of("mode","fixed_servers","server","socks5://127.0.0.1:"+proxy.port(),"bypass_list","<-loopback>"));if(error!=null&&!error.isEmpty()){failure.set("proxy: "+error);return;}error=context.setPreference("webrtc.ip_handling_policy","disable_non_proxied_udp");if(error!=null&&!error.isEmpty()){failure.set("WebRTC: "+error);return;}b.setWindowVisibility(true);b.setFocus(true);b.setWindowlessFrameRate(20);b.invalidate(CefPaintElementType.PET_VIEW);b.loadURL("https://example.com");}});
            client.addLoadHandler(new CefLoadHandlerAdapter(){@Override public void onLoadEnd(CefBrowser b,CefFrame f,int code){if(!f.isMain())return;if(code==200&&f.getURL().startsWith("https://example.com")){b.getText(text->{if(!text.toLowerCase(java.util.Locale.ROOT).contains("documentation")){if(!text.isBlank())failure.set("Unexpected page: "+text.substring(0,Math.min(180,text.length())));return;}secure.set(true);String html="<html><body>Audio probe<script>const c=new AudioContext();const o=c.createOscillator();const g=c.createGain();g.gain.value=.05;o.connect(g);g.connect(c.destination);o.start();c.resume();</script></body></html>";b.loadURL("data:text/html;base64,"+Base64.getEncoder().encodeToString(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));});}}@Override public void onLoadError(CefBrowser b,CefFrame f,ErrorCode code,String text,String url){if(f.isMain()&&!text.contains("ABORTED"))failure.set("Load: "+text);}});
            client.addAudioHandler(new CefAudioHandlerAdapter(){@Override public boolean getAudioParameters(CefBrowser b,CefAudioParameters p){p.sampleRate=24000;p.framesPerBuffer=2400;p.channelLayout=CefChannelLayout.CEF_CHANNEL_LAYOUT_MONO;return true;}@Override public void onAudioStreamPacket(CefBrowser b,DataPointer pointer,int frames,long pts){try{var channel=pointer.getData(0).forCapacity(frames*4);for(int i=0;i<frames;i++)if(Math.abs(channel.getFloat(i))>.001){audio.set(true);break;}}catch(Exception e){failure.set("PCM capture: "+e);}}});
            var browser=new CefBrowserOsr(client,"about:blank",false,context){{browser_rect_=new Rectangle(0,0,800,450);}@Override public Rectangle getViewRect(CefBrowser b){return new Rectangle(0,0,800,450);}@Override public void onPaint(CefBrowser b,boolean pop,Rectangle[] dirty,ByteBuffer image,int w,int h){if(w>0&&h>0&&image.remaining()>0)paint.set(true);}};browser.setCloseAllowed();browser.createImmediately();
            while(System.nanoTime()<deadline&&failure.get()==null&&!(secure.get()&&audio.get()&&paint.get())){app.N_DoMessageLoopWork();Thread.sleep(10);}
            boolean pass=secure.get()&&audio.get()&&paint.get()&&failure.get()==null;String result="Standalone Chromium: HTTPS="+secure+", paint="+paint+", captured PCM="+audio+", satellite crossings="+broker.crossings+", error="+failure.get();browser.close(true);long until=System.nanoTime()+1_000_000_000L;while(System.nanoTime()<until){app.N_DoMessageLoopWork();Thread.sleep(10);}client.dispose();context.dispose();app.dispose();System.out.println(result+". Minecraft was not launched.");if(!pass)throw new AssertionError(result);
        }
    }
}
