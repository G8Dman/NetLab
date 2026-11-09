package dev.netlab.media.client;
import com.google.gson.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
public final class PlaylistScreen extends Screen {
    private final JsonArray rows;private final Consumer<String> select;private final Screen parent;private int start;
    public PlaylistScreen(JsonArray rows,Consumer<String> select,Screen parent){super(Component.literal("Плейлист канала"));this.rows=rows==null?new JsonArray():rows;this.select=select;this.parent=parent;}
    @Override protected void init(){int w=Math.min(700,width-24),left=(width-w)/2,count=Math.max(1,(height-90)/32);start=Math.clamp(start,0,Math.max(0,rows.size()-count));for(int i=start;i<rows.size()&&i<start+count;i++){var row=rows.get(i).getAsJsonObject();String index=row.get("index").getAsString();var button=Button.builder(Component.literal(font.plainSubstrByWidth(index+" · "+row.get("seconds").getAsString()+"с · "+row.get("url").getAsString(),w-12)),b->{select.accept(index);onClose();}).bounds(left,42+(i-start)*32,w,27).build();button.setTooltip(Tooltip.create(Component.literal(row.get("url").getAsString())));addRenderableWidget(button);}addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(left,height-28,w,20).build());}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){start-=Integer.signum((int)dy);clearWidgets();init();return true;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){}
    @Override public void render(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,0xff102630);g.drawCenteredString(font,title,width/2,16,0x93e9e4);if(rows.isEmpty())g.drawCenteredString(font,"Выбери канал в списке «Каналы» или добавь первое видео.",width/2,height/2,0xb9d4d8);super.render(g,x,y,d);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
