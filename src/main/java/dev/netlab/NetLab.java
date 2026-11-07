package dev.netlab;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.*;
import java.util.function.Supplier;

@Mod(NetLab.ID)
public final class NetLab {
    public static final String ID="netlab";
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS=DeferredRegister.create(Registries.ARMOR_MATERIAL,ID);
    public static final DeferredHolder<ArmorMaterial,ArmorMaterial> GOGGLES_MATERIAL=MATERIALS.register("diagnostic_goggles",()->new ArmorMaterial(java.util.Map.of(ArmorItem.Type.HELMET,0),0,net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER,()->net.minecraft.world.item.crafting.Ingredient.of(Items.GLASS),java.util.List.of(new ArmorMaterial.Layer(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID,"diagnostic_goggles"))),0,0));
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ID);
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> COMPONENTS=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,ID);
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,net.minecraft.core.component.DataComponentType<net.minecraft.nbt.CompoundTag>> CONFIGURATION=COMPONENTS.register("configuration",()->net.minecraft.core.component.DataComponentType.<net.minecraft.nbt.CompoundTag>builder().persistent(net.minecraft.nbt.CompoundTag.CODEC).networkSynchronized(net.minecraft.network.codec.StreamCodec.of((buffer,value)->{},buffer->new net.minecraft.nbt.CompoundTag())).build());
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CISCIUM_BLOCK=materialBlock("ciscium_block",()->new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> VKUSNO_BLOCK=materialBlock("vkusno_i_tochka_block",()->new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN).strength(1.8f).sound(net.minecraft.world.level.block.SoundType.STONE)));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CISCIUM_ORE=materialBlock("ciscium_ore",()->new net.minecraft.world.level.block.DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(2,5),BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.STONE).strength(3).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> DEEPSLATE_CISCIUM_ORE=materialBlock("deepslate_ciscium_ore",()->new net.minecraft.world.level.block.DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(2,5),BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE).strength(4.5f,3).sound(net.minecraft.world.level.block.SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<ModuleAssemblerBlock> MODULE_ASSEMBLER=BLOCKS.register("module_assembler",()->new ModuleAssemblerBlock(BlockBehaviour.Properties.of().strength(3).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> MODULE_ASSEMBLER_ITEM=ITEMS.registerSimpleBlockItem("module_assembler",MODULE_ASSEMBLER);
    public static final DeferredItem<Item> RAW_CISCIUM=ITEMS.register("raw_ciscium",()->new Item(new Item.Properties()));
    public static final DeferredItem<Item> CISCIUM_INGOT=ITEMS.register("ciscium_ingot",()->new Item(new Item.Properties()));
    public static final DeferredItem<Item> BASIC_BOARD=ITEMS.register("basic_circuit_board",()->new Item(new Item.Properties()));
    public static final DeferredItem<Item> ADVANCED_BOARD=ITEMS.register("advanced_circuit_board",()->new Item(new Item.Properties()));
    public static final java.util.List<Supplier<? extends Item>> EXTRA_MODULE_ITEMS=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final DeferredItem<Item> MEMORY_CARD=ITEMS.register("cisco_memory_card",()->new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<ModuleAssemblerMenu>> MODULE_ASSEMBLER_MENU=MENUS.register("module_assembler",()->new net.minecraft.world.inventory.MenuType<>(ModuleAssemblerMenu::new,net.minecraft.world.flag.FeatureFlags.VANILLA_SET));
    public static final DeferredBlock<DeviceBlock> ENERGY_CABINET=device("energy_cabinet","energy",false);
    public static final DeferredBlock<DeviceBlock> CREATIVE_SOURCE=device("creative_energy_source","energy_source",false);
    public static final DeferredBlock<DeviceBlock> ROUTER=device("router","router",false);
    public static final DeferredBlock<DeviceBlock> SWITCH=device("switch","switch",false);
    public static final DeferredBlock<DeviceBlock> PC=device("pc","pc",false);
    public static final DeferredBlock<DeviceBlock> LAPTOP=device("laptop","pc",false);
    public static final DeferredBlock<DeviceBlock> ACCESS_POINT=device("access_point","ap",false);
    public static final DeferredBlock<DeviceBlock> TRAFFIC_CONTROLLER=device("traffic_controller","traffic",false);
    public static final DeferredBlock<DeviceBlock> RACK=device("rack","rack",true);
    public static final DeferredBlock<DeviceBlock> RACK_ROUTER=device("rack_router","router",true);
    public static final DeferredBlock<DeviceBlock> RACK_SWITCH=device("rack_switch","switch",true);
    public static final DeferredItem<PortableTerminalItem> PORTABLE_TERMINAL=ITEMS.register("portable_terminal",()->new PortableTerminalItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PowerCableItem> POWER_CABLE=ITEMS.register("power_cable",()->new PowerCableItem(new Item.Properties().stacksTo(1)));
    // Registry alias only, so existing saves keep their debug items until inventory conversion.
    public static final DeferredItem<PowerCableItem> WALL_POWER_CABLE=ITEMS.register("wall_power_cable",()->new PowerCableItem(new Item.Properties().stacksTo(1),true));
    public static final DeferredItem<RackSupplyItem> RACK_SUPPLY=ITEMS.register("rack_power_supply",()->new RackSupplyItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<CableItem> CABLE=ITEMS.register("ethernet_cable",()->new CableItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ScrewdriverItem> SCREWDRIVER=ITEMS.register("screwdriver",()->new ScrewdriverItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<GuideBookItem> GUIDE=ITEMS.register("cisco_bible",()->new GuideBookItem(new Item.Properties().stacksTo(1)));
    public static final java.util.Map<String,DeferredItem<ModuleItem>> MODULE_ITEMS=registerModules();
    public static final java.util.Map<String,DeferredItem<CableItem>> CABLE_ITEMS=registerCables();
    private static java.util.Map<String,DeferredItem<ModuleItem>> registerModules(){
        var out=new java.util.LinkedHashMap<String,DeferredItem<ModuleItem>>();
        for(String module:dev.netlab.core.Hardware.BUILTIN_MODULES)out.put(module,ITEMS.register(ModuleItem.itemId(module),()->new ModuleItem(new Item.Properties().stacksTo(16),module)));
        return out;
    }
    private static java.util.Map<String,DeferredItem<CableItem>> registerCables(){
        var out=new java.util.LinkedHashMap<String,DeferredItem<CableItem>>();out.put("copper",CABLE);
        for(String kind:dev.netlab.core.CableKind.TYPES)if(!kind.equals("copper"))out.put(kind,ITEMS.register(kind+"_cable",()->new CableItem(new Item.Properties().stacksTo(1),kind)));
        return out;
    }
    private static void cables(java.util.function.Consumer<RoutedCableItem> action){
        CABLE_ITEMS.values().forEach(item->action.accept(item.get()));action.accept(POWER_CABLE.get());action.accept(WALL_POWER_CABLE.get());
    }
    public static final DeferredItem<ArmorItem> GOGGLES=ITEMS.register("diagnostic_goggles",()->new ArmorItem(GOGGLES_MATERIAL,ArmorItem.Type.HELMET,new Item.Properties().stacksTo(1)));
    public static final Supplier<BlockEntityType<DeviceEntity>> DEVICE=BES.register("device",()->BlockEntityType.Builder.of(DeviceEntity::new,ROUTER.get(),SWITCH.get(),PC.get(),LAPTOP.get(),ACCESS_POINT.get(),TRAFFIC_CONTROLLER.get(),RACK.get(),RACK_ROUTER.get(),RACK_SWITCH.get(),ENERGY_CABINET.get(),CREATIVE_SOURCE.get()).build(null));
    public static final ModConfigSpec CONFIG;
    public static final ModConfigSpec.IntValue MAX_LENGTH;
    public static final ModConfigSpec.DoubleValue LATENCY, BANDWIDTH;
    static {
        var b=new ModConfigSpec.Builder();
        MAX_LENGTH=b.comment("Maximum cable length in blocks").defineInRange("maxCableLength",64,1,256);
        LATENCY=b.comment("Simulated one-way latency per link in milliseconds").defineInRange("latencyMs",1.0,0.0,10000.0);
        BANDWIDTH=b.comment("Global simulated bandwidth ceiling in Mbit/s; the packet clock also obeys port speed and serial clock rate").defineInRange("bandwidthMbps",100.0,0.01,100000.0);
        CONFIG=b.build();
    }
    private static DeferredBlock<DeviceBlock> device(String name,String kind,boolean rack) {
        var block=BLOCKS.register(name,()->new DeviceBlock(BlockBehaviour.Properties.of().strength(2).noOcclusion(),kind,rack));
        if(name.equals("laptop"))ITEMS.register(name,()->new LaptopItem(block.get(),new Item.Properties()));
        else if(!name.startsWith("rack_")) ITEMS.registerSimpleBlockItem(name,block);
        return block;
    }
    private static DeferredBlock<net.minecraft.world.level.block.Block> materialBlock(String name,Supplier<net.minecraft.world.level.block.Block> factory){
        var block=BLOCKS.register(name,factory);ITEMS.registerSimpleBlockItem(name,block);return block;
    }
    public NetLab(IEventBus bus,ModContainer container) {
        dev.netlab.api.RackDevices.register("router",ROUTER);dev.netlab.api.RackDevices.register("switch",SWITCH);dev.netlab.api.RackDevices.register("traffic",TRAFFIC_CONTROLLER);
        BLOCKS.register(bus);ITEMS.register(bus);BES.register(bus);MATERIALS.register(bus);MENUS.register(bus);COMPONENTS.register(bus);
        TABS.register("equipment",()->CreativeModeTab.builder().title(Component.literal("NetLab")).icon(()->ROUTER.get().asItem().getDefaultInstance())
            .displayItems((p,o)->{o.accept(GUIDE.get());o.accept(CISCIUM_ORE.get());o.accept(DEEPSLATE_CISCIUM_ORE.get());o.accept(RAW_CISCIUM.get());o.accept(CISCIUM_INGOT.get());o.accept(CISCIUM_BLOCK.get());o.accept(VKUSNO_BLOCK.get());o.accept(BASIC_BOARD.get());o.accept(ADVANCED_BOARD.get());o.accept(MEMORY_CARD.get());o.accept(MODULE_ASSEMBLER.get());o.accept(PORTABLE_TERMINAL.get());o.accept(ENERGY_CABINET.get());o.accept(CREATIVE_SOURCE.get());o.accept(POWER_CABLE.get());o.accept(SCREWDRIVER.get());o.accept(ROUTER.get());o.accept(SWITCH.get());o.accept(PC.get());o.accept(LAPTOP.get());o.accept(ACCESS_POINT.get());o.accept(TRAFFIC_CONTROLLER.get());o.accept(RACK.get());CABLE_ITEMS.values().forEach(item->o.accept(item.get()));o.accept(GOGGLES.get());}).build());
        TABS.register("modules",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.netlab.modules")).icon(()->MODULE_ITEMS.values().iterator().next().get().getDefaultInstance())
            .displayItems((p,o)->{MODULE_ITEMS.values().forEach(item->o.accept(item.get()));EXTRA_MODULE_ITEMS.forEach(item->o.accept(item.get()));o.accept(RACK_SUPPLY.get());}).build());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Demo::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CableInteraction::onBlock);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CableInteraction::onAir);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(NetworkServices::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Simulation::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Simulation::clear);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PowerSystem::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CableInventory::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->cables(c->c.preview(e)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->cables(c->c.clear(e.getEntity().getUUID())));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e)->cables(c->c.clear(e.getEntity().getUUID())));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->cables(RoutedCableItem::clear));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PortableTerminals::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PortableTerminals::clear);
        bus.addListener(PowerSystem::capabilities);
        TABS.register(bus); bus.addListener(Packets::register);bus.addListener(TerminalPackets::register); container.registerConfig(ModConfig.Type.SERVER,CONFIG);
    }
}
