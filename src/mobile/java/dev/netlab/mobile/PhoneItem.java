package dev.netlab.mobile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class PhoneItem extends Item {
    public PhoneItem(Properties props){super(props);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var item=player.getItemInHand(hand);if(!level.isClientSide){MobileItems.phoneId(item);MobilePackets.openPhone((ServerPlayer)player,item);}return InteractionResultHolder.sidedSuccess(item,level.isClientSide);}
}
