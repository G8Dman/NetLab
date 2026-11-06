package dev.netlab;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Creative inventory echoes contain a public fingerprint, never the private configuration. */
public final class ItemConfigurations {
    private static final String KEY="netlab:portableItemMemory";
    public static void reconcile(ServerPlayer player,ItemStack stack){
        var persistent=player.getPersistentData();var saved=persistent.getCompound(Player.PERSISTED_NBT_TAG);var memory=saved.getCompound(KEY);
        if(reconcile(memory,stack)){saved.put(KEY,memory);persistent.put(Player.PERSISTED_NBT_TAG,saved);}
    }
    public static boolean reconcile(CompoundTag memory,ItemStack stack){
        var fingerprint=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("netlabConfigFingerprint");
        if(!fingerprint.matches("[a-f0-9]{64}"))return false;
        String item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();var data=stack.getOrDefault(NetLab.CONFIGURATION.get(),new CompoundTag());
        if(data.isEmpty()){
            var record=memory.getCompound(fingerprint);if(record.getString("item").equals(item)&&record.contains("data"))stack.set(NetLab.CONFIGURATION.get(),record.getCompound("data").copy());
            return false;
        }
        if(memory.contains(fingerprint))return false;
        var record=new CompoundTag();record.putString("item",item);record.put("data",data.copy());long sequence=memory.getLong("sequence")+1;record.putLong("order",sequence);memory.putLong("sequence",sequence);memory.put(fingerprint,record);
        while(memory.getAllKeys().size()>33){String oldest=memory.getAllKeys().stream().filter(k->!k.equals("sequence")).min(java.util.Comparator.comparingLong(k->memory.getCompound(k).getLong("order"))).orElseThrow();memory.remove(oldest);}
        return true;
    }
}
