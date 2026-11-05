package dev.netlab.api;

import com.google.gson.Gson;
import dev.netlab.*;
import dev.netlab.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import java.util.*;
import java.util.function.Supplier;

/** Optional addons register rack-sized devices without a dependency from the base mod. */
public final class RackDevices {
    private static final Gson JSON=new Gson();
    private static final Map<String,Supplier<? extends Block>> TYPES=new java.util.concurrent.ConcurrentHashMap<>();
    public interface Guard {boolean allowed(ServerPlayer player,DeviceEntity host,int slot);}
    public static final List<Guard> GUARDS=new java.util.concurrent.CopyOnWriteArrayList<>();
    public record Installation(ServerPlayer player,DeviceEntity host,int slot){}
    public static final List<java.util.function.Consumer<Installation>> INSTALLED=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final Map<String,java.util.function.Consumer<Device>> INITIALIZERS=new java.util.concurrent.ConcurrentHashMap<>();
    public static void installed(ServerPlayer p,DeviceEntity host,int slot){PowerSystem.attached(host,slot);for(var hook:INSTALLED)hook.accept(new Installation(p,host,slot));}
    public static void register(String kind,Supplier<? extends Block> block){TYPES.put(kind,block);}
    public static Block block(String kind){var supplier=TYPES.get(kind);return supplier==null?null:supplier.get();}
    public static boolean supported(String kind){return TYPES.containsKey(kind);}
    public static boolean allowed(ServerPlayer p,DeviceEntity host,int slot){return GUARDS.stream().allMatch(g->g.allowed(p,host,slot));}
    public static Device restore(ItemStack stack,String kind){
        var tag=stack.has(NetLab.CONFIGURATION.get())?stack.get(NetLab.CONFIGURATION.get()):stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        Device d=tag.contains("netlabDevice")?JSON.fromJson(StoredJson.get(tag,"netlabDevice"),Device.class):new Device(kind);
        if(!d.kind.equals(kind))throw new IllegalArgumentException("Device kind mismatch");if(!tag.contains("netlabDevice")&&INITIALIZERS.containsKey(kind))INITIALIZERS.get(kind).accept(d);d.normalize();if(tag.contains("netlabDevice"))MemoryCards.migrate(d);else MemoryCards.fresh(d);return d;
    }
    public static ItemStack item(Device d){var block=block(d.kind);if(block==null)return ItemStack.EMPTY;MemoryCards.sync(d);var stack=new ItemStack(block);var tag=new CompoundTag();StoredJson.put(tag,"netlabDevice",JSON.toJson(MemoryCards.saved(d)));stack.set(NetLab.CONFIGURATION.get(),tag);return stack;}
}
