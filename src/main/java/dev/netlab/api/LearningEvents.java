package dev.netlab.api;

import dev.netlab.*;
import dev.netlab.core.*;
import com.google.gson.Gson;
import net.minecraft.server.level.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Optional, server-only evidence bus. No dependency from the simulation on Academy.
 * Observers must store derived facts only, never credentials, payloads or command text.
 */
public final class LearningEvents {
    public record Event(ServerLevel level,UUID actor,String kind,DeviceEntity host,int bay,Map<String,String> data){}
    public record Command(Event event,Device before,Device after,int beforeMode,int afterMode,boolean wasAuthenticated,
                          boolean authenticated,String input,String canonical,String result,List<Network.Operation> operations,List<Network.Traffic> traffic,Network network,boolean portable,boolean phone,boolean remote){}
    public record Hint(net.minecraft.core.BlockPos pos,String kind,String value){}
    public static final List<java.util.function.BiConsumer<ServerPlayer,Hint>> HINTS=new CopyOnWriteArrayList<>();
    public static final List<Consumer<Event>> EVENTS=new CopyOnWriteArrayList<>();
    public static final List<Consumer<Command>> COMMANDS=new CopyOnWriteArrayList<>();
    private static final Gson JSON=new Gson();
    private static final Map<UUID,long[]> HINT_BUDGET=new HashMap<>();
    public static void hint(ServerPlayer p,Hint hint){long now=p.serverLevel().getGameTime();if(HINT_BUDGET.size()>1024)HINT_BUDGET.keySet().removeIf(id->p.server.getPlayerList().getPlayer(id)==null);var budget=HINT_BUDGET.computeIfAbsent(p.getUUID(),k->new long[]{now,0});if(now<budget[0]||now-budget[0]>=20){budget[0]=now;budget[1]=0;}if(++budget[1]>80)return;for(var h:HINTS)try{h.accept(p,hint);}catch(RuntimeException error){com.mojang.logging.LogUtils.getLogger().warn("Invalid learning observation",error);}}

    public static void emit(ServerPlayer p,String kind,DeviceEntity host,int bay,Object... pairs){if(p!=null)emit(p.serverLevel(),p.getUUID(),kind,host,bay,pairs);}
    public static void emit(ServerLevel level,UUID actor,String kind,DeviceEntity host,int bay,Object... pairs){
        if(EVENTS.isEmpty()||actor==null||level==null)return;var data=new LinkedHashMap<String,String>();for(int i=0;i+1<pairs.length;i+=2){String key=String.valueOf(pairs[i]),value=String.valueOf(pairs[i+1]);if(value.length()<=512)data.put(key,value);}
        var event=new Event(level,actor,kind,host,bay,Map.copyOf(data));for(var listener:EVENTS)try{listener.accept(event);}catch(RuntimeException error){com.mojang.logging.LogUtils.getLogger().error("Optional learning observer failed: {}",kind,error);}
    }
    public static void owner(ServerLevel level,UUID actor,String kind,DeviceEntity host,int bay,Object... pairs){emit(level,actor,kind,host,bay,pairs);}
    public static Ticket command(ServerPlayer player,ConsoleAccess.Target target,Network network,String line,boolean portable,boolean phone){return COMMANDS.isEmpty()?null:new Ticket(player,target,network,line,portable,phone);}
    public static void finish(Ticket ticket,String result,Network n){if(ticket!=null)ticket.finish(result,n);}
    public static final class Ticket {
        private final ServerPlayer player;private final ConsoleAccess.Target target;private final Device before;private final int mode;private final boolean authenticated,portable,phone;private final String input,canonical;private final List<Network.Operation> operations=new ArrayList<>();private final Consumer<Network.Operation> previous;
        private Ticket(ServerPlayer player,ConsoleAccess.Target target,Network n,String line,boolean portable,boolean phone){this.player=player;this.target=target;this.portable=portable;this.phone=phone;mode=target.cli().mode;authenticated=target.cli().authenticated&&target.cli().pending.isBlank();input=authenticated&&!Console.sensitive(line)?line:"";String value=input;try{value=CommandGrammar.resolve(target.cli(),target.device(),input).value();}catch(RuntimeException ignored){}canonical=value;long remote=target.cli().remoteId;Device d=remote!=Long.MIN_VALUE?n.devices.getOrDefault(remote,target.device()):target.device();before=JSON.fromJson(JSON.toJson(d),Device.class);previous=n.observer;n.observer=op->{operations.add(op);if(previous!=null)previous.accept(op);};}
        private void finish(String result,Network n){n.observer=previous;long remote=target.cli().remoteId;Device d=remote!=Long.MIN_VALUE?n.devices.getOrDefault(remote,target.device()):target.device();Device after=JSON.fromJson(JSON.toJson(d),Device.class);var ops=List.copyOf(operations);var traffic=List.copyOf(n.traffic);int bay=target.id()==target.entity().slotId(1)?1:0;var event=new Event(player.serverLevel(),player.getUUID(),"command",target.entity(),bay,Map.of());int afterMode=target.cli().mode;boolean auth=target.cli().authenticated&&target.cli().pending.isBlank();var guard=Simulation.guard(player.serverLevel(),n);
            Simulation.after(player.serverLevel(),Math.max(n.cursor,Simulation.now(player.serverLevel())),()->{if(guard.getAsBoolean()){var command=new Command(event,before,after,mode,afterMode,authenticated,auth,input,canonical,result==null?"":result,ops,traffic,n,portable,phone,remote!=Long.MIN_VALUE);for(var listener:COMMANDS)try{listener.accept(command);}catch(RuntimeException error){com.mojang.logging.LogUtils.getLogger().error("Optional learning command observer failed",error);}}return true;});
        }
    }
    private LearningEvents(){}
}
