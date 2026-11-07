package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MobileItems {
    public static CompoundTag privateTag(ItemStack item){return item.getOrDefault(NetLab.CONFIGURATION.get(),new CompoundTag()).copy();}
    public static void seal(ItemStack item,CompoundTag tag){
        var previous=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        item.set(NetLab.CONFIGURATION.get(),tag);var publicTag=new CompoundTag();
        if(previous.contains(MobileItemStorage.TOKEN))publicTag.putString(MobileItemStorage.TOKEN,previous.getString(MobileItemStorage.TOKEN));
        if(previous.contains(MobileItemStorage.REVISION))publicTag.putLong(MobileItemStorage.REVISION,previous.getLong(MobileItemStorage.REVISION));
        try{publicTag.putString("netlabConfigFingerprint",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(tag.toString().getBytes(StandardCharsets.UTF_8))));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        if(tag.contains("sim")){var sim=MobileState.JSON.fromJson(StoredJson.get(tag,"sim"),SimCredentials.class);publicTag.putString("simLabel",sim.label);publicTag.putString("operator",sim.operator);publicTag.putString("simId",sim.id);}
        item.set(DataComponents.CUSTOM_DATA,CustomData.of(publicTag));
        MobileItemStorage.remember(item);
    }
    public static ItemStack sim(SimCredentials sim){var item=new ItemStack(Mobile.SIM.get());var tag=new CompoundTag();StoredJson.put(tag,"sim",MobileState.JSON.toJson(sim));seal(item,tag);return item;}
    public static SimCredentials sim(ItemStack item){var tag=privateTag(item);return tag.contains("sim")?MobileState.JSON.fromJson(StoredJson.get(tag,"sim"),SimCredentials.class):null;}
    public static String phoneId(ItemStack item){var tag=privateTag(item);if(!tag.contains("phoneId")){if(tag.isEmpty()&&item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().contains("netlabConfigFingerprint"))throw new IllegalArgumentException("Не удалось восстановить данные телефона. Нужен исходный предмет или сохранение его владельца.");tag.putString("phoneId",UUID.randomUUID().toString());seal(item,tag);}return tag.getString("phoneId");}
    public static void setPhoneSim(ItemStack item,SimCredentials sim){var tag=privateTag(item);if(sim==null)tag.remove("sim");else StoredJson.put(tag,"sim",MobileState.JSON.toJson(sim));seal(item,tag);}
}
