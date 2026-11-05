import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.sputnik.*;
import dev.netlab.sputnik.core.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.*;
/** Registry, item, payload and privacy checks, without creating any client/server/Level. */
public class SputnikRuntimeCheck {
    static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
    public static void main(String[] args)throws Exception{
        ServerThermalRuntimeCheck.main(args);
        for(var entry:Sputnik.DEVICES.entrySet())Registry.register(BuiltInRegistries.BLOCK,entry.getValue().getId(),new SputnikBlock(BlockBehaviour.Properties.of(),entry.getKey()));
        for(var entry:Sputnik.ITEMS.getEntries()){var module=SputnikHardware.MODULES.stream().filter(m->ModuleItem.itemId(m).equals(entry.getId().getPath())).findFirst().orElse(null);Item item=module!=null?new ModuleItem(new Item.Properties(),module):BuiltInRegistries.BLOCK.containsKey(entry.getId())?new BlockItem(BuiltInRegistries.BLOCK.get(entry.getId()),new Item.Properties()):new Item(new Item.Properties());Registry.register(BuiltInRegistries.ITEM,entry.getId(),item);if(item instanceof BlockItem b)b.registerBlocks(Item.BY_BLOCK,item);}
        Sputnik.integrate();restoration();var regs=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);var hub=new Device(SputnikHardware.HUB);SputnikHardware.initialize(hub);var s=SputnikState.get(hub);s.owner=UUID.randomUUID().toString();s.subscribers.put(UUID.randomUUID().toString(),ConfigStore.secret("only-a-test-key"));s.network="test-provider";s.save(hub);MemoryCards.demo(hub);Hardware.ensure(hub).powered=false;Hardware.install(hub,"control0",SputnikHardware.CONTROL);Hardware.install(hub,"tx0",SputnikHardware.TX);Hardware.install(hub,"rx0",SputnikHardware.RX);Hardware.ensure(hub).powered=true;MemoryCards.sync(hub);
        var item=RackDevices.item(hub);var saved=ItemStack.parse(regs,item.save(regs)).orElseThrow();var restored=RackDevices.restore(saved,hub.kind);check(SputnikHardware.hubReady(restored)&&SputnikState.get(restored).subscribers.size()==1&&SputnikState.get(restored).owner.equals(s.owner),"configured rack item retains HUB modules, registration and physical owner");
        var next=new Device(hub.kind);SputnikHardware.initialize(next);Hardware.ensure(next).powered=false;Hardware.install(next,"control0",SputnikHardware.CONTROL);Hardware.install(next,"tx0",SputnikHardware.TX);Hardware.install(next,"rx0",SputnikHardware.RX);var physical=SputnikState.get(next);physical.owner=UUID.randomUUID().toString();physical.save(next);next.extensions.put("netlab.memory",hub.extensions.get("netlab.memory"));MemoryCards.read(next);check(SputnikState.get(next).owner.equals(physical.owner)&&SputnikState.get(next).network.equals("test-provider"),"reading a card applies config while preserving the destination owner");
        var mod=new Device(SputnikHardware.MODEM);SputnikHardware.initialize(mod);var m=SputnikState.get(mod);m.key="private-terminal-key";m.save(mod);var publicCopy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(mod),Device.class);SputnikState.redact(publicCopy);check(!publicCopy.extensions.get(SputnikState.KEY).contains(m.key),"terminal key never appears in public block state");var copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(hub),Device.class);SputnikState.redact(copy);check(SputnikState.get(copy).subscribers.isEmpty(),"provider authentication verifiers never appear in public block state");
        for(String module:SputnikHardware.MODULES)check(ModuleItem.find(module)==Sputnik.MODULES.get(module).get()&&!ModuleFabrication.MODULES.contains(module),"Sputnik module resolves to its real item and cannot use cheap assembler");
        byte[] data=new byte[SocksProxy.CHUNK];new Random(97).nextBytes(data);var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),regs);var q=new SputnikPackets.Tunnel(net.minecraft.core.BlockPos.ZERO,42,"DATA","172.30.77.2","",0,data);SputnikPackets.Tunnel.CODEC.encode(buf,q);var decoded=SputnikPackets.Tunnel.CODEC.decode(buf);check(Arrays.equals(data,decoded.data())&&decoded.id()==42,"maximum TLS chunk roundtrips through actual custom payload codec");buf.release();
        buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),regs);var a=new SputnikPackets.Sound(UUID.randomUUID(),net.minecraft.core.BlockPos.ZERO,72,.7f,new byte[12000]);SputnikPackets.Sound.CODEC.encode(buf,a);check(SputnikPackets.Sound.CODEC.decode(buf).pcm().length==12000,"maximum PCM packet roundtrips through native payload codec");buf.release();
        var headless=new FabricationRuntimeCheck.HeadlessDevice(Sputnik.DEVICES.get(SputnikHardware.MODEM).get());SputnikHardware.initialize(headless.device);check(FrontPanel.extraStatus(headless,0).contains("GEO-1"),"client/preview diagnostics never cast a non-server Level or perform server radio scans");
        check(SatelliteModel.carriers(s.txMhz,s.rxMhz,m.txMhz,m.rxMhz),"new HUB and modem defaults form distinct compatible forward/return carriers");
        for(String kind:SputnikHardware.KINDS){var device=new Device(kind);SputnikHardware.initialize(device);var blockState=Sputnik.DEVICES.get(kind).get().defaultBlockState();var json=java.nio.file.Files.readString(java.nio.file.Path.of("src/sputnik/resources/assets/netlabsputnik/models/block/"+kind.substring(8)+".json"));var model=net.minecraft.client.renderer.block.model.BlockModel.fromString(json);check(!model.getElements().isEmpty(),"Sputnik model parses: "+kind);
            for(var jack:PortLayout.jacks(device,false,0))for(double dx:new double[]{-.45*jack.width(),0,.45*jack.width()})for(double dy:new double[]{-.45*jack.height(),0,.45*jack.height()}){double x=jack.x()+dx,y=jack.y()+dy;double front=model.getElements().stream().filter(box->box.rotation==null&&x*16>=box.from.x&&x*16<=box.to.x&&y*16>=box.from.y&&y*16<=box.to.y).mapToDouble(box->box.from.z/16).min().orElseThrow(()->new AssertionError("Socket outside model: "+kind+"/"+jack.port()));check(jack.z()<front,"Sputnik socket is ahead of its real front face: "+kind);var hit=blockState.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).clip(new net.minecraft.world.phys.Vec3(x,y,-1),new net.minecraft.world.phys.Vec3(x,y,1),net.minecraft.core.BlockPos.ZERO);check(hit!=null&&hit.getDirection()==net.minecraft.core.Direction.NORTH,"Sputnik socket is reachable through actual block selection shape: "+kind);}
        }
        System.out.println(checks+" Sputnik registry/card/privacy/payload checks passed; no client, server or world launched.");
    }
    static void restoration(){
        for(String kind:SputnikHardware.KINDS){
            var expected=new Device(kind);SputnikHardware.initialize(expected);
            for(boolean emptyPrivateTag:List.of(false,true)){
                var item=new ItemStack(Sputnik.DEVICES.get(kind).get());
                if(emptyPrivateTag)item.set(NetLab.CONFIGURATION.get(),new net.minecraft.nbt.CompoundTag());
                var restored=RackDevices.restore(item,kind);
                check(Hardware.ensure(restored).profile.equals(Hardware.ensure(expected).profile),"fresh or stripped component restores the real chassis: "+kind);
                check(restored.ports.size()==expected.ports.size(),"fresh or stripped component restores all fixed ports: "+kind);
                for(int i=0;i<expected.ports.size();i++)check(restored.portName(i).equals(expected.portName(i))&&restored.ports.get(i).medium.equals(expected.ports.get(i).medium),"fixed port identity survives item initialization: "+kind+"/"+i);
            }
            check(RackDevices.supported(kind)==List.of(SputnikHardware.HUB,SputnikHardware.GATEWAY).contains(kind),"initializers do not make outdoor equipment rack-installable");
            var broken=new Device(kind);Hardware.ensure(broken);broken.normalize();String id=broken.identity;
            var state=SputnikState.get(broken);state.owner=UUID.randomUUID().toString();state.network="preserve-network";state.save(broken);
            broken.gateway="10.1.2.1";broken.ports.getFirst().ip="10.1.2.3";broken.routes.add(new Device.Route("10.2.0.0","255.255.0.0","10.1.2.1"));
            check(SputnikHardware.repairGeneric(broken),"recognizes the saved generic fallback: "+kind);
            check(broken.identity.equals(id)&&SputnikState.get(broken).owner.equals(state.owner)&&SputnikState.get(broken).network.equals(state.network),"repair preserves physical identity, ownership and satellite configuration");
            check(broken.ports.size()==expected.ports.size()&&Hardware.ensure(broken).profile.equals(Hardware.ensure(expected).profile),"repair restores real hardware topology: "+kind);
            if(List.of(SputnikHardware.HUB,SputnikHardware.GATEWAY,SputnikHardware.MODEM).contains(kind))check(broken.ports.getFirst().ip.equals("10.1.2.3")&&broken.gateway.equals("10.1.2.1")&&broken.routes.size()==1,"repair preserves legitimate LAN addressing and routes");
            check(!SputnikHardware.repairGeneric(broken),"repair is idempotent");
            if(MemoryCards.required(broken)){MemoryCards.demo(broken);MemoryCards.sync(broken);MemoryCards.read(broken);check(broken.ports.size()==expected.ports.size(),"repaired hardware survives card save/read");}
        }
        var odu=new Device(SputnikHardware.ODU);SputnikHardware.initialize(odu);odu.ports.getFirst().up=false;
        check(!SputnikHardware.repairGeneric(odu)&&!odu.ports.getFirst().up,"valid hardware never loses deliberate shutdown settings");
        var n=new Network();n.devices.put(1L,odu);var cli=new dev.netlab.core.Console();
        for(String line:List.of("enable","configure terminal","interface TX-IF","no shutdown"))check(!cli.execute(odu,n,1,line).contains("does not exist"),"real ODU console accepts named radio port");
        check(odu.ports.getFirst().up,"real radio port enabled via console");
    }
}
