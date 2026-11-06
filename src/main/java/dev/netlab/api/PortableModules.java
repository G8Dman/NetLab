package dev.netlab.api;

import dev.netlab.*;
import dev.netlab.core.HandsetConfiguration.Binding;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import java.util.*;
import java.util.function.Supplier;

/** Addons contribute phone hardware without depending on the Mobile jar. */
public final class PortableModules {
    public record Definition(String code,String slot,String title,Supplier<? extends Item> item){}
    public static final Map<String,Definition> DEFINITIONS=new java.util.concurrent.ConcurrentHashMap<>();
    public static void register(Definition definition){DEFINITIONS.put(definition.slot(),definition);}
    public static Binding binding(ItemStack stack){var tag=stack.getOrDefault(NetLab.CONFIGURATION.get(),new CompoundTag()).getCompound("portableBinding");return tag.isEmpty()?null:new Binding(tag.getString("identity"),tag.getString("address"),tag.getString("dimension"));}
    public static void bind(ItemStack stack,Binding binding){
        var tag=stack.getOrDefault(NetLab.CONFIGURATION.get(),new CompoundTag()).copy();var data=new CompoundTag();data.putString("identity",binding.identity());data.putString("address",binding.address());data.putString("dimension",binding.dimension());tag.put("portableBinding",data);stack.set(NetLab.CONFIGURATION.get(),tag);
        var label=new CompoundTag();label.putString("portableServer",binding.address());try{label.putString("netlabConfigFingerprint",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(tag.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}stack.set(DataComponents.CUSTOM_DATA,CustomData.of(label));
    }
    public static final class ModuleItem extends Item {
        public final String code,slot,title;
        public ModuleItem(Properties properties,String code,String slot,String title){super(properties);this.code=code;this.slot=slot;this.title=title;}
        @Override public void inventoryTick(ItemStack stack,net.minecraft.world.level.Level level,net.minecraft.world.entity.Entity entity,int index,boolean selected){if(entity instanceof ServerPlayer player)ItemConfigurations.reconcile(player,stack);}
        @Override public Component getName(ItemStack stack){String address=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("portableServer");return Component.literal(title+(address.isBlank()?"":" ["+address+"]"));}
        @Override public InteractionResult useOn(UseOnContext context){
            var binder=PortableHosts.BINDERS.get(slot);if(binder==null)return InteractionResult.PASS;
            if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
            if(!(context.getPlayer() instanceof ServerPlayer player)||!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof DeviceEntity host))return InteractionResult.PASS;
            try{int bay=((DeviceBlock)host.getBlockState().getBlock()).rack&&context.getClickLocation().y-context.getClickedPos().getY()>=.5?1:0;var binding=binder.bind(player,host,bay);bind(context.getItemInHand(),binding);ItemConfigurations.reconcile(player,context.getItemInHand());player.displayClientMessage(Component.literal("Модуль привязан к серверу "+binding.address()),false);}
            catch(IllegalArgumentException error){player.displayClientMessage(Component.literal(error.getMessage()),false);}
            return InteractionResult.SUCCESS;
        }
    }
}
