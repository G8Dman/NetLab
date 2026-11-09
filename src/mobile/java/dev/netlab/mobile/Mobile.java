package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
import java.util.*;
import java.util.function.Supplier;

@Mod(Mobile.ID)
public final class Mobile {
    public static final String ID="netlabmobile";
    static {MobileHardware.init();}
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredBlock<MobileBlock> ANTENNA=block("sector_antenna",MobileHardware.ANTENNA),RRU=block("radio_unit",MobileHardware.RRU),BBU=block("baseband_unit",MobileHardware.BBU),CORE=block("mobile_core",MobileHardware.CORE),STATION=block("sim_station",MobileHardware.STATION);
    public static final Supplier<BlockEntityType<MobileEntity>> ENTITY=ENTITIES.register("mobile_device",()->BlockEntityType.Builder.of(MobileEntity::new,ANTENNA.get(),RRU.get(),BBU.get(),CORE.get(),STATION.get()).build(null));
    public static final DeferredItem<Item> BLANK_SIM=ITEMS.register("blank_sim",()->new Item(new Item.Properties()));
    public static final DeferredItem<SimCardItem> SIM=ITEMS.register("sim_card",()->new SimCardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<dev.netlab.api.PortableModules.ModuleItem> PHONE_DIAGNOSTICS=ITEMS.register("phone_diagnostics_module",()->new dev.netlab.api.PortableModules.ModuleItem(new Item.Properties().stacksTo(1),"CISCO-POCKET-NET","diagnostics","Модуль диагностики Cisco Pocket"));
    public static final DeferredItem<dev.netlab.api.PortableModules.ModuleItem> PHONE_TERMINAL=ITEMS.register("phone_terminal_module",()->new dev.netlab.api.PortableModules.ModuleItem(new Item.Properties().stacksTo(1),"CISCO-POCKET-CONSOLE","terminal","Модуль терминала Cisco Pocket"));
    public static final DeferredItem<PhoneItem> PHONE=ITEMS.register("cisco_pocket",()->new PhoneItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CableItem> RF=ITEMS.register("rf_cable",()->new CableItem(new Item.Properties().stacksTo(1),"rf"));
    public static final Map<String,DeferredItem<ModuleItem>> MODULE_ITEMS=new LinkedHashMap<>();
    static {for(String module:MobileHardware.MODULES)MODULE_ITEMS.put(module,ITEMS.register(ModuleItem.itemId(module),()->new ModuleItem(new Item.Properties().stacksTo(16),module)));}
    private static DeferredBlock<MobileBlock> block(String id,String kind){var b=BLOCKS.register(id,()->kind.equals(MobileHardware.ANTENNA)?new SectorAntennaBlock(BlockBehaviour.Properties.of().strength(3).noOcclusion()):new MobileBlock(BlockBehaviour.Properties.of().strength(3).noOcclusion(),kind));ITEMS.registerSimpleBlockItem(id,b);return b;}
    private static boolean integrated;
    public Mobile(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);TABS.register(bus);
        TABS.register("mobile",()->CreativeModeTab.builder().title(Component.literal("NetLab Mobile")).icon(()->PHONE.get().getDefaultInstance()).displayItems((p,o)->{o.accept(ANTENNA.get());o.accept(RRU.get());o.accept(BBU.get());o.accept(CORE.get());o.accept(STATION.get());o.accept(RF.get());o.accept(BLANK_SIM.get());o.accept(PHONE.get());o.accept(PHONE_DIAGNOSTICS.get());o.accept(PHONE_TERMINAL.get());MODULE_ITEMS.values().forEach(m->o.accept(m.get()));}).build());
        integrate();
        bus.addListener(MobilePackets::register);bus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event)->event.register(MobileChunks.TICKETS));
        var events=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;events.addListener(MobileService::tick);events.addListener(MobileChunks::tick);events.addListener(MobileDemo::register);
        events.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)->RF.get().preview(event));
        events.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event)->{MobileService.clear();MobilePackets.clear();RF.get().clear();});
        events.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event)->{if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)MobileItemStorage.inventory(player);});
        events.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)->{if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)MobileItemStorage.inventory(player);});
    }
    public static synchronized void integrate(){
        if(integrated)return;integrated=true;
        PortableModules.register(new PortableModules.Definition("CISCO-POCKET-NET","diagnostics","Диагностика и пакеты",PHONE_DIAGNOSTICS));
        PortableModules.register(new PortableModules.Definition("CISCO-POCKET-CONSOLE","terminal","Терминал: история 3 устройств",PHONE_TERMINAL));
        PortableHosts.SAVERS.add(PhoneDevices::saveApplications);
        PortableTerminals.CARRIER_ITEMS.add(stack->stack.is(PHONE.get()));
        NetLab.EXTRA_MODULE_ITEMS.add(PHONE_DIAGNOSTICS);
        NetLab.EXTRA_MODULE_ITEMS.add(PHONE_TERMINAL);
        RackDevices.register(MobileHardware.BBU,BBU);RackDevices.register(MobileHardware.CORE,CORE);
        for(String kind:List.of(MobileHardware.BBU,MobileHardware.CORE))RackDevices.INITIALIZERS.put(kind,MobileHardware::initialize);
        RackDevices.INSTALLED.add(i->{if(i.host().getLevel() instanceof net.minecraft.server.level.ServerLevel level){var ref=MobileRef.of(i.host(),i.slot());if(ref!=null&&ref.device().kind.startsWith("mobile_")){var s=ref.state();if(s.owner.isEmpty())s.owner=i.player().getUUID().toString();ref.changed(s);MobileChunks.register(level,i.host().getBlockPos());}}});
        RackDevices.GUARDS.add((p,h,b)->{var ref=MobileRef.of(h,b);return ref==null||!ref.device().kind.startsWith("mobile_")||ref.state().owns(p.getUUID());});
        var console=new MobileConsole();for(String kind:List.of(MobileHardware.BBU,MobileHardware.CORE,MobileHardware.RRU,MobileHardware.STATION))ConsoleExtensions.HANDLERS.put(kind,console);
        MemoryCards.APPLY.add(MobileState::physical);MemoryCards.PHYSICAL.add(MobileState::physical);TopologyExtensions.PUBLIC_STATE.add(MobileState::redact);
        dev.netlab.core.Hardware.REMOVE_GUARDS.add((d,slot)->{if(MobileHardware.MODEM.equals(dev.netlab.core.Hardware.ensure(d).modules.get(slot))&&MobileState.get(d).sim!=null)throw new IllegalArgumentException("Сначала извлеки SIM в приложении «Мобильная сеть»");});
        TopologyExtensions.BUILD.add(MobileService::topology);TopologyExtensions.WITNESSES.add(MobileTransit::witness);
    }
}
