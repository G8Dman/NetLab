package dev.netlab.sputnik;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.sputnik.core.*;
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
@Mod(Sputnik.ID)
public final class Sputnik {
    public static final String ID="netlabsputnik";
    static {SputnikHardware.init();}
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredItem<PortableModules.ModuleItem> PHONE_BROWSER=ITEMS.register("phone_browser_module",()->new PortableModules.ModuleItem(new Item.Properties().stacksTo(1),"CISCO-POCKET-WEB","browser","Модуль браузера Cisco Pocket"));
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final Map<String,DeferredBlock<SputnikBlock>> DEVICES=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<ModuleItem>> MODULES=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<CableItem>> CABLES=new LinkedHashMap<>();
    static {for(String kind:SputnikHardware.KINDS){var b=BLOCKS.register(kind.substring(8),()->new SputnikBlock(BlockBehaviour.Properties.of().strength(3).noOcclusion(),kind));DEVICES.put(kind,b);ITEMS.registerSimpleBlockItem(kind.substring(8),b);}for(String m:SputnikHardware.MODULES)MODULES.put(m,ITEMS.register(ModuleItem.itemId(m),()->new ModuleItem(new Item.Properties().stacksTo(16),m)));for(String c:List.of("coax_tx","coax_rx","sat_rf_tx","sat_rf_rx","audio"))CABLES.put(c,ITEMS.register(c+"_cable",()->new CableItem(new Item.Properties().stacksTo(1),c)));}
    public static final Supplier<BlockEntityType<SputnikEntity>> ENTITY=ENTITIES.register("sputnik_device",()->BlockEntityType.Builder.of(SputnikEntity::new,DEVICES.values().stream().map(Supplier::get).toArray(SputnikBlock[]::new)).build(null));
    private static boolean integrated;
    public static synchronized void integrate(){if(integrated)return;integrated=true;
        PortableModules.register(new PortableModules.Definition("CISCO-POCKET-WEB","browser","Браузер Sputnik",PHONE_BROWSER));
        NetLab.EXTRA_MODULE_ITEMS.add(PHONE_BROWSER);
        for(String k:SputnikHardware.KINDS)RackDevices.INITIALIZERS.put(k,SputnikHardware::initialize);
        for(String k:List.of(SputnikHardware.HUB,SputnikHardware.GATEWAY))RackDevices.register(k,DEVICES.get(k));
        RackDevices.INSTALLED.add(i->{var ref=SputnikRef.of(i.host(),i.slot());if(ref!=null&&SputnikHardware.KINDS.contains(ref.device().kind)){var s=ref.state();if(s.owner.isEmpty())s.owner=i.player().getUUID().toString();ref.changed(s);SputnikChunks.register(ref.level(),ref.host().getBlockPos());}});
        RackDevices.GUARDS.add((p,h,b)->{var ref=SputnikRef.of(h,b);return ref==null||!SputnikHardware.KINDS.contains(ref.device().kind)||ref.state().owns(p.getUUID());});
        var cli=new SputnikConsole();for(String k:SputnikHardware.KINDS)if(dev.netlab.core.DeviceKinds.console(k))ConsoleExtensions.HANDLERS.put(k,cli);
        MemoryCards.APPLY.add(SputnikState::physical);MemoryCards.PHYSICAL.add(SputnikState::physical);TopologyExtensions.PUBLIC_STATE.add(SputnikState::redact);TopologyExtensions.BUILD.add(SatelliteLinks::topology);TopologyExtensions.WITNESSES.add(SatelliteLinks::witness);
        FrontPanel.EXTRA_STATUS.add((h,b)->{var d=b==0?h.device:h.upperDevice;return d!=null&&SputnikHardware.KINDS.contains(d.kind)?(h.getLevel() instanceof net.minecraft.server.level.ServerLevel?SatelliteLinks.status(new SputnikRef(h,b,d)):SputnikState.get(d).network+" / "+SputnikState.get(d).satellite):"";});
    }
    public Sputnik(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);TABS.register(bus);integrate();NetLab.EXTRA_MODULE_ITEMS.addAll(MODULES.values());
        TABS.register("sputnik",()->CreativeModeTab.builder().title(Component.literal("NetLab Sputnik")).icon(()->DEVICES.get(SputnikHardware.VSAT).get().asItem().getDefaultInstance()).displayItems((p,o)->{DEVICES.values().forEach(b->o.accept(b.get()));CABLES.values().forEach(c->o.accept(c.get()));MODULES.values().forEach(m->o.accept(m.get()));o.accept(PHONE_BROWSER.get());}).build());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SputnikLearning::tick);bus.addListener(SputnikPackets::register);bus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e)->e.register(SputnikChunks.TICKETS));var events=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;events.addListener(SputnikPackets::tick);events.addListener(SputnikChunks::tick);events.addListener(SputnikDemo::register);events.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->CABLES.values().forEach(c->c.get().preview(e)));events.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->{InternetBridge.clear();SputnikPackets.clear();CABLES.values().forEach(c->c.get().clear());});
    }
}
