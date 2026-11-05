package dev.netlab.api;

import dev.netlab.*;
import dev.netlab.core.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ConsoleExtensions {
    public record Context(ServerPlayer player,ConsoleAccess.Target target,Network network){}
    public interface Handler {
        String open(Context context);
        CompletableFuture<String> execute(Context context,String line);
        /** Persist derived addon state before the transport session is discarded. */
        default void flush(ConsoleAccess.Target target){}
        default CommandGrammar.Completion complete(String prefix){return new CommandGrammar.Completion(prefix,List.of());}
    }
    public static final Map<String,Handler> HANDLERS=new java.util.concurrent.ConcurrentHashMap<>();
    /** Addon vocabulary falls back to the shared IOS grammar, including save commands. */
    public static CommandGrammar.Completion complete(ConsoleAccess.Target target,String prefix){
        if(!target.cli().pending.isEmpty()||!target.cli().authenticated)return new CommandGrammar.Completion(prefix,List.of());
        var memory=MemoryCards.complete(prefix);if(memory!=null)return memory;
        var handler=HANDLERS.get(target.device().kind);
        var custom=handler!=null?handler.complete(prefix):target.device().kind.equals("traffic")?TrafficControllers.complete(target.cli(),prefix):null;
        if(custom!=null&&!custom.choices().isEmpty())return custom;
        if(target.device().kind.equals("traffic")){var cli=new dev.netlab.core.Console();cli.mode=1;return cli.complete(target.device(),prefix);}
        return target.cli().complete(target.device(),prefix);
    }
    public static void flush(ConsoleAccess.Target target){
        int slot=target.id()==target.entity().slotId(1)?1:0;
        if(target.entity().isRemoved()||target.entity().portDevice(slot*128)!=target.device())return;
        var handler=HANDLERS.get(target.device().kind);if(handler!=null)handler.flush(target);
        MemoryCards.sync(target.device());target.entity().changed();
    }
}
