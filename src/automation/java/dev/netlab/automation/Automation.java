package dev.netlab.automation;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
import java.util.function.Supplier;

@Mod(Automation.ID)
public final class Automation {
    public static final String ID="netlabautomation";
    static {dev.netlab.automation.core.ServerHardware.register();}
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredBlock<AutomationBlock> INTERFACE=block("multiinterface","endpoint");
    public static final DeferredBlock<AutomationBlock> SERVER=block("management_server","server");
    public static final DeferredBlock<AutomationBlock> ANALYZER=block("packet_analyzer","sniffer");
    public static final java.util.Map<String,DeferredItem<dev.netlab.ModuleItem>> MODULE_ITEMS=new java.util.LinkedHashMap<>();
    static {for(String id:dev.netlab.automation.core.ServerHardware.MODULES)MODULE_ITEMS.put(id,ITEMS.register(dev.netlab.ModuleItem.itemId(id),()->new dev.netlab.ModuleItem(new Item.Properties().stacksTo(16),id)));}
    public static final Supplier<BlockEntityType<AutomationEntity>> ENTITY=ENTITIES.register("node",()->BlockEntityType.Builder.of(AutomationEntity::new,INTERFACE.get(),SERVER.get(),ANALYZER.get()).build(null));
    private static DeferredBlock<AutomationBlock> block(String id,String kind){
        var b=BLOCKS.register(id,()->new AutomationBlock(BlockBehaviour.Properties.of().strength(3).noOcclusion(),kind));
        ITEMS.registerSimpleBlockItem(id,b);return b;
    }
    private static boolean thermalIntegrated;
    public static synchronized void integrateThermal(){
        if(thermalIntegrated)return;thermalIntegrated=true;
        dev.netlab.MemoryCards.PHYSICAL.add(ServerThermal::preserve);dev.netlab.MemoryCards.APPLY.add(ServerThermal::preserve);
        dev.netlab.FrontPanel.EXTRA_STATUS.add((host,bay)->{var d=bay==0?host.device:host.upperDevice;return d==null?"":ServerThermal.summary(d);});
    }
    public Automation(IEventBus bus){
        MODULE_ITEMS.values().forEach(item->dev.netlab.NetLab.EXTRA_MODULE_ITEMS.add(item));
        integrateThermal();
        dev.netlab.api.RackDevices.register("server",SERVER);
        dev.netlab.api.RackDevices.INSTALLED.add(i->{var a=AutomationNode.of(i.host(),i.slot());if(a!=null){if(a.owner==null)a.owner=i.player().getUUID();a.changed();}});
        var console=new AutomationConsole();for(String kind:java.util.List.of("server","endpoint","sniffer"))dev.netlab.api.ConsoleExtensions.HANDLERS.put(kind,console);
        dev.netlab.MemoryCards.APPLY.add(AutomationNode::applyMemory);
        dev.netlab.api.RackDevices.GUARDS.add((player,host,slot)->{var a=AutomationNode.of(host,slot);return a==null||a.owns(player.getUUID());});
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);
        TABS.register("automation",()->CreativeModeTab.builder().title(Component.literal("NetLab Automation")).icon(()->SERVER.get().asItem().getDefaultInstance())
            .displayItems((p,o)->{o.accept(INTERFACE.get());o.accept(SERVER.get());o.accept(ANALYZER.get());}).build());
        TABS.register(bus);bus.addListener(AutomationPackets::register);bus.addListener(ManagementPackets::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(AutomationPrograms::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerThermal::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ManagementService::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(AutomationService::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(AutomationDemo::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(AutomationService::stop);
        dev.netlab.api.ApplicationTransport.OBSERVERS.add(AutomationService::capture);
    }
}
