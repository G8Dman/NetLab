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
    public static void flush(ConsoleAccess.Target target){
        int slot=target.id()==target.entity().slotId(1)?1:0;
        if(target.entity().isRemoved()||target.entity().portDevice(slot*128)!=target.device())return;
        var handler=HANDLERS.get(target.device().kind);if(handler!=null)handler.flush(target);
        MemoryCards.sync(target.device());target.entity().changed();
    }
}
