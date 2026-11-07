package dev.netlab.mobile;

import dev.netlab.ItemConfigurations;
import dev.netlab.NetLab;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/** World-owned backing store for private SIM/phone components stripped from creative echoes. */
public final class MobileItemStorage extends SavedData {
    static final String TOKEN="netlabMobileItem", REVISION="netlabMobileRevision";
    private CompoundTag items=new CompoundTag();

    public MobileItemStorage() {}
    public MobileItemStorage(CompoundTag tag){items=tag.getCompound("items").copy();}
    private static MobileItemStorage get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(MobileItemStorage::new,(tag,regs)->new MobileItemStorage(tag)),"netlab_mobile_items");
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.put("items",items.copy());return tag;}
    private static boolean managed(ItemStack stack){return stack.is(Mobile.PHONE.get())||stack.is(Mobile.SIM.get());}
    private static String itemId(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private static boolean token(String value){try{return UUID.fromString(value).toString().equals(value);}catch(IllegalArgumentException e){return false;}}

    /** Called on trusted server mutations, before an inventory packet can echo an older item. */
    public static void remember(ItemStack stack){
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null&&server.isSameThread()&&managed(stack))get(server).rememberStack(stack);
    }
    public boolean rememberStack(ItemStack stack){
        if(!managed(stack))return false;
        var data=MobileItems.privateTag(stack);if(data.isEmpty())return false;
        var label=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String key=label.getString(TOKEN),id=itemId(stack);
        var previous=items.getCompound(key);
        if(!token(key)||!previous.isEmpty()&&!previous.getString("item").equals(id)){key=UUID.randomUUID().toString();previous=new CompoundTag();}
        long revision=previous.getLong("revision");
        if(!previous.isEmpty()&&previous.getCompound("data").equals(data)){
            label.putString(TOKEN,key);label.putLong(REVISION,revision);
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(label));return false;
        }
        revision=Math.max(revision,Math.max(0,label.getLong(REVISION)))+1;
        label.putString(TOKEN,key);label.putLong(REVISION,revision);
        var record=new CompoundTag();record.putString("item",id);record.putLong("revision",revision);
        String legacy=previous.getString("legacyFingerprint");
        if(legacy.isBlank())legacy=previous.isEmpty()?label.getString("netlabConfigFingerprint"):previous.getCompound("public").getString("netlabConfigFingerprint");
        record.putString("legacyFingerprint",legacy);
        record.put("data",data.copy());record.put("public",label.copy());items.put(key,record);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(label));setDirty();return true;
    }
    /** Restores the latest committed state, including a deliberate SIM ejection. */
    public boolean reconcileStack(ItemStack stack){
        if(!managed(stack))return false;
        var label=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String key=label.getString(TOKEN),id=itemId(stack);var record=items.getCompound(key);
        if(record.isEmpty()){
            String fingerprint=label.getString("netlabConfigFingerprint");
            if(fingerprint.matches("[a-f0-9]{64}"))for(String candidate:items.getAllKeys()){
                var found=items.getCompound(candidate);
                if(found.getString("item").equals(id)&&(found.getCompound("public").getString("netlabConfigFingerprint").equals(fingerprint)||found.getString("legacyFingerprint").equals(fingerprint))){record=found;break;}
            }
        }
        var data=MobileItems.privateTag(stack);
        if(record.isEmpty())return rememberStack(stack);
        if(!record.getString("item").equals(id))return false;
        // A native item saved after the vault's last disk checkpoint is newer evidence.
        if(!data.isEmpty()&&label.getLong(REVISION)>record.getLong("revision"))return rememberStack(stack);
        boolean changed=!data.equals(record.getCompound("data"))||!label.equals(record.getCompound("public"));
        if(changed){stack.set(NetLab.CONFIGURATION.get(),record.getCompound("data").copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(record.getCompound("public").copy()));}
        return changed;
    }
    public static void reconcile(ServerPlayer player,ItemStack stack){
        if(!managed(stack))return;
        var store=get(player.server);store.reconcileStack(stack);
        if(MobileItems.privateTag(stack).isEmpty()&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("netlabConfigFingerprint").matches("[a-f0-9]{64}")){
            // Migrate pre-fix items from the old per-player cache; SIMs are transferable.
            ItemConfigurations.reconcile(player,stack);
            if(MobileItems.privateTag(stack).isEmpty())for(var other:player.server.getPlayerList().getPlayers()){
                if(other!=player)ItemConfigurations.reconcile(other,stack);
                if(!MobileItems.privateTag(stack).isEmpty())break;
            }
            store.reconcileStack(stack);
        }
    }
    public static void inventory(ServerPlayer player){
        for(var stack:player.getInventory().items)reconcile(player,stack);
        for(var stack:player.getInventory().offhand)reconcile(player,stack);
        reconcile(player,player.containerMenu.getCarried());
    }
}
