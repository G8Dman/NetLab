package dev.netlab.mobile;

import dev.netlab.mobile.core.MobileHardware;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** Two occupied cells, one physical device and one configured drop, anchored at the bottom. */
public final class SectorAntennaBlock extends MobileBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER=Shapes.or(box(3,0,2,13,16,5),box(6,3,5,10,5,16),box(2,0,1.5,14,2.8,2));
    private static final VoxelShape UPPER=Shapes.or(box(3,0,2,13,16,5),box(6,9,5,10,11,16));

    public SectorAntennaBlock(BlockBehaviour.Properties properties){super(properties,MobileHardware.ANTENNA);registerDefaultState(defaultBlockState().setValue(HALF,DoubleBlockHalf.LOWER));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(HALF);}
    public static boolean upper(BlockState state){return state.hasProperty(HALF)&&state.getValue(HALF)==DoubleBlockHalf.UPPER;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return upper(state)?null:super.newBlockEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var level=context.getLevel();var above=context.getClickedPos().above();
        if(level.isOutsideBuildHeight(above)||!level.getBlockState(above).canBeReplaced(context))return null;
        var facing=context.getClickedFace().getAxis().isHorizontal()?context.getClickedFace():context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING,facing).setValue(HALF,DoubleBlockHalf.LOWER);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity player,ItemStack stack){
        if(!upper(state))level.setBlock(pos.above(),state.setValue(HALF,DoubleBlockHalf.UPPER),Block.UPDATE_ALL);
        super.setPlacedBy(level,pos,state,player,stack);
    }
    /** Upgrade old single-cell antennas only into empty space; retain the existing device and cables. */
    public void ensureUpper(Level level,BlockPos pos,BlockState state){
        if(!level.isClientSide&&!upper(state)&&!level.isOutsideBuildHeight(pos.above())&&level.hasChunkAt(pos.above())&&level.getBlockState(pos.above()).isAir())level.setBlock(pos.above(),state.setValue(HALF,DoubleBlockHalf.UPPER),Block.UPDATE_ALL);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return orientShape(state,upper(state)?UPPER:LOWER);}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos){
        if(direction==(upper(state)?Direction.DOWN:Direction.UP)){
            if(neighbor.is(this)&&upper(neighbor)!=upper(state))return state.setValue(FACING,neighbor.getValue(FACING));
            if(upper(state))return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(upper(state)){var bottom=pos.below();var lower=level.getBlockState(bottom);if(lower.is(this)&&!upper(lower))return super.useWithoutItem(lower,level,bottom,player,hit);return InteractionResult.PASS;}
        ensureUpper(level,pos,state);return super.useWithoutItem(state,level,pos,player,hit);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder loot){return upper(state)?List.of():super.getDrops(state,loot);}
    @Override protected void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!old.is(next.getBlock())&&!level.isClientSide){
            var other=upper(old)?pos.below():pos.above();var partner=level.getBlockState(other);
            if(partner.is(this)&&upper(partner)!=upper(old)){
                if(upper(old))level.destroyBlock(other,true);else level.removeBlock(other,false);
            }
        }
        super.onRemove(old,level,pos,next,moved);
    }
}
