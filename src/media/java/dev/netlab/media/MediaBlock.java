package dev.netlab.media;
import dev.netlab.*;
import dev.netlab.media.core.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;
public final class MediaBlock extends DeviceBlock {
    public MediaBlock(BlockBehaviour.Properties p,String kind){super(p,kind,false);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MediaEntity(pos,state);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity who,ItemStack stack){super.setPlacedBy(level,pos,state,who,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof DeviceEntity h){var s=MediaState.get(h.device);if(s.owner.isEmpty()&&who!=null)s.owner=who.getUUID().toString();s.save(h.device);h.changed();}}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof DeviceEntity host)MediaService.open((ServerPlayer)player,new MediaRef(host,0,host.device));return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return orientShape(state,switch(kind){case MediaHardware.TV->box(0,0,1,16,16,5);case MediaHardware.PHONE->box(1,0,1,15,8,14);case MediaHardware.SPEAKER->box(2,0,2,14,15,14);default->box(0,0,1,16,4,14);});}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder loot){var be=loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY);return be instanceof DeviceEntity h?List.of(MemoryCards.deviceItem(this,h.device)):super.getDrops(state,loot);}
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){if(!level.isClientSide&&player.isCreative()&&level.getBlockEntity(pos) instanceof DeviceEntity h)popResource(level,pos,MemoryCards.deviceItem(this,h.device));return super.playerWillDestroy(level,pos,state,player);}
}
