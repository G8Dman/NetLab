package dev.netlab.client;

import dev.netlab.*;
import dev.netlab.core.Hardware;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Physical view only: no CLI controls. Slot selection and inventory changes go to the server. */
public final class FrontPanelScreen extends Screen {
    public final BlockPos pos;public final int bay;
    private Screen parent;
    private FrontPanel.State state;
    private int portPage,slotPage,moduleIndex,ticks;
    private String selectedSlot="",status="";
    private String pendingProfile="";
    private final Map<Integer,Long> counters=new HashMap<>(),blink=new HashMap<>();
    private int left,top,w;
    private boolean modulesView,memoryView;
    public FrontPanelScreen(BlockPos pos,int bay,String json){super(Component.literal("NetLab / Physical"));this.pos=pos;this.bay=bay;update(json);}
    public FrontPanelScreen(BlockPos pos,int bay,String json,Screen parent){this(pos,bay,json);this.parent=parent;}
    public void update(String json){
        var next=FrontPanel.GSON.fromJson(json,FrontPanel.State.class);
        boolean rebuild=state==null||state.powered()!=next.powered()||!state.slots().equals(next.slots())||!state.profile().equals(next.profile())||state.ports().size()!=next.ports().size();
        for(var port:next.ports()){Long old=counters.put(port.index(),port.packets());if(old!=null&&old!=port.packets())blink.put(port.index(),System.currentTimeMillis()+650);}
        state=next;if(!next.status().isBlank())status=next.status();
        if(next.slots().stream().noneMatch(s->s.key().equals(selectedSlot))){selectedSlot="";moduleIndex=0;}
        slotPage=Math.min(slotPage,Math.max(0,(next.slots().size()-1)/4));portPage=Math.min(portPage,Math.max(0,(next.ports().size()-1)/12));
        if(rebuild&&minecraft!=null){clearWidgets();init();}
    }
    private void send(String action,String slot,String value){PacketDistributor.sendToServer(new Packets.PanelRequest(pos,bay,action,slot,value));}
    private String moduleLabel(String id){var item=ModuleItem.find(id);return item==net.minecraft.world.item.Items.AIR?id:item.getDefaultInstance().getHoverName().getString();}
    private List<String> compatible(){String format=state.slots().stream().filter(s->s.key().equals(selectedSlot)).map(FrontPanel.Slot::format).findFirst().orElse("");return Hardware.CATALOG.values().stream().filter(m->Hardware.compatible(format,m)).map(Hardware.Module::id).toList();}
    private void button(String text,int x,int y,int width,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,width,20).build());}
    @Override protected void init(){
        w=Math.min(740,width-20);left=(width-w)/2;top=16;
        button(state.powered()?"Power: ON":"Power: OFF",left+8,top+25,90,()->send("power","",""));
        button(modulesView?"Порты":"Модули",left+104,top+25,75,()->{memoryView=false;modulesView=!modulesView;clearWidgets();init();});
        button("Память",left+185,top+25,75,()->{memoryView=!memoryView;clearWidgets();init();});
        button("Шасси: "+state.profile(),left+266,top+25,w-274,()->{
            if(state.profile().equals("Laptop-PT")||state.profile().equals("PC-PT")||state.profile().equals("Traffic Controller")||state.profile().equals("AccessPoint-PT")||state.profile().equals("Automation Server")||state.profile().equals("Multiinterface")||state.profile().equals("Packet Analyzer")||state.profile().startsWith("Sputnik ")||state.profile().startsWith("NetLab Mobile")||state.profile().equals("SIM Provisioner")){status="Фиксированное шасси устройства";return;}
            var profiles=Hardware.profiles(state.profile().contains("Switch")||state.profile().matches("29.*|35.*|36.*")?"switch":"router");
            int next=(profiles.indexOf(state.profile())+1)%profiles.size();String profile=profiles.get(next);
            if(!pendingProfile.equals(profile)){pendingProfile=profile;status="Смена на "+profile+" пересоздаёт встроенные порты. Нажми ещё раз для подтверждения.";}
            else {pendingProfile="";send("profile","",profile);}
        });
        if(memoryView){
            if(!"Встроенная память".equals(state.memory())){
                button("Вставить карту CISCO из инвентаря",left+8,top+65,w-16,()->send("memoryInsert","",""));
                button("Извлечь карту (содержимое сохранится)",left+8,top+91,w-16,()->send("memoryEject","",""));
                button("Прочитать карту / применить настройки",left+8,top+117,w-16,()->send("memoryRead","",""));
            }
            button("Закрыть",left+w-88,height-25,80,this::onClose);return;
        }
        if(!modulesView){
            if(state.ports().stream().anyMatch(p->p.medium().equals("wireless"))||state.profile().equals("Laptop-PT")||state.profile().equals("PC-PT"))
                button("Wi-Fi",left+8,top+115,70,()->PacketDistributor.sendToServer(new Packets.WifiRequest(pos,bay,"open","")));
            button("< Порты",left+83,top+115,80,()->{portPage=Math.max(0,portPage-1);});
            button("Порты >",left+w-88,top+115,80,()->{portPage=Math.min(Math.max(0,(state.ports().size()-1)/12),portPage+1);});
            button("Закрыть",left+w-88,height-25,80,this::onClose);return;
        }
        int y=top+65;
        for(int i=0;i<4;i++){int index=slotPage*4+i;if(index>=state.slots().size())break;var slot=state.slots().get(index);
            button(slot.key()+" ["+slot.format()+"] "+(slot.module().isEmpty()?"пусто":moduleLabel(slot.module())),left+8,y+i*23,w/2-18,()->{selectedSlot=slot.key();moduleIndex=0;});
        }
        button("< Слоты",left+8,y+96,80,()->{slotPage=Math.max(0,slotPage-1);clearWidgets();init();});
        button("Слоты >",left+w/2-90,y+96,80,()->{slotPage=Math.min(Math.max(0,(state.slots().size()-1)/4),slotPage+1);clearWidgets();init();});
        int x=left+w/2+5;
        button("<",x,y+25,25,()->{var items=compatible();if(!items.isEmpty())moduleIndex=Math.floorMod(moduleIndex-1,items.size());});
        button(">",left+w-33,y+25,25,()->{var items=compatible();if(!items.isEmpty())moduleIndex=(moduleIndex+1)%items.size();});
        button("Установить",x,y+73,w/4-12,()->{var items=compatible();if(!items.isEmpty())send("install",selectedSlot,items.get(Math.floorMod(moduleIndex,items.size())));});
        button("Извлечь",x+w/4,y+73,w/4-17,()->send("remove",selectedSlot,""));
        button("Закрыть",left+w-88,height-25,80,this::onClose);
    }
    @Override public void tick(){super.tick();if(++ticks%10==0)send("refresh","","");}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float dt){
        g.fill(0,0,width,height,0xff10212d);g.drawString(font,"PHYSICAL / "+state.hostname()+" / ячейка "+(bay+1),left+8,top+5,0xffffff);
        if(memoryView){g.drawWordWrap(font,Component.literal(state.memory()==null?"":state.memory()),left+8,top+155,w-16,0x9cddd4);}
        else if(!modulesView){
        g.fill(left+5,top+51,left+w-5,top+113,0xff34444c);
        long now=System.currentTimeMillis();
        for(int i=0;i<12;i++){int index=portPage*12+i;if(index>=state.ports().size())break;var port=state.ports().get(index);boolean pairs=dev.netlab.core.PortLayout.paired(state.profile());int col=pairs?i/2:i%6,row=pairs?i%2:i/6;
            int pw=(w-24)/6,x=left+12+col*pw,y=top+56+row*28;
            int color=port.medium().equals("fiber")?0xffffaa33:port.medium().equals("console")?0xff55ddff:port.medium().equals("serial")?0xffdd5555:0xff11191e;
            g.fill(x,y,x+pw-5,y+15,color);g.fill(x+3,y+3,x+pw-8,y+12,0xff101419);
            g.fill(x+3,y-2,x+7,y+1,state.powered()&&port.link()?0xff44ee66:0xff663333);
            g.fill(x+10,y-2,x+14,y+1,state.powered()&&blink.getOrDefault(port.index(),0L)>now?0xffffff33:0xff665522);
            String label=port.name().replace("GigabitEthernet","Gi").replace("FastEthernet","Fa").replace("Ethernet","Et");
            g.drawString(font,font.plainSubstrByWidth(label,pw-5),x,y+17,0xffffff);
            if(mouseX>=x&&mouseX<x+pw-5&&mouseY>=y&&mouseY<y+26)
                g.renderTooltip(font,Component.literal(port.name()+" / "+port.medium()+" / "+(port.role().isEmpty()?port.mbps()+" Mb/s":port.role())+" / packets "+port.packets()+" / "+(port.l2()?"L2":"L3")),mouseX,mouseY);
        }
        g.drawCenteredString(font,"LINK: зелёный  ACT: жёлтый   порты "+(portPage+1),width/2,top+121,0x9ec9db);
        g.drawString(font,"Модули — замена при выключенном питании",left+8,top+145,0xaabbcc);
        int count=Math.min(6,state.slots().size());
        if(count>0)for(int i=0;i<count;i++){var slot=state.slots().get(i);int sw=(w-16)/count,x=left+8+i*sw,y=top+165;
            g.fill(x,y,x+sw-4,y+25,slot.module().isEmpty()?0xff0a1014:0xff446477);
            g.drawString(font,font.plainSubstrByWidth(slot.key(),sw-8),x+3,y+3,0xffffff);
            g.drawString(font,font.plainSubstrByWidth(slot.module().isEmpty()?slot.format():moduleLabel(slot.module()),sw-8),x+3,y+14,0x8eccdf);
        }
        }else{
        int x=left+w/2+38,y=top+65;var modules=compatible();
        g.drawString(font,"Слот: "+selectedSlot,left+w/2+5,y+3,0xffffff);
        if(!modules.isEmpty()){
            String id=modules.get(Math.floorMod(moduleIndex,modules.size()));var m=Hardware.CATALOG.get(id);
            g.drawString(font,font.plainSubstrByWidth(moduleLabel(id),w/2-85),x,y+31,0x66ddff);
            g.drawString(font,m.format()+" / "+(m.ports()==0?"внутренний модуль":m.medium()+" / "+m.ports()+" ports"),left+w/2+5,y+53,0xaabbcc);
        }
        if(state.slots().isEmpty())g.drawString(font,"Фиксированные порты: модульных слотов нет",left+8,top+60,0xaabbcc);
        }
        super.render(g,mouseX,mouseY,dt);
        if(!status.isEmpty())g.drawWordWrap(font,Component.literal(status),left+8,height-47,w-105,0xffcc99);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(!modulesView&&button==0&&y>=top+165&&y<top+190){int count=Math.min(6,state.slots().size());
            if(count>0&&x>=left+8&&x<left+w-8){int index=Math.min(count-1,(int)((x-left-8)/((w-16)/count)));selectedSlot=state.slots().get(index).key();slotPage=index/4;moduleIndex=0;modulesView=true;clearWidgets();init();return true;}}
        return super.mouseClicked(x,y,button);
    }
}
