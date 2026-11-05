package dev.netlab;

import com.google.gson.*;
import dev.netlab.core.*;
import dev.netlab.api.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Configuration storage belongs to the physical device/card, not its network identity. */
public final class MemoryCards {
    private static final Gson JSON=new Gson();
    private static final String KEY="netlab.memory",VERSION="netlab.memoryVersion";
    public static final List<java.util.function.BiConsumer<Device,Device>> APPLY=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final List<java.util.function.BiConsumer<Device,Device>> PHYSICAL=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final class Card {public String kind="",image="";public boolean active;}
    public static boolean required(Device d){return DeviceKinds.memory(d.kind);}
    public static boolean present(Device d){return d.extensions.containsKey(KEY);}
    public static Card card(Device d){return present(d)?JSON.fromJson(d.extensions.get(KEY),Card.class):null;}
    private static Device copy(Device d){return JSON.fromJson(JSON.toJson(d),Device.class);}
    private static void put(Device d,Card c){d.extensions.put(KEY,JSON.toJson(c));}
    private static void seal(ItemStack stack,CompoundTag privateData){
        stack.set(NetLab.CONFIGURATION.get(),privateData);
        try{var tag=new CompoundTag();tag.putString("netlabConfigFingerprint",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(privateData.toString().getBytes(StandardCharsets.UTF_8))));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static void fresh(Device d){d.extensions.put(VERSION,"1");}
    public static void demo(Device d){if(required(d)&&!present(d)){fresh(d);var c=new Card();c.kind=d.kind;c.active=true;put(d,c);sync(d);}}
    public static void migrate(Device d){
        if(!required(d)||d.extensions.containsKey(VERSION))return;fresh(d);var c=new Card();c.active=true;c.kind=d.kind;put(d,c);sync(d);
    }
    private static String encode(Device d){
        var image=copy(d);image.extensions.remove(KEY);image.extensions.remove(VERSION);
        for(var port:image.ports){port.sent=0;port.received=0;port.drops=0;}if(image.wireless!=null)image.wireless.associated=Long.MIN_VALUE;
        // These are live observations and can be reconstructed; passwords/configuration remain.
        if(image.router!=null){image.router.activeUsers.clear();image.router.sessionTimes.clear();image.router.learnedMacs.clear();image.router.arp.clear();image.router.switchLog.clear();}
        try{var bytes=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(bytes)){gzip.write(JSON.toJson(image).getBytes(StandardCharsets.UTF_8));}String data=Base64.getEncoder().encodeToString(bytes.toByteArray());if(data.length()>24576)throw new IllegalArgumentException("Конфигурация превышает ёмкость карты (24 KiB сжатых данных)");return data;}catch(IOException e){throw new IllegalStateException(e);}
    }
    private static Device decode(String image){
        try(var gzip=new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(image)))){byte[] bytes=gzip.readNBytes(131073);if(bytes.length>131072)throw new IllegalArgumentException("Слишком большая конфигурация карты");var d=JSON.fromJson(new String(bytes,StandardCharsets.UTF_8),Device.class);d.normalize();return d;}catch(IOException|JsonParseException e){throw new IllegalArgumentException("Повреждённая карта памяти",e);}
    }
    public static void sync(Device d){if(!required(d)||!present(d))return;var c=card(d);if(c.active){c.kind=d.kind;c.image=encode(d);put(d,c);}}
    public static Device blank(Device d){
        var fresh=new Device(d.kind);fresh.identity=d.identity;fresh.hardware=d.hardware==null?null:copy(d).hardware;
        fresh.ports.clear();for(var port:d.ports){var p=new Device.Port(d.kind.equals("switch")||port.console);p.name=port.name;p.console=port.console;p.hidden=port.hidden;p.virtual=port.virtual;p.medium=port.medium;p.hardwareSlot=port.hardwareSlot;p.maxMbps=port.maxMbps;p.bandwidth=port.maxMbps*1000;p.l2=port.l2;fresh.ports.add(p);}
        fresh.extensions.put(VERSION,"1");if(present(d))fresh.extensions.put(KEY,d.extensions.get(KEY));
        // Ownership and endpoint identity are physical metadata, not transferable configuration.
        if(d.extensions.containsKey("automation")){var old=JsonParser.parseString(d.extensions.get("automation")).getAsJsonObject();var keep=new JsonObject();for(String k:List.of("owner","identity"))if(old.has(k))keep.add(k,old.get(k));fresh.extensions.put("automation",keep.toString());}
        for(var hook:PHYSICAL)hook.accept(fresh,d);return fresh;
    }
    public static Device saved(Device d){return required(d)&&(!present(d)||!card(d).active)?blank(d):d;}
    public static String status(Device d){return !required(d)?"Встроенная память":!present(d)?"Карта CISCO: нет (конфигурация временная)":card(d).active?"Карта CISCO: запись настроек включена":"Карта CISCO: введи memory read";}
    public static void insert(ServerPlayer p,Device d){
        if(!required(d))throw new IllegalArgumentException("Встроенная память; карта не требуется");if(present(d))throw new IllegalArgumentException("Карта уже установлена");
        ItemStack found=ItemStack.EMPTY;for(var s:p.getInventory().items)if(s.is(NetLab.MEMORY_CARD.get())){found=s;break;}
        if(found.isEmpty()&&!p.isCreative())throw new IllegalArgumentException("Нужна карта памяти CISCO в инвентаре");
        var tag=found.getOrDefault(NetLab.CONFIGURATION.get(),new CompoundTag());var c=tag.contains("cisConfig")?JSON.fromJson(tag.getString("cisConfig"),Card.class):new Card();
        if(c.image.isBlank()){c.kind=d.kind;c.active=true;}else c.active=false;put(d,c);fresh(d);sync(d);if(!found.isEmpty())found.shrink(1);
    }
    public static void eject(ServerPlayer p,Device d){
        if(!present(d))throw new IllegalArgumentException("Карта не установлена");sync(d);var stack=cardItem(d);d.extensions.remove(KEY);if(!p.addItem(stack))p.drop(stack,false);
    }
    public static ItemStack cardItem(Device d){if(!present(d))return ItemStack.EMPTY;var stack=new ItemStack(NetLab.MEMORY_CARD.get());var tag=new CompoundTag();var c=card(d);c.active=false;tag.putString("cisConfig",JSON.toJson(c));seal(stack,tag);return stack;}
    public static ItemStack deviceItem(net.minecraft.world.level.block.Block block,Device d){sync(d);var stack=new ItemStack(block);var tag=new CompoundTag();StoredJson.put(tag,"netlabDevice",JSON.toJson(saved(d)));seal(stack,tag);return stack;}
    public static void read(Device d){
        if(!present(d)||card(d).image.isBlank())throw new IllegalArgumentException("Нет записанной карты");var c=card(d);var image=decode(c.image);
        if(!image.kind.equals(d.kind)||!Hardware.ensure(image).profile.equals(Hardware.ensure(d).profile))throw new IllegalArgumentException("Нужно устройство того же типа и шасси");
        var modules=Hardware.ensure(d).modules;if(!modules.equals(Hardware.ensure(image).modules))throw new IllegalArgumentException("Сначала установи такие же аппаратные модули; карта их не создаёт");
        String identity=d.identity;var hardware=d.hardware;var physicalExtensions=new HashMap<>(d.extensions);
        d.hostname=image.hostname;d.ports=image.ports;d.gateway=image.gateway;d.routes=image.routes;d.router=image.router;d.wireless=image.wireless;d.preferredPort=image.preferredPort;d.extensions=image.extensions;
        d.identity=identity;d.hardware=hardware;d.extensions.put(VERSION,"1");
        for(var hook:APPLY)hook.accept(d,withExtensions(d,physicalExtensions));c.active=true;put(d,c);hardware.epoch++;sync(d);
    }
    private static Device withExtensions(Device d,Map<String,String> extensions){var old=new Device(d.kind);old.extensions=extensions;return old;}
    public static String command(ServerPlayer p,ConsoleAccess.Target target,String line){
        var cli=target.cli();
        // A password prompt consumes text verbatim; a remote EXEC belongs to its peer.
        if(!cli.pending.isEmpty()||!cli.authenticated||cli.remoteId!=Long.MIN_VALUE)return null;
        line=line.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        var resolver=cli;
        // TrafficControl uses its own LAN login instead of IOS enable; saving a
        // controller's existing lease never grants new LAN/VLAN permissions.
        if(target.device().kind.equals("traffic")){resolver=new dev.netlab.core.Console();resolver.mode=1;}
        String canonical=CommandGrammar.resolve(resolver,target.device(),line).value();
        if(canonical.startsWith("do "))canonical=canonical.substring(3);
        boolean save=ConfigurationCommands.isSave(canonical);
        if(!save&&!line.startsWith("memory ")&&!line.equals("show memory"))return null;
        if(!RackDevices.allowed(p,target.entity(),target.id()==target.entity().slotId(1)?1:0))return "% Нет доступа к устройству";
        if(line.equals("show memory"))return status(target.device());
        if(!save&&cli.mode!=1&&!target.device().kind.equals("traffic"))return "% Сначала enable";
        try{
            if(save){ConsoleExtensions.flush(target);String result=write(target.device());target.entity().changed();return result;}
            if(line.equals("memory read")){read(target.device());restoreServices(target.entity(),target.id()==target.entity().slotId(1)?1:0);}
            else return "% write memory | memory read | show memory";
            target.entity().changed();return "Карта памяти: OK";
        }catch(IllegalArgumentException e){return "% "+e.getMessage();}
    }
    /** Save running configuration and activate the physical card without reading stale settings. */
    public static String write(Device d){
        if(required(d)&&!present(d))throw new IllegalArgumentException("Вставь карту памяти CISCO; конфигурация не сохранена");
        String result=ConfigurationCommands.save(d);
        if(required(d)){var c=card(d);c.active=true;put(d,c);sync(d);}
        return result;
    }
    public static void restoreServices(DeviceEntity host,int slot){var d=slot==0?host.device:host.upperDevice;if(d!=null&&host.getLevel() instanceof net.minecraft.server.level.ServerLevel level){if(d.kind.equals("router"))dev.netlab.api.VpnStore.get(level).restore(host.slotId(slot),d);if(d.kind.equals("traffic"))TrafficControllers.restore(level,d);}}
    public static CommandGrammar.Completion complete(String prefix){if(!prefix.startsWith("memory")&&!prefix.startsWith("show mem"))return null;var choices=List.of("memory read","memory write","show memory").stream().filter(c->c.startsWith(prefix)).toList();return new CommandGrammar.Completion(choices.size()==1?choices.getFirst():prefix,choices);}
    public static CompoundTag publicTag(CompoundTag tag){
        for(String key:List.of("device","upperDevice"))if(tag.contains(key)){
            var d=JSON.fromJson(StoredJson.get(tag,key),Device.class);d.extensions.remove("netlab.vpn");d.extensions.remove("netlab.trafficLease");
            if(d.router!=null){d.router.startup="";d.router.flash.clear();d.router.enableSecret="";d.router.rsaPrivate="";d.router.users.clear();}
            if(present(d)){var c=card(d);c.image="";put(d,c);}
            if(d.extensions.containsKey("automation")){var raw=JsonParser.parseString(d.extensions.get("automation")).getAsJsonObject();var publicState=new JsonObject();for(String name:List.of("owner","identity","signal"))if(raw.has(name))publicState.add(name,raw.get(name));d.extensions.put("automation",publicState.toString());}
            for(var filter:dev.netlab.api.TopologyExtensions.PUBLIC_STATE)filter.accept(d);
            StoredJson.put(tag,key,JSON.toJson(d));
        }return tag;
    }
}
