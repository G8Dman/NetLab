package dev.netlab;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.network.PacketDistributor;

public class DeviceBlock extends Block implements EntityBlock {
  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty RACK_PART =
      net.minecraft.world.level.block.state.properties.IntegerProperty.create("rack_part", 0, 3);
  public final String kind;
  public final boolean rack;
  public DeviceBlock(BlockBehaviour.Properties p, String kind, boolean rack) {
    super(p);
    this.kind = kind;
    this.rack = rack;
    registerDefaultState(stateDefinition.any().setValue(RACK_PART, 0).setValue(FACING,net.minecraft.core.Direction.NORTH));
  }
  @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(RACK_PART,FACING);
  }
  @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
    return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
  }
  @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
  @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
  public static int turns(BlockState state){return (state.getValue(FACING).get2DDataValue()+2)%4;}
  public static VoxelShape orientShape(BlockState state,VoxelShape shape){
    int turns=turns(state);if(turns==0)return shape;VoxelShape result=Shapes.empty();
    for(var box:shape.toAabbs()){
      var a=dev.netlab.core.DeviceOrientation.rotate(box.minX,box.minY,box.minZ,turns);var b=dev.netlab.core.DeviceOrientation.rotate(box.maxX,box.maxY,box.maxZ,turns);
      result=Shapes.or(result,Shapes.box(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()),Math.max(a.x(),b.x()),Math.max(a.y(),b.y()),Math.max(a.z(),b.z())));
    }return result;
  }
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new DeviceEntity(pos, state);
  }
  @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,net.minecraft.world.entity.LivingEntity placer,ItemStack stack){
    if(!l.isClientSide&&l.getBlockEntity(p) instanceof DeviceEntity host&&(stack.has(NetLab.CONFIGURATION.get())||stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().contains("netlabDevice"))){
      host.device=dev.netlab.api.RackDevices.restore(stack,kind);PowerSystem.attached(host,0);host.changed();
    }
  }
  @Override
  protected VoxelShape getShape(
      BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p, CollisionContext c) {
    if (rack)
      return box(0, 0, 0, 16, 16, 16);
    if(kind.equals("energy")||kind.equals("energy_source"))return box(0,0,0,16,16,16);
    if(kind.equals("ap"))return orientShape(s,box(2,0,2,14,11,14));
    if(s.is(NetLab.LAPTOP.get())){
      VoxelShape shape=Shapes.or(box(1,0,1,15,1.2,14),box(1,1,12,15,11,13));
      if(l.getBlockEntity(p) instanceof DeviceEntity d&&d.device.ports.stream().anyMatch(port->port.console&&!port.hidden))shape=Shapes.or(shape,box(10.08,0,.448,12.96,1.2,2.24));
      return orientShape(s,shape);
    }
    if(kind.equals("pc"))return orientShape(s,Shapes.or(box(2,4,5.8,14,14,9),box(7,1,7,9,4,9),box(5,0,4,11,1,12),box(5,0,1.6,11,2.6,5)));
    return orientShape(s,box(0,0,2,16,4,14));
  }
  @Override
  protected InteractionResult useWithoutItem(
      BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
    if (!l.isClientSide && l.getBlockEntity(p) instanceof DeviceEntity d) {
      if(kind.equals("energy")||kind.equals("energy_source")){player.displayClientMessage(net.minecraft.network.chat.Component.literal(PowerSystem.status(d,0)),false);return InteractionResult.SUCCESS;}
      d.selectSlot(player.getUUID(), hit.getLocation().y-p.getY() >= .5 ? 1 : 0);
      var target = d.selectedDevice(player.getUUID());
      if(target == null || target.kind.equals("rack")) return InteractionResult.PASS;
      if(target.kind.equals("ap")){WirelessMenu.send((ServerPlayer)player,d,0,"",true);return InteractionResult.SUCCESS;}
      if(!target.kind.equals("pc")){
        FrontPanel.send((ServerPlayer)player,d,hit.getLocation().y-p.getY()>=.5&&rack?1:0,"");
        return InteractionResult.SUCCESS;
      }
      ConsoleAccess.close(d,player.getUUID());
      var session = d.session(player.getUUID());
      String banner = session.open(target);
      PacketDistributor.sendToPlayer((ServerPlayer) player,
          new Packets.Output(p,
              banner
                  + ("\nNetLab router lab / independent IOS-like simulator\nType ? for commands. PC: "
                     + "ipconfig IP MASK [GATEWAY]\n")
                  + d.portSummary() + "\n" + session.prompt(target),
              true));
    }
    return InteractionResult.sidedSuccess(l.isClientSide);
  }
  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState s, Level l, BlockPos p, Player player,
      InteractionHand hand, BlockHitResult hit) {
    if (stack.getItem() instanceof ScrewdriverItem||stack.getItem() instanceof PortableTerminalItem||stack.getItem() instanceof PowerCableItem||stack.getItem() instanceof RackSupplyItem)return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    if (stack.getItem() instanceof CableItem)
      return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    if (rack && stack.getItem() instanceof BlockItem bi
        && bi.getBlock() instanceof DeviceBlock db
        && dev.netlab.api.RackDevices.supported(db.kind)) {
      if (!l.isClientSide) {
        var entity=(DeviceEntity)l.getBlockEntity(p);
        int slot=hit.getLocation().y-p.getY() >= .5 ? 1 : 0;
        if(slot==1) {
          if(entity.upperDevice!=null) return ItemInteractionResult.CONSUME;
          entity.upperDevice=dev.netlab.api.RackDevices.restore(stack,db.kind); dev.netlab.core.Hardware.ensure(entity.upperDevice);entity.changed();
          dev.netlab.api.RackDevices.installed((ServerPlayer)player,entity,slot);
          if(!player.isCreative()||(stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)||stack.has(NetLab.CONFIGURATION.get())))stack.shrink(1);
          return ItemInteractionResult.CONSUME;
        }
        if(!entity.device.kind.equals("rack"))return ItemInteractionResult.CONSUME;
        // Keep the block entity: replacing the block would disconnect the occupied upper bay.
        entity.device=dev.netlab.api.RackDevices.restore(stack,db.kind);
        dev.netlab.core.Hardware.ensure(entity.device);entity.changed();
        dev.netlab.api.RackDevices.installed((ServerPlayer)player,entity,slot);
        if (!player.isCreative()||(stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)||stack.has(NetLab.CONFIGURATION.get())))
          stack.shrink(1);
      }
      return ItemInteractionResult.sidedSuccess(l.isClientSide);
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }
  @Override
  protected void onRemove(BlockState old, Level l, BlockPos p, BlockState next, boolean moved) {
    if (!old.is(next.getBlock()) && l.getBlockEntity(p) instanceof DeviceEntity d){
      d.disconnectAll();PowerSystem.disconnectAll(d);
      if(rack&&d.rackSupply&&!l.isClientSide)popResource(l,p,new ItemStack(NetLab.RACK_SUPPLY.get()));
      if(rack&&!l.isClientSide){popResource(l,p,dev.netlab.api.RackDevices.item(d.device));if(d.upperDevice!=null)popResource(l,p,dev.netlab.api.RackDevices.item(d.upperDevice));}
    }
    super.onRemove(old, l, p, next, moved);
  }
  @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){
    var drops=new java.util.ArrayList<>(super.getDrops(state,builder));var entity=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
    if(kind.equals("pc")&&entity instanceof DeviceEntity host)for(var stack:drops)if(stack.is(asItem())){
      var configured=MemoryCards.deviceItem(this,host.device);stack.set(NetLab.CONFIGURATION.get(),configured.get(NetLab.CONFIGURATION.get()));stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,configured.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
    }
    if(!kind.equals("pc")&&entity instanceof DeviceEntity host)for(var d:java.util.Arrays.asList(host.device,host.upperDevice))if(d!=null&&MemoryCards.present(d)&&!rack){MemoryCards.sync(d);drops.add(MemoryCards.cardItem(d));}
    return drops;
  }
  @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
    if(kind.equals("pc")&&!level.isClientSide&&player.isCreative()&&level.getBlockEntity(pos) instanceof DeviceEntity host)popResource(level,pos,MemoryCards.deviceItem(this,host.device));
    return super.playerWillDestroy(level,pos,state,player);
  }
}
