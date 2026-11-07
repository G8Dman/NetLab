package dev.netlab.mobile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class PhoneItem extends Item {
    public PhoneItem(Properties props){super(props);}
    @Override public void inventoryTick(ItemStack item,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){if(entity instanceof ServerPlayer player)MobileItemStorage.reconcile(player,item);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var item=player.getItemInHand(hand);if(!level.isClientSide){MobileItemStorage.reconcile((ServerPlayer)player,item);try{MobileItems.phoneId(item);MobilePackets.openPhone((ServerPlayer)player,item);}catch(IllegalArgumentException e){player.displayClientMessage(net.minecraft.network.chat.Component.literal(e.getMessage()),false);return InteractionResultHolder.fail(item);}}return InteractionResultHolder.sidedSuccess(item,level.isClientSide);}
    @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
        if(!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof dev.netlab.DeviceEntity host))return InteractionResult.PASS;
        if(context.getPlayer() instanceof ServerPlayer player){MobileItemStorage.reconcile(player,context.getItemInHand());int port=dev.netlab.CableGeometry.clickedPort(host,new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),false),player);PhoneTerminals.open(player,context.getItemInHand(),host,port);}
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
