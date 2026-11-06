import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.mobile.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.*;

/** Registry/NBT/payload boundary checks only. Does not instantiate a level, server or client. */
public class MobileRuntimeCheck {
    static int checks;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    public static void main(String[] args)throws Exception{
        FabricationRuntimeCheck.main(args);
        var kinds=Map.of("sector_antenna",MobileHardware.ANTENNA,"radio_unit",MobileHardware.RRU,"baseband_unit",MobileHardware.BBU,"mobile_core",MobileHardware.CORE,"sim_station",MobileHardware.STATION);
        for(var entry:Mobile.BLOCKS.getEntries())Registry.register(BuiltInRegistries.BLOCK,entry.getId(),entry.getId().getPath().equals("sector_antenna")?new SectorAntennaBlock(BlockBehaviour.Properties.of()):new MobileBlock(BlockBehaviour.Properties.of(),kinds.get(entry.getId().getPath())));
        for(var entry:Mobile.ITEMS.getEntries()){
            var id=entry.getId();String module=MobileHardware.MODULES.stream().filter(m->ModuleItem.itemId(m).equals(id.getPath())).findFirst().orElse(null);
            Item item=id.getPath().equals("phone_diagnostics_module")?new dev.netlab.api.PortableModules.ModuleItem(new Item.Properties(),"CISCO-POCKET-NET","diagnostics","Phone diagnostics"):module!=null?new ModuleItem(new Item.Properties(),module):BuiltInRegistries.BLOCK.containsKey(id)?new BlockItem(BuiltInRegistries.BLOCK.get(id),new Item.Properties()):new Item(new Item.Properties());
            Registry.register(BuiltInRegistries.ITEM,id,item);if(item instanceof BlockItem b)b.registerBlocks(Item.BY_BLOCK,item);
        }
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath(Mobile.ID,"mobile_device"),BlockEntityType.Builder.of(MobileEntity::new,Mobile.ANTENNA.get(),Mobile.RRU.get(),Mobile.BBU.get(),Mobile.CORE.get(),Mobile.STATION.get()).build(null));
        Mobile.integrate();var regs=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        antenna(regs);
        frontPanels();
        var core=RackDevices.restore(new ItemStack(Mobile.CORE.get()),MobileHardware.CORE);
        check(core.ports.size()==3&&core.ports.get(1).console&&MobileHardware.cellular(core)==2,"fresh rack EPC restores proper ports, not generic switch ports");
        var bbu=RackDevices.restore(new ItemStack(Mobile.BBU.get()),MobileHardware.BBU);check(Hardware.slots(bbu).keySet().containsAll(List.of("control0","baseband0","fronthaul0")),"rack BBU has module bays");
        saveCommands(core,bbu);
        var state=new MobileState();state.owner=UUID.randomUUID().toString();state.adminVerifier=MobileAuth.credential("operator-password");state.coreAddress="192.168.77.2";
        var sim=new SimCredentials();sim.issuer=core.identity;sim.operator=state.plmn;sim.label="Runtime SIM";state.subscribers.provision(sim);state.subscribers.activate(sim.id,MobileAuth.proof(sim.key,"challenge","activate|"+sim.id),"challenge");state.save(core);MemoryCards.demo(core);core.ports.get(0).ip="192.168.77.2";MemoryCards.sync(core);
        var item=RackDevices.item(core);var restored=RackDevices.restore(ItemStack.parse(regs,item.save(regs)).orElseThrow(),MobileHardware.CORE);
        check(restored.identity.equals(core.identity)&&MobileState.get(restored).subscribers.entries.get(sim.id).active,"moving core preserves physical issuer and active subscriber registry");
        var copy=RackDevices.restore(new ItemStack(Mobile.CORE.get()),MobileHardware.CORE);String newIdentity=copy.identity;var ownerState=new MobileState();ownerState.owner=UUID.randomUUID().toString();ownerState.save(copy);copy.extensions.put("netlab.memory",core.extensions.get("netlab.memory"));MemoryCards.read(copy);
        check(copy.identity.equals(newIdentity)&&!copy.identity.equals(core.identity)&&MobileState.get(copy).owner.equals(ownerState.owner),"configuration card does not clone physical core identity or ownership");
        check(MobileState.get(copy).subscribers.entries.size()==1,"configuration card contains subscriber settings");
        var tag=new CompoundTag();StoredJson.put(tag,"device",MobileState.JSON.toJson(core));var publicTag=MemoryCards.publicTag(tag);String publicJson=StoredJson.get(publicTag,"device");
        check(!publicJson.contains(sim.key)&&!publicJson.contains(state.adminVerifier)&&!publicJson.contains("subscribers"),"block update does not disclose SIM keys or operator verifier");
        var physicalSim=MobileItems.sim(sim);var simSaved=ItemStack.parse(regs,physicalSim.save(regs)).orElseThrow();check(MobileItems.sim(simSaved).key.equals(sim.key),"SIM secrets persist on server-side item NBT");
        var publicItem=simSaved.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();check(!publicItem.toString().contains(sim.key)&&publicItem.getString("simLabel").equals(sim.label),"public SIM item components include label but not key");
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),regs);NetLab.CONFIGURATION.get().streamCodec().encode(buf,MobileItems.privateTag(physicalSim));check(buf.readableBytes()==0&&NetLab.CONFIGURATION.get().streamCodec().decode(buf).isEmpty(),"private SIM component is empty on client wire");buf.release();
        var machine=new MobileEntity(BlockPos.ZERO,Mobile.STATION.get().defaultBlockState());machine.blank=new ItemStack(Mobile.BLANK_SIM.get());machine.pending=sim;machine.pendingCore="192.168.77.2";machine.phase="activate";machine.output=ItemStack.EMPTY;
        var disk=machine.saveWithoutMetadata(regs);var machine2=new MobileEntity(BlockPos.ZERO,Mobile.STATION.get().defaultBlockState());machine2.loadWithComponents(disk,regs);
        check(machine2.pending.id.equals(sim.id)&&machine2.pending.key.equals(sim.key)&&machine2.phase.equals("activate")&&machine2.blank.getCount()==1,"pending physical SIM transaction survives device NBT reload");
        check(!machine.getUpdateTag(regs).contains("mobilePhysical"),"station update never exposes pending physical inventory secrets");
        var phone=new ItemStack(Mobile.PHONE.get());String phoneId=MobileItems.phoneId(phone);MobileItems.setPhoneSim(phone,sim);var phone2=ItemStack.parse(regs,phone.save(regs)).orElseThrow();check(MobileItems.phoneId(phone2).equals(phoneId)&&MobileItems.sim(phone2).key.equals(sim.key),"phone preserves installed SIM and physical handset ID");MobileItems.setPhoneSim(phone2,null);check(MobileItems.sim(phone2)==null,"phone SIM eject removes credential");
        var pc=new Device("pc");Hardware.initializeHost(pc,true);Hardware.ensure(pc).powered=false;Hardware.install(pc,"mobile0",MobileHardware.MODEM);var pcState=new MobileState();pcState.sim=sim;pcState.save(pc);
        boolean rejected=false;try{Hardware.remove(pc,"mobile0");}catch(IllegalArgumentException e){rejected=true;}check(rejected&&MobileHardware.modem(pc),"cannot strand physical SIM by removing occupied modem");
        var pcItem=MemoryCards.deviceItem(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("netlab","laptop")),pc);var pc2=RackDevices.restore(ItemStack.parse(regs,pcItem.save(regs)).orElseThrow(),"pc");check(MobileState.get(pc2).sim.key.equals(sim.key)&&MobileHardware.modem(pc2),"laptop drop preserves SIM in built-in private memory");
        for(String m:MobileHardware.MODULES)check(ModuleItem.find(m)==Mobile.MODULE_ITEMS.get(m).get()&&!ModuleFabrication.MODULES.contains(m),"addon module removable as correct item, unavailable in assembler: "+m);
        var action=new MobilePackets.Action(BlockPos.ZERO,1,"phone-id","pair","sample-password");buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),regs);MobilePackets.Action.CODEC.encode(buf,action);check(MobilePackets.Action.CODEC.decode(buf).equals(action),"mobile request codec round trip");buf.release();
        var response=new MobilePackets.State(BlockPos.ZERO,1,"","{\"status\":\"SIM готова\"}",true);buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),regs);MobilePackets.State.CODEC.encode(buf,response);check(MobilePackets.State.CODEC.decode(buf).equals(response),"mobile response codec round trip");buf.release();
        System.out.println(checks+" mobile registry/NBT/security checks passed; no client, server or world launched.");
    }
    static void saveCommands(Device core,Device bbu){
        var n=new Network();n.devices.put(1L,core);n.devices.put(2L,bbu);
        for(var device:List.of(core,bbu,new Device("server"),new Device("endpoint"),new Device("router"),new Device("switch"))){
            boolean rejected=false;try{MemoryCards.write(device);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("CISCO");}
            check(rejected,"explicit save without card reports missing CISCO card: "+device.kind);
            MemoryCards.demo(device);device.hostname="Saved-"+device.kind;device.extensions.put("save-check","live addon configuration");
            var card=MemoryCards.card(device);card.active=false;device.extensions.put("netlab.memory",MobileState.JSON.toJson(card));
            check(MemoryCards.write(device).contains("[OK]")&&MemoryCards.card(device).active,"write activates inserted card: "+device.kind);
            String hostname=device.hostname;device.hostname="Unsaved";device.extensions.put("save-check","modified");MemoryCards.read(device);
            check(device.hostname.equals(hostname)&&device.extensions.get("save-check").equals("live addon configuration"),"write stores running state instead of reading stale image: "+device.kind);
            check(ConfigStore.restore(device.os().startup).hostname.equals(hostname),"startup configuration agrees with physical card: "+device.kind);
        }
        Hardware.ensure(bbu).powered=false;
        Hardware.install(bbu,"control0",MobileHardware.CONTROL);Hardware.install(bbu,"baseband0",MobileHardware.BASEBAND);Hardware.install(bbu,"fronthaul0",MobileHardware.FRONTHAUL);Hardware.ensure(bbu).powered=true;
        int port=bbu.findInterface("Fronthaul0/0",false);check(port>=0&&bbu.ports.get(port).up,"new fronthaul module starts enabled");
        // Reproduce the user's saved state, without silently overriding deliberate shutdown.
        for(var p:bbu.ports)if(p.medium.equals("fronthaul"))p.up=false;
        var cli=new Console();for(String command:List.of("enable","configure terminal","interface Fronthaul0/0","no shutdown","end"))check(!cli.execute(bbu,n,2,command).startsWith("% Interface does not exist"),"BBU handbook command: "+command);
        MemoryCards.write(bbu);bbu.ports.get(port).up=false;MemoryCards.read(bbu);check(bbu.ports.get(port).up,"documented BBU no shutdown survives saving and reading card");
        var host=new MobileEntity(net.minecraft.core.BlockPos.ZERO,Mobile.BBU.get().defaultBlockState());
        host.device=bbu;var target=new ConsoleAccess.Target(host,bbu,0,cli);
        check(ConsoleExtensions.complete(target,"wr").value().strip().equals("write"),"Mobile Tab falls back to shared write grammar");
        cli.pending="enable";check(ConsoleExtensions.complete(target,"memory").choices().isEmpty(),"memory completion cannot disclose commands at password prompt");
    }
    static void antenna(RegistryAccess registries)throws Exception{
        var block=(SectorAntennaBlock)Mobile.ANTENNA.get();var lower=block.defaultBlockState();var upper=lower.setValue(SectorAntennaBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        check(!SectorAntennaBlock.upper(lower)&&SectorAntennaBlock.upper(upper),"two-block antenna defaults to lower half");
        check(block.newBlockEntity(BlockPos.ZERO,lower) instanceof MobileEntity&&block.newBlockEntity(BlockPos.ZERO.above(),upper)==null,"one antenna owns one device, no duplicate upper radio/ports");
        check(upper.updateShape(Direction.DOWN,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),null,BlockPos.ZERO.above(),BlockPos.ZERO).isAir(),"orphan upper antenna disappears");
        check(!upper.updateShape(Direction.DOWN,lower,null,BlockPos.ZERO.above(),BlockPos.ZERO).isAir(),"upper half survives on matching lower half");
        check(lower.updateShape(Direction.UP,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),null,BlockPos.ZERO,BlockPos.ZERO.above())==lower,"legacy lower half survives until empty-space migration");
        var getDrops=SectorAntennaBlock.class.getDeclaredMethod("getDrops",net.minecraft.world.level.block.state.BlockState.class,net.minecraft.world.level.storage.loot.LootParams.Builder.class);getDrops.setAccessible(true);
        check(((java.util.List<?>)getDrops.invoke(block,upper,null)).isEmpty(),"upper half cannot yield a duplicate antenna item");
        for(Direction facing:Direction.Plane.HORIZONTAL){
            var bottom=lower.setValue(DeviceBlock.FACING,facing);var top=upper.setValue(DeviceBlock.FACING,facing);var a=bottom.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).bounds();var b=top.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).bounds();
            check(a.minY==0&&a.maxY==1&&b.minY==0&&b.maxY==1,"both antenna cells have full panel height: "+facing);
            boolean bottomTouches=switch(facing){case NORTH->a.maxZ==1;case SOUTH->a.minZ==0;case EAST->a.minX==0;case WEST->a.maxX==1;default->false;};
            boolean topTouches=switch(facing){case NORTH->b.maxZ==1;case SOUTH->b.minZ==0;case EAST->b.minX==0;case WEST->b.maxX==1;default->false;};
            check(bottomTouches&&topTouches,"both brackets reach rear neighbor boundary: "+facing);
            var moved=upper.updateShape(Direction.DOWN,bottom,null,BlockPos.ZERO.above(),BlockPos.ZERO);check(moved.getValue(DeviceBlock.FACING)==facing,"upper panel follows base orientation: "+facing);
            check(bottom.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(DeviceBlock.FACING)==facing.getClockWise(),"antenna rotation supported: "+facing);
            check(top.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT).getValue(SectorAntennaBlock.HALF)==top.getValue(SectorAntennaBlock.HALF),"mirror preserves upper half: "+facing);
        }
        var ops=RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,registries);var legacyJson=com.google.gson.JsonParser.parseString("{\"Name\":\"netlabmobile:sector_antenna\",\"Properties\":{\"facing\":\"west\",\"rack_part\":\"0\"}}");
        var legacy=net.minecraft.world.level.block.state.BlockState.CODEC.parse(ops,legacyJson).getOrThrow();check(!SectorAntennaBlock.upper(legacy)&&legacy.getValue(DeviceBlock.FACING)==Direction.WEST,"old antenna state retains facing and becomes lower half");
        var folder=java.nio.file.Path.of("src/mobile/resources/assets/netlabmobile");var variants=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(folder.resolve("blockstates/sector_antenna.json"))).getAsJsonObject().getAsJsonObject("variants");check(variants.size()==8,"eight facing/half model variants");
        for(String name:List.of("block/sector_antenna","block/sector_antenna_upper","item/sector_antenna")){var json=java.nio.file.Files.readString(folder.resolve("models/"+name+".json"));var model=net.minecraft.client.renderer.block.model.BlockModel.fromString(json);check(!model.getElements().isEmpty(),"antenna baked model parses: "+name);}
    }
    static void frontPanels()throws Exception{
        var models=Map.of(MobileHardware.ANTENNA,"sector_antenna",MobileHardware.RRU,"radio_unit",MobileHardware.BBU,"baseband_unit",MobileHardware.CORE,"mobile_core",MobileHardware.STATION,"sim_station");
        for(var e:models.entrySet()){
            var device=new Device(e.getKey());MobileHardware.initialize(device);
            if(e.getKey().equals(MobileHardware.BBU)){Hardware.ensure(device).powered=false;Hardware.install(device,"fronthaul0",MobileHardware.FRONTHAUL);}
            var json=java.nio.file.Files.readString(java.nio.file.Path.of("src/mobile/resources/assets/netlabmobile/models/block/"+e.getValue()+".json"));var model=net.minecraft.client.renderer.block.model.BlockModel.fromString(json);
            var state=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(Mobile.ID,e.getValue())).defaultBlockState();var profile=PortLayout.profile(device);
            for(var jack:PortLayout.jacks(device,false,0))for(double dx:new double[]{-.45*jack.width(),0,.45*jack.width()})for(double dy:new double[]{-.45*jack.height(),0,.45*jack.height()}){
                double x=jack.x()+dx,y=jack.y()+dy;double front=model.getElements().stream().filter(box->x*16>=box.from.x&&x*16<=box.to.x&&y*16>=box.from.y&&y*16<=box.to.y).mapToDouble(box->box.from.z/16).min().orElseThrow();
                check(jack.z()<front,"visible socket lies ahead of actual baked model, across full click area: "+e.getValue());
                var hits=state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).clip(new net.minecraft.world.phys.Vec3(x,y,-1),new net.minecraft.world.phys.Vec3(x,y,1),BlockPos.ZERO);
                check(hits!=null&&hits.getDirection()==Direction.NORTH,"visible socket area is reachable through real block selection shape: "+e.getValue());
            }
            if(!e.getKey().equals(MobileHardware.ANTENNA)){double front=model.getElements().stream().filter(box->profile.powerX()*16>=box.from.x&&profile.powerX()*16<=box.to.x&&profile.powerY()*16>=box.from.y&&profile.powerY()*16<=box.to.y).mapToDouble(box->box.from.z/16).min().orElseThrow();check(profile.face()<front,"PWR indicator is outside model face: "+e.getValue());}
        }
    }
}
