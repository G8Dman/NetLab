package dev.netlab.sputnik.client;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.netlab.sputnik.SputnikPackets;
import org.lwjgl.glfw.GLFW;

/** Closing this view leaves the browser and its audio alive. Stop explicitly disposes the session. */
public final class BrowserScreen extends Screen {
    public final BlockPos pc;private final Screen parent;private EditBox address,gateway;private Button sound;private boolean pageFocused;
    private BrowserSession session(){return BrowserSession.current;}
    public BrowserScreen(BlockPos pc,Screen parent){super(Component.literal("NetLab Sputnik / Browser"));this.pc=pc;this.parent=parent;}
    private Button button(String name,int x,int y,int w,Runnable f){return addRenderableWidget(Button.builder(Component.literal(name),b->f.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){if(session()==null||!session().pc.equals(pc)){if(session()!=null)session().close();BrowserSession.current=new BrowserSession(pc);}var s=session();if(s.browser!=null)s.browser.setWindowlessFrameRate(20);
        button("←",8,7,24,()->{if(s.browser!=null)s.browser.goBack();});button("→",36,7,24,()->{if(s.browser!=null)s.browser.goForward();});button("↻",64,7,24,()->{if(s.browser!=null)s.browser.reload();});
        address=addRenderableWidget(new EditBox(font,94,8,Math.max(60,width-218),18,Component.literal("Адрес сайта")));address.setMaxLength(2048);address.setValue(s.browser==null?"https://example.com":s.browser.address);
        button("Перейти",width-118,7,110,this::navigate);
        gateway=addRenderableWidget(new EditBox(font,140,34,Math.max(80,width-405),18,Component.literal("IP шлюза (пусто = автоматически)")));gateway.setMaxLength(15);gateway.setValue(s.gateway);gateway.setResponder(v->s.gateway=v.strip());
        sound=button("Звук: вкл/выкл",width-250,33,120,()->s.mute=!s.mute);button("Остановить",width-124,33,116,()->{s.close();BrowserSession.current=null;minecraft.setScreen(parent);});
        button("Свернуть / играть",width-154,height-26,146,this::onClose);
    }
    @Override public void tick(){var s=session();if(s!=null&&sound!=null)sound.setMessage(Component.literal(s.mute?"Звук: ВЫКЛ":"Звук: ВКЛ"));if(s!=null&&s.browser!=null&&!address.isFocused()&&!s.browser.address.equals("about:blank"))address.setValue(s.browser.address);}
    private void navigate(){try{var s=session();s.gateway=gateway.getValue().strip();s.navigate(address.getValue());if(s.browser!=null)s.browser.setFocus(true);pageFocused=true;address.setFocused(false);gateway.setFocused(false);}catch(RuntimeException e){session().status=e.getMessage();}}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){}
    @Override public void render(GuiGraphics g,int x,int y,float dt){g.fill(0,0,width,height,0xff10232e);g.drawString(font,"Шлюз провайдера:",8,39,0xb3dce1,false);g.fill(8,60,width-8,height-34,0xffeef1f3);var s=session();if(s!=null&&s.browser!=null){int w=width-16,h=height-94;double scale=minecraft.getWindow().getGuiScale();s.browser.resize((int)(w*scale),(int)(h*scale));s.browser.draw(g,8,60,w,h);}else g.drawString(font,"Введи адрес настоящего сайта и нажми «Перейти»",16,74,0x303c49,false);super.render(g,x,y,dt);if(s!=null){String info=s.browser!=null&&!s.browser.error.isEmpty()?s.browser.error:s.status;g.drawString(font,font.plainSubstrByWidth(info,width-175),8,height-20,0xffcc99,false);}}
    private boolean page(double x,double y){return x>=8&&x<width-8&&y>=60&&y<height-34&&session()!=null&&session().browser!=null;}
    private int px(double x){return session().browser.px(x-8,width-16);}private int py(double y){return session().browser.py(y-60,height-94);}
    @Override public boolean mouseClicked(double x,double y,int b){pageFocused=page(x,y);if(pageFocused){address.setFocused(false);gateway.setFocused(false);session().browser.setFocus(true);session().browser.mouse(px(x),py(y),b,true);return true;}return super.mouseClicked(x,y,b);}
    @Override public boolean mouseReleased(double x,double y,int b){if(pageFocused&&session().browser!=null){session().browser.mouse(px(x),py(y),b,false);return true;}return super.mouseReleased(x,y,b);}
    @Override public void mouseMoved(double x,double y){if(page(x,y))session().browser.move(px(x),py(y));super.mouseMoved(x,y);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){if(pageFocused&&session().browser!=null){session().browser.move(px(x),py(y));return true;}return super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(page(x,y)){session().browser.wheel(px(x),py(y),dy);return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}if((address.isFocused()||gateway.isFocused())&&key==GLFW.GLFW_KEY_ENTER){navigate();return true;}if(pageFocused&&session().browser!=null){session().browser.key(key,scan,mods,true);return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean keyReleased(int key,int scan,int mods){if(pageFocused&&session()!=null&&session().browser!=null){session().browser.key(key,scan,mods,false);return true;}return super.keyReleased(key,scan,mods);}
    @Override public boolean charTyped(char ch,int mods){if(pageFocused&&session().browser!=null){session().browser.typed(ch,mods);return true;}return super.charTyped(ch,mods);}
    @Override public void onClose(){if(session()!=null&&session().browser!=null){session().browser.setFocus(false);session().browser.setWindowlessFrameRate(1);}minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
