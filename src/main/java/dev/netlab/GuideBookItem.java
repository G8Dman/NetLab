package dev.netlab;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Client opens its resource-driven handbook; using the item changes no world state. */
public final class GuideBookItem extends Item {
    public GuideBookItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide)dev.netlab.client.Client.openGuide();
        else if(player instanceof net.minecraft.server.level.ServerPlayer p)dev.netlab.api.LearningEvents.emit(p,"book.open",null,0);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
}
