package dev.netlab.mobile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reconcile transferred/creative-echoed SIMs before their credentials are consumed. */
public final class SimCardItem extends Item {
    public SimCardItem(Properties properties){super(properties);}
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
        if(entity instanceof ServerPlayer player)MobileItemStorage.reconcile(player,stack);
    }
}
