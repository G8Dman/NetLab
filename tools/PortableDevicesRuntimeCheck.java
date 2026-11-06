import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.mobile.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import java.util.*;

/** Persistent item/NBT and actual public component wire encoding, without a world. */
public final class PortableDevicesRuntimeCheck {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static ItemStack wire(ItemStack item,RegistryAccess registries){var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),registries);ItemStack.STREAM_CODEC.encode(buffer,item);var decoded=ItemStack.STREAM_CODEC.decode(buffer);buffer.release();return decoded;}
    public static void main(String[] args)throws Exception{
        SputnikRuntimeCheck.main(args);var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        // The fixture replaces FML startup; reproduce its registry sync flags for the real ItemStack wire codec.
        var sync=net.neoforged.neoforge.registries.BaseMappedRegistry.class.getDeclaredMethod("setSync",boolean.class);sync.setAccessible(true);sync.invoke(BuiltInRegistries.ITEM,true);sync.invoke(BuiltInRegistries.DATA_COMPONENT_TYPE,true);
        var laptop=new Device("pc");Hardware.initializeHost(laptop,true);LaptopBattery.prepare(laptop);laptop.extensions.put(LaptopBattery.KEY,"12345");laptop.ports.getFirst().ip="192.168.1.23";laptop.gateway="192.168.1.1";Wireless.state(laptop).clientSsid="PortableLAN";Wireless.state(laptop).clientKey="private-wifi-test";laptop.os().dns.add("192.168.1.53");laptop.os().pcFiles.put("notes.txt","persistent note");Hardware.ensure(laptop).modules.put("app0","CISCO-AUTOMATION");
        var stack=MemoryCards.deviceItem(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("netlab:laptop")),laptop);var disk=ItemStack.parse(registries,stack.save(registries)).orElseThrow();var restored=RackDevices.restore(disk,"pc");
        check(restored.identity.equals(laptop.identity)&&restored.ports.getFirst().ip.equals("192.168.1.23")&&restored.gateway.equals("192.168.1.1"),"moving laptop retains its physical identity and network configuration");
        check(Wireless.state(restored).clientKey.equals("private-wifi-test")&&restored.os().dns.equals(List.of("192.168.1.53"))&&restored.os().pcFiles.get("notes.txt").equals("persistent note")&&Hardware.ensure(restored).modules.get("app0").equals("CISCO-AUTOMATION"),"moving laptop retains wireless secret, DNS, files and installed module");
        check(LaptopBattery.stored(restored)==12345,"breaking and placing does not refill the battery");
        var empty=restored;empty.extensions.put(LaptopBattery.KEY,"0");LaptopBattery.prepare(empty);String ip=empty.ports.getFirst().ip;check(!Hardware.powered(empty)&&empty.ports.getFirst().ip.equals(ip),"empty battery disables power while keeping the IP");
        var memory=new CompoundTag();ItemConfigurations.reconcile(memory,stack);var echo=wire(stack,registries);check(echo.getOrDefault(NetLab.CONFIGURATION.get(),new CompoundTag()).isEmpty(),"private laptop configuration never crosses the client component stream");check(!echo.get(DataComponents.CUSTOM_DATA).toString().contains("private-wifi-test"),"public laptop item contains battery label and opaque fingerprint only");
        ItemConfigurations.reconcile(new CompoundTag(),echo);check(echo.getOrDefault(NetLab.CONFIGURATION.get(),new CompoundTag()).isEmpty(),"another player's empty archive cannot recover private settings from a public fingerprint");
        ItemConfigurations.reconcile(memory,echo);var moved=RackDevices.restore(echo,"pc");check(moved.identity.equals(laptop.identity)&&LaptopBattery.stored(moved)==12345&&moved.ports.getFirst().ip.equals("192.168.1.23"),"creative inventory roundtrip restores owned laptop settings and battery");
        var wrong=new ItemStack(Items.STICK);wrong.set(DataComponents.CUSTOM_DATA,stack.get(DataComponents.CUSTOM_DATA));ItemConfigurations.reconcile(memory,wrong);check(!wrong.has(NetLab.CONFIGURATION.get()),"public fingerprint cannot restore data onto a different item type");
        var phone=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Mobile.ID,"cisco_pocket")));var config=new HandsetConfiguration();config.install("terminal",new HandsetConfiguration.Installed("CISCO-POCKET-CONSOLE",null));PhoneDevices.save(phone,config);String phoneId=MobileItems.phoneId(phone);
        var history=new PhoneTerminalMemory();var a=UUID.randomUUID().toString();var b=UUID.randomUUID().toString();var c=UUID.randomUUID().toString();var d=UUID.randomUUID().toString();
        history.open(a).remember("show version",false);history.open(b).remember("show vlan brief",false);history.open(c).remember("show ip route",false);history.open(a);history.open(d).remember("show clock",false);
        var privateTag=MobileItems.privateTag(phone);StoredJson.put(privateTag,"terminalHistory",MobileState.JSON.toJson(history));MobileItems.seal(phone,privateTag);
        var phoneSaved=ItemStack.parse(registries,phone.save(registries)).orElseThrow();var loaded=MobileState.JSON.fromJson(StoredJson.get(MobileItems.privateTag(phoneSaved),"terminalHistory"),PhoneTerminalMemory.class);
        check(PhoneDevices.configuration(phoneSaved).has("terminal")&&MobileItems.phoneId(phoneSaved).equals(phoneId),"phone terminal module persists with the same physical phone");
        check(loaded.devices.size()==3&&!loaded.devices.containsKey(b)&&loaded.open(a).history.equals(List.of("show version")),"NBT roundtrip preserves last-three recency and per-device command recall");
        ItemConfigurations.reconcile(memory,phone);var phoneEcho=wire(phone,registries);check(!phoneEcho.get(DataComponents.CUSTOM_DATA).toString().contains("show version"),"phone terminal commands remain private in item synchronization");ItemConfigurations.reconcile(memory,phoneEcho);check(MobileItems.privateTag(phoneEcho).contains("terminalHistory")&&PhoneDevices.configuration(phoneEcho).has("terminal"),"creative inventory keeps phone history and terminal hardware");
        for(int i=0;i<50;i++){var tag=MobileItems.privateTag(phone);tag.putInt("testRevision",i);MobileItems.seal(phone,tag);ItemConfigurations.reconcile(memory,phone);}check(memory.getAllKeys().size()<=33,"private creative restore archive is bounded");
        System.out.println(checks+" portable battery, item persistence, phone-history and private wire checks passed; no game launched.");
    }
}
