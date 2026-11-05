package dev.netlab.automation.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Screen-owned dropdown: captures input before the controls underneath it. */
final class ManagementDropdown {
    record Entry(String title,String detail,ItemStack icon,Runnable select) {}
    private final Font font;
    private final int x,y,w,rows,bottom;
    private final String title,page;
    private final List<Entry> entries;
    private final Runnable close,previous,next;
    private final EditBox search;
    private List<Entry> filtered;
    private int scroll,selected;
    private static final int ROW=27;

    ManagementDropdown(Font font,int x,int anchor,int w,int screenHeight,String title,List<Entry> entries,String page,Runnable previous,Runnable next,Runnable close){
        this.font=font;this.x=x;this.w=w;this.title=title;this.entries=List.copyOf(entries);this.page=page;
        this.previous=previous;this.next=next;this.close=close;
        rows=Math.max(1,Math.min(6,(screenHeight-66)/ROW));
        int h=58+rows*ROW;
        y=Math.max(4,Math.min(anchor,screenHeight-h-4));bottom=y+h;
        search=new EditBox(font,x+6,y+19,w-12,18,Component.literal("Поиск в списке"));search.setMaxLength(64);
        search.setHint(Component.literal("Поиск по имени или ID…"));search.setFocused(true);
        filtered=this.entries;
        search.setResponder(text->{String query=text.strip().toLowerCase(Locale.ROOT);filtered=this.entries.stream().filter(e->(e.title()+" "+e.detail()).toLowerCase(Locale.ROOT).contains(query)).toList();scroll=0;selected=0;});
    }
    private int start(){return y+41;}
    private boolean inside(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<bottom;}
    void render(GuiGraphics g,int mx,int my,float delta){
        g.pose().pushPose();g.pose().translate(0,0,300);
        g.fill(x-1,y-1,x+w+1,bottom+1,0xff8cbdbb);g.fill(x,y,x+w,bottom,0xff142d39);
        g.drawString(font,font.plainSubstrByWidth(title,w-25),x+6,y+5,0xffdcf2ed,false);g.drawString(font,"×",x+w-13,y+5,0xffdcf2ed,false);
        search.render(g,mx,my,delta);
        Entry hovered=null;
        for(int row=0;row<rows;row++){
            int at=scroll+row,ry=start()+row*ROW;if(at>=filtered.size())break;
            var e=filtered.get(at);boolean hover=mx>=x+3&&mx<x+w-3&&my>=ry&&my<ry+ROW;
            if(at==selected||hover)g.fill(x+3,ry,x+w-3,ry+ROW-1,hover?0xff355b69:0xff244653);
            int tx=x+7;if(!e.icon().isEmpty()){g.renderItem(e.icon(),tx,ry+5);tx+=23;}
            g.drawString(font,font.plainSubstrByWidth(e.title(),w-(tx-x)-12),tx,ry+4,0xffedf8f5,false);
            g.drawString(font,font.plainSubstrByWidth(e.detail(),w-(tx-x)-12),tx,ry+15,0xff9ec9c9,false);
            if(hover)hovered=e;
        }
        if(filtered.isEmpty())g.drawString(font,"Ничего не найдено",x+7,start()+7,0xffa2cfce,false);
        if(filtered.size()>rows){int track=rows*ROW,thumb=Math.max(10,track*rows/filtered.size());int offset=(track-thumb)*scroll/Math.max(1,filtered.size()-rows);g.fill(x+w-3,start()+offset,x+w-1,start()+offset+thumb,0xff9acccb);}
        int footer=bottom-9;
        if(previous!=null)g.drawString(font,"<",x+6,footer,0xffbce5e0,false);
        if(next!=null)g.drawString(font,">",x+w-12,footer,0xffbce5e0,false);
        String range=filtered.isEmpty()?"0":(scroll+1)+"–"+Math.min(scroll+rows,filtered.size())+" / "+filtered.size();
        String footerText=page.isEmpty()?range:(w<230?page:range+"  •  "+page);
        g.drawCenteredString(font,footerText,x+w/2,footer,0xff9ec9c9);
        if(hovered!=null)g.renderTooltip(font,Component.literal(hovered.title()+" / "+hovered.detail()),mx,my);
        g.pose().popPose();
    }
    boolean mouseClicked(double mx,double my,int button){
        if(!inside(mx,my)){close.run();return true;}
        if(button!=0)return true;
        if(my<y+17&&mx>=x+w-20){close.run();return true;}
        if(search.mouseClicked(mx,my,button))return true;
        if(my>=bottom-13){if(mx<x+25&&previous!=null)previous.run();else if(mx>=x+w-25&&next!=null)next.run();return true;}
        int row=(int)(my-start())/ROW,at=scroll+row;
        if(my>=start()&&row<rows&&at<filtered.size()){var entry=filtered.get(at);close.run();entry.select().run();}
        return true;
    }
    boolean mouseScrolled(double amount){scroll=Math.clamp(scroll+(amount<0?3:amount>0?-3:0),0,Math.max(0,filtered.size()-rows));selected=filtered.isEmpty()?0:Math.clamp(selected,scroll,Math.min(filtered.size()-1,scroll+rows-1));return true;}
    boolean keyPressed(int key,int scan,int mods){
        if(key==256){close.run();return true;}
        if(key==264||key==265||key==266||key==267){int step=key==264?1:key==265?-1:key==266?-rows:rows;selected=Math.clamp(selected+step,0,Math.max(0,filtered.size()-1));scroll=Math.clamp(scroll,Math.max(0,selected-rows+1),selected);return true;}
        if((key==257||key==335)&&!filtered.isEmpty()){var entry=filtered.get(selected);close.run();entry.select().run();return true;}
        search.keyPressed(key,scan,mods);return true;
    }
    boolean charTyped(char ch,int mods){search.charTyped(ch,mods);return true;}
    boolean mouseDragged(double mx,double my,int button,double dx,double dy){search.mouseDragged(mx,my,button,dx,dy);return true;}
    boolean mouseReleased(double mx,double my,int button){search.mouseReleased(mx,my,button);return true;}
}
