package dev.netlab.media;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.media.core.*;
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
@Mod(Media.ID)
public final class Media {
    public static final String ID="netlabmedia";
    static {MediaHardware.init();}
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final Map<String,DeferredBlock<MediaBlock>> DEVICES=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<ModuleItem>> MODULES=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<CableItem>> CABLES=new LinkedHashMap<>();
    public static final DeferredItem<PortableModules.ModuleItem> PHONE_SIP=ITEMS.register("phone_sip_module",()->new PortableModules.ModuleItem(new Item.Properties().stacksTo(1),"CISCO-POCKET-SIP","telephony","Модуль IP-телефонии Cisco Pocket"));
    static {for(String kind:MediaHardware.KINDS){var b=BLOCKS.register(kind.substring(6),()->new MediaBlock(BlockBehaviour.Properties.of().strength(3).noOcclusion(),kind));DEVICES.put(kind,b);ITEMS.registerSimpleBlockItem(kind.substring(6),b);}for(String code:MediaHardware.MODULES)MODULES.put(code,ITEMS.register(ModuleItem.itemId(code),()->new ModuleItem(new Item.Properties().stacksTo(16),code)));for(String c:List.of("audio","hdmi"))CABLES.put(c,ITEMS.register(c+"_cable",()->new CableItem(new Item.Properties().stacksTo(1),c)));}
    public static final Supplier<BlockEntityType<MediaEntity>> ENTITY=ENTITIES.register("media_device",()->BlockEntityType.Builder.of(MediaEntity::new,DEVICES.values().stream().map(Supplier::get).toArray(MediaBlock[]::new)).build(null));
    private static boolean integrated;
    public static synchronized void integrate(){if(integrated)return;integrated=true;
        PortableModules.register(new PortableModules.Definition("CISCO-POCKET-SIP","telephony","IP-телефония",PHONE_SIP));NetLab.EXTRA_MODULE_ITEMS.add(PHONE_SIP);
        PortableHosts.PREPARED.add(MediaService::portable);
        for(String kind:MediaHardware.KINDS)RackDevices.INITIALIZERS.put(kind,MediaHardware::initialize);
        for(String kind:List.of(MediaHardware.HEADEND,MediaHardware.PBX))RackDevices.register(kind,DEVICES.get(kind));
        RackDevices.INSTALLED.add(i->{var r=MediaRef.of(i.host(),i.slot());if(r!=null&&MediaHardware.KINDS.contains(r.device().kind)){var s=r.state();if(s.owner.isEmpty())s.owner=i.player().getUUID().toString();r.changed(s);}});
        RackDevices.GUARDS.add((p,h,b)->{var r=MediaRef.of(h,b);return r==null||!MediaHardware.KINDS.contains(r.device().kind)||r.state().owns(p.getUUID());});
        var console=new MediaConsole();for(String k:MediaHardware.KINDS)if(dev.netlab.core.DeviceKinds.console(k))ConsoleExtensions.HANDLERS.put(k,console);
        MemoryCards.APPLY.add(MediaState::physical);MemoryCards.PHYSICAL.add(MediaState::physical);TopologyExtensions.PUBLIC_STATE.add(MediaState::redact);
        dev.netlab.sputnik.InternetBridge.external=new MediaInternet();dev.netlab.sputnik.SputnikPackets.EXTRA_AUDIO=MediaAudio::browser;
    }
    public Media(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);TABS.register(bus);integrate();NetLab.EXTRA_MODULE_ITEMS.addAll(MODULES.values());
        TABS.register("media",()->CreativeModeTab.builder().title(Component.literal("NetLab Media")).icon(()->DEVICES.get(MediaHardware.TV).get().asItem().getDefaultInstance()).displayItems((p,o)->{DEVICES.values().forEach(b->o.accept(b.get()));MODULES.values().forEach(m->o.accept(m.get()));CABLES.values().forEach(c->o.accept(c.get()));o.accept(PHONE_SIP.get());}).build());
        bus.addListener(MediaPackets::register);var events=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;events.addListener(MediaService::tick);events.addListener(MediaLearning::tick);LearningEvents.HINTS.add(MediaLearning::hint);events.addListener(MediaDemo::register);events.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->CABLES.values().forEach(c->c.get().preview(e)));events.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->{MediaService.clear();CABLES.values().forEach(c->c.get().clear());});
    }
}
