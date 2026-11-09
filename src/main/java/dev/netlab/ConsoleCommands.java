package dev.netlab;

import dev.netlab.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Console input remains on the server thread; network replies are completed by the packet clock. */
public final class ConsoleCommands {
    private static final Map<DeviceEntity,Set<UUID>> PC_BUSY=new WeakHashMap<>();
    private static String prompt(ConsoleAccess.Target t){return t.device().kind.equals("traffic")?t.cli().pending.isBlank()?TrafficControllers.prompt(t.cli()):"Password:":t.cli().prompt(t.device());}
    private static String limit(String text){return text.length()>145000?text.substring(0,145000)+"\n% Output truncated":text;}
    public static void handle(ServerPlayer p,Packets.Command msg){
        if(PortableTerminals.handle(p,msg))return;
        var l=p.serverLevel();if(msg.command().length()>256||p.distanceToSqr(msg.pos().getX()+.5,msg.pos().getY()+.5,msg.pos().getZ()+.5)>64||!l.hasChunkAt(msg.pos())||!(l.getBlockEntity(msg.pos()) instanceof DeviceEntity pc)||!pc.device.kind.equals("pc"))return;
        if(msg.command().equals("__terminal")){
            var check=ConsoleAccess.inspect(pc,p.getUUID(),true);var target=check.target();if(target==null){TerminalHistory.unavailable(p,pc,check.problem());return;}
            var store=TerminalHistory.get(l);TerminalHistory.Entry e;try{e=store.entry(pc,target,p.getUUID());}catch(IllegalArgumentException error){TerminalHistory.error(p,pc,error.getMessage(),true);return;}var network=pc.network();
            var extension=dev.netlab.api.ConsoleExtensions.HANDLERS.get(target.device().kind);
            String banner=extension!=null?extension.open(new dev.netlab.api.ConsoleExtensions.Context(p,target,network)):target.device().kind.equals("traffic")?TrafficControllers.open(l,network,target.id(),target.cli()):target.cli().open(target.device())+"\n"+prompt(target);
            if(e.attachSession(target.cli().sessionId+":"+target.cli().loginEpoch))e.busy=false;
            dev.netlab.api.LearningEvents.emit(p,"console.open",target.entity(),target.id()==target.entity().slotId(1)?1:0);
            if(!e.busy)store.append(e,"Terminal: 9600 8N1\n"+banner);TerminalHistory.send(p,pc,e,"",true,true,true);return;
        }
        if(!Hardware.powered(pc.device)&&msg.token().isEmpty()){PacketDistributor.sendToPlayer(p,new Packets.Output(msg.pos(),"ПК выключен. Включи питание.",false));return;}
        if(!msg.token().isEmpty()){
            var check=ConsoleAccess.inspect(pc,p.getUUID(),false);var target=check.target();
            if(target==null){PacketDistributor.sendToPlayer(p,new TerminalPackets.Data(msg.pos(),msg.token(),check.problem(),"[]",false,false,false,false));return;}
            var store=TerminalHistory.get(l);var e=store.entry(pc,target,p.getUUID());if(!e.token.equals(msg.token()))return;
            if(e.busy){TerminalHistory.send(p,pc,e,"",false,false,true);return;}
            long request=e.beginRequest();
            String before=prompt(target),echo=target.cli().echo(msg.command());boolean secret=!echo.equals(msg.command());
            store.command(e,msg.command(),secret);var network=pc.network();ConsoleAccess.traffic(pc,target);
            var extension=dev.netlab.api.ConsoleExtensions.HANDLERS.get(target.device().kind);
            var learning=dev.netlab.api.LearningEvents.command(p,target,network,msg.command(),false,false);
            String memoryResult=MemoryCards.command(p,target,msg.command());
            if(memoryResult!=null){dev.netlab.api.LearningEvents.finish(learning,memoryResult,network);target.entity().changed();String reply=before+" "+msg.command()+"\n"+memoryResult+"\n"+prompt(target);store.append(e,reply);TerminalHistory.send(p,pc,e,reply,false,false,true);return;}
            if(extension!=null){
                String prefix=before+" "+echo;e.busy=true;store.append(e,prefix);TerminalHistory.send(p,pc,e,prefix,false,false,true);
                java.util.concurrent.CompletableFuture<String> future;
                try{future=extension.execute(new dev.netlab.api.ConsoleExtensions.Context(p,target,network),msg.command());}
                catch(RuntimeException error){future=java.util.concurrent.CompletableFuture.completedFuture("% "+error.getMessage());}
                var wait=future.thenApply(text->{dev.netlab.api.LearningEvents.finish(learning,text,network);return text;}).thenCompose(text->Simulation.after(l,Math.max(network.cursor,Simulation.now(l)),()->text));
                Study.publish(network,l);WirelessWorld.changed(l,network);
                wait.whenComplete((text,error)->{
                    var current=ConsoleAccess.inspect(pc,p.getUUID(),false);
                    if(!store.current(e)||!e.currentRequest(request))return;e.busy=false;
                    String reply=current.target()==null||current.target().cli()!=target.cli()?"Консольное соединение изменилось.":error==null?text:"% "+error.getMessage();
                    reply=limit(reply)+"\n"+prompt(target);store.append(e,reply);TerminalHistory.send(p,pc,e,reply,false,false,current.target()!=null);
                });return;
            }
            String result=target.device().kind.equals("traffic")?TrafficControllers.execute(l,network,target.id(),target.cli(),p.getUUID(),msg.command()):target.cli().execute(target.device(),network,target.id(),msg.command());
            dev.netlab.api.LearningEvents.finish(learning,result,network);
            String output=limit(result)+(target.device().kind.equals("traffic")?"":"\n"+prompt(target));var guard=Simulation.guard(l,network);Study.publish(network,l);WirelessWorld.changed(l,network);target.entity().changed();pc.changed();
            String prefix=before+" "+echo;
            if(network.cursor>Simulation.now(l)){
                e.busy=true;store.append(e,prefix);TerminalHistory.send(p,pc,e,prefix,false,false,true);
                Simulation.after(l,network.cursor,()->{
                    var current=ConsoleAccess.inspect(pc,p.getUUID(),false);if(current.target()==null||current.target().cli()!=target.cli())return current.problem().isBlank()?"Сессия изменилась.":current.problem();return guard.getAsBoolean()?output:"Передача прервана: кабель, питание или устройство изменились в пути.\n"+prompt(target);
                }).whenComplete((text,error)->{
                    var current=ConsoleAccess.inspect(pc,p.getUUID(),false);if(!store.current(e)||!e.currentRequest(request))return;
                    e.busy=false;String reply=error==null?text:"% "+error.getMessage();store.append(e,reply);TerminalHistory.send(p,pc,e,reply,false,false,current.target()!=null);
                });
            }else{String reply=prefix+"\n"+output;store.append(e,reply);TerminalHistory.send(p,pc,e,reply,false,false,true);}return;
        }
        if(ConsoleAccess.active(pc,p.getUUID())){PacketDistributor.sendToPlayer(p,new Packets.Output(msg.pos(),"Открой Terminal из Desktop, чтобы обновить сессию.",false));return;}
        var busy=PC_BUSY.computeIfAbsent(pc,k->new HashSet<>());if(!busy.add(p.getUUID()))return;
        var cli=pc.session(p.getUUID());var n=pc.network();String before=cli.prompt(pc.device),echo=cli.echo(msg.command());String result;
        var learning=dev.netlab.api.LearningEvents.command(p,new ConsoleAccess.Target(pc,pc.device,pc.slotId(0),cli),n,msg.command(),false,false);
        try{result=cli.execute(pc.device,n,pc.slotId(0),msg.command());n.refreshServices();}catch(RuntimeException e){busy.remove(p.getUUID());throw e;}
        dev.netlab.api.LearningEvents.finish(learning,result,n);
        String output=before+" "+echo+"\n"+limit(result)+"\n"+cli.prompt(pc.device);var guard=Simulation.guard(l,n);Study.publish(n,l);WirelessWorld.changed(l,n);pc.changed();
        Simulation.after(l,n.cursor,()->guard.getAsBoolean()?output:"Передача прервана: соединение изменилось в пути.\n"+cli.prompt(pc.device)).whenComplete((text,error)->{busy.remove(p.getUUID());if(!pc.isRemoved()&&l.getBlockEntity(msg.pos())==pc)PacketDistributor.sendToPlayer(p,new Packets.Output(msg.pos(),error==null?text:"% "+error.getMessage(),false));});
    }
}
