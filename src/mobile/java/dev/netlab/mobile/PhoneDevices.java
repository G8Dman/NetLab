package dev.netlab.mobile;

import com.google.gson.*;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Real item ownership gates a transient cellular network node and all phone applications. */
public final class PhoneDevices {
    private static final String CONFIG="phoneModules";
    private static final Map<String,Entry> ENTRIES=new HashMap<>();
    private static final class Entry {PortableHosts.Host host;String result="";boolean busy;}
    public static void clear(){ENTRIES.clear();}
    public static HandsetConfiguration configuration(ItemStack phone){var tag=MobileItems.privateTag(phone);return tag.contains(CONFIG)?MobileState.JSON.fromJson(StoredJson.get(tag,CONFIG),HandsetConfiguration.class):new HandsetConfiguration();}
    public static void save(ItemStack phone,HandsetConfiguration config){var tag=MobileItems.privateTag(phone);StoredJson.put(tag,CONFIG,MobileState.JSON.toJson(config));MobileItems.seal(phone,tag);}
    private static String key(ServerPlayer player,String id){return player.getUUID()+"/"+id;}
    public static ItemStack held(ServerPlayer player,String id){for(var item:List.of(player.getMainHandItem(),player.getOffhandItem()))if(item.is(Mobile.PHONE.get())&&MobileItems.privateTag(item).getString("phoneId").equals(id))return item;return ItemStack.EMPTY;}
    public static PortableHosts.Host prepare(ServerPlayer player,ItemStack phone){
        MobileItemStorage.reconcile(player,phone);String id=MobileItems.phoneId(phone);var e=ENTRIES.get(key(player,id));
        if(e==null||e.host.owner!=player||e.host.getLevel()!=player.serverLevel()){
            ENTRIES.entrySet().removeIf(old->!old.getValue().host.present());
            e=new Entry();var device=new Device("pc");device.identity=id;device.hostname="CiscoPocket";Hardware.ensure(device).profile="Cisco Pocket";device.ports.clear();var cellular=new Device.Port(false);cellular.name="Cellular0";cellular.medium="cellular";cellular.maxMbps=10;device.ports.add(cellular);device.preferredPort=0;
            e.host=new PortableHosts.Host(player,id,device,()->!held(player,id).isEmpty(),()->MobileService.lease(player.serverLevel(),"phone:"+id)!=null,module->{var item=held(player,id);if(item.isEmpty())return false;String slot=module.equals("CISCO-AUTOMATION")?"automation":module;return configuration(item).has(slot);},()->{var item=held(player,id);return item.isEmpty()?null:configuration(item).binding();});
            var privateTag=MobileItems.privateTag(phone);if(privateTag.contains("phoneMedia"))device.extensions.put("netlabmedia",StoredJson.get(privateTag,"phoneMedia"));
            ENTRIES.put(key(player,id),e);
        }
        var config=configuration(phone);Hardware.ensure(e.host.device).epoch=config.epoch;
        Hardware.ensure(e.host.device).modules.clear();if(config.has("automation"))Hardware.ensure(e.host.device).modules.put("app0","CISCO-AUTOMATION");
        var lease=MobileService.lease(player.serverLevel(),"phone:"+id);var port=e.host.device.ports.getFirst();port.up=lease!=null;port.ip=lease==null?"":lease.ip;port.mask="255.255.255.0";e.host.device.gateway=lease==null?"":lease.core.state().subscriberGateway;
        for(var hook:PortableHosts.PREPARED)hook.accept(e.host);
        return e.host;
    }
    public static PortableHosts.Host host(ServerLevel level,String client){
        if(!client.startsWith("phone:"))return null;String id=client.substring(6);
        for(var e:ENTRIES.values())if(e.host.device.identity.equals(id)&&e.host.getLevel()==level&&e.host.present())return e.host;return null;
    }
    public static void saveApplications(PortableHosts.Host host){var item=held(host.owner,host.device.identity);if(item.isEmpty()||!host.device.extensions.containsKey("netlabmedia"))return;var tag=MobileItems.privateTag(item);StoredJson.put(tag,"phoneMedia",host.device.extensions.get("netlabmedia"));MobileItems.seal(item,tag);}
    public static boolean matchesSim(ItemStack phone,String simId,String simKey){if(phone.isEmpty())return false;var sim=MobileItems.sim(phone);return sim!=null&&sim.id.equals(simId)&&sim.key.equals(simKey);}
    public static boolean matches(ServerLevel level,String client,String simId,String simKey){var h=host(level,client);return h!=null&&matchesSim(held(h.owner,h.device.identity),simId,simKey);}
    public static void attached(MobileService.Lease lease){var h=host(lease.core.level(),lease.client);if(h!=null){var p=h.device.ports.getFirst();p.ip=lease.ip;p.up=true;h.device.gateway=lease.core.state().subscriberGateway;}}
    public static void publicState(ServerPlayer player,ItemStack phone,JsonObject out){
        var host=prepare(player,phone);var config=configuration(phone);out.addProperty("endpoint",host.getBlockPos().asLong());out.addProperty("ip",host.device.ports.getFirst().ip);var modules=new JsonArray();
        for(var slot:HandsetConfiguration.SLOTS){var def=PortableModules.DEFINITIONS.get(slot);if(def==null)continue;var row=new JsonObject();row.addProperty("slot",slot);row.addProperty("title",def.title());row.addProperty("installed",config.has(slot));if(slot.equals("automation")&&config.binding()!=null)row.addProperty("server",config.binding().address());modules.add(row);}out.add("modules",modules);
        var entry=ENTRIES.get(key(player,host.device.identity));out.addProperty("result",entry.result);out.addProperty("busy",entry.busy);
    }
    public static void install(ServerPlayer player,ItemStack phone,String slot){
        var def=PortableModules.DEFINITIONS.get(slot);if(def==null)throw new IllegalArgumentException("Нужно соответствующее дополнение");
        ItemStack found=ItemStack.EMPTY;for(var item:player.getInventory().items)if(item.is(def.item().get())){found=item;break;}
        if(found.isEmpty())throw new IllegalArgumentException("Нужен модуль: "+def.title());var config=configuration(phone);
        ItemConfigurations.reconcile(player,found);
        config.install(slot,new HandsetConfiguration.Installed(def.code(),PortableModules.binding(found)));save(phone,config);found.shrink(1);prepare(player,phone);
    }
    public static void eject(ServerPlayer player,ItemStack phone,String slot){
        var def=PortableModules.DEFINITIONS.get(slot);if(def==null)throw new IllegalArgumentException("Неизвестный модуль");var config=configuration(phone);var installed=config.eject(slot);var item=new ItemStack(def.item().get());if(installed.binding()!=null)PortableModules.bind(item,installed.binding());save(phone,config);if(!player.addItem(item))player.drop(item,false);prepare(player,phone);
    }
    public static void diagnose(ServerPlayer player,ItemStack phone,String action,String json){
        var host=prepare(player,phone);PortableHosts.require(player,host,"diagnostics");var entry=ENTRIES.get(key(player,host.device.identity));if(entry.busy)throw new IllegalArgumentException("Дождись предыдущего пакета");
        var request=JsonParser.parseString(json).getAsJsonObject();String address=request.get("address").getAsString().strip();Device.ip(address);var network=host.network();var operations=new java.util.ArrayList<Network.Operation>();network.observer=operations::add;String result;
        if(action.equals("ping"))result=network.ping(host.slotId(0),address).text();
        else{String protocol=request.get("protocol").getAsString().toLowerCase(Locale.ROOT);if(!List.of("tcp","udp").contains(protocol))throw new IllegalArgumentException("TCP или UDP");int port=request.get("port").getAsInt();if(port<1||port>65535)throw new IllegalArgumentException("Порт 1..65535");byte[] payload=request.get("payload").getAsString().getBytes(StandardCharsets.UTF_8);if(payload.length<1||payload.length>1024)throw new IllegalArgumentException("Полезная нагрузка 1..1024 байт");var sent=network.transmit(host.slotId(0),address,protocol,port,payload);result=sent.result().text()+"\n"+protocol.toUpperCase(Locale.ROOT)+" / "+payload.length+" байт";}
        entry.busy=true;entry.result="Пакет в пути…";long epoch=Hardware.ensure(host.device).epoch;var guard=Simulation.guard(player.serverLevel(),network);Study.publish(network,player.serverLevel());WirelessWorld.changed(player.serverLevel(),network);String output=result;
        Simulation.after(player.serverLevel(),network.cursor,()->{entry.busy=false;entry.result=host.online()&&Hardware.ensure(host.device).epoch==epoch&&guard.getAsBoolean()?output:"Передача прервана: телефон, SIM или радиосеть изменились";if(host.online()&&guard.getAsBoolean()&&operations.stream().anyMatch(op->op.result().success()))LearningEvents.emit(player,"mobile.diagnosis",host,0,"routed",operations.stream().filter(op->op.result().success()).flatMap(op->op.result().path().stream()).map(network.devices::get).filter(java.util.Objects::nonNull).anyMatch(d->dev.netlab.core.DeviceKinds.router(d.kind)));if(entry.result.length()>8000)entry.result=entry.result.substring(0,8000)+"\n…";return true;});
    }
}
