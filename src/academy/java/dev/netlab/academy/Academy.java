package dev.netlab.academy;
import com.google.gson.*;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.academy.core.Course;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import static net.minecraft.commands.Commands.literal;
@Mod("netlabacademy")
public final class Academy {
    private static final Gson JSON=new Gson();
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue EXTERNAL=BUILDER.comment("Require a real external site/video for the installed-suite final. Does not disable optional tasks.").define("requireExternalInternetForFinal",false),VOICE=BUILDER.define("requireMicrophoneForFinal",false),FLUIDS=BUILDER.define("requireFluidTanksForFinal",false);
    private static final ModConfigSpec SPEC=BUILDER.build();
    private static final Map<UUID,Long> BOOK=new HashMap<>(),PAGES=new HashMap<>();
    private static final Map<UUID,String> SENT=new HashMap<>();
    private static final Set<UUID> DIRTY=new HashSet<>();
    static Set<String> mods(){var s=new HashSet<String>();ModList.get().getMods().forEach(m->s.add(m.getModId()));return Set.copyOf(s);}
    public static Course.Policy policy(){boolean ae2=false;try{if(ModList.get().isLoaded("netlabautomation"))ae2=(Boolean)Class.forName("dev.netlab.automation.compat.Ae2AutomationPolicy").getMethod("enabled").invoke(null);}catch(ReflectiveOperationException ignored){}return new Course.Policy(EXTERNAL.get(),VOICE.get(),FLUIDS.get(),ae2);}
    public Academy(IEventBus bus,ModContainer container){
        AcademyConditions.register(bus);container.registerConfig(ModConfig.Type.SERVER,SPEC);bus.addListener(AcademyPackets::register);
        var events=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        events.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p){restore(p);sync(p,false);}});
        events.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{SENT.remove(e.getEntity().getUUID());BOOK.remove(e.getEntity().getUUID());PAGES.remove(e.getEntity().getUUID());DIRTY.remove(e.getEntity().getUUID());});
        events.addListener((PlayerEvent.ItemCraftedEvent e)->{if(e.getEntity() instanceof ServerPlayer p)crafted(p,BuiltInRegistries.ITEM.getKey(e.getCrafting().getItem()).toString());});
        events.addListener((PlayerEvent.ItemSmeltedEvent e)->{if(e.getEntity() instanceof ServerPlayer p&&e.getSmelting().is(NetLab.CISCIUM_INGOT.get()))prove(p,"B03");});
        events.addListener((net.neoforged.neoforge.event.level.BlockDropsEvent e)->{if(!e.isCanceled()&&e.getBreaker() instanceof ServerPlayer p&&BuiltInRegistries.BLOCK.getKey(e.getState().getBlock()).toString().matches("netlab:(deepslate_)?ciscium_ore")&&e.getTool().isCorrectToolForDrops(e.getState())&&e.getDrops().stream().anyMatch(i->i.getItem().is(NetLab.RAW_CISCIUM.get())))prove(p,"B02");});
        events.addListener((net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e)->{if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p){String id=BuiltInRegistries.BLOCK.getKey(e.getPlacedBlock().getBlock()).toString();fact(p,"placed:"+id);if(p.serverLevel().getBlockEntity(e.getPos()) instanceof DeviceEntity h)event(new LearningEvents.Event(p.serverLevel(),p.getUUID(),"placed",h,0,Map.of()));}});
        events.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e)->e.getDispatcher().register(literal("netlab").then(literal("academy").executes(c->{var p=c.getSource().getPlayerOrException();BOOK.put(p.getUUID(),p.serverLevel().getGameTime()+1200);sync(p,true);return 1;}))));
        events.addListener(Academy::tick);
        events.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->{BOOK.clear();PAGES.clear();DIRTY.clear();SENT.clear();});
        LearningEvents.HINTS.add((p,h)->{if(h.kind().equals("goggles.cable")&&p.getItemBySlot(EquipmentSlot.HEAD).is(NetLab.GOGGLES.get())){var hit=CableGeometry.pick(p);if(hit!=null&&hit.device().getBlockPos().equals(h.pos())&&Integer.toString(hit.port()).equals(h.value())&&!hit.device().links.get(hit.port()).name().isBlank()&&progress(p.serverLevel(),p.getUUID()).fact("named:"+hit.device().links.get(hit.port()).circuit()))prove(p,"P11");}if(h.kind().equals("study.speed")&&p.getItemBySlot(EquipmentSlot.HEAD).is(NetLab.GOGGLES.get())&&progress(p.serverLevel(),p.getUUID()).evidence.contains("D01"))prove(p,"D03");});
        LearningEvents.EVENTS.add(Academy::event);LearningEvents.COMMANDS.add(Evidence::command);
        RackDevices.INSTALLED.add(i->{event(new LearningEvents.Event(i.player().serverLevel(),i.player().getUUID(),"rack.install",i.host(),i.slot(),Map.of()));});
    }
    public static Course.Progress progress(ServerLevel l,UUID actor){return AcademyStore.get(l).player(actor);}
    public static void changed(ServerLevel l,UUID actor){var progress=progress(l,actor);while(progress.values.size()>8192){var iterator=progress.values.keySet().iterator();iterator.next();iterator.remove();}AcademyStore.get(l).setDirty();DIRTY.add(actor);}
    public static void prove(ServerLevel l,UUID actor,String... ids){var p=progress(l,actor);Collections.addAll(p.evidence,ids);changed(l,actor);}
    public static void prove(ServerPlayer p,String... ids){prove(p.serverLevel(),p.getUUID(),ids);}
    static void fact(ServerPlayer p,String key){var data=progress(p.serverLevel(),p.getUUID());data.fact(key,true);combine(data);changed(p.serverLevel(),p.getUUID());}
    static void combine(Course.Progress p){
        if(p.fact("craft:netlab:cisco_bible")&&p.fact("book.open"))p.prove("B05");
        if(p.fact("craft:netlab:module_assembler")&&p.fact("placed:netlab:module_assembler"))p.prove("B11");
        if(p.fact("craft:netlab:diagnostic_goggles")&&p.fact("goggles"))p.prove("B15");
        if(p.fact("craft:netlab:energy_cabinet")&&p.fact("craft:netlab:rack_power_supply"))p.prove("P03");
        if(p.fact("read:console")&&p.fact("read:ipv4"))p.prove("B06");
        if(p.fact("help")&&p.fact("read:command"))p.prove("C05");
    }
    private static final Map<String,String> CRAFT=Map.ofEntries(Map.entry("basic_board","B04"),Map.entry("copper_cable","B07"),Map.entry("ethernet_cable","B07"),Map.entry("pc","B08"),Map.entry("laptop","B08"),Map.entry("switch","B09"),Map.entry("router","B10"),Map.entry("advanced_board","B13"),Map.entry("portable_terminal","B14"),Map.entry("cisco_memory_card","B16"),Map.entry("memory_card","B16"),Map.entry("ciscium_block","E04"),Map.entry("vkusno_i_tochka_block","E05"));
    private static void crafted(ServerPlayer p,String item){fact(p,"craft:"+item);String suffix=item.startsWith("netlab:")?item.substring(7):"";if(CRAFT.containsKey(suffix))prove(p,CRAFT.get(suffix));Evidence.crafted(p,item);}
    private static void event(LearningEvents.Event e){
        var p=e.level().getServer().getPlayerList().getPlayer(e.actor());if(e.kind().equals("book.open")){BOOK.put(e.actor(),e.level().getGameTime()+1200);if(p!=null){fact(p,"book.open");sync(p,false);}return;}
        Evidence.event(e);changed(e.level(),e.actor());
    }
    public static void page(ServerPlayer p,String article){
        long now=p.serverLevel().getGameTime();if(now>BOOK.getOrDefault(p.getUUID(),-1L)||now==PAGES.getOrDefault(p.getUUID(),-1L))return;PAGES.put(p.getUUID(),now);BOOK.put(p.getUUID(),now+1200);
        if(article.equals("netlabacademy:course/route"))prove(p,"B01");
        // UI reports only article reads. It cannot submit task IDs, configuration or network success.
        if(article.startsWith("netlab:commands/")){fact(p,"read:command");if(article.contains("ip_address")||article.contains("ipconfig")||article.contains("ip_interface"))fact(p,"read:ipv4");if(article.contains("console")||article.contains("enable")||article.contains("configure_terminal"))fact(p,"read:console");}
    }
    private static void restore(ServerPlayer p){
        var data=progress(p.serverLevel(),p.getUUID());var manager=p.server.getAdvancements();for(var t:Catalog.COURSE.available(mods(),policy())){var root=manager.get(ResourceLocation.parse("netlabacademy:"+t.group()+"/root"));if(root!=null)p.getAdvancements().award(root,"course");if(data.completed.contains(t.id())){var a=manager.get(ResourceLocation.parse("netlabacademy:"+t.path()));if(a!=null)p.getAdvancements().award(a,"evidence");}}
    }
    static void sync(ServerPlayer p,boolean open){var data=progress(p.serverLevel(),p.getUUID());var active=Catalog.COURSE.available(mods(),policy()).stream().map(Course.Task::id).collect(java.util.stream.Collectors.toSet());var j=new JsonObject();j.add("mods",JSON.toJsonTree(mods()));j.add("policy",JSON.toJsonTree(policy()));j.add("completed",JSON.toJsonTree(data.completed.stream().filter(active::contains).toList()));j.add("evidence",JSON.toJsonTree(data.evidence.stream().filter(active::contains).toList()));j.addProperty("goals",Catalog.COURSE.currentGoalsComplete(data,mods(),policy()));String payload=j.toString();if(open||!payload.equals(SENT.get(p.getUUID()))){SENT.put(p.getUUID(),payload);PacketDistributor.sendToPlayer(p,new AcademyPackets.State(payload,open));}}
    private static void tick(ServerTickEvent.Post e){
        if(e.getServer().getTickCount()%20!=0)return;
        Evidence.scan(e.getServer());
        for(var p:e.getServer().getPlayerList().getPlayers()){
            var data=progress(p.serverLevel(),p.getUUID());if(p.getItemBySlot(EquipmentSlot.HEAD).is(NetLab.GOGGLES.get())){data.fact("goggles",true);combine(data);}
            var earned=Catalog.COURSE.advance(data,mods(),policy());if(!earned.isEmpty()){changed(p.serverLevel(),p.getUUID());restore(p);}if(DIRTY.remove(p.getUUID()))sync(p,false);
        }
    }
}
