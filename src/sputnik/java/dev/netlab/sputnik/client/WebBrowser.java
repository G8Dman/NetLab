package dev.netlab.sputnik.client;

import de.keksuccino.rinku.RinkuRenderer;
import org.cef.*;
import org.cef.browser.*;
import org.cef.event.*;
import org.cef.handler.*;
import org.cef.network.*;
import org.cef.misc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.*;
import java.nio.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Isolated request context; CEF sends encrypted HTTPS via a loopback SOCKS tunnel into NetLab. */
public final class WebBrowser extends CefBrowserOsr {
    private record Frame(byte[] bgra,int w,int h){}
    private static final class Texture extends RinkuRenderer {Texture(){super(false);}void paint(Frame f){var data=org.lwjgl.system.MemoryUtil.memAlloc(f.bgra.length);try{data.put(f.bgra).flip();super.onPaint(data,f.w,f.h);}finally{org.lwjgl.system.MemoryUtil.memFree(data);}}void destroy(){cleanup();}}
    private final AtomicReference<Frame> image=new AtomicReference<>(),popup=new AtomicReference<>();private final Texture texture=new Texture(),popupTexture=new Texture();private volatile Rectangle popupRect=new Rectangle();private volatile boolean popupShown;private int buttons;private volatile boolean closed;
    public volatile String error="",title="",address="about:blank";
    public WebBrowser(CefClient client,CefRequestContext context){super(client,"about:blank",false,context);texture.initialize();popupTexture.initialize();setCloseAllowed();}
    public void readyView(){wasResized(browser_rect_.width,browser_rect_.height);notifyScreenInfoChanged();}
    /** Upload the latest frame once and reuse its texture for in-world media displays. */
    public ResourceLocationHandle frameHandle(){Frame f=image.getAndSet(null);if(f!=null)texture.paint(f);return texture.isTextureReady()?new ResourceLocationHandle(texture.getTextureIdentifier()):null;}
    public record ResourceLocationHandle(net.minecraft.resources.ResourceLocation texture){}
    public void resize(int w,int h){w=Math.clamp(w,64,1920);h=Math.clamp(h,64,1080);if(browser_rect_.width!=w||browser_rect_.height!=h){browser_rect_=new Rectangle(0,0,w,h);wasResized(w,h);}}
    @Override public void onPaint(CefBrowser browser,boolean pop,Rectangle[] dirty,ByteBuffer bytes,int w,int h){if(closed||w<=0||h<=0||w>4096||h>4096)return;byte[] copy=new byte[Math.multiplyExact(Math.multiplyExact(w,h),4)];bytes.duplicate().get(copy);(pop?popup:image).set(new Frame(copy,w,h));}
    @Override public void onPopupShow(CefBrowser b,boolean shown){popupShown=shown;}
    @Override public void onPopupSize(CefBrowser b,Rectangle rect){popupRect=rect;}
    public void draw(GuiGraphics g,int x,int y,int w,int h){Frame f=image.getAndSet(null);if(f!=null)texture.paint(f);if(texture.isTextureReady())g.blit(texture.getTextureIdentifier(),x,y,w,h,0,0,texture.getTextureWidth(),texture.getTextureHeight(),texture.getTextureWidth(),texture.getTextureHeight());if(popupShown){Frame p=popup.getAndSet(null);if(p!=null)popupTexture.paint(p);var r=popupRect;if(popupTexture.isTextureReady())g.blit(popupTexture.getTextureIdentifier(),x+r.x*w/browser_rect_.width,y+r.y*h/browser_rect_.height,r.width*w/browser_rect_.width,r.height*h/browser_rect_.height,0,0,r.width,r.height,r.width,r.height);}}
    public int px(double x,int w){return (int)(x*browser_rect_.width/Math.max(1,w));}public int py(double y,int h){return (int)(y*browser_rect_.height/Math.max(1,h));}
    public void move(int x,int y){sendMouseEvent(new CefMouseEvent(CefMouseEvent.MOUSE_MOVED,x,y,0,0,buttons));}
    public void mouse(int x,int y,int button,boolean down){int b=button==1?2:button==2?1:0;int mask=16<<b;if(down)buttons|=mask;else buttons&=~mask;sendMouseEvent(new CefMouseEvent(down?1:0,x,y,1,b,buttons));}
    public void wheel(int x,int y,double delta){sendMouseWheelEvent(new CefMouseWheelEvent(0,x,y,delta*3,buttons));}
    public void key(int key,int scan,int mods,boolean down){var e=new CefKeyEvent(down?CefKeyEvent.KEY_PRESS:CefKeyEvent.KEY_RELEASE,key,(char)key,mods);e.scancode=scan;sendKeyEvent(e);}
    public void typed(char ch,int mods){sendKeyEvent(new CefKeyEvent(CefKeyEvent.KEY_TYPE,ch,ch,mods));}
    public void disposeBrowser(){closed=true;close(true);texture.destroy();popupTexture.destroy();}
    public static boolean allowed(String url){try{String scheme=URI.create(url).getScheme();return Set.of("http","https","ws","wss","about","data","blob").contains(scheme==null?"":scheme.toLowerCase(Locale.ROOT));}catch(RuntimeException e){return false;}}
}
