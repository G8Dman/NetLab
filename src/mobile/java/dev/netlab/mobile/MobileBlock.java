package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public class MobileBlock extends DeviceBlock {
    public MobileBlock(BlockBehaviour.Properties props,String kind){super(props,kind,false);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MobileEntity(pos,state);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity placer,ItemStack stack){super.setPlacedBy(l,p,s,placer,stack);if(!l.isClientSide&&l.getBlockEntity(p) instanceof MobileEntity host){var config=MobileState.get(host.device);if(config.owner.isEmpty()&&placer!=null)config.owner=placer.getUUID().toString();config.save(host.device);var tag=MobileItems.privateTag(stack);if(tag.contains("mobilePhysical"))host.physical(tag.getCompound("mobilePhysical"),l.registryAccess());MobileChunks.register((net.minecraft.server.level.ServerLevel)l,p);host.changed();}}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){if(!l.isClientSide&&l.getBlockEntity(p) instanceof MobileEntity host)MobilePackets.open((ServerPlayer)player,host,0);return InteractionResult.sidedSuccess(l.isClientSide);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return orientShape(s,switch(kind){case MobileHardware.ANTENNA->box(3,0,3,13,16,7);case MobileHardware.RRU->box(1,0,1.5,15,12,14);case MobileHardware.STATION->box(1,0,1.5,15,9,14);default->box(0,0,1.5,16,4,14);});}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder loot){var host=loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(host instanceof MobileEntity mobile)return List.of(mobile.configuredItem());return super.getDrops(state,loot);}
    @Override protected void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moved){if(!old.is(next.getBlock())&&!level.isClientSide)MobileChunks.remove((net.minecraft.server.level.ServerLevel)level,pos);super.onRemove(old,level,pos,next,moved);}
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){if(!level.isClientSide&&player.isCreative()&&level.getBlockEntity(pos) instanceof MobileEntity host)popResource(level,pos,host.configuredItem());return super.playerWillDestroy(level,pos,state,player);}
}
