import dev.netlab.*;
import dev.netlab.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.flag.FeatureFlags;
import java.util.*;

/** Standalone registry/menu fixture: never creates a client, server, level or GameTest. */
public class FabricationRuntimeCheck {
    static int checks;
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
    public static void main(String[] args)throws Exception{
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),new ArrayList<>(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        ((MappedRegistry<?>)BuiltInRegistries.DATA_COMPONENT_TYPE).unfreeze();Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,ResourceLocation.fromNamespaceAndPath("netlab","configuration"),net.minecraft.core.component.DataComponentType.<net.minecraft.nbt.CompoundTag>builder().persistent(net.minecraft.nbt.CompoundTag.CODEC).networkSynchronized(net.minecraft.network.codec.StreamCodec.of((buffer,value)->{},buffer->new net.minecraft.nbt.CompoundTag())).build());
        ((MappedRegistry<?>)BuiltInRegistries.BLOCK).unfreeze();((MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();((MappedRegistry<?>)BuiltInRegistries.MENU).unfreeze();((MappedRegistry<?>)BuiltInRegistries.BLOCK_ENTITY_TYPE).unfreeze();
        for(var entry:NetLab.BLOCKS.getEntries()){
            var id=entry.getId();Block block=id.getPath().equals("module_assembler")?new ModuleAssemblerBlock(BlockBehaviour.Properties.of()):new Block(BlockBehaviour.Properties.of());
            Registry.register(BuiltInRegistries.BLOCK,id,block);
        }
        for(var entry:NetLab.ITEMS.getEntries()){
            String module=Hardware.CATALOG.keySet().stream().filter(m->ModuleItem.itemId(m).equals(entry.getId().getPath())).findFirst().orElse(null);
            Item item=module!=null?new ModuleItem(new Item.Properties(),module):BuiltInRegistries.BLOCK.containsKey(entry.getId())?new BlockItem(BuiltInRegistries.BLOCK.get(entry.getId()),new Item.Properties()):new Item(new Item.Properties());Registry.register(BuiltInRegistries.ITEM,entry.getId(),item);if(item instanceof BlockItem blockItem)blockItem.registerBlocks(Item.BY_BLOCK,item);
        }
        Registry.register(BuiltInRegistries.MENU,ResourceLocation.fromNamespaceAndPath("netlab","module_assembler"),new MenuType<>(ModuleAssemblerMenu::new,FeatureFlags.VANILLA_SET));
        for(int index=0;index<ModuleFabrication.MODULES.size();index++){
            var inventory=new Inventory(null);var menu=new ModuleAssemblerMenu(1,inventory);var cost=ModuleFabrication.cost(ModuleFabrication.MODULES.get(index));
            check(menu.clickMenuButton(null,index),"valid module selection");
            menu.getSlot(0).set(new ItemStack(NetLab.BASIC_BOARD.get(),cost.boards()));menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT,cost.copper()));
            check(menu.getSlot(3).getItem().isEmpty(),"missing redstone cannot produce module");
            menu.getSlot(2).set(new ItemStack(Items.REDSTONE,cost.redstone()));check(menu.canCraft(),"exact quantities fabricate");
            var taken=menu.quickMoveStack(null,3);
            check(taken.is(NetLab.MODULE_ITEMS.get(ModuleFabrication.MODULES.get(index)).get())&&taken.getCount()==1,"shift click yields correct module");
            check(menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(2).getItem().isEmpty(),"craft consumes exact quantities once");
            check(menu.quickMoveStack(null,3).isEmpty()&&!menu.canCraft(),"empty station cannot duplicate output");
            int amount=0;for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(taken.getItem()))amount+=inventory.getItem(i).getCount();
            check(amount==1,"one crafted module reaches inventory");
        }
        var full=new Inventory(null);for(int i=0;i<36;i++)full.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        var menu=new ModuleAssemblerMenu(2,full);var c=menu.cost();
        menu.getSlot(0).set(new ItemStack(NetLab.BASIC_BOARD.get(),c.boards()));menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT,c.copper()));menu.getSlot(2).set(new ItemStack(Items.REDSTONE,c.redstone()));
        check(menu.quickMoveStack(null,3).isEmpty()&&menu.canCraft(),"full inventory preserves ingredients and output");
        check(!menu.clickMenuButton(null,-1)&&!menu.clickMenuButton(null,9999),"invalid selection rejected");
        menu.getSlot(1).set(new ItemStack(Items.STONE,64));check(!menu.canCraft()&&menu.getSlot(3).getItem().isEmpty(),"wrong ingredient cannot fabricate");
        var ops=RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var obtainable=new HashSet<String>(List.of("netlab:ciscium_ore","netlab:deepslate_ciscium_ore","netlab:raw_ciscium"));
        for(var entry:dev.netlab.automation.Automation.BLOCKS.getEntries()){
            String kind=switch(entry.getId().getPath()){case "management_server"->"server";case "multiinterface"->"endpoint";default->"sniffer";};
            Registry.register(BuiltInRegistries.BLOCK,entry.getId(),new dev.netlab.automation.AutomationBlock(BlockBehaviour.Properties.of(),kind));
        }
        for(String folder:new String[]{"src/main/resources/data/netlab/recipe","src/automation/resources/data/netlabautomation/recipe"}){
            // Recipe parsing additionally checks all custom result and ingredient item IDs.
            for(var entry:dev.netlab.automation.Automation.ITEMS.getEntries())if(!BuiltInRegistries.ITEM.containsKey(entry.getId())){
                String module=dev.netlab.automation.core.ServerHardware.MODULES.stream().filter(m->ModuleItem.itemId(m).equals(entry.getId().getPath())).findFirst().orElse(null);
                Item item=entry.getId().getPath().equals("phone_automation_module")?new dev.netlab.api.PortableModules.ModuleItem(new Item.Properties(),"CISCO-POCKET-AUTOMATION","automation","Phone automation"):module==null?new BlockItem(BuiltInRegistries.BLOCK.get(entry.getId()),new Item.Properties()):new ModuleItem(new Item.Properties(),module);
                Registry.register(BuiltInRegistries.ITEM,entry.getId(),item);if(item instanceof BlockItem b)b.registerBlocks(Item.BY_BLOCK,item);
            }
            try(var files=java.nio.file.Files.list(java.nio.file.Path.of(folder))){for(var file:files.filter(p->p.toString().endsWith(".json")).toList()){
                var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file));
                var recipe=net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops,json).getOrThrow();var result=recipe.getResultItem(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));check(!result.isEmpty(),"recipe result: "+file);
                obtainable.add(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
                if(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting){
                    var definition=json.getAsJsonObject();var stacks=new ArrayList<ItemStack>();int width=3,height=3;
                    if(definition.has("pattern")){
                        var pattern=definition.getAsJsonArray("pattern");height=pattern.size();width=pattern.get(0).getAsString().length();
                        for(var row:pattern)for(char symbol:row.getAsString().toCharArray())stacks.add(symbol==' '?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(definition.getAsJsonObject("key").getAsJsonObject(String.valueOf(symbol)).get("item").getAsString()))));
                    }else{
                        for(var ingredient:definition.getAsJsonArray("ingredients"))stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient.getAsJsonObject().get("item").getAsString()))));
                        while(stacks.size()<9)stacks.add(ItemStack.EMPTY);
                    }
                    var input=net.minecraft.world.item.crafting.CraftingInput.of(width,height,stacks);
                    check(crafting.matches(input,null)&&crafting.assemble(input,RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)).is(result.getItem()),"recipe matches and creates its advertised item: "+file);
                }
            }}
        }
        for(var item:NetLab.ITEMS.getEntries()){
            String id=item.getId().toString(),name=item.getId().getPath();if(name.startsWith("module_")&&!name.equals("module_assembler")||name.equals("wall_power_cable")||name.equals("creative_energy_source"))continue;
            check(obtainable.contains(id),"ordinary item has a recipe or natural ore source: "+id);
        }
        var ore=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/data/netlab/worldgen/configured_feature/ore_ciscium.json")));
        check(net.minecraft.world.level.levelgen.feature.ConfiguredFeature.DIRECT_CODEC.parse(ops,ore).result().isPresent(),"configured ore feature parses");
        var placement=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/data/netlab/worldgen/placed_feature/ore_ciscium.json"))).getAsJsonObject();
        for(var modifier:placement.getAsJsonArray("placement"))check(net.minecraft.world.level.levelgen.placement.PlacementModifier.CODEC.parse(ops,modifier).result().isPresent(),"ore placement modifier parses: "+modifier);
        var batchInventory=new Inventory(null);var batch=new ModuleAssemblerMenu(3,batchInventory);var cost=batch.cost();
        batch.getSlot(0).set(new ItemStack(NetLab.BASIC_BOARD.get(),cost.boards()*3));batch.getSlot(1).set(new ItemStack(Items.COPPER_INGOT,cost.copper()*3));batch.getSlot(2).set(new ItemStack(Items.REDSTONE,cost.redstone()*3));
        var normal=batch.getSlot(3).remove(1);batch.getSlot(3).onTake(null,normal);
        check(normal.getCount()==1&&batch.getSlot(0).getItem().getCount()==cost.boards()*2,"ordinary pickup consumes one recipe");
        check(!batch.quickMoveStack(null,3).isEmpty()&&!batch.quickMoveStack(null,3).isEmpty()&&batch.quickMoveStack(null,3).isEmpty(),"remaining ingredients support exactly two more crafts");
        management(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        portableConfiguration();
        System.out.println(checks+" real menu/recipe/configuration checks passed; no client, server or world launched.");
    }
    static final class HeadlessDevice extends DeviceEntity {
        HeadlessDevice(DeviceBlock block){super(net.minecraft.world.level.block.entity.BlockEntityType.Builder.<DeviceEntity>of((pos,state)->null,block).build(null),BlockPos.ZERO,block.defaultBlockState());}
    }
    static void portableConfiguration(){
        var block=new DeviceBlock(BlockBehaviour.Properties.of(),"endpoint",false);var host=new HeadlessDevice(block);var d=host.device;
        var handler=new dev.netlab.automation.AutomationConsole();var previous=dev.netlab.api.ConsoleExtensions.HANDLERS.put("endpoint",handler);
        var node=dev.netlab.automation.AutomationNode.of(host,0);MemoryCards.demo(d);
        var cli=new Console();var n=new Network();n.devices.put(1L,d);var lease=new PortableLease(d,0,cli);
        for(String command:List.of("enable","configure terminal","hostname PORTABLE-ENDPOINT","interface gig0/0","ip address 10.4.0.11 255.255.255.0","no shutdown","end"))cli.execute(d,n,1,command);
        node.manager="10.4.0.1";node.target=Direction.EAST;node.control=true;node.extract=false;
        var target=new ConsoleAccess.Target(host,d,host.slotId(0),cli);dev.netlab.api.ConsoleExtensions.flush(target);lease.close();
        dev.netlab.automation.AutomationNode.invalidate(d);var again=dev.netlab.automation.AutomationNode.of(host,0);
        check(again.manager.equals("10.4.0.1")&&again.target==Direction.EAST&&again.control&&!again.extract,"portable close flushes addon settings independently from command history");
        check(d.hostname.equals("PORTABLE-ENDPOINT")&&d.ports.getFirst().ip.equals("10.4.0.11")&&cli.history.isEmpty(),"portable close retains live network configuration and clears history only");
        MemoryCards.read(d);again=dev.netlab.automation.AutomationNode.of(host,0);
        check(again.manager.equals("10.4.0.1")&&again.target==Direction.EAST&&d.ports.getFirst().ip.equals("10.4.0.11"),"portable changes are on the physical CISCO card and survive its reload");
        var next=new PortableLease(d,1,new Console());check(next.cli.mode==0&&next.cli.history.isEmpty(),"next portable session contains no previous command transcript");
        var replacement=new Device("endpoint");host.device=replacement;dev.netlab.api.ConsoleExtensions.flush(target);
        check(replacement.ports.getFirst().ip.isEmpty()&&!replacement.extensions.containsKey("automation"),"closing a stale terminal cannot write old settings onto a replacement board");
        var volatileNode=dev.netlab.automation.AutomationNode.of(host,0);volatileNode.manager="10.4.0.1";replacement.ports.getFirst().ip="10.4.0.12";
        var volatileCli=new Console();var volatileLease=new PortableLease(replacement,2,volatileCli);dev.netlab.api.ConsoleExtensions.flush(new ConsoleAccess.Target(host,replacement,host.slotId(0),volatileCli));volatileLease.close();
        check(replacement.ports.getFirst().ip.equals("10.4.0.12")&&volatileNode.manager.equals("10.4.0.1"),"closing portable session without a card retains running settings until reboot");
        check(!MemoryCards.present(replacement)&&MemoryCards.saved(replacement).ports.getFirst().ip.isEmpty(),"terminal close does not bypass the existing physical memory-card requirement");
        var rack=new HeadlessDevice(new DeviceBlock(BlockBehaviour.Properties.of(),"rack",true));rack.upperDevice=new Device("server");var server=dev.netlab.automation.AutomationNode.of(rack,1);MemoryCards.demo(rack.upperDevice);
        var previousServer=dev.netlab.api.ConsoleExtensions.HANDLERS.put("server",handler);
        server.passwordHash="test-only-verifier";server.stocks.add(new dev.netlab.automation.core.AutomationProgram.Stock("10.4.0.11","10.4.0.12","minecraft:coal",-1,1,16,8));
        server.patterns.add(new dev.netlab.automation.core.AutomationProgram.Pattern("torches","10.4.0.11","10.4.0.13","10.4.0.12","minecraft:torch",16,List.of("minecraft:coal","-","-","minecraft:stick","-","-","-","-","-")));
        dev.netlab.api.ConsoleExtensions.flush(new ConsoleAccess.Target(rack,rack.upperDevice,rack.slotId(1),new Console()));MemoryCards.read(rack.upperDevice);var serverAgain=dev.netlab.automation.AutomationNode.of(rack,1);
        check(serverAgain.passwordHash.equals("test-only-verifier")&&serverAgain.stocks.size()==1&&serverAgain.patterns.size()==1,"portable flush preserves upper-rack server password, stock rules and crafting patterns on its card");
        if(previousServer==null)dev.netlab.api.ConsoleExtensions.HANDLERS.remove("server");else dev.netlab.api.ConsoleExtensions.HANDLERS.put("server",previousServer);
        if(previous==null)dev.netlab.api.ConsoleExtensions.HANDLERS.remove("endpoint");else dev.netlab.api.ConsoleExtensions.HANDLERS.put("endpoint",previous);
    }
    static void management(HolderLookup.Provider registries)throws Exception{
        var hints=new dev.netlab.core.TrafficMark("ITEM_DATA","netlab:"+"a".repeat(249),64);
        var event=new Study.Event(Long.MAX_VALUE,255,Long.MIN_VALUE,255,"SPAN/TCP",4094,false,Long.MAX_VALUE-1,Long.MAX_VALUE,60064,hints.phase(),hints.item(),hints.count());
        var traceJson=new com.google.gson.Gson().toJson(new Study.Batch(0,Collections.nCopies(256,event)));
        var traceBuffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),(RegistryAccess)registries);
        Packets.Trace.CODEC.encode(traceBuffer,new Packets.Trace(traceJson));var decoded=Packets.Trace.CODEC.decode(traceBuffer);traceBuffer.release();
        check(decoded.json().equals(traceJson),"256 maximally sized item hints fit the real trace payload codec");
        var trace=new com.google.gson.Gson().fromJson(decoded.json(),Study.Batch.class);
        check(trace.events().size()==256&&trace.events().getFirst().item().equals(hints.item())&&trace.events().getFirst().count()==64,"item trace timestamps and public item hints survive serialization");
        dev.netlab.MemoryCards.APPLY.add(dev.netlab.automation.AutomationNode::applyMemory);
        var router=new Device("router");Hardware.ensure(router);MemoryCards.fresh(router);router.hostname="Volatile";router.ports.get(0).ip="172.18.0.1";
        var volatileSave=MemoryCards.saved(router);check(volatileSave.ports.get(0).ip.isEmpty()&&!volatileSave.hostname.equals("Volatile"),"no card means no persistent configuration");
        var pc=new Device("pc");pc.ports.get(0).ip="172.18.0.2";check(MemoryCards.saved(pc)==pc,"PC uses built-in configuration memory");
        var pcItem=MemoryCards.deviceItem(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("netlab","pc")),pc);var pcSaved=ItemStack.parse(registries,pcItem.save(registries)).orElseThrow();var pcRestored=dev.netlab.api.RackDevices.restore(pcSaved,"pc");check(pcRestored.ports.get(0).ip.equals("172.18.0.2")&&pcRestored.identity.equals(pc.identity),"broken PC's configured item restores network settings and built-in identity");
        var legacy=new Device("router");Hardware.ensure(legacy);legacy.hostname="SavedRouter";legacy.ports.get(0).ip="172.18.0.3";legacy.os().enableSecret="secret-verifier";
        MemoryCards.migrate(legacy);check(MemoryCards.present(legacy)&&MemoryCards.card(legacy).active,"legacy configurations acquire an active card");
        var clone=new Device("router");Hardware.ensure(clone);String identity=clone.identity;clone.extensions.put("netlab.memory",legacy.extensions.get("netlab.memory"));MemoryCards.read(clone);
        check(clone.hostname.equals("SavedRouter")&&clone.ports.get(0).ip.equals("172.18.0.3")&&clone.os().enableSecret.equals("secret-verifier"),"card restores complete network/password configuration");
        check(clone.identity.equals(identity)&&clone.hardware.modules.isEmpty(),"reading configuration does not clone device identity or installed items");
        var stack=MemoryCards.cardItem(legacy);var serialized=stack.save(registries);var restored=ItemStack.parse(registries,serialized).orElseThrow();check(restored.has(NetLab.CONFIGURATION.get()),"physical card configuration survives item NBT");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),(RegistryAccess)registries);NetLab.CONFIGURATION.get().streamCodec().encode(buffer,restored.get(NetLab.CONFIGURATION.get()));
        check(buffer.readableBytes()==0&&NetLab.CONFIGURATION.get().streamCodec().decode(buffer).isEmpty(),"card secrets are never sent as held-item network components");buffer.release();
        var sourceServer=new Device("server");Hardware.ensure(sourceServer);String oldOwner=UUID.randomUUID().toString();sourceServer.extensions.put("automation",new com.google.gson.Gson().toJson(Map.of("owner",oldOwner,"identity",UUID.randomUUID().toString(),"passwordHash","hidden-verifier","patterns",List.of(Map.of("name","torches")),"stocks",List.of(Map.of("destinationSlot",1)))));MemoryCards.migrate(sourceServer);
        var targetServer=new Device("server");Hardware.ensure(targetServer);String newOwner=UUID.randomUUID().toString(),nodeId=UUID.randomUUID().toString();targetServer.extensions.put("automation",new com.google.gson.Gson().toJson(Map.of("owner",newOwner,"identity",nodeId)));targetServer.extensions.put("netlab.memory",sourceServer.extensions.get("netlab.memory"));MemoryCards.read(targetServer);
        var config=com.google.gson.JsonParser.parseString(targetServer.extensions.get("automation")).getAsJsonObject();check(config.get("owner").getAsString().equals(newOwner)&&config.get("identity").getAsString().equals(nodeId),"server card preserves destination ownership and endpoint identity");
        check(config.getAsJsonArray("patterns").size()==1&&config.getAsJsonArray("stocks").size()==1&&config.get("passwordHash").getAsString().equals("hidden-verifier"),"server card retains automation rules, recipes and password verifier");
        var cleanServer=new Device("server");Hardware.ensure(cleanServer);cleanServer.extensions.put("netlab.memory",sourceServer.extensions.get("netlab.memory"));MemoryCards.read(cleanServer);var clean=com.google.gson.JsonParser.parseString(cleanServer.extensions.get("automation")).getAsJsonObject();check(!clean.has("owner")&&!clean.has("identity"),"reading into an unclaimed server never clones the card's physical owner or endpoint identity");
        var tag=new net.minecraft.nbt.CompoundTag();StoredJson.put(tag,"device",new com.google.gson.Gson().toJson(targetServer));var publicTag=MemoryCards.publicTag(tag);String publicJson=StoredJson.get(publicTag,"device");
        check(!publicJson.contains("hidden-verifier")&&!publicJson.contains("torches"),"block updates expose neither server credentials nor private recipes");
        var startup=ConfigStore.restore(ConfigStore.snapshot(legacy));check(!startup.extensions.containsKey("netlab.memory")&&MemoryCards.present(legacy),"startup snapshots do not recursively embed physical cards");
        // Trimmed 1x2 ingredients at the bottom/right must still consume the original 3x3 slots.
        var cells=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));cells.set(4,new ItemStack(Items.COAL));cells.set(7,new ItemStack(Items.STICK));var positioned=net.minecraft.world.item.crafting.CraftingInput.ofPositioned(3,3,cells);
        var remains=net.minecraft.core.NonNullList.withSize(positioned.input().size(),ItemStack.EMPTY);var result=dev.netlab.automation.CraftingGrids.plan(cells,positioned,remains,ItemStack.EMPTY,new ItemStack(Items.TORCH,4));
        check(result.get(4).isEmpty()&&result.get(7).isEmpty()&&result.get(9).is(Items.TORCH)&&result.get(9).getCount()==4,"trimmed recipe coordinates map back to the adapter's original slots");
        check(cells.get(4).getCount()==1&&cells.get(7).getCount()==1,"planning a craft does not modify inputs");
        cells=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));cells.set(8,new ItemStack(Items.MILK_BUCKET));positioned=net.minecraft.world.item.crafting.CraftingInput.ofPositioned(3,3,cells);remains=net.minecraft.core.NonNullList.withSize(1,new ItemStack(Items.BUCKET));
        result=dev.netlab.automation.CraftingGrids.plan(cells,positioned,remains,ItemStack.EMPTY,new ItemStack(Items.CAKE));check(result.get(8).is(Items.BUCKET)&&result.get(9).is(Items.CAKE),"container remainders return to the correct shifted input slot");
        try{dev.netlab.automation.CraftingGrids.plan(cells,positioned,remains,new ItemStack(Items.CAKE,64),new ItemStack(Items.CAKE));throw new AssertionError("Full output accepted");}catch(IllegalArgumentException expected){checks++;}
        check(cells.get(8).is(Items.MILK_BUCKET),"failed crafting cannot consume ingredients");
        var fuel=com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(Map.of("inventory",List.of(Map.of("slot",0,"item","minecraft:coal","count",64),Map.of("slot",1,"item","minecraft:coal","count",3))))).getAsJsonObject();check(dev.netlab.automation.AutomationPrograms.count(fuel,"minecraft:coal",1)==3,"fuel rule checks the selected slot rather than coal elsewhere");check(dev.netlab.automation.AutomationPrograms.count(fuel,"minecraft:coal",-1)==67&&dev.netlab.automation.AutomationPrograms.count(fuel,"minecraft:coal",2)==0,"all-slot stock and an empty selected slot are distinct conditions");
        var capability=new net.neoforged.neoforge.items.ItemStackHandler(3){@Override public ItemStack extractItem(int slot,int amount,boolean simulate){return slot==0?ItemStack.EMPTY:super.extractItem(slot,amount,simulate);}};
        capability.setStackInSlot(0,new ItemStack(Items.COAL,64));capability.setStackInSlot(2,new ItemStack(Items.IRON_INGOT,24));
        var inventory=new com.google.gson.JsonArray();for(int slot=0;slot<3;slot++)inventory.add(dev.netlab.automation.MachineAccess.slotSnapshot(capability,slot,true));var sourceSnapshot=new com.google.gson.JsonObject();sourceSnapshot.add("inventory",inventory);
        check(capability.getStackInSlot(0).getCount()==64&&capability.getStackInSlot(2).getCount()==24,"network snapshot extraction previews never consume items");
        var drainRule=new dev.netlab.automation.core.AutomationProgram.Stock("10.0.0.1","10.0.0.2","*",-1,-1,1,16);
        var selected=dev.netlab.automation.AutomationPrograms.drain(drainRule,sourceSnapshot).orElseThrow();
        check(selected.sourceSlot()==2&&selected.item().equals("minecraft:iron_ingot")&&selected.amount()==16,"any-item RPC snapshot skips capability-blocked coal and chooses actual extractable iron");
        check(dev.netlab.automation.MachineAccess.slotSnapshot(capability,2,false).get("extractable").getAsInt()==0,"endpoint permission denial suppresses wildcard extraction candidates");
        var specific=new dev.netlab.automation.core.AutomationProgram.Stock("a","b","*",0,-1,1,16);check(dev.netlab.automation.AutomationPrograms.drain(specific,sourceSnapshot).isEmpty(),"an explicit blocked source slot cannot fall back to another slot");
        var savedApp=com.google.gson.JsonParser.parseString(targetServer.extensions.get("automation")).getAsJsonObject();savedApp.getAsJsonArray("stocks").add(new com.google.gson.Gson().toJsonTree(drainRule));targetServer.extensions.put("automation",savedApp.toString());MemoryCards.sync(targetServer);MemoryCards.read(targetServer);
        var savedDrain=com.google.gson.JsonParser.parseString(targetServer.extensions.get("automation")).getAsJsonObject().getAsJsonArray("stocks").get(1).getAsJsonObject();
        check(savedDrain.get("item").getAsString().equals("*")&&savedDrain.get("batch").getAsInt()==16,"any-item rule survives CISCO card serialization and reload");
        var journal=new dev.netlab.automation.TransferJournal();var job=new dev.netlab.automation.TransferJournal.Job();job.owner=UUID.randomUUID();job.controllerId=UUID.randomUUID();job.sourceId=UUID.randomUUID();job.destinationId=UUID.randomUUID();job.stack=new ItemStack(Items.IRON_INGOT,8);job.claim=new dev.netlab.automation.core.Claim(8);journal.add(job);check(journal.audit.valid()&&journal.audit.entries.size()==1,"reserving real items appends an audit entry");
        var saved=journal.save(new net.minecraft.nbt.CompoundTag(),registries);check(saved.contains("audit")&&saved.getList("jobs",net.minecraft.nbt.Tag.TAG_COMPOUND).size()==1,"escrow and audit are saved independently of device configuration cards");
    }

}
