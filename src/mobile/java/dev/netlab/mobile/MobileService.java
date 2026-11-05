package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.mobile.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

/** Live registration is separate from persistent SIM/configuration and is re-established after restart. */
public final class MobileService {
    public static final class Lease {
        public String client,simId,ip,apn,status="";public MobileRef ue,core;public MobileRadio.Cell cell;public RadioModel.Signal signal;public long expires;
        public CryptoChannel upTx,upRx,downTx,downRx;
        public long ueEpoch,coreEpoch,bbuEpoch,rruEpoch;
    }
    private static final Map<ServerLevel,Map<String,Lease>> LEASES=new WeakHashMap<>();
    private static final Map<ServerLevel,Set<String>> PENDING=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<String,String>> STATUS=new WeakHashMap<>();
    static Map<String,Lease> leases(ServerLevel level){return LEASES.computeIfAbsent(level,l->new HashMap<>());}
    public static void clear(){LEASES.clear();PENDING.clear();STATUS.clear();}
    public static void forget(ServerLevel level,String client){leases(level).remove(client);}
    public static String reason(Throwable error){while(error.getCause()!=null)error=error.getCause();return error.getMessage()==null?"Ошибка регистрации":error.getMessage();}
    public static String key(MobileRef pc){return "pc:"+pc.device().identity;}
    public static Lease lease(ServerLevel l,String key){var lease=leases(l).get(key);return lease!=null&&valid(l,lease)?lease:null;}
    public static String status(ServerLevel l,String key){var lease=lease(l,key);return lease==null?STATUS.computeIfAbsent(l,x->new HashMap<>()).getOrDefault(key,"Нет регистрации"):"В сети: "+lease.ip;}
    private static void status(ServerLevel l,String key,String text){var map=STATUS.computeIfAbsent(l,x->new HashMap<>());if(map.size()>2048)map.clear();map.put(key,text);}
    public static boolean valid(ServerLevel l,Lease lease){
        if(leases(l).get(lease.client)!=lease||l.getGameTime()>lease.expires||!lease.core.live()||!Hardware.powered(lease.core.device())||!MobileRadio.live(lease.cell))return false;
        if(Hardware.ensure(lease.core.device()).epoch!=lease.coreEpoch||Hardware.ensure(lease.cell.bbu().device()).epoch!=lease.bbuEpoch||Hardware.ensure(lease.cell.rru().device()).epoch!=lease.rruEpoch||lease.ue!=null&&Hardware.ensure(lease.ue.device()).epoch!=lease.ueEpoch)return false;
        var s=lease.core.state();var sub=s.subscribers.entries.get(lease.simId);if(sub==null||!sub.active||!s.apn.equals(lease.apn))return false;
        if(lease.ue!=null){var u=lease.ue.state();return lease.ue.live()&&Hardware.powered(lease.ue.device())&&MobileHardware.modem(lease.ue.device())&&u.clientEnabled&&u.sim!=null&&u.sim.id.equals(lease.simId)&&u.sim.key.equals(sub.key);}
        return true;
    }
    public static void disconnect(MobileRef pc){
        leases(pc.level()).remove(key(pc));var state=pc.state();state.clientEnabled=false;
        int port=MobileHardware.cellular(pc.device());if(port>=0){pc.device().ports.get(port).ip="";pc.device().ports.get(port).up=false;}
        if(pc.device().preferredPort==port){pc.device().preferredPort=state.wiredPort;pc.device().gateway=state.wiredGateway;}if(pc.device().wireless!=null)pc.device().wireless.clientSsid=state.wifiSsid;pc.changed(state);
    }
    public static void connect(MobileRef pc){
        if(!MobileHardware.modem(pc.device())||!Hardware.powered(pc.device()))throw new IllegalArgumentException("Установи модем и включи ПК");var state=pc.state();if(state.sim==null)throw new IllegalArgumentException("Вставь активированную SIM");
        if(!state.clientEnabled){state.wiredGateway=pc.device().gateway;state.wiredPort=pc.device().preferredPort;state.wifiSsid=pc.device().wireless==null?"":pc.device().wireless.clientSsid;}state.clientEnabled=true;
        if(pc.device().wireless!=null){pc.device().wireless.clientSsid="";pc.device().wireless.associated=Long.MIN_VALUE;}
        pc.changed(state);refresh(pc.level(),key(pc),pc,Vec3.atCenterOf(pc.host().getBlockPos()),state.sim,MobileRadio.cells(pc.level()));
    }
    public static void phone(ServerPlayer player,net.minecraft.world.item.ItemStack phone){var sim=MobileItems.sim(phone);if(sim==null)return;refresh(player.serverLevel(),"phone:"+MobileItems.phoneId(phone),null,player.position(),sim,MobileRadio.cells(player.serverLevel()));}
    private static void refresh(ServerLevel level,String key,MobileRef ue,Vec3 position,SimCredentials sim,List<MobileRadio.Cell> cells){
        if(PENDING.computeIfAbsent(level,l->new HashSet<>()).contains(key))return;
        MobileRadio.Reception chosen=null;MobileRef core=null;
        for(var r:MobileRadio.scan(level,position,cells))try{if(r.signal().bars()==0)continue;var candidate=MobileRef.core(level,r.cell().bbu().state().coreAddress);if(candidate.device().identity.equals(sim.issuer)&&candidate.state().plmn.equals(sim.operator)){chosen=r;core=candidate;break;}}catch(IllegalArgumentException ignored){}
        if(chosen==null){leases(level).remove(key);status(level,key,"Нет подходящей базовой станции");return;}
        var old=lease(level,key);if(old!=null&&old.simId.equals(sim.id)&&old.cell.antenna().id()==chosen.cell().antenna().id()){old.signal=chosen.signal();old.expires=level.getGameTime()+80;return;}
        // Hysteresis prevents alternating cells of nearly equal strength.
        if(old!=null&&chosen.signal().rsrp()<old.signal.rsrp()+3){old.expires=level.getGameTime()+80;return;}
        var selected=chosen;var epc=core;String nonce=MobileAuth.secret(),proof=MobileAuth.proof(sim.key,nonce,"attach|"+sim.id);
        PENDING.get(level).add(key);status(level,key,"Регистрация SIM…");
        var radio=Simulation.after(level,Simulation.now(level)+20_000_000L,()->{if(!MobileRadio.live(selected.cell()))throw new IllegalArgumentException("Радиотракт отключён");return true;});
        radio.thenCompose(v->transport(selected.cell().bbu(),epc,"LTE-ATTACH "+sim.id+" "+nonce+" "+proof)).thenCompose(v->{
            var c=epc.state();if(!Hardware.powered(epc.device())||!sim.apn.equals(c.apn)||!c.subscribers.authenticate(sim,epc.device().identity,c.plmn,nonce,proof))throw new IllegalArgumentException("SIM не активирована, отозвана либо APN изменён");
            for(var other:leases(level).values())if(!other.client.equals(key)&&other.simId.equals(sim.id)&&valid(level,other))throw new IllegalArgumentException("SIM уже используется другим устройством");
            return transport(epc,selected.cell().bbu(),"LTE-ATTACH-ACCEPT "+sim.id);
        }).thenAccept(v->{
            if(!epc.live()||!MobileRadio.live(selected.cell()))throw new IllegalArgumentException("Сеть изменилась во время регистрации");
            if(ue!=null&&(!ue.live()||!ue.state().clientEnabled||ue.state().sim==null||!ue.state().sim.id.equals(sim.id)))throw new IllegalArgumentException("Абонент отключён");
            // Recheck at commit: simultaneous attach requests cannot duplicate a SIM lease.
            for(var other:leases(level).values())if(!other.client.equals(key)&&other.simId.equals(sim.id)&&valid(level,other))throw new IllegalArgumentException("SIM уже используется");
            var c=epc.state();var sub=c.subscribers.entries.get(sim.id);if(sub==null||!sub.active)throw new IllegalArgumentException("SIM отозвана");
            var lease=new Lease();lease.client=key;lease.simId=sim.id;lease.apn=sim.apn;lease.ip=c.subscribers.address(sub,c.subscriberGateway);lease.core=epc;lease.ue=ue;lease.cell=selected.cell();lease.signal=selected.signal();lease.expires=level.getGameTime()+80;epc.changed(c);leases(level).put(key,lease);
            byte[] ul=CryptoChannel.derive(sim.key,(nonce+"UL").getBytes(StandardCharsets.UTF_8)),dl=CryptoChannel.derive(sim.key,(nonce+"DL").getBytes(StandardCharsets.UTF_8));lease.upTx=new CryptoChannel(ul);lease.upRx=new CryptoChannel(ul);lease.downTx=new CryptoChannel(dl);lease.downRx=new CryptoChannel(dl);
            lease.coreEpoch=Hardware.ensure(epc.device()).epoch;lease.bbuEpoch=Hardware.ensure(lease.cell.bbu().device()).epoch;lease.rruEpoch=Hardware.ensure(lease.cell.rru().device()).epoch;lease.ueEpoch=ue==null?0:Hardware.ensure(ue.device()).epoch;
            if(ue!=null){int port=MobileHardware.cellular(ue.device());var p=ue.device().ports.get(port);p.up=true;p.ip=lease.ip;p.mask="255.255.255.0";ue.device().preferredPort=port;ue.device().gateway=c.subscriberGateway;ue.host().changed();}
        }).whenComplete((v,e)->{PENDING.get(level).remove(key);if(e!=null){leases(level).remove(key);status(level,key,reason(e));}});
    }
    private static CompletableFuture<Void> transport(MobileRef from,MobileRef to,String text){return ApplicationTransport.sendAsync(from.level(),new ApplicationTransport.Endpoint(from.host(),from.bay()),new ApplicationTransport.Endpoint(to.host(),to.bay()),text.getBytes(StandardCharsets.UTF_8),false).thenApply(r->{if(!r.success())throw new IllegalArgumentException(r.status());return null;});}
    public static void topology(Level level,Network n){if(!(level instanceof ServerLevel l))return;
        for(var lease:new ArrayList<>(leases(l).values()))if(lease.ue!=null&&valid(l,lease)&&n.devices.containsKey(lease.ue.id())&&n.devices.containsKey(lease.core.id())){
            int up=MobileHardware.cellular(lease.ue.device()),cp=MobileHardware.cellular(lease.core.device());var p=lease.core.device().ports.get(cp);p.up=true;p.ip=lease.core.state().subscriberGateway;p.mask="255.255.255.0";
            n.wires.add(new Network.Wire(new Network.Endpoint(lease.ue.id(),up),new Network.Endpoint(lease.core.id(),cp),lease.signal.distance(),"cellular"));
        }n.linkTransits.put("cellular",(network,wire,from,ready,bytes,protocol)->MobileTransit.send(l,network,wire,from,ready,bytes,protocol));
    }
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(event.getServer().getTickCount()%20!=0)return;for(var l:event.getServer().getAllLevels()){
        leases(l).entrySet().removeIf(e->!valid(l,e.getValue()));var cells=MobileRadio.cells(l);
        for(var ref:MobileRef.loaded(l))if(ref.device().kind.equals("pc")&&Hardware.powered(ref.device())&&MobileHardware.modem(ref.device())){var s=ref.state();if(s.clientEnabled&&s.sim!=null)refresh(l,key(ref),ref,Vec3.atCenterOf(ref.host().getBlockPos()),s.sim,cells);}
        for(var p:l.players())for(var stack:List.of(p.getMainHandItem(),p.getOffhandItem()))if(stack.is(Mobile.PHONE.get())&&MobileItems.sim(stack)!=null)refresh(l,"phone:"+MobileItems.phoneId(stack),null,p.position(),MobileItems.sim(stack),cells);
    }}
}
