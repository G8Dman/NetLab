package dev.netlab.media;

import dev.netlab.DeviceEntity;
import dev.netlab.PowerGeometry;
import dev.netlab.core.DeviceOrientation;
import dev.netlab.media.core.MediaHardware;
import dev.netlab.media.core.TelevisionSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The same device and cable IDs are used for both mountings. */
public final class TelevisionBlock extends MediaBlock {
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    private static final VoxelShape STANDING = Shapes.or(
            box(1,3,1,15,15,5), box(7,.5,2.5,9,3,3.5), box(3,0,.5,13,.5,8));
    private static final VoxelShape WALL = box(1,3,12,15,15,16);
    private static final SocketMount STAND_DATA = new SocketMount(3,Vec3.ZERO);
    private static final SocketMount STAND_POWER = new SocketMount(1,Vec3.ZERO);
    private static final Vec3 WALL_OFFSET = new Vec3(0,0,TelevisionSurface.WALL_OFFSET);
    private static final SocketMount WALL_DATA = new SocketMount(3,WALL_OFFSET);
    private static final SocketMount WALL_POWER = new SocketMount(1,WALL_OFFSET);
    private static final PowerGeometry.Jack POWER = new PowerGeometry.Jack(
            PowerGeometry.BOARD_IN,TelevisionSurface.SIDE_DEPTH,.37,.943,.085,false);

    public TelevisionBlock(Properties properties){
        super(properties,MediaHardware.TV);
        registerDefaultState(defaultBlockState().setValue(WALL_MOUNTED,false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);
        builder.add(WALL_MOUNTED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        Direction face=context.getClickedFace();
        if(face==Direction.DOWN)return null;
        BlockState state=placement(face,context.getHorizontalDirection());
        return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null;
    }

    BlockState placement(Direction face,Direction looking){
        boolean wall=face.getAxis().isHorizontal();
        return defaultBlockState().setValue(WALL_MOUNTED,wall).setValue(FACING,wall?face:looking.getOpposite());
    }

    private static Direction support(BlockState state){
        return state.getValue(WALL_MOUNTED)?state.getValue(FACING).getOpposite():Direction.DOWN;
    }

    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        Direction side=support(state);
        BlockPos anchor=pos.relative(side);
        return level.getBlockState(anchor).isFaceSturdy(level,anchor,side.getOpposite());
    }

    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,
            LevelAccessor level,BlockPos pos,BlockPos neighborPos){
        if(direction==support(state)&&!state.canSurvive(level,pos))return Blocks.AIR.defaultBlockState();
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }

    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return orientShape(state,state.getValue(WALL_MOUNTED)?WALL:STANDING);
    }

    @Override public SocketMount socketMount(BlockState state,boolean power){
        if(state.getValue(WALL_MOUNTED))return power?WALL_POWER:WALL_DATA;
        return power?STAND_POWER:STAND_DATA;
    }
    @Override public PowerGeometry.Jack powerInputJack(BlockState state){return POWER;}
    @Override public boolean hasFrontPowerIndicator(){return false;}

    public static double depthOffset(BlockState state){return state.getValue(WALL_MOUNTED)?TelevisionSurface.WALL_OFFSET:0;}

    static String togglePower(MediaState settings){
        settings.enabled=!settings.enabled;
        return settings.enabled?"Телевизор включён":"Телевизор выключен";
    }

    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(hit.getDirection()!=state.getValue(FACING))return InteractionResult.PASS;
        var at=hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        var pixel=TelevisionSurface.pixel(at.x,at.y,at.z,turns(state));
        if(pixel!=null){
            if(level.isClientSide)dev.netlab.media.client.MediaClient.clickTelevision(pos,pixel);
        }else if(player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof DeviceEntity host){
            var point=DeviceOrientation.inverse(at.x,at.y,at.z,turns(state));
            if(point.y()>=.188&&point.y()<.29&&(point.x()>=.77||point.x()<=.23)){
                var tv=MediaRef.of(host,0);
                var settings=tv.state();
                if(!player.mayBuild()||!settings.owns(player.getUUID()))return InteractionResult.FAIL;
                String message;
                if(point.x()>=.77){
                    message=togglePower(settings);
                }else if(player.isShiftKeyDown()){
                    settings.volume=settings.volume>=.99?.25:Math.min(1,settings.volume+.25);
                    message="Громкость: "+Math.round(settings.volume*100)+"%";
                }else{
                    settings.speakers=!settings.speakers;
                    message=settings.speakers?"Динамики включены":"Динамики выключены";
                }
                tv.changed(settings);
                serverPlayer.displayClientMessage(Component.literal(message),true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

}
