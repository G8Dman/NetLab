package dev.netlab;

import dev.netlab.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;

public final class LaptopItem extends BlockItem {
    public LaptopItem(Block block,Properties properties){super(block,properties.stacksTo(1));}
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){if(entity instanceof ServerPlayer p)ItemConfigurations.reconcile(p,stack);}
    @Override public net.minecraft.world.InteractionResult useOn(UseOnContext context){if(context.getPlayer() instanceof ServerPlayer p){var item=context.getItemInHand();ItemConfigurations.reconcile(p,item);var label=item.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();if(label.contains("netlabConfigFingerprint")&&!item.getOrDefault(NetLab.CONFIGURATION.get(),new net.minecraft.nbt.CompoundTag()).contains("netlabDevice")){p.displayClientMessage(Component.literal("Не найдена сохранённая конфигурация этого предмета. Возьми исходный выпавший ноутбук."),false);return net.minecraft.world.InteractionResult.FAIL;}}return super.useOn(context);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){
        var tag=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if(tag.contains("laptopBattery"))lines.add(Component.literal("Аккумулятор: "+tag.getInt("laptopBattery")+"%"));
        lines.add(Component.literal("Встроенная память сохраняет настройки при переносе."));super.appendHoverText(stack,context,lines,flags);
    }
}
