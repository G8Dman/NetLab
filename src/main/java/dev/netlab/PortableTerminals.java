package dev.netlab;

import dev.netlab.core.*;
import dev.netlab.api.*;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Ephemeral authentication; an optional physical carrier can retain a bounded transcript. */
public final class PortableTerminals {
    public static final List<java.util.function.Predicate<net.minecraft.world.item.ItemStack>> CARRIER_ITEMS=new java.util.concurrent.CopyOnWriteArrayList<>();
    public interface Carrier {
        boolean held(ServerPlayer player);
        ConsoleTranscript open(String deviceIdentity);
        void save();
    }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final class Session {
        final String token;final ServerLevel level;final ConsoleAccess.Target target;final PortableLease lease;final Carrier carrier;final ConsoleTranscript transcript;boolean busy;
        Session(ServerLevel level,ConsoleAccess.Target target,Carrier carrier){this.level=level;this.target=target;this.carrier=carrier;transcript=carrier==null?null:carrier.open(target.device().identity);lease=new PortableLease(target.device(),level.getGameTime(),target.cli());token=lease.token;}
    }
    private static boolean held(ServerPlayer p){return p.getMainHandItem().is(NetLab.PORTABLE_TERMINAL.get())||p.getOffhandItem().is(NetLab.PORTABLE_TERMINAL.get());}
    private static boolean valid(ServerPlayer p,Session s){var t=s.target;var e=t.entity();int bay=t.id()==e.slotId(1)?1:0;return s.lease.valid(t.device(),s.carrier==null?held(p):s.carrier.held(p),p.distanceToSqr(e.getBlockPos().getCenter()),s.level.getGameTime())&&p.serverLevel()==s.level&&!e.isRemoved()&&s.level.hasChunkAt(e.getBlockPos())&&s.level.getBlockEntity(e.getBlockPos())==e&&e.portDevice(bay*128)==t.device()&&p.distanceToSqr(e.getBlockPos().getCenter())<=64&&Hardware.powered(t.device())&&RackDevices.allowed(p,e,bay);}
    private static String prompt(Session s){var t=s.target;return t.device().kind.equals("traffic")?t.cli().pending.isBlank()?TrafficControllers.prompt(t.cli()):"Password:":t.cli().prompt(t.device());}
    private static void send(ServerPlayer p,Session s,String text,boolean open,boolean connected){if(text.length()>145000)text=text.substring(0,145000);String history="[]";if(s.transcript!=null){s.transcript.appendText(text);PhoneTerminalMemory.trim(s.transcript);s.carrier.save();history=new com.google.gson.Gson().toJson(s.transcript.history);if(open)text=s.transcript.text;}PacketDistributor.sendToPlayer(p,new TerminalPackets.Data(s.target.entity().getBlockPos(),s.token,text,history,open,open,s.busy,connected));}
    private static void close(UUID player){var s=SESSIONS.remove(player);if(s!=null){s.lease.close();s.target.device().os().activeUsers.remove(s.target.cli().sessionId);s.target.device().os().sessionTimes.remove(s.target.cli().sessionId);if(!s.target.entity().isRemoved()&&s.level.hasChunkAt(s.target.entity().getBlockPos()))ConsoleExtensions.flush(s.target);}}
    public static void open(ServerPlayer p,DeviceEntity e,int port){
        open(p,e,port,null);
    }
    public static void open(ServerPlayer p,DeviceEntity e,int port,Carrier carrier){
        if(carrier!=null&&!carrier.held(p))return;
        if(port<0){p.displayClientMessage(Component.literal("Нажми порт Console на передней панели."),true);return;}
        var d=e.portDevice(port);if(d==null||port%128>=d.ports.size()||!d.ports.get(port%128).console||d.kind.equals("pc")){p.displayClientMessage(Component.literal("Нужен Console устройства, а не Ethernet или RS232 компьютера."),true);return;}
        if(!Hardware.powered(d)){p.displayClientMessage(Component.literal("Устройство не работает. "+Hardware.powerProblem(d)),true);return;}
        if(!RackDevices.allowed(p,e,port/128)){p.displayClientMessage(Component.literal("Устройство принадлежит другому игроку"),true);return;}
        close(p.getUUID());var cli=new dev.netlab.core.Console();var t=new ConsoleAccess.Target(e,d,e.slotId(port/128),cli);var s=new Session(p.serverLevel(),t,carrier);SESSIONS.put(p.getUUID(),s);var n=e.network();var extension=ConsoleExtensions.HANDLERS.get(d.kind);
        String banner=extension!=null?extension.open(new ConsoleExtensions.Context(p,t,n)):d.kind.equals("traffic")?TrafficControllers.open(p.serverLevel(),n,t.id(),cli):cli.open(d)+"\n"+prompt(s);
        LearningEvents.emit(p,"console.open",e,port/128,"portable",carrier==null,"phone",carrier!=null);
        send(p,s,(carrier==null?"Портативный терминал. Настройки остаются на устройстве; история после закрытия очищается.":"Терминал Cisco Pocket. История трёх последних устройств; авторизация каждый раз заново.")+"\n"+MemoryCards.status(d)+"\n"+banner,true,true);
    }
    public static boolean handle(ServerPlayer p,Packets.Command m){
        if(!m.token().startsWith("p:"))return false;var s=SESSIONS.get(p.getUUID());if(s==null||!s.token.equals(m.token())||!s.target.entity().getBlockPos().equals(m.pos()))return true;
        if(m.command().equals("__portable_close")){close(p.getUUID());return true;}
        if(!valid(p,s)){send(p,s,"Портативная сессия закрыта: питание, устройство или доступ изменились.",false,false);close(p.getUUID());return true;}
        if(m.command().length()>256||s.busy)return true;s.lease.touch(s.level.getGameTime());s.busy=true;var t=s.target;var n=t.entity().network();if(s.transcript!=null)s.transcript.remember(m.command(),!t.cli().pending.isEmpty()||dev.netlab.core.Console.sensitive(m.command()));String prefix=prompt(s)+" "+t.cli().echo(m.command());send(p,s,prefix,false,true);
        var learning=LearningEvents.command(p,t,n,m.command(),true,s.carrier!=null);
        CompletableFuture<String> future;try{var extension=ConsoleExtensions.HANDLERS.get(t.device().kind);String memory=MemoryCards.command(p,t,m.command());future=memory!=null?CompletableFuture.completedFuture(memory):extension!=null?extension.execute(new ConsoleExtensions.Context(p,t,n),m.command()):CompletableFuture.completedFuture(t.device().kind.equals("traffic")?TrafficControllers.execute(s.level,n,t.id(),t.cli(),p.getUUID(),m.command()):t.cli().execute(t.device(),n,t.id(),m.command()));}catch(RuntimeException error){future=CompletableFuture.completedFuture("% "+error.getMessage());}
        t.cli().history.clear();var guard=Simulation.guard(s.level,n);Study.publish(n,s.level);WirelessWorld.changed(s.level,n);ConsoleExtensions.flush(t);
        future.thenApply(text->{LearningEvents.finish(learning,text,n);return text;}).thenCompose(text->Simulation.after(s.level,Math.max(n.cursor,Simulation.now(s.level)),()->text)).whenComplete((text,error)->{
            ConsoleExtensions.flush(t);if(SESSIONS.get(p.getUUID())!=s)return;s.busy=false;
            if(!valid(p,s)){send(p,s,"Портативная сессия завершена.",false,false);close(p.getUUID());return;}
            String reply=error!=null?"% "+error.getMessage():guard.getAsBoolean()?text:"Передача прервана изменением сети.";send(p,s,reply+"\n"+prompt(s),false,true);
        });return true;
    }
    public static boolean complete(ServerPlayer p,Packets.Complete m){var s=SESSIONS.get(p.getUUID());if(s==null||!s.target.entity().getBlockPos().equals(m.pos())||!valid(p,s))return false;s.lease.touch(s.level.getGameTime());var t=s.target;var result=ConsoleExtensions.complete(t,m.prefix());if(!result.value().equals(m.prefix()))LearningEvents.emit(p,"console.tab",t.entity(),t.id()==t.entity().slotId(1)?1:0,"value",result.value());PacketDistributor.sendToPlayer(p,new Packets.Completed(m.pos(),m.prefix(),result.value(),String.join("\n",result.choices())));return true;}
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(event.getServer().getTickCount()%20!=0)return;for(var id:new ArrayList<>(SESSIONS.keySet())){var p=event.getServer().getPlayerList().getPlayer(id);var s=SESSIONS.get(id);if(p==null||!valid(p,s)){if(p!=null)send(p,s,"Портативный терминал отключён.",false,false);close(id);}}}
    public static void clear(net.neoforged.neoforge.event.server.ServerStoppedEvent event){for(var id:new ArrayList<>(SESSIONS.keySet()))close(id);}
}
