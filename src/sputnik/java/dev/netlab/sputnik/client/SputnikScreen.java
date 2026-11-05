package dev.netlab.sputnik.client;
import com.google.gson.*;
import dev.netlab.sputnik.*;
import dev.netlab.sputnik.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
public final class SputnikScreen extends Screen {
    public final BlockPos pos;public final int bay;private JsonObject state;private final Screen parent;private EditBox satellite,azimuth,elevation,volume;private int ticks,left,w;
    public SputnikScreen(BlockPos pos,int bay,String json,Screen parent){super(Component.literal("NetLab Sputnik"));this.pos=pos;this.bay=bay;state=JsonParser.parseString(json).getAsJsonObject();this.parent=parent;}
    public void update(String json){state=JsonParser.parseString(json).getAsJsonObject();}
    private String text(String k){return state.has(k)?state.get(k).getAsString():"";}private void send(String a,String v){PacketDistributor.sendToServer(new SputnikPackets.Action(pos,bay,a,v));}
    private void button(String t,int x,int y,int ww,Runnable f){addRenderableWidget(Button.builder(Component.literal(t),b->f.run()).bounds(x,y,ww,20).build());}
    private EditBox field(String label,String value,int x,int y,int width){var b=addRenderableWidget(new EditBox(font,x,y,width,20,Component.literal(label)));b.setValue(value);return b;}
    @Override protected void init(){w=Math.min(650,width-24);left=(width-w)/2;String k=text("kind");if(k.equals(SputnikHardware.DISH)||k.equals(SputnikHardware.VSAT)){satellite=field("GEO-1",text("satellite"),left,120,100);azimuth=field("Азимут",Integer.toString((int)state.get("azimuth").getAsDouble()),left+112,120,100);elevation=field("Угол",Integer.toString((int)state.get("elevation").getAsDouble()),left+224,120,100);button("Навести",left+336,120,w-336,()->send("point",satellite.getValue()+" "+azimuth.getValue()+" "+elevation.getValue()));}else if(k.equals(SputnikHardware.SPEAKER)){volume=field("Громкость 0..100",Integer.toString((int)(state.get("volume").getAsDouble()*100)),left,120,120);button("Громкость",left+130,120,120,()->send("volume",volume.getValue()));button("Вкл / выкл",left+260,120,120,()->send("toggle",""));}
        button("Скопировать ID",left,153,160,()->minecraft.keyboardHandler.setClipboard(text("id")));
        button("Оборудование / питание / модули / память",left,height-54,w,()->send("hardware",""));button("Закрыть",left,height-28,w,this::onClose);}
    @Override public void tick(){if(++ticks%20==0)send("refresh","");}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){}
    @Override public void render(GuiGraphics g,int x,int y,float dt){g.fill(0,0,width,height,0xff102530);g.drawCenteredString(font,"NetLab Sputnik / "+text("name"),width/2,16,0x9ce3e4);g.drawString(font,"IP "+text("ip")+" | "+text("memory"),left,40,0xffffff,false);g.drawString(font,"ID "+text("id"),left,58,0xb8cbd4,false);g.drawString(font,"Сеть "+text("network")+" | "+text("satellite"),left,78,0xd5e4e6,false);String kind=text("kind");if(kind.equals(SputnikHardware.DISH)||kind.equals(SputnikHardware.VSAT))g.drawString(font,"Спутник / азимут / угол. GEO-1: 180° / 45°",left,103,0xc3d8df,false);else if(kind.equals(SputnikHardware.SPEAKER))g.drawString(font,"Подключи AudioOut компьютера к AudioIn. Колонке нужно FE.",left,103,0xc3d8df,false);else g.drawWordWrap(font,Component.literal("Настройка через Console. help; show sputnik; sat panel. HUB: CONTROL + TX + RX."),left,103,w,0xc3d8df);g.drawWordWrap(font,Component.literal(text("status")),left,height-90,w,0xffcb92);super.render(g,x,y,dt);}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
