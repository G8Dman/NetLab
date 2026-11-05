package dev.netlab.sputnik;

import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.sputnik.core.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

/** Construction happens only when an operator explicitly invokes the demo command. */
public final class SputnikDemo {
    public static void register(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("netlab").then(Commands.literal("sputnik").requires(s->s.hasPermission(2)).then(Commands.literal("kit").executes(c->{var p=c.getSource().getPlayerOrException();Sputnik.DEVICES.values().forEach(b->give(p,new ItemStack(b.get())));Sputnik.CABLES.values().forEach(b->give(p,new ItemStack(b.get())));Sputnik.MODULES.values().forEach(b->give(p,new ItemStack(b.get())));give(p,new ItemStack(NetLab.GUIDE.get()));give(p,new ItemStack(NetLab.PORTABLE_TERMINAL.get()));give(p,new ItemStack(NetLab.MEMORY_CARD.get(),8));return 1;}))));
        e.getDispatcher().register(Commands.literal("netlab").then(Commands.literal("demo").requires(s->s.hasPermission(2)).then(Commands.literal("sputnik").executes(c->{var p=c.getSource().getPlayerOrException();var l=p.serverLevel();var base=p.blockPosition().offset(-6,0,4);
            for(int x=0;x<12;x++)for(int z=0;z<8;z++){var floor=base.offset(x,-1,z);if(!l.hasChunkAt(floor)||l.isOutsideBuildHeight(floor)||!l.getWorldBorder().isWithinBounds(floor)||!l.isEmptyBlock(floor)&&!l.getBlockState(floor).isFaceSturdy(l,floor,Direction.UP)){c.getSource().sendFailure(Component.literal("Нужна загруженная ровная область 12×8×4 к югу (+Z). Ничего не изменено."));return 0;}for(int y=0;y<4;y++){var q=base.offset(x,y,z);if(l.isOutsideBuildHeight(q)||!l.getWorldBorder().isWithinBounds(q)||!l.hasChunkAt(q)||!l.isEmptyBlock(q)){c.getSource().sendFailure(Component.literal("Освободи область 12×8×4 к югу (+Z). Мешает "+q.toShortString()+". Ничего не изменено."));return 0;}}}
            int subnet=77;var occupied=new HashSet<String>();for(var h:DeviceEntity.loaded(l))for(var d:Arrays.asList(h.device,h.upperDevice))if(d!=null)for(var port:d.ports)if(!port.ip.isBlank())occupied.add(port.ip);while(subnet<250){final int n=subnet;if(occupied.stream().noneMatch(ip->ip.startsWith("192.168."+n+".")||ip.startsWith("172.30."+n+".")||ip.startsWith("10.254."+n+".")))break;subnet++;}if(subnet>=250){c.getSource().sendFailure(Component.literal("Не найдена свободная подсеть для стенда"));return 0;}
            for(int x=0;x<12;x++)for(int z=0;z<8;z++){var q=base.offset(x,-1,z);if(l.isEmptyBlock(q))l.setBlockAndUpdate(q,Blocks.SMOOTH_STONE.defaultBlockState());}
            String lan="192.168."+subnet+".",provider="172.30."+subnet+".",air="10.254."+subnet+".";
            var rack=place(l,base.offset(7,0,1),NetLab.RACK.get());rack.device=device(SputnikHardware.HUB);rack.upperDevice=device(SputnikHardware.GATEWAY);var h=Hardware.ensure(rack.device);h.powered=false;Hardware.install(rack.device,"control0",SputnikHardware.CONTROL);Hardware.install(rack.device,"tx0",SputnikHardware.TX);Hardware.install(rack.device,"rx0",SputnikHardware.RX);h.powered=true;
            var hub=new SputnikRef(rack,0,rack.device);var gateway=new SputnikRef(rack,1,rack.upperDevice);hub.device().ports.get(0).ip=provider+"1";hub.device().ports.get(4).ip=air+"1";gateway.device().ports.get(0).ip=provider+"2";gateway.device().gateway=provider+"1";
            var dish=place(l,base.offset(1,0,1),Sputnik.DEVICES.get(SputnikHardware.DISH).get());var odu=place(l,base.offset(4,0,1),Sputnik.DEVICES.get(SputnikHardware.ODU).get());var terminal=place(l,base.offset(1,0,5),Sputnik.DEVICES.get(SputnikHardware.VSAT).get());var modem=place(l,base.offset(4,0,5),Sputnik.DEVICES.get(SputnikHardware.MODEM).get());var pc=place(l,base.offset(8,0,5),NetLab.LAPTOP.get());var speaker=place(l,base.offset(10,0,5),Sputnik.DEVICES.get(SputnikHardware.SPEAKER).get());
            pc.device=new Device("pc");Hardware.initializeHost(pc.device,true);SputnikHardware.audio(pc.device);pc.device.ports.get(0).ip=lan+"100";pc.device.gateway=lan+"1";modem.device.ports.get(0).ip=lan+"1";modem.device.ports.get(4).ip=air+"2";modem.device.gateway=air+"1";hub.device().routes.add(new Device.Route(lan+"0","255.255.255.0",air+"2"));
            String key=UUID.randomUUID().toString();var hs=hub.state();hs.owner=p.getUUID().toString();hs.subscribers.put(modem.device.identity,ConfigStore.secret(key));hub.changed(hs);var ms=SputnikState.get(modem.device);ms.key=key;ms.owner=p.getUUID().toString();ms.save(modem.device);var gs=gateway.state();gs.owner=p.getUUID().toString();gs.internet=true;gs.hubAddress=hub.ip();gateway.changed(gs);
            for(var host:List.of(dish,odu,terminal,speaker)){var s=SputnikState.get(host.device);s.owner=p.getUUID().toString();s.save(host.device);}
            wire(pc,0,modem,0,"copper");wire(rack,0,rack,128,"copper");wire(rack,2,odu,0,"coax_tx");wire(rack,3,odu,1,"coax_rx");wire(odu,2,dish,0,"sat_rf_tx");wire(odu,3,dish,1,"sat_rf_rx");wire(modem,2,terminal,0,"coax_tx");wire(modem,3,terminal,1,"coax_rx");int audio=pc.device.ports.size()-1;wire(pc,audio,speaker,0,"audio");
            PowerSystem.demo(l,rack,base.offset(6,0,3),base.offset(5,0,3),List.of(Map.entry(rack,0),Map.entry(rack,1),Map.entry(odu,0),Map.entry(modem,0),Map.entry(speaker,0)));
            for(var host:List.of(rack,dish,odu,terminal,modem,pc,speaker)){MemoryCards.demo(host.device);if(host.upperDevice!=null)MemoryCards.demo(host.upperDevice);host.changed();SputnikChunks.register(l,host.getBlockPos());}SatelliteLinks.clear();give(p,new ItemStack(NetLab.GUIDE.get()));give(p,new ItemStack(NetLab.PORTABLE_TERMINAL.get()));give(p,new ItemStack(NetLab.GOGGLES.get()));String ip=provider+"2";
            c.getSource().sendSuccess(()->Component.literal("Стенд Sputnik построен: "+base.toShortString()+". Ноутбук → Desktop → Браузер. Шлюз: "+ip+" (можно оставить пустым). Колонка подключена, TX/RX и питание настроены. GEO-1: 180° / 45°. При OFFLINE проверь свободное небо к северу. Реальные сайты используют интернет этого Minecraft-сервера."),false);return 1;}))));}
    private static Device device(String kind){var d=new Device(kind);SputnikHardware.initialize(d);return d;}
    private static DeviceEntity place(ServerLevel l,BlockPos p,DeviceBlock b){l.setBlockAndUpdate(p,b.defaultBlockState().setValue(DeviceBlock.FACING,Direction.NORTH));return (DeviceEntity)l.getBlockEntity(p);}
    private static void wire(DeviceEntity a,int ap,DeviceEntity b,int bp,String type){int color=type.startsWith("coax")?0xe1e0c7:type.startsWith("sat_rf")?0xbaca64:type.equals("audio")?0x222222:0x237de6;a.links.put(ap,new DeviceEntity.Peer(b.getBlockPos().asLong(),bp,color,"",type));b.links.put(bp,new DeviceEntity.Peer(a.getBlockPos().asLong(),ap,color,"",type));a.connectionChanged(ap);b.connectionChanged(bp);}
    private static void give(ServerPlayer p,ItemStack s){if(!p.addItem(s))p.drop(s,false);}
}
