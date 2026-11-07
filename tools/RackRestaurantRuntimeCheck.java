import dev.netlab.*;
import dev.netlab.compat.RackCarry;
import dev.netlab.core.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.item.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
import java.nio.file.*;
import java.util.*;

/** Installed Carry On + Structurize NBT/bytecode fixtures. No client, server, Level or GameTest. */
public final class RackRestaurantRuntimeCheck {
    static int checks;
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    static final class Marker extends Block {
        Marker(){super(Properties.of());}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_FACING);}
    }
    enum RackVariant implements net.minecraft.util.StringRepresentable {
        EMPTY,AIR; public String getSerializedName(){return this==EMPTY?"blockrackempty":"blockrackair";}
    }
    static final class StorageMarker extends Block {
        static final EnumProperty<RackVariant> VARIANT=EnumProperty.create("variant",RackVariant.class);
        StorageMarker(){super(Properties.of());}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_FACING,VARIANT);}
    }
    static void setup()throws Exception{
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),new ArrayList<>(),Map.of());
        var mods=net.neoforged.fml.ModList.of(List.of(),List.of());
        var index=net.neoforged.fml.ModList.class.getDeclaredField("indexedMods");index.setAccessible(true);index.set(mods,Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        ((MappedRegistry<?>)BuiltInRegistries.BLOCK).unfreeze();((MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();((MappedRegistry<?>)BuiltInRegistries.BLOCK_ENTITY_TYPE).unfreeze();
        var deviceBlocks=new ArrayList<Block>();
        var kinds=Map.ofEntries(Map.entry("router","router"),Map.entry("switch","switch"),Map.entry("pc","pc"),Map.entry("laptop","pc"),Map.entry("access_point","ap"),Map.entry("traffic_controller","traffic"),Map.entry("rack","rack"),Map.entry("rack_router","router"),Map.entry("rack_switch","switch"),Map.entry("energy_cabinet","energy"),Map.entry("creative_energy_source","energy_source"));
        for(var entry:NetLab.BLOCKS.getEntries()){
            String path=entry.getId().getPath();
            Block block=kinds.containsKey(path)?new DeviceBlock(BlockBehaviour.Properties.of(),kinds.get(path),path.startsWith("rack")):new Block(BlockBehaviour.Properties.of());
            Registry.register(BuiltInRegistries.BLOCK,entry.getId(),block);if(block instanceof DeviceBlock)deviceBlocks.add(block);
        }
        for(var entry:NetLab.ITEMS.getEntries()){
            var block=BuiltInRegistries.BLOCK.getOptional(entry.getId());
            var item=block.isPresent()?new BlockItem(block.get(),new Item.Properties()):new Item(new Item.Properties());
            Registry.register(BuiltInRegistries.ITEM,entry.getId(),item);
            if(item instanceof BlockItem bi)bi.registerBlocks(Item.BY_BLOCK,item);
        }
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,ResourceLocation.parse("netlab:device"),BlockEntityType.Builder.of(DeviceEntity::new,deviceBlocks.toArray(Block[]::new)).build(null));
        Registry.register(BuiltInRegistries.BLOCK,ResourceLocation.parse("minecolonies:blockhutcook"),new Marker());
        Registry.register(BuiltInRegistries.BLOCK,ResourceLocation.parse("minecolonies:blockhutkitchen"),new Marker());
        Registry.register(BuiltInRegistries.BLOCK,ResourceLocation.parse("minecolonies:blockminecoloniesrack"),new StorageMarker());
    }
    static void carry(RegistryAccess registries)throws Exception{
        var state=NetLab.RACK.get().defaultBlockState().setValue(DeviceBlock.FACING,net.minecraft.core.Direction.WEST);
        var rack=new DeviceEntity(new BlockPos(2,64,3),state);
        rack.device=new Device("router");Hardware.ensure(rack.device);rack.device.hostname="CARRIED-ROUTER";rack.device.ports.getFirst().ip="192.168.70.1";rack.device.os().enableSecret="fixture-private-secret";MemoryCards.demo(rack.device);MemoryCards.sync(rack.device);
        rack.upperDevice=new Device("switch");Hardware.ensure(rack.upperDevice);rack.upperDevice.hostname="CARRIED-SWITCH";rack.upperDevice.extensions.put("fixture-program","persistent automation recipe");MemoryCards.demo(rack.upperDevice);MemoryCards.sync(rack.upperDevice);
        rack.rackSupply=true;rack.rackSupplyId=UUID.randomUUID().toString();rack.energy=1234;
        check(RackCarry.refusal(null,rack)==null,"rack with both bays and no cables is allowed");
        rack.links.put(128,new DeviceEntity.Peer(55L,0));check(RackCarry.refusal(null,rack)!=null,"upper-bay network/console cable prevents pickup");rack.links.clear();
        rack.powerLinks.put(300,new PowerSystem.Peer(55L,512,"supply","device",true,List.of()));check(RackCarry.refusal(null,rack)!=null,"power cable prevents pickup");rack.powerLinks.clear();
        dev.netlab.api.RackDevices.Guard denyUpper=(player,host,bay)->bay!=1;dev.netlab.api.RackDevices.GUARDS.add(denyUpper);
        try{check(RackCarry.refusal(null,rack)!=null,"ownership of the upper device is checked too");}finally{dev.netlab.api.RackDevices.GUARDS.remove(denyUpper);}
        var data=new CompoundTag();data.putString("type","BLOCK");data.put("block",NbtUtils.writeBlockState(state));data.put("tile",rack.saveWithId(registries));
        var carry=new tschipp.carryon.common.carry.CarryOnData(data);
        Tag serialized=tschipp.carryon.common.carry.CarryOnData.CODEC.encodeStart(NbtOps.INSTANCE,carry).getOrThrow();
        var decoded=tschipp.carryon.common.carry.CarryOnData.CODEC.parse(NbtOps.INSTANCE,serialized).getOrThrow();
        var pos=new BlockPos(20,75,30);var placed=(DeviceEntity)decoded.getBlockEntity(pos,registries);
        check(placed!=null&&placed.getBlockPos().equals(pos),"real Carry On codec recreates the block entity at the new position");
        check(placed.device.identity.equals(rack.device.identity)&&placed.upperDevice.identity.equals(rack.upperDevice.identity),"both physical device identities survive the move");
        check(placed.device.hostname.equals("CARRIED-ROUTER")&&placed.device.ports.getFirst().ip.equals("192.168.70.1"),"router's saved configuration survives");
        check(placed.device.os().enableSecret.equals("fixture-private-secret")&&MemoryCards.present(placed.device)&&MemoryCards.present(placed.upperDevice),"both memory cards and saved credentials remain installed");
        check(placed.upperDevice.extensions.get("fixture-program").equals("persistent automation recipe"),"upper-bay device extensions survive");
        check(placed.rackSupply&&placed.rackSupplyId.equals(rack.rackSupplyId)&&placed.energy==1234,"rack power supply identity and stored FE survive");
        check(placed.links.isEmpty()&&placed.powerLinks.isEmpty(),"no old cable endpoints are resurrected");
        check(placed.getBlockState().getValue(DeviceBlock.FACING)==net.minecraft.core.Direction.WEST,"carried block state retains orientation");
        // The installed Carry On removes the BE before the block. DeviceBlock.onRemove therefore cannot drop the same boards again.
        var node=new ClassNode();new ClassReader(Ae2AutomationRuntimeCheck.bytes("tschipp.carryon.common.carry.PickupHandler")).accept(node,0);
        var method=node.methods.stream().filter(m->m.name.equals("tryPickUpBlock")).findFirst().orElseThrow();
        int removeEntity=-1,removeBlock=-1,i=0;
        for(var instruction:method.instructions){if(instruction instanceof MethodInsnNode call){if(call.name.equals("removeBlockEntity"))removeEntity=i;if(call.name.equals("removeBlock"))removeBlock=i;}i++;}
        check(removeEntity>=0&&removeBlock>removeEntity,"installed Carry On prevents duplicate rack-device drops by removing its BE first");
    }
    static void blueprints(RegistryAccess registries)throws Exception{
        Path root=Path.of("src/main/resources/blueprints/netlab/netlab_fastfood");
        var json=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("pack.json"))).getAsJsonObject();
        var meta=new com.ldtteam.structurize.storage.StructurePackMeta(json,root,"netlab");
        check(meta!=null,"real Structurize accepts style metadata");
        var files=new ArrayList<Path>();try(var paths=Files.walk(root)){paths.filter(p->p.toString().endsWith(".blueprint")).forEach(files::add);}
        check(files.size()==10,"restaurant and cookery each provide all five levels");
        for(var file:files){
            var tag=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());
            var blueprint=com.ldtteam.structurize.blueprints.v1.BlueprintUtil.readBlueprintFromNBT(tag,registries);
            check(blueprint!=null&&blueprint.getSizeX()==25&&blueprint.getSizeY()==12&&blueprint.getSizeZ()==25,"real Structurize reads dimensions: "+file);
            check(blueprint.getPrimaryBlockOffset().equals(new BlockPos(5,1,9)),"primary position stays fixed across upgrades: "+file);
            var palette=blueprint.getPalette();var packed=blueprint.getStructure();boolean cook=file.getFileName().toString().startsWith("cookery");
            var primary=palette[packed[1][9][5]];
            check(BuiltInRegistries.BLOCK.getKey(primary.getBlock()).toString().equals(cook?"minecolonies:blockhutkitchen":"minecolonies:blockhutcook"),"correct functional workplace marker: "+file);
            var specs=tag.getList("palette",Tag.TAG_COMPOUND);
            for(int entry=0;entry<specs.size();entry++){
                var spec=specs.getCompound(entry);var actual=palette[entry];
                check(BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals(spec.getString("Name")),"palette block resolves without silently becoming air");
                for(String key:spec.getCompound("Properties").getAllKeys()){
                    var property=actual.getBlock().getStateDefinition().getProperty(key);
                    check(property!=null&&actual.getValues().get(property).toString().equals(spec.getCompound("Properties").getString(key)) || property instanceof EnumProperty && property.getName().equals("variant"),"block property is legal: "+key);
                }
            }
            check(Arrays.stream(palette).noneMatch(p->BuiltInRegistries.BLOCK.getKey(p.getBlock()).getNamespace().equals("netlab")),"decorative easter egg is never a building material");
            var tile=blueprint.getTileEntityData(BlockPos.ZERO,new BlockPos(5,1,9));
            check(tile!=null&&tile.getInt("colony")==0&&tile.getCompound("blueprintDataProvider").getString("pack").equals("Вкусно и точка"),"workplace is unbound to a world and carries the selected style: "+file);
        }
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("mixin.service",Ae2AutomationRuntimeCheck.Service.class.getName());System.setProperty("mixin.bootstrapService",Ae2AutomationRuntimeCheck.Bootstrap.class.getName());
        MixinBootstrap.init();MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.SERVER);Mixins.addConfiguration("netlab.mixins.json");
        var transformer=((Ae2AutomationRuntimeCheck.Service)MixinService.getService()).transformer();
        String target="tschipp.carryon.common.carry.PickupHandler";byte[] original=Ae2AutomationRuntimeCheck.bytes(target);byte[] modified=transformer.transformClassBytes(target,target,original);
        var transformed=new ClassNode();new ClassReader(modified).accept(transformed,0);
        check(!Arrays.equals(original,modified)&&transformed.methods.stream().anyMatch(m->m.name.contains("netlab$checkRack")),"real Mixin transformer applies to installed Carry On 2.2.6.13");
        setup();var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);carry(registries);blueprints(registries);
        System.out.println("NetLab rack/restaurant: "+checks+" checks passed; no Minecraft startup or world created.");
    }
}
