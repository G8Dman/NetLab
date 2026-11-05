package dev.netlab;

import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerPlayer;

public final class PortableTerminalItem extends Item {
    public PortableTerminalItem(Properties p){super(p);}
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof DeviceEntity e)||c.getPlayer()==null)return InteractionResult.PASS;
        if(!c.getLevel().isClientSide){int port=CableGeometry.clickedPort(e,new net.minecraft.world.phys.BlockHitResult(c.getClickLocation(),c.getClickedFace(),c.getClickedPos(),false),c.getPlayer());PortableTerminals.open((ServerPlayer)c.getPlayer(),e,port);}
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
}
