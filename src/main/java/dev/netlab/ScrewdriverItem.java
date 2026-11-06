package dev.netlab;

import dev.netlab.api.RackDevices;
import dev.netlab.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** Removes only the pointed bay; its cables are disconnected and configuration travels with the item. */
public final class ScrewdriverItem extends Item {
    public ScrewdriverItem(Properties p){super(p);}
    @Override public InteractionResult useOn(UseOnContext c){
        var l=c.getLevel();var p=c.getClickedPos();var player=c.getPlayer();
        if(player==null||!player.isShiftKeyDown()||!(l.getBlockEntity(p) instanceof DeviceEntity host)||!((DeviceBlock)host.getBlockState().getBlock()).rack)return InteractionResult.PASS;
        if(l.isClientSide)return InteractionResult.SUCCESS;
        if(!player.mayBuild())return InteractionResult.CONSUME;
        int power=PowerGeometry.clicked(host,new net.minecraft.world.phys.BlockHitResult(c.getClickLocation(),c.getClickedFace(),p,false),player);
        if(power>=310&&power<328){if(!RackDevices.allowed((ServerPlayer)player,host,0)||!RackDevices.allowed((ServerPlayer)player,host,1))return InteractionResult.CONSUME;if(PowerSystem.removeSupply(host,player)){var part=new ItemStack(NetLab.RACK_SUPPLY.get());if(!player.addItem(part))player.drop(part,false);}return InteractionResult.CONSUME;}
        int slot=c.getClickLocation().y-p.getY()>=.5?1:0;var d=slot==0?host.device:host.upperDevice;
        if(d==null||d.kind.equals("rack"))return InteractionResult.CONSUME;
        if(!RackDevices.allowed((ServerPlayer)player,host,slot)){player.displayClientMessage(net.minecraft.network.chat.Component.literal("Нет доступа к устройству"),true);return InteractionResult.CONSUME;}
        PowerSystem.disconnect(host,512+slot,player);
        var item=RackDevices.item(d);if(item.isEmpty())return InteractionResult.PASS;
        for(int port:new java.util.ArrayList<>(host.links.keySet()))if(port/128==slot)host.disconnect(port,player);
        if(slot==0)host.device=new Device("rack");else host.upperDevice=null;
        host.changed();if(!player.addItem(item))player.drop(item,false);
        return InteractionResult.CONSUME;
    }
}
