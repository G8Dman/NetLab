package dev.netlab.sputnik;
import dev.netlab.*;
import dev.netlab.sputnik.core.*;
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
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;
public final class SputnikBlock extends DeviceBlock {
    public SputnikBlock(BlockBehaviour.Properties p,String kind){super(p,kind,false);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new SputnikEntity(p,s);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity who,ItemStack stack){super.setPlacedBy(l,p,s,who,stack);if(!l.isClientSide&&l.getBlockEntity(p) instanceof DeviceEntity h){if(h.links.isEmpty())SputnikHardware.repairGeneric(h.device);var state=SputnikState.get(h.device);if(state.owner.isEmpty()&&who!=null)state.owner=who.getUUID().toString();if(java.util.Set.of(SputnikHardware.DISH,SputnikHardware.VSAT).contains(kind)){boolean configured=stack.has(NetLab.CONFIGURATION.get());if(!configured)state.azimuth=s.getValue(FACING).toYRot();else l.setBlockAndUpdate(p,s.setValue(FACING,Direction.fromYRot(state.azimuth)));}state.save(h.device);h.changed();}}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){if(!l.isClientSide&&l.getBlockEntity(p) instanceof DeviceEntity h)SputnikPackets.panel((ServerPlayer)player,new SputnikRef(h,0,h.device),true,"");return InteractionResult.sidedSuccess(l.isClientSide);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return orientShape(s,switch(kind){case SputnikHardware.DISH->box(-8,0,2,24,30,15);case SputnikHardware.VSAT->box(0,0,2,16,22,15);case SputnikHardware.SPEAKER->box(2,0,2,14,15,14);case SputnikHardware.ODU->box(1,0,2,15,10,14);default->box(0,0,1.5,16,4,14);});}
    @Override protected List<ItemStack> getDrops(BlockState s,LootParams.Builder loot){var be=loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY);return be instanceof DeviceEntity h?List.of(MemoryCards.deviceItem(this,h.device)):super.getDrops(s,loot);}
    @Override public BlockState playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){if(!l.isClientSide&&player.isCreative()&&l.getBlockEntity(p) instanceof DeviceEntity h)popResource(l,p,MemoryCards.deviceItem(this,h.device));return super.playerWillDestroy(l,p,s,player);}
}
