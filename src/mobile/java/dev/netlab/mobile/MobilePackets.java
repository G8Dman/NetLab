package dev.netlab.mobile;

import com.google.gson.*;
import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

/** Client messages carry intentions and public measurements, never SIM authentication keys. */
public final class MobilePackets {
    private static final Map<UUID,Long> RATE=new HashMap<>();
    public static void clear(){RATE.clear();}
    public record Action(BlockPos pos,int bay,String phone,String action,String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mobile.ID,"action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Action::pos,ByteBufCodecs.VAR_INT,Action::bay,ByteBufCodecs.stringUtf8(64),Action::phone,ByteBufCodecs.stringUtf8(24),Action::action,ByteBufCodecs.stringUtf8(2048),Action::value,Action::new);
        public Type<Action> type(){return TYPE;}
    }
    public record State(BlockPos pos,int bay,String phone,String json,boolean open) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mobile.ID,"state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,State::pos,ByteBufCodecs.VAR_INT,State::bay,ByteBufCodecs.stringUtf8(64),State::phone,ByteBufCodecs.stringUtf8(32768),State::json,ByteBufCodecs.BOOL,State::open,State::new);
        public Type<State> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("1");r.playToServer(Action.TYPE,Action.CODEC,(m,c)->handle((ServerPlayer)c.player(),m));r.playToClient(State.TYPE,State.CODEC,(m,c)->dev.netlab.mobile.client.MobileClient.receive(m));}
    public static void open(ServerPlayer p,DeviceEntity host,int bay){var r=MobileRef.of(host,bay);if(r!=null)send(p,r,"",true);}
    public static void openPhone(ServerPlayer p,ItemStack phone){sendPhone(p,phone,"",true);}
    private static void give(ServerPlayer p,ItemStack item){MobileItemStorage.reconcile(p,item);if(!p.addItem(item))p.drop(item,false);}
    private static ItemStack find(ServerPlayer p,java.util.function.Predicate<ItemStack> test){for(var s:p.getInventory().items){if(s.is(Mobile.SIM.get()))MobileItemStorage.reconcile(p,s);if(test.test(s))return s;}throw new IllegalArgumentException("Нужный предмет отсутствует в инвентаре");}
    private static void simState(JsonObject out,SimCredentials sim){out.addProperty("sim",sim==null?"Нет SIM":sim.label+" / "+sim.operator);out.addProperty("hasSim",sim!=null);}
    private static void reception(ServerPlayer p,JsonObject out,net.minecraft.world.phys.Vec3 at,String key){
        var scan=MobileRadio.scan(p.serverLevel(),at);var rows=new JsonArray();var lease=MobileService.lease(p.serverLevel(),key);MobileRadio.Reception best=scan.isEmpty()?null:scan.getFirst();
        if(lease!=null)for(var r:scan)if(r.cell().antenna().id()==lease.cell.antenna().id())best=r;
        out.addProperty("registration",MobileService.status(p.serverLevel(),key));out.addProperty("bars",best==null?0:best.signal().bars());out.addProperty("rsrp",best==null?-140:best.signal().rsrp());out.addProperty("sinr",best==null?-30:best.signal().sinr());out.addProperty("distance",best==null?0:best.signal().distance());
        for(var r:scan.stream().limit(8).toList()){var row=new JsonObject();row.addProperty("name",r.cell().bbu().device().hostname);row.addProperty("signal",String.format(Locale.ROOT,"%.0f dBm / %.0f m / B%d",r.signal().rsrp(),r.signal().distance(),r.cell().site().band()));row.addProperty("height",r.cell().site().height());rows.add(row);}out.add("cells",rows);
    }
    private static void sendPhone(ServerPlayer p,ItemStack phone,String status,boolean open){var out=new JsonObject();MobileItemStorage.reconcile(p,phone);String id=MobileItems.phoneId(phone);out.addProperty("kind","phone");out.addProperty("name","Cisco Pocket");out.addProperty("status",status);simState(out,MobileItems.sim(phone));PhoneDevices.publicState(p,phone,out);reception(p,out,p.position(),"phone:"+id);PacketDistributor.sendToPlayer(p,new State(BlockPos.ZERO,0,id,out.toString(),open));}
    public static void send(ServerPlayer p,MobileRef r,String status,boolean open){
        if(!r.state().owns(p.getUUID())){p.displayClientMessage(Component.literal("Устройство принадлежит другому игроку"),true);return;}
        var s=r.state();var out=new JsonObject();out.addProperty("kind",r.device().kind);out.addProperty("name",r.device().hostname);out.addProperty("ip",r.ip());out.addProperty("core",s.coreAddress);out.addProperty("status",status);out.addProperty("memory",MemoryCards.status(r.device()));out.addProperty("power",Hardware.powered(r.device()));out.addProperty("paired",!s.stationKey.isEmpty());out.addProperty("subscribers",s.subscribers.entries.size());out.addProperty("radio",s.radioEnabled);out.addProperty("band",s.band);out.addProperty("channel",s.channel);out.addProperty("range",s.range);out.addProperty("bbuReady",MobileHardware.bbuReady(r.device()));simState(out,s.sim);
        if(r.device().kind.equals("pc"))reception(p,out,net.minecraft.world.phys.Vec3.atCenterOf(r.host().getBlockPos()),MobileService.key(r));
        if(r.host() instanceof MobileEntity m){out.addProperty("blank",m.blank.getCount());out.addProperty("output",!m.output.isEmpty());out.addProperty("pending",m.pending!=null);out.addProperty("issuing",m.issuing);if(status.isBlank())out.addProperty("status",m.last);}
        PacketDistributor.sendToPlayer(p,new State(r.host().getBlockPos(),r.bay(),"",out.toString(),open));
    }
    private static void handle(ServerPlayer p,Action req){
        long now=p.serverLevel().getGameTime();if(now-RATE.getOrDefault(p.getUUID(),now-2)<2)return;if(RATE.size()>1024)RATE.clear();RATE.put(p.getUUID(),now);
        if(!req.phone.isEmpty()){for(var item:List.of(p.getMainHandItem(),p.getOffhandItem())){if(item.is(Mobile.PHONE.get()))MobileItemStorage.reconcile(p,item);if(item.is(Mobile.PHONE.get())&&MobileItems.privateTag(item).getString("phoneId").equals(req.phone)){
            String status="";try{if(!req.action.equals("refresh")&&!p.mayBuild())throw new IllegalArgumentException("Нет доступа к изменению телефона");switch(req.action){case "moduleInstall"->PhoneDevices.install(p,item,req.value);case "moduleEject"->PhoneDevices.eject(p,item,req.value);case "ping","packet"->PhoneDevices.diagnose(p,item,req.action,req.value);case "refresh"->MobileService.phone(p,item);case "insert"->{if(MobileItems.sim(item)!=null)throw new IllegalArgumentException("SIM уже установлена");var found=find(p,x->x.is(Mobile.SIM.get())&&MobileItems.sim(x)!=null);MobileItems.setPhoneSim(item,MobileItems.sim(found));found.shrink(1);MobileService.phone(p,item);}case "eject"->{var sim=MobileItems.sim(item);if(sim==null)throw new IllegalArgumentException("Нет SIM");MobileService.forget(p.serverLevel(),"phone:"+req.phone);MobileItems.setPhoneSim(item,null);give(p,MobileItems.sim(sim));}default->throw new IllegalArgumentException("Неизвестное действие");}}catch(RuntimeException e){status=e.getMessage()==null?"Некорректный запрос телефона":e.getMessage();}sendPhone(p,item,status,false);return;}}return;}
        if(req.bay<0||req.bay>1||!p.serverLevel().hasChunkAt(req.pos)||p.distanceToSqr(req.pos.getCenter())>64||!(p.serverLevel().getBlockEntity(req.pos) instanceof DeviceEntity host))return;
        var r=MobileRef.of(host,req.bay);if(r==null||!r.state().owns(p.getUUID())||!(r.device().kind.startsWith("mobile_")||MobileHardware.modem(r.device())))return;
        if(!req.action.equals("refresh")&&!p.mayBuild())return;String status="";
        try{var s=r.state();if(s.owner.isEmpty())s.owner=p.getUUID().toString();
            switch(req.action){
                case "refresh"->{}
                case "hardware"->{FrontPanel.send(p,host,req.bay,"");return;}
                case "connect"->{MobileService.connect(r);status="Регистрация запрошена";}
                case "disconnect"->{MobileService.disconnect(r);status="Модем отключён";}
                case "insert"->{if(!r.device().kind.equals("pc"))throw new IllegalArgumentException("Нужен ПК");if(Hardware.ensure(r.device()).powered)throw new IllegalArgumentException("Сначала выключи ПК в физической панели");if(s.sim!=null)throw new IllegalArgumentException("SIM уже установлена");var found=find(p,x->x.is(Mobile.SIM.get())&&MobileItems.sim(x)!=null);s.sim=MobileItems.sim(found);found.shrink(1);r.changed(s);}
                case "eject"->{if(!r.device().kind.equals("pc"))throw new IllegalArgumentException("Нужен ПК");if(Hardware.ensure(r.device()).powered)throw new IllegalArgumentException("Сначала выключи ПК");if(s.sim==null)throw new IllegalArgumentException("Нет SIM");var sim=s.sim;MobileService.disconnect(r);s=r.state();s.sim=null;r.changed(s);give(p,MobileItems.sim(sim));}
                case "blank"->{var m=station(r);if(!m.blank.isEmpty()||m.pending!=null)throw new IllegalArgumentException("Станок занят");var found=find(p,x->x.is(Mobile.BLANK_SIM.get()));m.blank=found.copyWithCount(1);found.shrink(1);m.changed();}
                case "returnBlank"->{var m=station(r);if(m.blank.isEmpty())throw new IllegalArgumentException("Нет пустой SIM");var stack=m.blank;m.blank=ItemStack.EMPTY;m.changed();give(p,stack);}
                case "take"->{var m=station(r);if(m.output.isEmpty()||m.pending!=null)throw new IllegalArgumentException("Выпуск ещё не завершён");var stack=m.output;m.output=ItemStack.EMPTY;m.changed();give(p,stack);}
                case "pair"->{station(r);MobileProvisioner.pair(r,req.value).whenComplete((v,e)->{if(r.live())send(p,r,e==null?v:MobileService.reason(e),false);});status="Авторизация станка…";}
                case "issue"->{station(r);MobileProvisioner.issue(r,req.value).whenComplete((v,e)->{if(r.live())send(p,r,e==null?v:MobileService.reason(e),false);});status="Выпуск SIM…";}
                default->throw new IllegalArgumentException("Неизвестное действие");
            }
        }catch(IllegalArgumentException e){status=e.getMessage();}send(p,r,status,false);
    }
    private static MobileEntity station(MobileRef r){if(!r.device().kind.equals(MobileHardware.STATION)||!(r.host() instanceof MobileEntity m))throw new IllegalArgumentException("Нужен SIM-станок");return m;}
}
