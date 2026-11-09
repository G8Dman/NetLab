package dev.netlab.media.core;
import java.util.*;

/** A terminal can participate in one call. Signalling completion drives these transitions. */
public final class CallBook {
    public enum State {RINGING,ACTIVE}
    public record Call(UUID id,String caller,String callee,State state,long deadline) {public String other(String endpoint){if(endpoint.equals(caller))return callee;if(endpoint.equals(callee))return caller;throw new IllegalArgumentException("Не участник звонка");}}
    private final Map<UUID,Call> calls=new LinkedHashMap<>();
    public Call find(String endpoint){return calls.values().stream().filter(c->c.caller.equals(endpoint)||c.callee.equals(endpoint)).findFirst().orElse(null);}
    public Call invite(String caller,String callee,long now){if(caller.equals(callee))throw new IllegalArgumentException("Нельзя звонить самому себе");if(find(caller)!=null||find(callee)!=null)throw new IllegalArgumentException("Абонент занят");if(calls.size()>=32)throw new IllegalArgumentException("АТС занята");var c=new Call(UUID.randomUUID(),caller,callee,State.RINGING,now+600);calls.put(c.id,c);return c;}
    public Call answer(String endpoint,long now){var c=find(endpoint);if(c==null||!c.callee.equals(endpoint)||c.state!=State.RINGING||now>=c.deadline)throw new IllegalArgumentException("Нет входящего звонка");var accepted=new Call(c.id,c.caller,c.callee,State.ACTIVE,Long.MAX_VALUE);calls.put(c.id,accepted);return accepted;}
    public void hangup(String endpoint){var c=find(endpoint);if(c!=null)calls.remove(c.id);}
    public List<Call> all(){return List.copyOf(calls.values());}
    public void expire(long now){calls.values().removeIf(c->c.state==State.RINGING&&now>=c.deadline);}
    public void clear(){calls.clear();}
}
