package dev.netlab.automation;

import com.google.gson.*;
import dev.netlab.DeviceEntity;
import dev.netlab.automation.api.MachineAdapters;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.*;

/** Capability access respects the chosen face; never reads arbitrary machine NBT. */
public final class MachineAccess {
    public static BlockPos target(AutomationNode node){return node.getBlockPos().relative(node.target);}
    public static IItemHandler items(AutomationNode node){
        var l=(ServerLevel)node.getLevel();var pos=target(node);if(!l.hasChunkAt(pos))return null;
        if(l.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE))return CraftingGrids.get(l).grid(node);
        var cap=l.getCapability(Capabilities.ItemHandler.BLOCK,pos,node.target.getOpposite());if(cap!=null)return cap;
        // Only vanilla fallback: do not bypass third-party capability side/access policies.
        if(BuiltInRegistries.BLOCK.getKey(l.getBlockState(pos).getBlock()).getNamespace().equals("minecraft")&&l.getBlockEntity(pos) instanceof Container c)
            return c instanceof WorldlyContainer sided?new SidedInvWrapper(sided,node.target.getOpposite()):new InvWrapper(c);
        return null;
    }
    public static JsonObject snapshot(AutomationNode node){
        var l=(ServerLevel)node.getLevel();var pos=target(node);var out=new JsonObject();out.addProperty("target",pos.toShortString());out.addProperty("face",node.target.getOpposite().getName());
        if(!l.hasChunkAt(pos)){out.addProperty("state","unloaded");return out;}
        var state=l.getBlockState(pos);out.addProperty("block",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        out.addProperty("ip",node.ip());out.addProperty("name",node.device.hostname);out.addProperty("extract",node.extract);out.addProperty("insert",node.insert);out.addProperty("control",node.control);
        var properties=new JsonObject();state.getValues().forEach((key,value)->properties.addProperty(key.getName(),value.toString()));out.add("properties",properties);
        out.addProperty("redstoneInput",l.getBestNeighborSignal(pos));out.addProperty("workState","unknown");
        var items=items(node);if(items!=null){
            out.addProperty("slots",items.getSlots());var slots=new JsonArray();
            for(int i=0;i<Math.min(256,items.getSlots());i++)slots.add(slotSnapshot(items,i,node.extract));out.add("inventory",slots);
            out.addProperty("truncated",items.getSlots()>256);
        }
        var fluid=MachineFluids.handler(node);
        if(fluid!=null){out.add("tanks",MachineFluids.snapshot(fluid,node.extract));out.addProperty("tanksTruncated",fluid.getTanks()>MachineFluids.MAX_TANKS);}
        var energy=l.getCapability(Capabilities.EnergyStorage.BLOCK,pos,node.target.getOpposite());
        if(energy!=null){out.addProperty("energy",energy.getEnergyStored());out.addProperty("energyCapacity",energy.getMaxEnergyStored());out.addProperty("energyReceive",energy.canReceive());out.addProperty("energyExtract",energy.canExtract());}
        if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))out.addProperty("lit",state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT));
        if(l.getBlockEntity(pos) instanceof JukeboxBlockEntity j){out.addProperty("workState",j.getSongPlayer().isPlaying()?"playing":"idle");out.addProperty("songTicks",j.getSongPlayer().getTicksSinceSongStarted());}
        commonWorker(l,pos,node.target.getOpposite(),out);
        for(var adapter:MachineAdapters.ADAPTERS)if(adapter.matches(l,pos,node.target.getOpposite()))out.add("adapter",adapter.describe(l,pos,node.target.getOpposite()));
        return out;
    }
    /** Capability-only observation: simulate extraction without moving any real stack. */
    public static JsonObject slotSnapshot(IItemHandler items,int slot,boolean extractionAllowed){
        var s=items.getStackInSlot(slot);var row=new JsonObject();row.addProperty("slot",slot);row.addProperty("item",BuiltInRegistries.ITEM.getKey(s.getItem()).toString());row.addProperty("count",s.getCount());
        var preview=extractionAllowed&&!s.isEmpty()?items.extractItem(slot,Math.min(64,s.getCount()),true):ItemStack.EMPTY;
        row.addProperty("extractable",ItemStack.isSameItemSameComponents(s,preview)?Math.clamp(preview.getCount(),0,s.getCount()):0);
        if(s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)||s.has(net.minecraft.core.component.DataComponents.ITEM_NAME))row.addProperty("customName",DeviceEntity.cleanName(s.getHoverName().getString()));row.addProperty("componentsHash",s.getComponents().hashCode());return row;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void commonWorker(ServerLevel l,BlockPos p,Direction face,JsonObject out){
        try{
            Class<?> capabilities=Class.forName("org.cyclops.commoncapabilities.api.capability.Capabilities$Worker");
            var capability=(BlockCapability)capabilities.getField("BLOCK").get(null);Object worker=l.getCapability(capability,p,face);
            if(worker!=null){Class<?> api=Class.forName("org.cyclops.commoncapabilities.api.capability.work.IWorker");boolean has=(boolean)api.getMethod("hasWork").invoke(worker),can=(boolean)api.getMethod("canWork").invoke(worker);out.addProperty("workState",has?(can?"working":"blocked"):"idle");}
        }catch(ClassNotFoundException ignored){}catch(ReflectiveOperationException|LinkageError e){out.addProperty("workerIntegration","unavailable");}
    }
    public static int count(AutomationNode node,String item){var h=items(node);if(h==null)return 0;int count=0;for(int i=0;i<Math.min(256,h.getSlots());i++){var s=h.getStackInSlot(i);if(BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(item))count+=s.getCount();}return count;}
    public static ItemStack extract(AutomationNode node,String item,int amount){
        return extract(node,item,amount,-1);
    }
    public static ItemStack extract(AutomationNode node,String item,int amount,int slot){
        if(!node.extract)return ItemStack.EMPTY;var h=items(node);if(h==null)return ItemStack.EMPTY;
        for(int i=0;i<Math.min(256,h.getSlots());i++){var s=h.getStackInSlot(i);if(!s.isEmpty()&&(item.equals("*")||BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(item))){
            if(slot>=0&&i!=slot)continue;
            var preview=h.extractItem(i,Math.clamp(amount,1,64),true);if(preview.isEmpty())continue;
            var extracted=h.extractItem(i,preview.getCount(),false);if(!extracted.isEmpty())return extracted;
        }}return ItemStack.EMPTY;
    }
    public static ItemStack insert(AutomationNode node,ItemStack stack){
        return insert(node,stack,-1);
    }
    public static ItemStack insert(AutomationNode node,ItemStack stack,int slot){
        if(!node.insert)return stack.copy();var h=items(node);if(h==null)return stack.copy();var remaining=stack.copy();
        for(int i=0;i<Math.min(256,h.getSlots())&&!remaining.isEmpty();i++){if(slot>=0&&i!=slot)continue;remaining=h.insertItem(i,remaining,false);}return remaining;
    }
    public static String act(AutomationNode node,String action){
        if(!node.control)throw new IllegalArgumentException("Управление блоком запрещено на мультиинтерфейсе");
        var l=(ServerLevel)node.getLevel();var p=target(node);if(!l.hasChunkAt(p))throw new IllegalArgumentException("Целевой чанк не загружен");
        if(action.startsWith("signal ")){node.signal=Math.clamp(Integer.parseInt(action.substring(7).strip()),0,15);node.changed();l.updateNeighborsAt(node.getBlockPos(),node.getBlockState().getBlock());return "Redstone = "+node.signal;}
        if(l.getBlockEntity(p) instanceof JukeboxBlockEntity j){
            if(action.equals("play")){j.tryForcePlaySong();return "Play";}
            if(action.equals("stop")){j.getSongPlayer().stop(l,j.getBlockState());j.setChanged();return "Stop";}
        }
        for(var adapter:MachineAdapters.ADAPTERS)if(adapter.matches(l,p,node.target.getOpposite()))return adapter.act(l,p,node.target.getOpposite(),action);
        throw new IllegalArgumentException("Нет адаптера для действия: "+action);
    }
}
