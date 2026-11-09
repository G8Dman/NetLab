package dev.netlab.mobile.client;

import com.google.gson.*;
import dev.netlab.mobile.*;
import dev.netlab.client.DesktopApps;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Phone applications reuse the same optional addon screens and authenticated network endpoints. */
public final class PhoneScreen extends Screen {
    public final String phone;
    private final Screen parent;
    private JsonObject state;
    private int tab,left,w,ticks,moduleStep,moduleScroll;
    private String address="",payload="NetLab Pocket",port="5420",protocol="UDP";
    public PhoneScreen(String phone,String json,Screen parent){super(Component.literal("Cisco Pocket"));this.phone=phone;this.parent=parent;state=JsonParser.parseString(json).getAsJsonObject();}
    private String text(String key){return state.has(key)?state.get(key).getAsString():"";}
    private JsonArray modules(){return state.has("modules")?state.getAsJsonArray("modules"):new JsonArray();}
    private java.util.List<JsonElement> visibleModules(){var all=modules().asList();int count=Math.max(1,(height-171)/36);moduleScroll=Math.clamp(moduleScroll,0,Math.max(0,all.size()-count));return all.subList(moduleScroll,Math.min(all.size(),moduleScroll+count));}
    private boolean installed(String slot){for(var item:modules()){var row=item.getAsJsonObject();if(row.get("slot").getAsString().equals(slot))return row.get("installed").getAsBoolean();}return false;}
    private void send(String action,String value){PacketDistributor.sendToServer(new MobilePackets.Action(BlockPos.ZERO,0,phone,action,value));}
    private Button button(String title,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(title),b->action.run()).bounds(x,y,width,20).build());}
    private EditBox field(String value,int x,int y,int width,int max,java.util.function.Consumer<String> setter){var box=new EditBox(font,x,y,width,20,Component.empty());box.setMaxLength(max);box.setValue(value);box.setResponder(setter);return addRenderableWidget(box);}
    public void update(String json){var next=JsonParser.parseString(json).getAsJsonObject();boolean changed=!java.util.Objects.equals(next.get("modules"),state.get("modules"))||!next.get("ip").equals(state.get("ip"));state=next;if(changed){clearWidgets();init();}}
    private void open(String role){
        if(!installed(role))return;var app=DesktopApps.APPS.stream().filter(a->role.equals("telephony")?a.module().equals("CISCO-POCKET-SIP"):role.equals("browser")?a.module().isEmpty()&&a.title().equals("Браузер"):a.module().equals("CISCO-AUTOMATION")).findFirst();
        if(app.isPresent())app.get().open().accept(BlockPos.of(state.get("endpoint").getAsLong()),this);
    }
    private void packet(String action){var q=new JsonObject();q.addProperty("address",address);q.addProperty("protocol",protocol);q.addProperty("port",port);q.addProperty("payload",payload);send(action,q.toString());}
    @Override protected void init(){
        w=Math.min(700,width-24);left=(width-w)/2;moduleStep=36;
        String[] tabs={"Связь","Модули","Сеть","Приложения"};for(int i=0;i<tabs.length;i++){final int selected=i;button((tab==i?"› ":"")+tabs[i],left+i*w/4,90,w/4-3,()->{tab=selected;clearWidgets();init();});}
        int y=130;
        if(tab==0){button("Вставить SIM",left,y,w/2-4,()->send("insert",""));button("Извлечь SIM",left+w/2+4,y,w/2-4,()->send("eject",""));}
        if(tab==1){y=124;for(var item:visibleModules()){var row=item.getAsJsonObject();String slot=row.get("slot").getAsString();boolean installed=row.get("installed").getAsBoolean();button(installed?"Извлечь":"Установить",left+w-112,y,112,()->send(installed?"moduleEject":"moduleInstall",slot));y+=moduleStep;}}
        if(tab==2&&installed("diagnostics")){
            field(address,left,128,w-100,15,v->address=v);field(port,left+w-92,128,92,5,v->port=v);field(payload,left,164,w,512,v->payload=v);
            button(protocol,left,190,72,()->{protocol=protocol.equals("UDP")?"TCP":"UDP";clearWidgets();init();});button("Ping",left+80,190,90,()->packet("ping"));button("Отправить пакет",left+178,190,w-178,()->packet("packet"));
        }
        if(tab==3){button("Браузер Sputnik",left,130,w,()->open("browser")).active=installed("browser")&&!text("ip").isBlank();button("Управление мультиинтерфейсами",left,158,w,()->open("automation")).active=installed("automation")&&!text("ip").isBlank();button("IP-телефония",left,186,w,()->open("telephony")).active=installed("telephony")&&!text("ip").isBlank();}
        button("Назад",left,height-26,w,this::onClose);
    }
    @Override public void tick(){if(++ticks%20==0)send("refresh","");}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float delta){}
    private void line(GuiGraphics g,String value,int y,int color){g.drawString(font,font.plainSubstrByWidth(value,w),left,y,color);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        g.fill(0,0,width,height,0xff102630);g.drawCenteredString(font,"Cisco Pocket",width/2,15,0x93e9e4);line(g,text("sim")+"  |  IP "+text("ip"),36,0xffffff);line(g,text("registration"),52,0x91eaa8);
        int bars=state.has("bars")?state.get("bars").getAsInt():0;for(int i=0;i<5;i++)g.fill(left+i*10,81-i*3,left+i*10+7,84,i<bars?0xff62dfa1:0xff344f58);
        if(tab==0){line(g,"Регистрация поддерживается, пока телефон находится в руке.",118,0xa5c9d0);int y=160;if(state.has("cells"))for(var cell:state.getAsJsonArray("cells")){if(y>height-58)break;var row=cell.getAsJsonObject();line(g,row.get("name").getAsString()+" | "+row.get("signal").getAsString(),y,0xc5dce0);y+=15;}}
        if(tab==1){int y=126;for(var item:visibleModules()){var row=item.getAsJsonObject();g.drawString(font,font.plainSubstrByWidth(row.get("title").getAsString(),w-124),left,y,0xffffff);String slot=row.get("slot").getAsString();String note=row.has("server")?"Сервер "+row.get("server").getAsString():slot.equals("automation")?"Сначала ПКМ модулем по своему серверу":slot.equals("terminal")?"ПКМ телефоном по Console; без SIM":"Установка из инвентаря игрока";g.drawString(font,font.plainSubstrByWidth(note,w-124),left,y+15,0x9bbfc8);y+=moduleStep;}}
        if(tab==2){if(!installed("diagnostics"))line(g,"Установи модуль диагностики в разделе «Модули».",132,0xffd08d);else{line(g,"IP устройства NetLab",116,0xb8d7dc);g.drawString(font,"Порт",left+w-92,116,0xb8d7dc);line(g,"Данные пакета",153,0xb8d7dc);String output=text("result");int yy=218;for(String row:output.split("\n")){if(yy>height-54)break;line(g,row,yy,0xb2e8c6);yy+=11;}}}
        if(tab==3&&height>300){line(g,installed("terminal")?"Терминал: ПКМ телефоном по Console устройства, без SIM.":"Сетевые приложения требуют SIM и соответствующий модуль.",218,0xb8d7dc);line(g,"Браузер: Sputnik · управление: Automation · звонки: Media",235,0xb8d7dc);}
        String status=text("status");if(status.isBlank()&&tab==1&&visibleModules().size()<modules().size())status="Колесо мыши — остальные модули";if(status.isBlank()&&tab==2&&height<300)status=text("result").split("\n",2)[0];line(g,status,height-40,0xffc789);super.render(g,mouseX,mouseY,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(tab==1&&dy!=0){moduleScroll+=dy<0?1:-1;clearWidgets();init();return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean isPauseScreen(){return false;}
}
