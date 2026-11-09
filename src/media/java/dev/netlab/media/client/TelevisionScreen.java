package dev.netlab.media.client;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
/** Clicking the embedded player permits playback when a provider blocks autoplay. */
public final class TelevisionScreen extends Screen {
    private final BlockPos pos;private final Screen parent;private int x,y,w,h;
    public TelevisionScreen(BlockPos pos,Screen parent){super(Component.literal("IPTV"));this.pos=pos;this.parent=parent;}
    private MediaVideoSession session(){return MediaVideoSession.SESSIONS.get(pos.asLong());}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){g.fill(0,0,width,height,0xff07151d);w=Math.min(width-16,(height-44)*16/9);h=w*9/16;x=(width-w)/2;y=(height-h)/2;var s=session();if(s!=null&&s.browser!=null)s.browser.draw(g,x,y,w,h);else g.drawCenteredString(font,s==null?"Нет сигнала: проверь приставку, канал и питание":s.status,width/2,height/2,0xffcc99);g.drawCenteredString(font,"Нажми на плеер для запуска · Esc — назад",width/2,height-14,0x93b8c3);super.render(g,mx,my,delta);}
    @Override public boolean mouseClicked(double mx,double my,int button){var s=session();if(s!=null&&s.browser!=null){s.browser.mouse(s.browser.px(mx-x,w),s.browser.py(my-y,h),button,true);return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseReleased(double mx,double my,int button){var s=session();if(s!=null&&s.browser!=null){s.browser.mouse(s.browser.px(mx-x,w),s.browser.py(my-y,h),button,false);return true;}return super.mouseReleased(mx,my,button);}
    @Override public void mouseMoved(double mx,double my){var s=session();if(s!=null&&s.browser!=null)s.browser.move(s.browser.px(mx-x,w),s.browser.py(my-y,h));super.mouseMoved(mx,my);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
