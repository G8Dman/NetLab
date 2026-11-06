package dev.netlab;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class CableInteraction {
    public static void onBlock(PlayerInteractEvent.RightClickBlock event){
        var player=event.getEntity();
        if(player.isShiftKeyDown()&&player.getItemInHand(event.getHand()).isEmpty()&&event.getLevel().getBlockEntity(event.getPos()) instanceof DeviceEntity host){
            int power=PowerGeometry.clicked(host,event.getHitVec(),player),port=CableGeometry.clickedPort(host,event.getHitVec(),player);boolean energy=host.powerLinks.containsKey(power),data=host.links.containsKey(port);
            if(energy||data){if(!event.getLevel().isClientSide&&player.mayBuild()){if(energy)PowerSystem.disconnect(host,power,player);else host.disconnect(port,player);player.displayClientMessage(Component.literal("Кабель отключён."),true);}event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));return;}
        }
        if(event.getEntity().getItemInHand(event.getHand()).getItem() instanceof RoutedCableItem cable){
            cable.useOn(new net.minecraft.world.item.context.UseOnContext(event.getEntity(),event.getHand(),event.getHitVec()));
            event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));return;
        }
        if(use(event.getEntity(),event.getHand(),event.getHitVec())){
            event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }
    public static void onAir(PlayerInteractEvent.RightClickItem event){
        if(use(event.getEntity(),event.getHand(),null)){
            event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }
    private static boolean use(Player player,InteractionHand hand,BlockHitResult block){
        ItemStack stack=player.getItemInHand(hand);
        if(!(stack.getItem() instanceof DyeItem)&&!stack.is(Items.NAME_TAG))return false;
        var hit=CableGeometry.pick(player);DeviceEntity device=null;int port=-1;
        if(hit!=null){device=hit.device();port=hit.port();}
        else if(block!=null&&player.level().getBlockEntity(block.getBlockPos()) instanceof DeviceEntity d){
            int p=CableGeometry.clickedPort(d,block,player);if(d.links.containsKey(p)){device=d;port=p;}
        }
        if(device==null)return false;
        if(player.level().isClientSide)return true;
        if(!player.mayBuild())return true;
        var peer=device.links.get(port);int color=peer.color();String name=peer.name();
        if(stack.getItem() instanceof DyeItem dye)color=dye.getDyeColor().getTextureDiffuseColor();
        else {
            Component custom=stack.get(DataComponents.CUSTOM_NAME);
            if(custom==null){player.displayClientMessage(Component.literal("Сначала переименуй бирку в наковальне."),true);return true;}
            name=DeviceEntity.cleanName(custom.getString());
        }
        if(!device.decorate(port,color,name)){player.displayClientMessage(Component.literal("Второй конец кабеля не загружен или соединение уже удалено."),true);return true;}
        boolean painting=stack.getItem() instanceof DyeItem;
        if(!player.isCreative())stack.shrink(1);
        player.displayClientMessage(Component.literal(painting?"Кабель окрашен.":"Имя кабеля: "+name),true);
        return true;
    }
}
