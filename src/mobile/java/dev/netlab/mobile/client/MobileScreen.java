package dev.netlab.mobile.client;

import com.google.gson.*;
import dev.netlab.mobile.*;
import dev.netlab.mobile.core.MobileHardware;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Small physical panel, SIM provisioner and live receiver dashboard. */
public final class MobileScreen extends Screen {
    public final BlockPos pos;public final int bay;public final String phone;private final Screen parent;
    private JsonObject state;private EditBox input;private String savedInput="";private int left,w,ticks;private String currentKind="";
    public MobileScreen(BlockPos pos,int bay,String phone,String json,Screen parent){super(Component.literal("NetLab Mobile"));this.pos=pos;this.bay=bay;this.phone=phone;this.parent=parent;state=JsonParser.parseString(json).getAsJsonObject();}
    private String text(String key){return state.has(key)?state.get(key).getAsString():"";}
    private boolean bool(String key){return state.has(key)&&state.get(key).getAsBoolean();}
    private double number(String key){return state.has(key)?state.get(key).getAsDouble():0;}
    public void update(String json){state=JsonParser.parseString(json).getAsJsonObject();if(!currentKind.equals(text("kind"))&&minecraft!=null){clearWidgets();init();}}
    private void send(String action,String value){PacketDistributor.sendToServer(new MobilePackets.Action(pos,bay,phone,action,value));}
    private void button(String name,int x,int y,int width,Runnable f){addRenderableWidget(Button.builder(Component.literal(name),b->f.run()).bounds(x,y,width,20).build());}
    @Override protected void init(){w=Math.min(660,width-20);left=(width-w)/2;currentKind=text("kind");int y=130,half=(w-12)/2;
        if(currentKind.equals("phone")||currentKind.equals("pc")){
            button("Вставить SIM из инвентаря",left,y,half,()->send("insert",""));button("Извлечь SIM",left+half+12,y,half,()->send("eject",""));
            if(currentKind.equals("pc")){button("Подключиться",left,y+26,half,()->send("connect",""));button("Отключиться",left+half+12,y+26,half,()->send("disconnect",""));}
        }else if(currentKind.equals(MobileHardware.STATION)){
            input=addRenderableWidget(new EditBox(font,left,94,half,20,Component.literal("Пароль EPC")));input.setMaxLength(64);input.setValue(savedInput);input.setResponder(v->savedInput=v);input.setFormatter((value,index)->net.minecraft.util.FormattedCharSequence.forward("*".repeat(value.length()),net.minecraft.network.chat.Style.EMPTY));
            button("Авторизовать станок",left+half+12,94,half,()->{send("pair",input.getValue());input.setValue("");});
            button("Вставить пустую SIM",left,120,half,()->send("blank",""));button("Вернуть пустую SIM",left+half+12,120,half,()->send("returnBlank",""));
            button("Выпустить / повторить запрос",left,146,half,()->send("issue",""));button("Забрать готовую SIM",left+half+12,146,half,()->send("take",""));
        }
        if(!currentKind.equals("phone")&&!currentKind.equals(MobileHardware.ANTENNA))button("Оборудование / питание / модули / память",left,height-52,w,()->send("hardware",""));
        button("Обновить",left,height-26,half,()->send("refresh",""));button("Закрыть",left+half+12,height-26,half,this::onClose);
    }
    @Override public void tick(){if(++ticks%20==0)send("refresh","");}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    private void line(GuiGraphics g,String value,int y,int color){g.drawString(font,font.plainSubstrByWidth(value,w),left,y,color);}
    @Override public void render(GuiGraphics g,int x,int y,float dt){
        g.fill(0,0,width,height,0xff102630);g.drawCenteredString(font,"NetLab Mobile / "+text("name"),width/2,14,0x90e6e9);String kind=text("kind");
        if(kind.equals("phone")||kind.equals("pc")){
            line(g,text("sim"),38,0xffffff);line(g,text("registration"),54,0x91eaa8);
            int bars=(int)number("bars");for(int i=0;i<5;i++)g.fill(left+i*12,82-i*6,left+i*12+8,90,i<bars?0xff62dfa1:0xff344f58);
            line(g,String.format(java.util.Locale.ROOT,"RSRP %.0f dBm | SINR %.1f dB | %.0f м",number("rsrp"),number("sinr"),number("distance")),100,0xa5d5da);
            if(kind.equals("pc"))line(g,"SIM меняется при выключенном ПК; параметры сети выдаёт EPC.",114,0xb2bdc9);
            int row=kind.equals("pc")?202:178;if(state.has("cells"))for(var cell:state.getAsJsonArray("cells")){if(row>=height-90)break;var c=cell.getAsJsonObject();line(g,c.get("name").getAsString()+" | "+c.get("signal").getAsString()+" | высота "+c.get("height").getAsInt(),row,0xb4cfd7);row+=14;}
        }else{
            line(g,"IP "+text("ip")+" | EPC "+text("core")+" | питание: "+bool("power"),38,0xffffff);line(g,text("memory"),54,0xc3d3de);
            if(kind.equals(MobileHardware.STATION)){line(g,"Пустая: "+text("blank")+" | готовая: "+bool("output")+" | запрос сохранён: "+bool("pending"),70,0x90e6ae);line(g,"Пароль администратора EPC (для авторизации станка):",82,0xb2bdc9);}
            else if(kind.equals(MobileHardware.ANTENNA)){line(g,"Пассивная антенна: два RF-порта к одному RRU, кабели до 8 м.",80,0xb7d9e2);line(g,"Минимум 12 блоков над окрестной землёй. Направление — поворот блока.",98,0xb7d9e2);line(g,"Диапазон, наклон и дальность настраиваются в Console радиомодуля RRU.",116,0xb7d9e2);}
            else {line(g,"Настройка через Console (ПК, ноутбук или портативный терминал).",78,0xb7d9e2);line(g,"В терминале: help; show mobile; mobile panel",94,0xb7d9e2);line(g,"BBU готов: "+bool("bbuReady")+" | абонентов: "+text("subscribers"),114,0x90e6ae);if(kind.equals(MobileHardware.RRU))line(g,"Radio "+bool("radio")+" / B"+text("band")+" / канал "+text("channel")+" / дальность "+text("range"),132,0xb7d9e2);}
        }
        line(g,text("status"),height-61,0xffc789);super.render(g,x,y,dt);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
