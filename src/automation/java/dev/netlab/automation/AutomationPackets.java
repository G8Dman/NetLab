package dev.netlab.automation;

import com.google.gson.*;
import dev.netlab.DeviceEntity;
import dev.netlab.core.Device;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

public final class AutomationPackets {
    private static String queue(ServerPlayer player,AutomationNode node,java.util.concurrent.CompletableFuture<?> future){
        String[] status={"Запрос в пути; ожидание передачи пакетов"};
        future.whenComplete((value,error)->{
            status[0]=error==null?(value instanceof com.google.gson.JsonObject?"Ответ мультиинтерфейса получен":String.valueOf(value)):AutomationService.reason(error);
            if(!node.isRemoved()){node.last=status[0];node.changed();if(player.serverLevel()==node.getLevel()&&player.distanceToSqr(node.getBlockPos().getCenter())<=64)send(player,node,status[0],false);}
        });return status[0];
    }
    private static final Gson JSON=new Gson();
    private static final Map<UUID,Long> RATE=new HashMap<>();
    public record Action(BlockPos pos,String action,String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Automation.ID,"action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Action::pos,ByteBufCodecs.stringUtf8(24),Action::action,ByteBufCodecs.stringUtf8(2048),Action::value,Action::new);
        public Type<Action> type(){return TYPE;}
    }
    public record State(BlockPos pos,String json,boolean open) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Automation.ID,"state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,State::pos,ByteBufCodecs.stringUtf8(131072),State::json,ByteBufCodecs.BOOL,State::open,State::new);
        public Type<State> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1");
        r.playToServer(Action.TYPE,Action.CODEC,(msg,ctx)->handle((ServerPlayer)ctx.player(),msg));
        r.playToClient(State.TYPE,State.CODEC,(msg,ctx)->dev.netlab.automation.client.AutomationClient.receive(msg));
    }
    public static void send(ServerPlayer player,AutomationNode node,String status,boolean open){
        if(!node.owns(player.getUUID())){player.displayClientMessage(Component.literal("Устройство принадлежит другому игроку"),true);return;}
        if(node.owner==null){node.owner=player.getUUID();node.changed();}
        var out=new JsonObject();out.addProperty("kind",node.device.kind);out.addProperty("name",node.device.hostname);out.addProperty("ip",node.ip());out.addProperty("mask",node.device.ports.getFirst().mask);out.addProperty("gateway",node.device.gateway);
        out.addProperty("manager",node.manager);out.addProperty("target",node.target.getName());out.addProperty("extract",node.extract);out.addProperty("insert",node.insert);out.addProperty("control",node.control);out.addProperty("requireVpn",node.requireVpn);out.addProperty("running",node.running);out.addProperty("status",status.isBlank()?node.last:status);out.addProperty("remote",node.remoteSnapshot);out.add("rules",JSON.toJsonTree(node.rules));out.add("captures",JSON.toJsonTree(node.captures));out.addProperty("capturedTicket",node.capturedTicket);
        if(node.device.kind.equals("endpoint"))try{out.add("snapshot",MachineAccess.snapshot(node));}catch(RuntimeException e){out.addProperty("snapshotError","Адаптер блока не смог прочитать состояние");}
        var jobs=new JsonArray();if(node.device.kind.equals("server"))for(var j:TransferJournal.get(player.serverLevel()).jobs.values().stream().filter(j->player.getUUID().equals(j.owner)).sorted(Comparator.comparing((TransferJournal.Job j)->!j.hasCargo())).limit(128).toList()){
            var job=new JsonObject();job.addProperty("ticket",j.ticket.toString());job.addProperty("state",j.state);job.addProperty("count",j.amount());job.addProperty("unit",j.unit());job.addProperty("note",j.note);jobs.add(job);
        }out.add("jobs",jobs);
        String json=JSON.toJson(out);if(json.length()>125000){out.add("jobs",new JsonArray());out.addProperty("remote","");out.remove("snapshot");out.addProperty("status","Ответ слишком большой: показаны только текущие настройки");json=JSON.toJson(out);}
        PacketDistributor.sendToPlayer(player,new State(node.getBlockPos(),json,open));
    }
    private static void handle(ServerPlayer p,Action req){
        var l=p.serverLevel();if(!l.hasChunkAt(req.pos)||p.distanceToSqr(req.pos.getX()+.5,req.pos.getY()+.5,req.pos.getZ()+.5)>64||!(l.getBlockEntity(req.pos) instanceof DeviceEntity host))return;
        var node=AutomationNode.of(host,0);if(node==null||!node.owns(p.getUUID()))return;
        if(!req.action.equals("refresh")&&!node.device.kind.equals("sniffer")){send(p,node,"Настройка и программирование — через Console с ПК/ноутбука",false);return;}
        long now=l.getGameTime();if(now-RATE.getOrDefault(p.getUUID(),now-3)<2)return;if(RATE.size()>1024)RATE.clear();RATE.put(p.getUUID(),now);
        if(node.owner==null)node.owner=p.getUUID();
        String status="";try{
            String[] a=req.value.strip().split("\\s+");
            switch(req.action){
                case "refresh"->{}
                case "network"->{
                    if(a.length<2||a.length>3)throw new IllegalArgumentException("IP MASK [GATEWAY]");Device.ip(a[0]);Device.mask(a[1]);
                    long mask=Device.mask(a[1]);long inverse=(~mask)&0xffffffffL;if((inverse&(inverse+1))!=0)throw new IllegalArgumentException("Маска должна быть непрерывной");
                    String gateway=a.length==3&&!a[2].equals("-")?a[2]:"";if(!gateway.isBlank())Device.ip(gateway);
                    for(var other:DeviceEntity.loaded(l))if(other!=node.host&&other.device.ports.stream().anyMatch(port->port.ip.equals(a[0])))throw new IllegalArgumentException("IP уже занят");
                    node.device.ports.getFirst().ip=a[0];node.device.ports.getFirst().mask=a[1];node.device.gateway=gateway;status="Адрес настроен";
                }
                case "name"->node.device.hostname=DeviceEntity.cleanName(req.value);
                case "target"->{if(!node.device.kind.equals("endpoint"))throw new IllegalArgumentException("Нужен мультиинтерфейс");node.target=node.target.getClockWise(net.minecraft.core.Direction.Axis.X);if(node.target==net.minecraft.core.Direction.WEST||node.target==net.minecraft.core.Direction.EAST)node.target=net.minecraft.core.Direction.DOWN;}
                case "face"->{var d=net.minecraft.core.Direction.byName(req.value);if(d==null)throw new IllegalArgumentException("Неизвестная сторона");node.target=d;}
                case "manager"->{if(!req.value.isBlank())Device.ip(req.value);node.manager=req.value.strip();}
                case "extract"->node.extract=!node.extract;
                case "insert"->node.insert=!node.insert;
                case "control"->{node.control=!node.control;if(!node.control){node.signal=0;l.updateNeighborsAt(node.getBlockPos(),node.getBlockState().getBlock());}}
                case "vpn"->node.requireVpn=!node.requireVpn;
                case "running"->{server(node);node.running=!node.running;}
                case "inspect"->{server(node);status=queue(p,node,AutomationService.inspect(node,AutomationService.find(l,req.value.strip())));}
                case "machine"->{server(node);int space=req.value.indexOf(' ');if(space<0)throw new IllegalArgumentException("IP ACTION");status=queue(p,node,AutomationService.action(node,req.value.substring(0,space),req.value.substring(space+1)));}
                case "transfer"->{server(node);if(a.length!=4)throw new IllegalArgumentException("SOURCE DESTINATION ITEM COUNT");status=queue(p,node,AutomationService.start(node,a[0],a[1],a[2],Integer.parseInt(a[3])));}
                case "rule"->{server(node);if(a.length!=5||node.rules.size()>=16)throw new IllegalArgumentException("SOURCE DESTINATION ITEM MINIMUM BATCH; максимум 16 правил");Device.ip(a[0]);Device.ip(a[1]);if(!a[2].matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Предмет: namespace:item");node.rules.add(new AutomationEntity.Rule(a[0],a[1],a[2],Math.clamp(Integer.parseInt(a[3]),1,4096),Math.clamp(Integer.parseInt(a[4]),1,dev.netlab.automation.core.ServerHardware.itemLimit(node.device))));status="Правило добавлено";}
                case "clearRules"->{server(node);node.rules.clear();node.running=false;status="Правила очищены";}
                case "claim"->{server(node);if(a.length!=2)throw new IllegalArgumentException("TICKET DESTINATION");status=queue(p,node,AutomationService.claim(node,UUID.fromString(a[0]),a[1],false));}
                case "return"->{server(node);status=queue(p,node,AutomationService.claim(node,UUID.fromString(req.value.strip()),"",true));}
                case "clearCaptures"->{node.captures.clear();node.capturedTicket="";}
                default->throw new IllegalArgumentException("Неизвестное действие");
            }
            node.changed();
        }catch(IllegalArgumentException e){status=e.getMessage()==null?"Некорректное значение":e.getMessage();}
        node.last=status.isBlank()?node.last:status;send(p,node,status,false);
    }
    private static void server(AutomationNode node){if(!node.device.kind.equals("server"))throw new IllegalArgumentException("Нужен сервер управления");}
}
