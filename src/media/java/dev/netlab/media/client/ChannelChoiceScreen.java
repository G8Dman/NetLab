package dev.netlab.media.client;
import com.google.gson.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BiConsumer;
public final class ChannelChoiceScreen extends Screen {
    private final JsonArray rows;private final BiConsumer<String,String> select;private final Screen parent;private int start;
    public ChannelChoiceScreen(JsonArray rows,BiConsumer<String,String> select,Screen parent){super(Component.literal("Телеканалы"));this.rows=rows==null?new JsonArray():rows;this.select=select;this.parent=parent;}
    @Override protected void init(){int w=Math.min(600,width-30),left=(width-w)/2,count=Math.max(1,(height-90)/27);start=Math.clamp(start,0,Math.max(0,rows.size()-count));for(int i=start;i<rows.size()&&i<start+count;i++){var row=rows.get(i).getAsJsonObject();String number=row.get("number").getAsString(),name=row.get("name").getAsString();addRenderableWidget(Button.builder(Component.literal(number+" | "+name+" · "+row.get("videos").getAsString()+" видео"),b->{select.accept(number,name);onClose();}).bounds(left,44+(i-start)*27,w,23).build());}addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(left,height-28,w,20).build());}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){start-=Integer.signum((int)dy);clearWidgets();init();return true;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){}
    @Override public void render(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,0xff102630);g.drawCenteredString(font,title,width/2,16,0x93e9e4);if(rows.isEmpty())g.drawCenteredString(font,"Каналов пока нет. Создай первый в форме управления.",width/2,height/2,0xb9d4d8);super.render(g,x,y,d);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
