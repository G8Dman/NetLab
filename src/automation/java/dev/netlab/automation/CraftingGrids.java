package dev.netlab.automation;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.*;

/** Real item storage for the crafting-table adapter; never part of configuration cards. */
public final class CraftingGrids extends SavedData {
    private final Map<UUID,ItemStackHandler> grids=new LinkedHashMap<>();
    private final Map<UUID,String> locks=new HashMap<>();
    public static CraftingGrids get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(CraftingGrids::new,CraftingGrids::load),"netlab_crafting_grids");}
    private ItemStackHandler create(){return new ItemStackHandler(10){@Override public boolean isItemValid(int slot,ItemStack stack){return slot<9;}@Override protected void onContentsChanged(int slot){setDirty();}};}
    public ItemStackHandler grid(AutomationNode endpoint){return grids.computeIfAbsent(endpoint.identity,id->create());}
    public boolean lock(AutomationNode endpoint,String pattern){var h=grid(endpoint);if(h.getStackInSlot(9).isEmpty()){boolean empty=true;for(int i=0;i<9;i++)empty&=h.getStackInSlot(i).isEmpty();if(empty)locks.remove(endpoint.identity);}var current=locks.get(endpoint.identity);if(current!=null&&!current.equals(pattern))return false;locks.put(endpoint.identity,pattern);setDirty();return true;}
    public String craft(AutomationNode endpoint,String expected){
        if(!endpoint.control||!endpoint.insert||!endpoint.extract)throw new IllegalArgumentException("Для крафта нужны permission extract/insert/control on");
        var level=(ServerLevel)endpoint.getLevel();if(!level.getBlockState(MachineAccess.target(endpoint)).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE))throw new IllegalArgumentException("Нужен подключённый верстак");
        var h=grid(endpoint);var stacks=new ArrayList<ItemStack>();for(int i=0;i<9;i++)stacks.add(h.getStackInSlot(i).copy());var positioned=CraftingInput.ofPositioned(3,3,stacks);var input=positioned.input();
        var holder=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow(()->new IllegalArgumentException("В сетке нет подходящего рецепта"));
        var result=holder.value().assemble(input,level.registryAccess());if(result.isEmpty()||!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).toString().equals(expected))throw new IllegalArgumentException("Результат отличается от шаблона");
        var after=plan(stacks,positioned,holder.value().getRemainingItems(input),h.getStackInSlot(9),result);
        for(int i=0;i<10;i++)h.setStackInSlot(i,after.get(i));setDirty();return "Создано: "+result.getCount()+" "+expected;
    }
    /** Build a complete transaction before changing the grid, including trimmed recipe coordinates. */
    public static List<ItemStack> plan(List<ItemStack> stacks,CraftingInput.Positioned positioned,net.minecraft.core.NonNullList<ItemStack> remaining,ItemStack output,ItemStack result){
        if(stacks.size()!=9||positioned.input().isEmpty()||result.isEmpty())throw new IllegalArgumentException("Пустая сетка или результат");
        if(!output.isEmpty()&&!ItemStack.isSameItemSameComponents(result,output)||output.getCount()+result.getCount()>result.getMaxStackSize())throw new IllegalArgumentException("Выходной слот занят");
        var after=new ArrayList<ItemStack>();
        for(int i=0;i<9;i++){
            var next=stacks.get(i).copy();if(!next.isEmpty())next.shrink(1);
            int x=i%3-positioned.left(),y=i/3-positioned.top();
            var remainder=x>=0&&y>=0&&x<positioned.input().width()&&y<positioned.input().height()?remaining.get(x+y*positioned.input().width()):ItemStack.EMPTY;
            if(!remainder.isEmpty()){if(next.isEmpty())next=remainder.copy();else if(ItemStack.isSameItemSameComponents(next,remainder)&&next.getCount()+remainder.getCount()<=next.getMaxStackSize())next.grow(remainder.getCount());else throw new IllegalArgumentException("Нет места для возвратного предмета в слоте "+i);}after.add(next);
        }
        var complete=result.copy();complete.grow(output.getCount());after.add(complete);return after;
    }
    public void drop(ServerLevel level,AutomationNode endpoint){var h=grids.remove(endpoint.identity);locks.remove(endpoint.identity);if(h!=null)for(int i=0;i<10;i++)net.minecraft.world.Containers.dropItemStack(level,endpoint.getBlockPos().getX()+.5,endpoint.getBlockPos().getY()+.5,endpoint.getBlockPos().getZ()+.5,h.getStackInSlot(i));setDirty();}
    private static CraftingGrids load(CompoundTag tag,HolderLookup.Provider registries){var data=new CraftingGrids();for(var entry:tag.getList("grids",Tag.TAG_COMPOUND)){var t=(CompoundTag)entry;var h=data.create();h.deserializeNBT(registries,t.getCompound("items"));data.grids.put(t.getUUID("id"),h);if(!t.getString("lock").isBlank())data.locks.put(t.getUUID("id"),t.getString("lock"));}return data;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){var rows=new ListTag();grids.forEach((id,h)->{var t=new CompoundTag();t.putUUID("id",id);t.putString("lock",locks.getOrDefault(id,""));t.put("items",h.serializeNBT(registries));rows.add(t);});tag.put("grids",rows);return tag;}
}
