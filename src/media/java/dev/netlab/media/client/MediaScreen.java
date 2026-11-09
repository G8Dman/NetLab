package dev.netlab.media.client;
import com.google.gson.*;
import dev.netlab.media.*;
import dev.netlab.media.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Compact forms keep values while asynchronous network operations update the status. */
public final class MediaScreen extends Screen {
    public final BlockPos pos;public final int bay;
    private final Screen parent;private JsonObject state;private final Map<String,String> values=new HashMap<>();private final Map<String,EditBox> fields=new HashMap<>();private final List<Label> labels=new ArrayList<>();private record Label(String text,int x,int y){}
    private int left,w,top,ticks,scroll,contentBottom;private String service="iptv";private boolean ready;
    public MediaScreen(BlockPos pos,int bay,String json,Screen parent){super(Component.literal("NetLab Media"));this.pos=pos;this.bay=bay;this.parent=parent;state=JsonParser.parseString(json).getAsJsonObject();}
    private String str(String k){return state.has(k)?state.get(k).getAsString():"";}private boolean yes(String k){return state.has(k)&&state.get(k).getAsBoolean();}
    private boolean pocket(){return pos.getY()==-2048;}
    private String value(String k,String fallback){return values.getOrDefault(k,fallback);}
    private void field(String key,String title,String initial,int x,int y,int width,int max,boolean secret){contentBottom=Math.max(contentBottom,y+34);y-=scroll;labels.add(new Label(title,x,y));var box=new EditBox(font,x,y+12,width,20,Component.literal(title));box.setMaxLength(max);box.setValue(value(key,initial));box.setResponder(v->values.put(key,v));if(secret)box.setFormatter((text,index)->net.minecraft.util.FormattedCharSequence.forward("•".repeat(text.length()),net.minecraft.network.chat.Style.EMPTY));fields.put(key,box);box.visible=y>=52&&y+32<height-68;addRenderableWidget(box);}
    private Button button(String title,int x,int y,int width,Runnable action){boolean footer=title.equals("Назад")||title.equals("Оборудование / питание");if(!footer){contentBottom=Math.max(contentBottom,y+22);y-=scroll;}var button=Button.builder(Component.literal(title),b->action.run()).bounds(x,y,width,20).build();button.visible=footer||y>=52&&y+20<height-68;return addRenderableWidget(button);}
    private JsonObject data(String... pairs){var o=new JsonObject();for(int i=0;i<pairs.length;i+=2)o.addProperty(pairs[i],pairs[i+1]);return o;}
    private void send(String action,JsonObject data){PacketDistributor.sendToServer(new MediaPackets.Action(pos,bay,action,data.toString()));}
    private void command(String text){send(yes("admin")?"admin":"command",data("command",text));}
    private void rebuild(){clearWidgets();init();}
    public void update(String json){var next=JsonParser.parseString(json).getAsJsonObject();boolean layout=!Objects.equals(state.get("admin"),next.get("admin"))||!Objects.equals(state.get("kind"),next.get("kind"));state=next;if(layout&&minecraft!=null){scroll=0;rebuild();}}
    @Override protected void init(){fields.clear();labels.clear();w=Math.min(700,width-24);left=(width-w)/2;top=64;contentBottom=top;String kind=str("kind");int half=(w-8)/2;
        if(kind.equals("pc")&&!pocket()&&!yes("admin")){
            button(service.equals("iptv")?"› IPTV":"IPTV",left,top,half,()->{service="iptv";rebuild();});button(service.equals("pbx")?"› IP-АТС":"IP-АТС",left+half+8,top,half,()->{service="pbx";rebuild();});
            field("loginServer","IP сервера",service.equals("iptv")?"192.168.50.10":"192.168.60.10",left,top+30,w,15,false);field("loginPassword","Пароль администратора","",left,top+72,w,64,true);button("Подключиться",left,top+122,w,()->send("login",data("server",value("loginServer",service.equals("iptv")?"192.168.50.10":"192.168.60.10"),"password",value("loginPassword",""),"kind",service)));
        }else if(yes("admin")){
            boolean iptv=str("service").equals("iptv");
            if(iptv){field("channel","Номер канала","7",left,top,92,4,false);field("name","Название канала","Cisco Music",left+100,top,w-100,64,false);button("Создать / переименовать",left,top+38,w,()->command("iptv channel "+value("channel","7")+" "+value("name","Cisco Music")));
                field("url","Ссылка YouTube или прямое видео","",left,top+68,w-108,1024,false);field("seconds","Секунд","180",left+w-100,top+68,100,5,false);button("Добавить в плейлист",left,top+106,w,()->command("iptv add "+value("channel","7")+" "+value("seconds","180")+" "+value("url","")));
                button("Каналы ▾",left,top+214,w,()->minecraft.setScreen(new ChannelChoiceScreen(state.getAsJsonArray("channels"),(number,name)->{values.put("channel",number);values.put("name",name);send("inspectChannel",data("channel",number));},this)));
                button("В эфир",left,top+134,half,()->command("iptv start "+value("channel","7")));button("Остановить",left+half+8,top+134,half,()->command("iptv stop "+value("channel","7")));
                field("index","Номер видео для удаления / переноса","1",left,top+166,half,2,false);button("Удалить видео",left+half+8,top+178,half,()->command("iptv remove "+value("channel","7")+" "+value("index","1")));
                field("targetIndex","Новая позиция видео","1",left,top+244,half,2,false);button("Переместить видео",left+half+8,top+256,half,()->command("iptv move "+value("channel","7")+" "+value("index","1")+" "+value("targetIndex","1")));button("Плейлист ▾",left,top+286,w,()->minecraft.setScreen(new PlaylistScreen(state.getAsJsonArray("playlist"),index->{values.put("index",index);},this)));
            }else{field("user","Внутренний номер","1001",left,top,w,12,false);field("userPassword","Пароль регистрации телефона","",left,top+44,w,64,true);button("Создать / сменить пароль",left,top+88,w,()->command("sip user "+value("user","1001")+" "+value("userPassword","")));button("Удалить абонента",left,top+116,w,()->command("sip delete "+value("user","1001")));}
        }else if(kind.equals(MediaHardware.PHONE)||pocket()){
            field("server","IP АТС",str("server"),left,top,w,15,false);field("number","Свой номер",str("number"),left,top+40,half,12,false);field("password","Пароль регистрации","",left+half+8,top+40,half,64,true);button("Сохранить и зарегистрировать",left,top+80,w,()->send("phoneConfig",data("server",value("server",str("server")),"number",value("number",str("number")),"password",value("password",""))));
            field("dial","Номер собеседника","1002",left,top+110,w,12,false);button("Позвонить",left,top+148,half,()->send("dial",data("number",value("dial","1002"))));button("Ответить",left+half+8,top+148,half,()->send("answer",new JsonObject()));button("Завершить",left,top+176,half,()->send("hangup",new JsonObject()));button("Громкая связь: "+(yes("speakers")?"вкл":"выкл"),left+half+8,top+176,half,()->{send("audio",data("volume",str("volume").isBlank()?"0.7":str("volume"),"speakers",Boolean.toString(!yes("speakers"))));});
        }else if(kind.equals(MediaHardware.STB)){
            field("server","IP медиасервера",str("server"),left,top,w,15,false);field("channel","Канал",str("channel"),left,top+44,w,4,false);button("Переключить",left,top+88,w,()->send("tune",data("server",value("server",str("server")),"channel",value("channel",str("channel")))));button("Физическая панель / IP / память",left,top+120,w,()->send("hardware",new JsonObject()));button("Каналы ▾",left,top+150,w,()->minecraft.setScreen(new ChannelChoiceScreen(state.getAsJsonArray("channels"),(number,name)->values.put("channel",number),this)));
        }else if(kind.equals(MediaHardware.TV)){
            button("Смотреть / управление плеером",left,top,w,()->minecraft.setScreen(new TelevisionScreen(pos,this)));button("Встроенные динамики: "+(yes("speakers")?"вкл":"выкл"),left,top+30,w,()->send("audio",data("volume",str("volume").isBlank()?"0.7":str("volume"),"speakers",Boolean.toString(!yes("speakers")))));
        }
        if(!kind.equals("pc")&&!kind.equals(MediaHardware.STB))button("Оборудование / питание",left,height-60,w,()->send("hardware",new JsonObject()));
        if(Set.of(MediaHardware.SPLITTER,MediaHardware.SPEAKER,MediaHardware.TV).contains(kind)){field("volume","Громкость 0..100",Integer.toString((int)(state.has("volume")?state.get("volume").getAsDouble()*100:70)),left,top+90,half,3,false);button("Применить громкость",left+half+8,top+102,half,()->{try{send("audio",data("volume",Double.toString(Integer.parseInt(value("volume","70"))/100.0),"speakers",Boolean.toString(yes("speakers"))));}catch(NumberFormatException ignored){}});}
        button("Назад",left,height-26,w,this::onClose);ready=true;
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.clamp(scroll-(int)(dy*24),0,Math.max(0,contentBottom-(height-70)));rebuild();return true;}
    @Override public void tick(){if(++ticks%20==0)send("refresh",new JsonObject());}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){}
    private void line(GuiGraphics g,String text,int y,int color){g.drawString(font,font.plainSubstrByWidth(text,w),left,y,color);}
    @Override public void render(GuiGraphics g,int x,int y,float delta){g.fill(0,0,width,height,0xff0d202a);g.fill(left-8,8,left+w+8,51,0xff183640);g.drawCenteredString(font,str("name"),width/2,16,0x93e9e4);line(g,"NetLab Media  |  "+str("ip")+"  "+str("serviceIP"),34,0xa5c9d0);for(var label:labels)if(label.y>=52&&label.y+32<height-68)g.drawString(font,label.text,label.x,label.y,0xabcbd1);
        if(str("kind").equals(MediaHardware.PHONE)||pocket()){String stateText=(yes("registered")?"REGISTERED":"Не зарегистрирован")+" · "+str("call")+" "+str("peer");line(g,stateText,top-12,yes("registered")?0x88e7ac:0xffc17f);if(height>350)line(g,yes("voice")?"Микрофон: PTT Simple Voice Chat; Esc — свернуть; оставайся рядом / держи Pocket.":"Simple Voice Chat не подключён к голосовому серверу.",top+206,0xa5c9d0);}
        String status=str("status");if(status.isBlank())status=height<350?"Колесо мыши — прокрутка формы":str("memory");line(g,status,height-42,0xffcc8b);super.render(g,x,y,delta);}
    @Override public void onClose(){if((pocket()||str("kind").equals(MediaHardware.PHONE))&&(str("call").isBlank()||str("call").equals("IDLE")))send("release",new JsonObject());minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
