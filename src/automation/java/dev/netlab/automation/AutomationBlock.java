package dev.netlab.automation;

import dev.netlab.DeviceBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class AutomationBlock extends DeviceBlock {
    public AutomationBlock(BlockBehaviour.Properties p,String kind){super(p,kind,false);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new AutomationEntity(p,s);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(l,p,s,placer,stack);
        if(!l.isClientSide&&l.getBlockEntity(p) instanceof AutomationEntity node){var app=AutomationNode.of(node,0);if(app.owner==null)app.owner=placer==null?null:placer.getUUID();app.changed();}
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(!l.isClientSide&&l.getBlockEntity(p) instanceof AutomationEntity node){var app=AutomationNode.of(node,0);if(!app.owns(player.getUUID()))return InteractionResult.FAIL;if(kind.equals("sniffer"))AutomationPackets.send((ServerPlayer)player,app,"",true);else dev.netlab.FrontPanel.send((ServerPlayer)player,node,0,"Настройка через Console → Terminal");}
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        return orientShape(s,kind.equals("endpoint")?box(2,0,2,14,8,14):box(0,0,2,16,kind.equals("server")?4:6,14));
    }
    @Override protected boolean isSignalSource(BlockState state){return kind.equals("endpoint");}
    @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder loot){
        var entity=loot.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if(kind.equals("server")&&entity instanceof AutomationEntity host)return java.util.List.of(dev.netlab.MemoryCards.deviceItem(this,host.device));
        return super.getDrops(state,loot);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(kind.equals("server")&&!level.isClientSide&&player.isCreative()&&level.getBlockEntity(pos) instanceof AutomationEntity host)popResource(level,pos,dev.netlab.MemoryCards.deviceItem(this,host.device));
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!old.is(next.getBlock())&&!level.isClientSide&&level.getBlockEntity(pos) instanceof AutomationEntity host){var node=AutomationNode.of(host,0);if(node!=null)CraftingGrids.get((net.minecraft.server.level.ServerLevel)level).drop((net.minecraft.server.level.ServerLevel)level,node);}
        super.onRemove(old,level,pos,next,moved);
    }
    @Override protected int getSignal(BlockState s,BlockGetter l,BlockPos p,Direction side){
        return l.getBlockEntity(p) instanceof AutomationEntity n&&kind.equals("endpoint")?AutomationNode.of(n,0).signal:0;
    }
}
