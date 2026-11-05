package dev.netlab.automation.client;

import com.google.gson.*;
import dev.netlab.automation.AutomationPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class AutomationScreen extends Screen {
    public final BlockPos pos;
    private JsonObject state;
    private int page,scroll,ticks,left,top,panelWidth,logTop;
    private final Map<String,EditBox> fields=new LinkedHashMap<>();
    private final Map<String,String> values=new HashMap<>();
    private final List<String> labels=new ArrayList<>();
    public AutomationScreen(BlockPos pos,String json){super(Component.literal("NetLab Automation"));this.pos=pos;state=JsonParser.parseString(json).getAsJsonObject();}
    public void update(String json){state=JsonParser.parseString(json).getAsJsonObject();}
    private String text(String key){return state.has(key)?state.get(key).getAsString():"";}
    private boolean flag(String key){return state.has(key)&&state.get(key).getAsBoolean();}
    private void remember(){fields.forEach((k,v)->values.put(k,v.getValue()));}
    private String field(String key){return fields.containsKey(key)?fields.get(key).getValue():values.getOrDefault(key,"");}
    private void send(String action,String value){PacketDistributor.sendToServer(new AutomationPackets.Action(pos,action,value));}
    private void button(String title,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(title),b->action.run()).bounds(left+x,top+y,w,20).build());}
    private void edit(String key,String title,int y,String initial){
        labels.add(y+":"+title);var box=new EditBox(font,left+128,top+y,panelWidth-140,20,Component.literal(title));box.setMaxLength(256);box.setValue(values.getOrDefault(key,initial));fields.put(key,box);addRenderableWidget(box);
    }
    @Override protected void init(){
        fields.clear();labels.clear();panelWidth=Math.min(670,width-16);left=(width-panelWidth)/2;top=8;
        boolean server=text("kind").equals("server");String[] pages=server?new String[]{"Сеть","Перенос","Правила","Управление","Журнал"}:new String[]{"Сеть",text("kind").equals("sniffer")?"Захват":"Блок"};
        if(page>=pages.length)page=0;int tabWidth=panelWidth/pages.length;
        for(int i=0;i<pages.length;i++){final int selected=i;button(pages[i],i*tabWidth,24,tabWidth-3,()->{remember();page=selected;scroll=0;rebuildWidgets();});}
        int half=(panelWidth-12)/2;logTop=top+52;
        if(page==0){
            edit("name","Имя устройства",52,text("name"));edit("ip","IP-адрес",76,text("ip"));edit("mask","Маска",100,text("mask"));edit("gateway","Шлюз",124,text("gateway"));
            button("Сохранить сеть",0,150,half,()->{send("network",field("ip")+" "+field("mask")+" "+(field("gateway").isBlank()?"-":field("gateway")));});
            button("Сохранить имя",half+8,150,half,()->send("name",field("name")));logTop=top+178;
        }else if(!server&&text("kind").equals("endpoint")){
            edit("manager","IP сервера",52,text("manager"));button("Доверять серверу",0,78,panelWidth-4,()->send("manager",field("manager")));
            Direction[] directions=Direction.values();for(int i=0;i<directions.length;i++){final var direction=directions[i];button(direction.getName(),i*(panelWidth/6),102,panelWidth/6-3,()->send("face",direction.getName()));}
            button("Извлечение",0,126,panelWidth/3-3,()->send("extract",""));button("Вставка",panelWidth/3,126,panelWidth/3-3,()->send("insert",""));button("Управление",panelWidth*2/3,126,panelWidth/3-3,()->send("control",""));logTop=top+154;
        }else if(!server){button("Очистить захват",0,52,half,()->send("clearCaptures",""));button("Копировать билет",half+8,52,half,()->{if(!text("capturedTicket").isBlank())minecraft.keyboardHandler.setClipboard(text("capturedTicket"));});logTop=top+80;
        }else if(page==1||page==2){
            edit("source","IP источника",52,"");edit("destination","IP получателя",76,"");edit("item","Предмет (mod:id)",100,"minecraft:iron_ingot");
            if(page==1)edit("batch","Размер партии",124,"16");else edit("ruleSize","Запас / партия",124,"64 16");
            if(page==1){button("Передать",0,150,half,()->send("transfer",field("source")+" "+field("destination")+" "+field("item")+" "+field("batch")));button("Опросить источник",half+8,150,half,()->send("inspect",field("source")));logTop=top+178;}
            else{int third=panelWidth/3;button("Добавить",0,150,third-3,()->send("rule",field("source")+" "+field("destination")+" "+field("item")+" "+field("ruleSize")));button("Старт / стоп",third,150,third-3,()->send("running",""));button("Очистить",third*2,150,third-3,()->send("clearRules",""));logTop=top+178;}
        }else if(page==3){
            edit("inspect","IP интерфейса",52,"");button("Прочитать состояние",0,78,half,()->send("inspect",field("inspect")));button("Требовать VPN",half+8,78,half,()->send("vpn",""));
            edit("action","Действие",104,"signal 15");button("Выполнить на блоке",0,130,panelWidth-4,()->send("machine",field("inspect")+" "+field("action")));logTop=top+158;
        }else{
            edit("ticket","Билет (UUID)",52,"");edit("sink","IP получателя",76,"");button("Получить по билету",0,102,half,()->send("claim",field("ticket")+" "+field("sink")));button("Вернуть источнику",half+8,102,half,()->send("return",field("ticket")));button("Копировать журнал",0,126,panelWidth-4,()->minecraft.keyboardHandler.setClipboard(state.get("jobs").toString()));logTop=top+154;
        }
        button("Обновить",0,height-top-26,half,()->send("refresh",""));button("Закрыть",half+8,height-top-26,half,this::onClose);
    }
    private List<String> content(){
        var lines=new ArrayList<String>();lines.add(text("status"));boolean server=text("kind").equals("server");
        if(page==0){lines.add("Ethernet: GigabitEthernet0/0; TCP 5420, VPN UDP 4500");lines.add("Подключи устройство кабелем NetLab к коммутатору.");}
        else if(server){
            lines.add("Правила: "+(flag("running")?"работают":"остановлены")+" | VPN между роутерами: "+(flag("requireVpn")?"обязателен":"не обязателен"));
            if(page==2)for(var r:state.getAsJsonArray("rules"))lines.add(r.toString());
            else if(page==4)for(var j:state.getAsJsonArray("jobs"))lines.add(j.toString());
            else{lines.add("Действия: signal 0..15, play, stop; нужны разрешения интерфейса.");if(!text("remote").isBlank())pretty(JsonParser.parseString(text("remote")),"",lines);}
        }else if(text("kind").equals("endpoint")){
            lines.add("Соседний блок: "+text("target")+" | извлечение="+flag("extract")+", вставка="+flag("insert")+", управление="+flag("control"));
            if(state.has("snapshot"))pretty(state.get("snapshot"),"",lines);if(state.has("snapshotError"))lines.add(text("snapshotError"));
        }else{
            lines.add("Подключи к SPAN-порту. Здесь видны только реально пришедшие кадры.");lines.add("Последний открытый билет: "+(text("capturedTicket").isBlank()?"не найден":text("capturedTicket")));for(var capture:state.getAsJsonArray("captures"))lines.add(capture.getAsString());
        }
        var wrapped=new ArrayList<String>();for(var line:lines){if(line.isEmpty())wrapped.add("");while(!line.isEmpty()){String part=font.plainSubstrByWidth(line,panelWidth-16);if(part.isEmpty())break;wrapped.add(part);line=line.substring(part.length());}}return wrapped;
    }
    private static void pretty(JsonElement element,String path,List<String> lines){
        if(element.isJsonObject())element.getAsJsonObject().entrySet().forEach(e->pretty(e.getValue(),path.isEmpty()?e.getKey():path+"."+e.getKey(),lines));
        else if(element.isJsonArray()){int i=0;for(var e:element.getAsJsonArray())pretty(e,path+"["+(i++)+"]",lines);}
        else lines.add(path+": "+element.getAsString());
    }
    @Override public void tick(){super.tick();if(++ticks%40==0)send("refresh","");}
    @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){remember();super.resize(mc,width,height);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,scroll-(int)(dy*3));return true;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public void render(GuiGraphics g,int x,int y,float dt){
        g.fill(0,0,width,height,0xf0101d29);g.drawString(font,title.getString()+" / "+text("name"),left,top+5,0x8cddff,false);
        for(var label:labels){int colon=label.indexOf(':');g.drawString(font,label.substring(colon+1),left+2,top+Integer.parseInt(label.substring(0,colon))+6,0xe5e9ed,false);}
        int bottom=height-34;g.fill(left,logTop,left+panelWidth,bottom,0xff071019);
        var lines=content();int shown=Math.max(1,(bottom-logTop-8)/11);scroll=Math.clamp(scroll,0,Math.max(0,lines.size()-shown));
        g.enableScissor(left,logTop,left+panelWidth,bottom);for(int i=scroll;i<Math.min(lines.size(),scroll+shown);i++)g.drawString(font,lines.get(i),left+5,logTop+4+(i-scroll)*11,0x98e4be,false);g.disableScissor();
        super.render(g,x,y,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
