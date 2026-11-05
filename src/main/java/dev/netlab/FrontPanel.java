package dev.netlab;

import com.google.gson.Gson;
import dev.netlab.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Physical panel requests never grant CLI access. Every mutation is validated server-side. */
public final class FrontPanel {
    public static final List<java.util.function.BiFunction<DeviceEntity,Integer,String>> EXTRA_STATUS=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static String extraStatus(DeviceEntity host,int bay){return EXTRA_STATUS.stream().map(hook->hook.apply(host,bay)).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.joining(" | "));}
    public record Port(int index,String name,String medium,int mbps,boolean l2,boolean admin,boolean link,long packets,String role){}
    public record Slot(String key,String format,String module){}
    public record State(String hostname,String profile,boolean powered,List<Port> ports,List<Slot> slots,String status,String memory){}
    public static final Gson GSON=new Gson();
    public static void send(ServerPlayer player,DeviceEntity entity,int bay,String status){
        send(player,entity,bay,status,true);
    }
    public static void send(ServerPlayer player,DeviceEntity entity,int bay,String status,boolean open){
        Device device=bay==0?entity.device:entity.upperDevice;if(device==null||device.kind.equals("rack"))return;
        var h=Hardware.ensure(device);var network=entity.network();var ports=new ArrayList<Port>();
        for(int i=0;i<device.ports.size();i++){var p=device.ports.get(i);if(p.hidden||p.virtual||p.medium.equals("cellular")||p.medium.equals("satellite"))continue;
            boolean link=network.linkUp(entity.slotId(bay),i);
            if(p.console){var peer=entity.links.get(bay*128+i);if(peer!=null&&entity.getLevel().hasChunkAt(BlockPos.of(peer.pos()))&&entity.getLevel().getBlockEntity(BlockPos.of(peer.pos())) instanceof DeviceEntity other){var remote=other.portDevice(peer.port());link=h.powered&&remote!=null&&Hardware.powered(remote);}}
            ports.add(new Port(i,device.portName(i),CableKind.medium(device,i),p.maxMbps,p.l2||device.kind.equals("switch"),p.up,link,p.sent+p.received,
                CableKind.medium(device,i).equals("serial")?network.serialRole(entity.slotId(bay),i)+" / clock "+p.clock:p.console?"9600 8N1":""));
        }
        if(device.kind.equals("switch")||device.kind.equals("router"))ports.sort((a,b)->InterfaceOrder.compare(a.name(),b.name()));
        var slots=new ArrayList<Slot>();Hardware.slots(device).forEach((key,format)->slots.add(new Slot(key,format,h.modules.getOrDefault(key,""))));
        String energy=PowerSystem.status(entity,bay);status=status.isBlank()?energy:status+" | "+energy;
        String extra=extraStatus(entity,bay);if(!extra.isBlank())status+=" | "+extra;
        PacketDistributor.sendToPlayer(player,new Packets.PanelData(entity.getBlockPos(),bay,GSON.toJson(new State(device.hostname,h.profile,h.powered,ports,slots,status,MemoryCards.status(device))),open));
    }
    public static void handle(ServerPlayer player,Packets.PanelRequest request){
        var level=player.serverLevel();var pos=request.pos();
        if(request.bay()<0||request.bay()>1||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!level.hasChunkAt(pos)
            ||!(level.getBlockEntity(pos) instanceof DeviceEntity entity))return;
        Device d=request.bay()==0?entity.device:entity.upperDevice;
        if(d!=null&&!dev.netlab.api.RackDevices.allowed(player,entity,request.bay()))return;
        if(d==null||!d.kind.equals("router")&&!d.kind.equals("switch")&&!d.kind.equals("pc")&&!d.kind.equals("traffic")&&!d.kind.equals("ap")&&!dev.netlab.api.ConsoleExtensions.HANDLERS.containsKey(d.kind))return;
        if(request.action().equals("open")){send(player,entity,request.bay(),"",true);return;}
        String status="";
        try{
            var h=Hardware.ensure(d);String action=request.action();
            if(!action.equals("refresh")&&!player.mayBuild())return;
            switch(action){
                case "memoryInsert"->{if(h.powered)throw new IllegalArgumentException("Сначала выключи устройство");MemoryCards.insert(player,d);status=MemoryCards.status(d);}
                case "memoryEject"->{if(h.powered)throw new IllegalArgumentException("Сначала выключи устройство");MemoryCards.eject(player,d);status=MemoryCards.status(d);}
                case "memoryRead"->{MemoryCards.read(d);MemoryCards.restoreServices(entity,request.bay());status=MemoryCards.status(d);}
                case "refresh"->{}
                case "power"->{
                    if(h.powered){h.powered=false;h.epoch++;}
                    else {
                        String powerProblem=Hardware.powerProblem(d);if(!powerProblem.isBlank())throw new IllegalArgumentException(powerProblem);
                        if(MemoryCards.required(d)){if(MemoryCards.present(d)&&MemoryCards.card(d).active)MemoryCards.read(d);else{var blank=MemoryCards.blank(d);d.hostname=blank.hostname;d.ports=blank.ports;d.gateway="";d.routes.clear();d.router=blank.router;d.wireless=null;d.extensions=blank.extensions;}}
                        h.powered=true;h.epoch++;
                        for(var hook:MemoryCards.APPLY)hook.accept(d,d);MemoryCards.restoreServices(entity,request.bay());
                    }
                    status=d.kind.equals("pc")?(h.powered?"ПК включён":"ПК выключен; IP и Wi-Fi профиль сохраняются"):
                        h.powered?"Питание включено; загружен startup-config":"Устройство выключено. Несохранённая конфигурация будет потеряна при загрузке.";
                }
                case "install"->{
                    var module=Hardware.CATALOG.get(request.value());if(module==null)throw new IllegalArgumentException("Неизвестный модуль");
                    ItemStack held=ItemStack.EMPTY;
                    if(!player.isCreative()){
                        for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(stack.getItem() instanceof ModuleItem item&&item.module.equals(request.value())){held=stack;break;}}
                        if(held.isEmpty())throw new IllegalArgumentException("Нужен предмет модуля в инвентаре");
                    }
                    Hardware.install(d,request.slot(),request.value());if(!player.isCreative())held.shrink(1);status="Установлен "+request.value();
                }
                case "remove"->{
                    String slot=request.slot();
                    var affected=Hardware.portsIn(d,slot);
                    if(slot.endsWith("/sfp0"))affected=Hardware.portsIn(d,slot.substring(0,slot.lastIndexOf('/')));
                    for(int p:affected)if(entity.links.containsKey(request.bay()*128+p))throw new IllegalArgumentException("Сначала отсоедини кабели модуля");
                    String removed=Hardware.remove(d,slot);var item=ModuleItem.find(removed);if(item!=net.minecraft.world.item.Items.AIR&&!player.isCreative()){var stack=new ItemStack(item);if(!player.getInventory().add(stack))player.drop(stack,false);}status="Извлечён "+removed;
                }
                case "profile"->{
                    if(!d.kind.equals("router")&&!d.kind.equals("switch"))throw new IllegalArgumentException("Шасси этого устройства фиксировано");
                    if(entity.links.keySet().stream().anyMatch(p->p/128==request.bay()))throw new IllegalArgumentException("Сначала отсоедини все кабели устройства");
                    Hardware.changeProfile(d,request.value());status="Шасси изменено (учебный профиль совместимости)";
                }
                default->throw new IllegalArgumentException("Неизвестное действие");
            }
            if(!action.equals("refresh")){entity.changed();ConsoleAccess.close(entity,player.getUUID());}
        }catch(IllegalArgumentException e){status=e.getMessage();}
        send(player,entity,request.bay(),status,false);
    }
}
