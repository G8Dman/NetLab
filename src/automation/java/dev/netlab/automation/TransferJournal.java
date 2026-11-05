package dev.netlab.automation;

import dev.netlab.automation.core.Claim;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Server-only escrow. Neither inventory components nor bearer tickets are block update data. */
public final class TransferJournal extends SavedData {
    public static final class Job {
        public UUID ticket=UUID.randomUUID(),sourceId,destinationId,owner,controllerId;
        public long source,destination,ready;
        public int destinationSlot=-1;
        public ItemStack stack=ItemStack.EMPTY;
        public FluidStack fluid=FluidStack.EMPTY;
        public boolean liquid;
        public boolean hasCargo(){return liquid?!fluid.isEmpty():!stack.isEmpty();}
        public int amount(){return liquid?fluid.getAmount():stack.getCount();}
        public String cargoId(){return liquid?net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString():net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
        public String unit(){return liquid?" mB":" шт.";}
        public Claim claim=new Claim(0);
        public String state="pending",note="Ожидает доставки";
    }
    public final Map<UUID,Job> jobs=new LinkedHashMap<>();
    public dev.netlab.automation.core.AuditChain audit=new dev.netlab.automation.core.AuditChain();
    private final Map<UUID,String> observed=new HashMap<>();
    @Override public void setDirty(){
        if(audit.valid())for(var j:jobs.values()){String event=j.ticket+"|"+(j.liquid?"fluid:":"")+j.cargoId()+"|"+j.state+"|"+j.amount()+"|"+j.sourceId+"|"+j.destinationId+"|"+j.note;if(!event.equals(observed.put(j.ticket,event)))audit.add(event);}
        super.setDirty();
    }
    public static TransferJournal get(ServerLevel l){return l.getDataStorage().computeIfAbsent(new Factory<>(TransferJournal::new,TransferJournal::load),"netlab_automation_transfers");}
    private static TransferJournal load(CompoundTag tag,HolderLookup.Provider regs){
        var journal=new TransferJournal();for(var entry:tag.getList("jobs",Tag.TAG_COMPOUND)){var t=(CompoundTag)entry;var j=new Job();
            j.destinationSlot=t.contains("destinationSlot")?t.getInt("destinationSlot"):-1;j.ticket=t.getUUID("ticket");j.sourceId=t.getUUID("sourceId");j.destinationId=t.getUUID("destinationId");j.controllerId=t.getUUID("controllerId");j.owner=t.getUUID("owner");j.source=t.getLong("source");j.destination=t.getLong("destination");j.ready=t.getLong("ready");j.stack=ItemStack.parseOptional(regs,t.getCompound("stack"));j.liquid=t.getBoolean("liquid");j.fluid=FluidStack.parseOptional(regs,t.getCompound("fluid"));j.claim=new Claim(j.amount());j.state=t.getString("state");j.note=t.getString("note");journal.jobs.put(j.ticket,j);
        }if(tag.contains("audit"))journal.audit=new com.google.gson.Gson().fromJson(dev.netlab.StoredJson.get(tag,"audit"),dev.netlab.automation.core.AuditChain.class);return journal;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider regs){
        var list=new ListTag();for(var j:jobs.values()){var t=new CompoundTag();t.putInt("destinationSlot",j.destinationSlot);t.putUUID("ticket",j.ticket);t.putUUID("sourceId",j.sourceId);t.putUUID("destinationId",j.destinationId);t.putUUID("controllerId",j.controllerId);t.putUUID("owner",j.owner);t.putLong("source",j.source);t.putLong("destination",j.destination);t.putLong("ready",j.ready);if(!j.stack.isEmpty())t.put("stack",j.stack.save(regs));t.putBoolean("liquid",j.liquid);if(!j.fluid.isEmpty())t.put("fluid",j.fluid.save(regs));t.putString("state",j.state);t.putString("note",j.note);list.add(t);}tag.put("jobs",list);dev.netlab.StoredJson.put(tag,"audit",new com.google.gson.Gson().toJson(audit));return tag;
    }
    public void add(Job job){
        if(jobs.size()>=512){var it=jobs.values().iterator();while(it.hasNext()&&jobs.size()>=512){if(!it.next().hasCargo())it.remove();}}
        if(jobs.size()>=512)throw new IllegalArgumentException("Журнал заполнен: сначала заверши ожидающие передачи");
        jobs.put(job.ticket,job);observed.keySet().retainAll(jobs.keySet());setDirty();
    }
    public boolean full(){return jobs.values().stream().filter(Job::hasCargo).count()>=512;}
}
