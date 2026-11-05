package dev.netlab.client;

import dev.netlab.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WirelessScreen extends Screen {
    public final BlockPos pos;public final int bay;
    private final Screen parent;
    private WirelessMenu.State state;
    private EditBox ssid,password,channel,range;
    private Button securityButton;
    private String security,status="";private boolean isolated;private int left,top,w,ticks,page;
    public WirelessScreen(BlockPos pos,int bay,String json,Screen parent){super(Component.literal("NetLab / Wi-Fi"));this.pos=pos;this.bay=bay;this.parent=parent;update(json);security=state.security();isolated=state.isolated();}
    public void update(String json){state=FrontPanel.GSON.fromJson(json,WirelessMenu.State.class);if(!state.status().isBlank())status=state.status();}
    private EditBox field(int y,String value,int max,boolean secret){var e=new EditBox(font,left+105,y,w-115,20,Component.literal("Wi-Fi"));e.setMaxLength(max);e.setValue(value);if(secret)e.setFormatter((text,cursor)->FormattedCharSequence.forward("*".repeat(text.length()),net.minecraft.network.chat.Style.EMPTY));addRenderableWidget(e);return e;}
    private void button(String label,int x,int y,int width,Runnable click){addRenderableWidget(Button.builder(Component.literal(label),b->click.run()).bounds(x,y,width,20).build());}
    private void send(String action,String json){PacketDistributor.sendToServer(new Packets.WifiRequest(pos,bay,action,json));}
    private void settings(String action){try{int c=state.ap()?Integer.parseInt(channel.getValue()):6;int r=state.ap()?Integer.parseInt(range.getValue()):32;
        send(action,FrontPanel.GSON.toJson(new WirelessMenu.Settings(ssid.getValue(),security,password.getValue(),c,r,isolated)));password.setValue("");
    }catch(NumberFormatException e){status="Канал и дальность должны быть числами";}}
    @Override protected void init(){
        w=Math.min(520,width-20);left=(width-w)/2;top=Math.max(6,(height-230)/2);
        ssid=field(top+30,state.ssid(),32,false);password=field(top+56,"",63,true);
        if(state.ap()){
            channel=field(top+82,""+state.channel(),2,false);range=field(top+108,""+state.range(),2,false);
            addRenderableWidget(Button.builder(Component.literal("Защита: "+security),b->{security=security.equals("open")?"wpa2":"open";b.setMessage(Component.literal("Защита: "+security));}).bounds(left+5,top+136,w/2-10,20).build());
            addRenderableWidget(Button.builder(Component.literal("Изоляция: "+isolated),b->{isolated=!isolated;b.setMessage(Component.literal("Изоляция: "+isolated));}).bounds(left+w/2+5,top+136,w/2-10,20).build());
            button("Применить",left+5,top+162,w/3-10,()->settings("configure"));
            button("Радио ON/OFF",left+w/3+5,top+162,w/3-10,()->send("radio",""));
            button("Питание ON/OFF",left+2*w/3+5,top+162,w/3-10,()->send("power",""));
        }else{
            securityButton=addRenderableWidget(Button.builder(Component.literal("Защита: "+security),b->{security=security.equals("open")?"wpa2":"open";b.setMessage(Component.literal("Защита: "+security));}).bounds(left+5,top+82,w/2-10,20).build());
            button("Подключить / DHCP",left+w/2+5,top+82,w/2-10,()->settings("join"));
            button("Поиск",left+5,top+108,w/4-10,()->send("refresh",""));
            button("Отключить",left+w/4+5,top+108,w/4-10,()->send("disconnect",""));
            button("Адаптер / питание",left+w/2+5,top+108,w/2-10,()->PacketDistributor.sendToServer(new Packets.PanelRequest(pos,bay,"open","","")));
        }
        if(state.ap())button("Панель / карта памяти",left+5,height-35,w-100,()->PacketDistributor.sendToServer(new Packets.PanelRequest(pos,bay,"open","","")));
        button("Назад",left+w-85,height-35,80,this::onClose);
    }
    @Override public void tick(){super.tick();if(++ticks%20==0)send("refresh","");}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float dt){
        g.fill(0,0,width,height,0xff102332);
        g.drawString(font,state.ap()?"Wi-Fi / точка доступа":"Wi-Fi / "+(state.adapter()?state.connection():"нет адаптера"),left+5,top,0xffffff);
        g.drawString(font,"SSID",left+5,top+36,0xffffff);g.drawString(font,"Пароль",left+5,top+62,0xffffff);
        if(state.ap()){
            g.drawString(font,"Канал 1–11",left+5,top+88,0xffffff);g.drawString(font,"Радиус 4–64",left+5,top+114,0xffffff);
            g.drawString(font,"Питание: "+state.powered()+"  Радио: "+state.enabled(),left+5,top+190,0x9dddee);
        }else{
            int rows=Math.max(1,Math.min(5,(height-45-(top+143))/18));int start=page*rows;
            for(int i=0;i<rows&&start+i<state.networks().size();i++){var ap=state.networks().get(start+i);int y=top+140+i*18;
                g.fill(left+5,y,left+w-5,y+16,0xff243e50);g.drawString(font,font.plainSubstrByWidth(ap.ssid()+" / "+ap.security()+" / "+ap.signal()+"% / ch "+ap.channel(),w-20),left+10,y+4,0x99eeff);
            }
            if(state.networks().isEmpty())g.drawString(font,"Сетей нет. Проверь адаптер, питание и радиус.",left+5,top+145,0xaabbcc);
            g.drawString(font,"IP: "+(state.address().isEmpty()?"не получен":state.address()),left+5,height-31,0xaabbcc);
        }
        super.render(g,mouseX,mouseY,dt);g.drawString(font,font.plainSubstrByWidth(status,w),left+5,height-12,0xffcc99);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(!state.ap()&&button==0&&x>=left+5&&x<left+w-5&&y>=top+140){int rows=Math.max(1,Math.min(5,(height-45-(top+143))/18));int row=(int)(y-top-140)/18,index=page*rows+row;
            if(row<rows&&index<state.networks().size()){var candidate=state.networks().get(index);ssid.setValue(candidate.ssid());security=candidate.security();securityButton.setMessage(Component.literal("Защита: "+security));status="Выбрана "+candidate.ssid()+" ("+candidate.security()+")";return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!state.ap()){int rows=Math.max(1,Math.min(5,(height-45-(top+143))/18));page=Math.clamp(page+(dy<0?1:-1),0,Math.max(0,(state.networks().size()-1)/rows));return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
