import dev.netlab.automation.compat.Ae2AutomationPolicy;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.*;
import org.spongepowered.asm.service.*;

import java.io.*;
import java.lang.reflect.*;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.*;

/** Applies the REAL Sponge transformer to the installed AE2 jar. No world, client or server is started. */
public final class Ae2AutomationRuntimeCheck {
    static int checks;
    static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }
    static byte[] bytes(String name) throws IOException {
        try (var in = Ae2AutomationRuntimeCheck.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) throw new FileNotFoundException(name);
            return in.readAllBytes();
        }
    }
    public static final class Service extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
        public String getName() { return "NetLab isolated bytecode check"; }
        public boolean isValid() { return true; }
        public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
        public IClassProvider getClassProvider() { return this; }
        public IClassBytecodeProvider getBytecodeProvider() { return this; }
        public ITransformerProvider getTransformerProvider() { return null; }
        public IClassTracker getClassTracker() { return null; }
        public IMixinAuditTrail getAuditTrail() { return null; }
        public Collection<String> getPlatformAgents() { return List.of(); }
        public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("NetLab check"); }
        public InputStream getResourceAsStream(String name) { return Ae2AutomationRuntimeCheck.class.getClassLoader().getResourceAsStream(name); }
        public URL[] getClassPath() { return new URL[0]; }
        public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
        public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, Ae2AutomationRuntimeCheck.class.getClassLoader());
        }
        public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
        public ClassNode getClassNode(String name) throws IOException { return getClassNode(name, false); }
        public ClassNode getClassNode(String name, boolean transform) throws IOException { return getClassNode(name, transform, 0); }
        public ClassNode getClassNode(String name, boolean transform, int flags) throws IOException {
            var node = new ClassNode(); new ClassReader(bytes(name.replace('/', '.'))).accept(node, flags); return node;
        }
        public IMixinTransformer transformer() { return getInternal(IMixinTransformerFactory.class).createTransformer(); }
    }
    public static final class Bootstrap implements IMixinServiceBootstrap {
        public String getName() { return "NetLab isolated bytecode check"; }
        public String getServiceClassName() { return Service.class.getName(); }
        public void bootstrap() {}
    }
    public static final class Properties implements IGlobalPropertyService {
        private record Key(String value) implements IPropertyKey {}
        private final Map<IPropertyKey,Object> values = new HashMap<>();
        public IPropertyKey resolveKey(String key) { return new Key(key); }
        @SuppressWarnings("unchecked") public <T> T getProperty(IPropertyKey key) { return (T)values.get(key); }
        public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
        public <T> T getProperty(IPropertyKey key, T fallback) { T value=getProperty(key); return value==null?fallback:value; }
        public String getPropertyString(IPropertyKey key, String fallback) { Object value=values.get(key); return value==null?fallback:value.toString(); }
    }
    static final class TransformedLoader extends ClassLoader {
        final Map<String,byte[]> classes;
        TransformedLoader(Map<String,byte[]> classes) { super(Ae2AutomationRuntimeCheck.class.getClassLoader()); this.classes=classes; }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!classes.containsKey(name)) return super.loadClass(name, resolve);
            Class<?> type=findLoadedClass(name);
            if(type==null) { byte[] value=classes.get(name); type=defineClass(name,value,0,value.length); }
            if(resolve) resolveClass(type);
            return type;
        }
    }
    static void setting(boolean disabled) throws Exception {
        if (!Ae2AutomationPolicy.CONFIG.isLoaded()) {
            var config=CommentedConfig.inMemory(); config.set("disableAe2Automation",disabled);
            Ae2AutomationPolicy.CONFIG.correct(config);
            Class<?> type=Class.forName("net.neoforged.fml.config.LoadedConfig");
            var constructor=type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var loaded=constructor.newInstance(config,null,null);
            Ae2AutomationPolicy.CONFIG.acceptConfig((IConfigSpec.ILoadedConfig)loaded);
        } else Ae2AutomationPolicy.DISABLE.set(disabled);
    }
    static Object allocate(Class<?> type) throws Exception {
        // Inert objects only: no constructor, Level, tick loop, sockets or game lifecycle.
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return ((sun.misc.Unsafe)field.get(null)).allocateInstance(type);
    }
    static Object invoke(Class<?> type,Object receiver,String name,Object... args) throws Exception {
        Method target=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();
        target.setAccessible(true); return target.invoke(receiver,args);
    }
    public static void main(String[] args) throws Exception {
        check(!Ae2AutomationPolicy.enabled(),"pack rule defaults off before config load");
        setting(true); check(Ae2AutomationPolicy.enabled(),"server config enables the rule");
        check(Ae2AutomationPolicy.automationItem(net.minecraft.resources.ResourceLocation.parse("ae2:export_bus")),"automation item is recognized");
        check(Ae2AutomationPolicy.automationItem(net.minecraft.resources.ResourceLocation.parse("ae2:64k_crafting_storage")),"real CPU item ID is recognized");
        for(String id:List.of("terminal","crafting_terminal","drive","chest","storage_bus","1k_item_storage_cell"))
            check(!Ae2AutomationPolicy.automationItem(net.minecraft.resources.ResourceLocation.parse("ae2:"+id)),"manual storage item remains available: "+id);
        System.setProperty("mixin.service",Service.class.getName());
        System.setProperty("mixin.bootstrapService",Bootstrap.class.getName());
        MixinBootstrap.init(); MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("netlabautomation.mixins.json");
        Mixins.addConfiguration("netlab.mixins.json");
        var transformer=((Service)MixinService.getService()).transformer();
        if(args.length>0 && args[0].equals("without-ae2")) {
            String name="dev.netlab.automation.compat.Ae2AutomationPolicy";
            check(Arrays.equals(bytes(name),transformer.transformClassBytes(name,name,bytes(name))),"optional mixins skip all absent AE2 targets");
            System.out.println("NetLab AE2 absent: "+checks+" checks passed; no AE2 linkage"); return;
        }
        var transformed=new LinkedHashMap<String,byte[]>();
        var targets=List.of("appeng.me.storage.NetworkStorage",
                "appeng.blockentity.storage.MEChestBlockEntity$ChestMonitorHandler",
                "appeng.helpers.InterfaceLogic$InterfaceInventory", "appeng.parts.automation.IOBusPart",
                "appeng.helpers.InterfaceLogic$Ticker", "appeng.helpers.patternprovider.PatternProviderLogic$Ticker",
                "appeng.blockentity.crafting.MolecularAssemblerBlockEntity", "appeng.blockentity.storage.IOPortBlockEntity",
                "appeng.parts.automation.AnnihilationPlanePart", "appeng.me.service.CraftingService",
                "appeng.crafting.execution.CraftingCpuLogic", "appeng.parts.automation.FormationPlanePart",
                "appeng.api.inventories.InternalInventoryItemHandler");
        for(String name:targets) {
            byte[] original=bytes(name), modified=transformer.transformClassBytes(name,name,original);
            var node=new ClassNode(); new ClassReader(modified).accept(node,0);
            check(!Arrays.equals(original,modified) && node.methods.stream().anyMatch(m->m.name.contains("netlab$")),"actual mixin applied: "+name);
            transformed.put(name,modified);
        }
        // Load targets separately: other AE2/NeoForge APIs stay in the parent class loader.
        var loader=new TransformedLoader(transformed);
        var sourceType=Class.forName("appeng.api.networking.security.IActionSource");
        Object machine=sourceType.getMethod("empty").invoke(null);
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),new ArrayList<>(),Map.of());
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        Object inertPlayer=allocate(net.minecraft.server.level.ServerPlayer.class);
        Object player=Proxy.newProxyInstance(loader,new Class<?>[]{sourceType},(o,m,a)->m.getName().equals("player")?Optional.of(inertPlayer):Optional.empty());
        Object mode=Class.forName("appeng.api.config.Actionable").getField("MODULATE").get(null);
        Class<?> storageType=Class.forName("appeng.api.storage.MEStorage");
        long[] moved={0};
        Object backend=Proxy.newProxyInstance(loader,new Class<?>[]{storageType},(o,m,a)->switch(m.getName()) {
            case "isPreferredStorageFor" -> true;
            case "insert","extract" -> { moved[0]+=(long)a[1]; yield a[1]; }
            case "hashCode" -> System.identityHashCode(o);
            case "equals" -> o==a[0];
            default -> m.getReturnType()==boolean.class?false:null;
        });
        Class<?> network=loader.loadClass(targets.getFirst()); Object inventory=network.getConstructor().newInstance();
        network.getMethod("mount",int.class,storageType).invoke(inventory,0,backend);
        for(String operation:List.of("insert","extract")) {
            check((long)invoke(network,inventory,operation,null,16L,mode,machine)==0 && moved[0]==0,"machine "+operation+" refuses transfer without consuming items/fluids");
            check((long)invoke(network,inventory,operation,null,16L,mode,player)==16,"manual terminal "+operation+" reaches storage");
            moved[0]=0;
        }
        for(String name:targets.subList(1,3)) {
            var type=loader.loadClass(name); Object monitor=allocate(type);
            check((long)invoke(type,monitor,"insert",null,1000L,mode,machine)==0,"direct local buffer/ME chest refuses automated fluid/item insert");
            check((long)invoke(type,monitor,"extract",null,16L,mode,machine)==0,"direct local buffer/ME chest refuses automated extraction");
        }
        // The common IOBusPart is abstract; its transformed bytecode above verifies import/export hooks.
        for(String name:targets.subList(4,9)) {
            var type=loader.loadClass(name);
            check(invoke(type,allocate(type),"tickingRequest",null,1)==Ae2AutomationPolicy.slowTick(),"existing device pauses before touching inventories: "+name);
        }
        var crafting=loader.loadClass("appeng.me.service.CraftingService");
        check(invoke(crafting,allocate(crafting),"submitJob",null,null,null,false,machine)==Ae2AutomationPolicy.deniedJob(),"new autocrafting job is rejected before reserving ingredients");
        var cpu=loader.loadClass("appeng.crafting.execution.CraftingCpuLogic");
        invoke(cpu,allocate(cpu),"tickCraftingLogic",null,null); check(true,"existing crafting CPU job is paused without clearing its buffer");
        var plane=loader.loadClass("appeng.parts.automation.FormationPlanePart");
        check((long)invoke(plane,allocate(plane),"placeInWorld",null,16L,mode)==0,"formation plane does not place or consume items");
        // Registry bootstrap only, for real ItemStack sentinels; still no world/server/client.
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var adapter=loader.loadClass("appeng.api.inventories.InternalInventoryItemHandler");
        Object handler=allocate(adapter); var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL,16);
        check(invoke(adapter,handler,"insertItem",0,stack,false)==stack && stack.getCount()==16,"external pipe insertion returns the whole original stack");
        check(invoke(adapter,handler,"extractItem",0,16,false)==net.minecraft.world.item.ItemStack.EMPTY,"external pipe extraction returns empty");
        setting(false);
        check((long)invoke(network,inventory,"insert",null,16L,mode,machine)==16,"disabling the pack rule restores machine transfers");
        System.out.println("NetLab AE2: "+checks+" real transformation and behavior checks passed (no game startup)");
    }
}
