package dev.netlab.automation;
import dev.netlab.api.LearningEvents;
import dev.netlab.core.*;
import dev.netlab.automation.core.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.Supplier;
/** Optional observer metadata travels with escrow, never changes transfer authorization. */
public final class AutomationLearning {
    public record Origin(UUID actor,boolean gui,boolean phone,boolean rule,boolean any,UUID batch){}
    private static final ThreadLocal<Origin> CURRENT=new ThreadLocal<>();
    public static Origin origin(AutomationNode s){var c=CURRENT.get();return c!=null?c:new Origin(s.owner,false,false,false,false,UUID.randomUUID());}
    public static <T>T scoped(Origin origin,Supplier<T> fn){var old=CURRENT.get();CURRENT.set(origin);try{return fn.get();}finally{if(old==null)CURRENT.remove();else CURRENT.set(old);}}
    public static <T>T gui(net.minecraft.server.level.ServerPlayer p,boolean phone,Supplier<T> fn){return scoped(new Origin(p.getUUID(),true,phone,false,false,UUID.randomUUID()),fn);}
    public static <T>T rule(AutomationNode s,boolean any,Supplier<T> fn){return scoped(new Origin(s.owner,false,false,true,any,UUID.randomUUID()),fn);}
    public static void emit(AutomationNode s,String kind,Object... fields){if(s.getLevel() instanceof ServerLevel l)LearningEvents.owner(l,s.owner,kind,s.host,s.slot,fields);}
    public static void delivered(AutomationNode server,TransferJournal.Job job,AutomationNode src,AutomationNode dst,int accepted,boolean stolen){
        if(accepted<=0)return;var o=job.learning;UUID actor=o==null?job.owner:o.actor();boolean furnace=server.getLevel().getBlockState(MachineAccess.target(dst)).getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock;
        boolean fuel=!job.liquid&&furnace&&job.destinationSlot==1;
        var n=src.host.network();var path=n.routePath(src.id(),dst.ip()).path();boolean routed=path.stream().map(n.devices::get).filter(Objects::nonNull).anyMatch(d->d.kind.equals("router"));
        int total=accepted;for(var j:TransferJournal.get((ServerLevel)server.getLevel()).jobs.values())if(j!=job&&j.learning!=null&&o!=null&&j.learning.batch().equals(o.batch())&&j.state.equals("delivered"))total+=j.learningDelivered;
        job.learningDelivered+=accepted;
        LearningEvents.owner((ServerLevel)server.getLevel(),actor,"automation.deliver",server.host,server.slot,"accepted",accepted,"liquid",job.liquid,"remaining",job.amount(),"slot",job.destinationSlot,"furnaceFuel",fuel,"routed",routed,"encrypted",job.learningEncrypted,"satellite",job.learningSatellite,"gui",o!=null&&o.gui(),"phone",o!=null&&o.phone(),"rule",o!=null&&o.rule(),"anyRule",o!=null&&o.any(),"batchDelivered",total,"accelerated",ServerHardware.has(server.device,ServerHardware.ACCEL),"cooling",ServerHardware.cooling(server.device)==2&&ServerThermal.ready(server.device),"restoredRule",o!=null&&o.rule()&&job.learningRestored,"stolen",stolen,"ownCargo",Objects.equals(job.owner,actor));
    }
    private AutomationLearning(){}
}
