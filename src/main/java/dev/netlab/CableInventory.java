package dev.netlab;

import com.google.gson.Gson;
import dev.netlab.core.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** The server owns material accounting. An unloaded second endpoint can never refund twice. */
public final class CableInventory extends SavedData {
    private static final Gson JSON=new Gson();
    public CableLedger ledger=new CableLedger();
    private static CableInventory get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(CableInventory::new,(tag,regs)->{var data=new CableInventory();if(tag.contains("ledger"))data.ledger=JSON.fromJson(StoredJson.get(tag,"ledger"),CableLedger.class);return data;}),"netlab_cable_inventory");}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){StoredJson.put(tag,"ledger",JSON.toJson(ledger));return tag;}
    public static CableLedger.End end(DeviceEntity host,int port,boolean power){return new CableLedger.End(host.getBlockPos().asLong(),port,power);}
    private static String token(CableLedger.End local,CableLedger.End remote,String circuit){return circuit.isBlank()?CableLedger.legacy(local,remote):circuit;}
    public static void install(DeviceEntity a,int ap,DeviceEntity b,int bp,ItemStack stack,boolean creative){
        if(!(a.getLevel() instanceof ServerLevel level))throw new IllegalStateException("Cable placement requires a server level");var data=get(level);install(data.ledger,a,ap,b,bp,stack,creative);data.setDirty();
    }
    /** Shared with the standalone fixture; called only after connect reports a successful new link. */
    public static void install(CableLedger ledger,DeviceEntity a,int ap,DeviceEntity b,int bp,ItemStack stack,boolean creative){
        if(!(stack.getItem() instanceof RoutedCableItem)||stack.isEmpty())throw new IllegalArgumentException("Нужен кабель в руке");boolean power=stack.getItem() instanceof PowerCableItem;
        if(power){var x=a.powerLinks.get(ap);var y=b.powerLinks.get(bp);if(x==null||y==null||x.pos()!=b.getBlockPos().asLong()||x.port()!=bp||y.pos()!=a.getBlockPos().asLong()||y.port()!=ap||!x.circuit().isBlank()||!y.circuit().isBlank())throw new IllegalArgumentException("Соединение уже изменилось");
            String circuit=ledger.install(end(a,ap,true),end(b,bp,true),BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),!creative);
            a.powerLinks.put(ap,new PowerSystem.Peer(x.pos(),x.port(),x.localId(),x.remoteId(),x.surface(),x.anchors(),circuit));b.powerLinks.put(bp,new PowerSystem.Peer(y.pos(),y.port(),y.localId(),y.remoteId(),y.surface(),y.anchors(),circuit));
        }else{var x=a.links.get(ap);var y=b.links.get(bp);if(x==null||y==null||x.pos()!=b.getBlockPos().asLong()||x.port()!=bp||y.pos()!=a.getBlockPos().asLong()||y.port()!=ap||!x.circuit().isBlank()||!y.circuit().isBlank())throw new IllegalArgumentException("Соединение уже изменилось");
            String circuit=ledger.install(end(a,ap,false),end(b,bp,false),BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),!creative);var label=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();int color=label.contains("netlabCableColor")?label.getInt("netlabCableColor"):x.color();String name=label.getString("netlabCableName");if(name.isBlank()&&stack.has(DataComponents.CUSTOM_NAME))name=stack.get(DataComponents.CUSTOM_NAME).getString();
            a.links.put(ap,new DeviceEntity.Peer(x.pos(),x.port(),color,name,x.cable(),x.anchors(),circuit));b.links.put(bp,new DeviceEntity.Peer(y.pos(),y.port(),color,name,y.cable(),y.anchors(),circuit));
        }
        if(!creative)stack.shrink(1);a.changed();b.changed();
    }
    public static Item item(String cable){if(cable.equals("power"))return BuiltInRegistries.ITEM.get(ResourceLocation.parse("netlab:power_cable"));for(var item:BuiltInRegistries.ITEM)if(item instanceof CableItem wire&&wire.cableType.equals(cable))return item;return Items.AIR;}
    private static String legacyType(DeviceEntity h,int port,String stored){if(!stored.equals("auto"))return stored;var d=h.portDevice(port);if(d==null||port%128>=d.ports.size())return "copper";String medium=CableKind.medium(d,port%128);return switch(medium){case "console"->"console";case "fiber","fronthaul"->"fiber";case "serial"->"serial_dce";case "phone"->"phone";case "copper"->"copper";default->medium;};}
    public static ItemStack claim(CableLedger ledger,CableLedger.End local,CableLedger.End remote,String circuit,String type,int color,String name){
        String token=token(local,remote,circuit);if(circuit.isBlank()){var item=item(type);ledger.register(token,local,remote,BuiltInRegistries.ITEM.getKey(item).toString(),item!=Items.AIR);}
        String material=ledger.claim(token,local);ledger.acknowledge(token,local);if(material.isBlank())return ItemStack.EMPTY;var result=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(material)));
        if(!local.power()&&!result.isEmpty()&&(color!=CableKind.color(result.getItem() instanceof CableItem cable?cable.cableType:type)||!name.isBlank())){var label=new CompoundTag();label.putInt("netlabCableColor",color&0xffffff);label.putString("netlabCableName",DeviceEntity.cleanName(name));result.set(DataComponents.CUSTOM_DATA,CustomData.of(label));if(!name.isBlank())result.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal(DeviceEntity.cleanName(name)));}return result;
    }
    private static void deliver(DeviceEntity host,ItemStack item,Player player){if(item.isEmpty())return;if(player!=null){if(!player.addItem(item))player.drop(item,false);}else Block.popResource(host.getLevel(),host.getBlockPos(),item);}
    public static void refund(DeviceEntity host,int port,DeviceEntity.Peer peer,Player recipient){if(!(host.getLevel() instanceof ServerLevel level))return;var data=get(level);var item=claim(data.ledger,end(host,port,false),new CableLedger.End(peer.pos(),peer.port(),false),peer.circuit(),legacyType(host,port,peer.cable()),peer.color(),peer.name());data.setDirty();deliver(host,item,recipient);}
    public static void refund(DeviceEntity host,int port,PowerSystem.Peer peer,Player recipient){if(!(host.getLevel() instanceof ServerLevel level))return;var data=get(level);var item=claim(data.ledger,end(host,port,true),new CableLedger.End(peer.pos(),peer.port(),true),peer.circuit(),"power",0,"");data.setDirty();deliver(host,item,recipient);}
    private static void ack(DeviceEntity host,int port,long position,int remote,boolean power,String circuit){if(host.getLevel() instanceof ServerLevel level){var data=get(level);var a=end(host,port,power);var b=new CableLedger.End(position,remote,power);data.ledger.acknowledge(token(a,b,circuit),b);data.setDirty();}}
    public static void acknowledge(DeviceEntity host,int port,DeviceEntity.Peer peer){ack(host,port,peer.pos(),peer.port(),false,peer.circuit());}
    public static void acknowledge(DeviceEntity host,int port,PowerSystem.Peer peer){ack(host,port,peer.pos(),peer.port(),true,peer.circuit());}
    public static boolean active(DeviceEntity host,int port,long position,int remote,boolean power,String circuit){if(!(host.getLevel() instanceof ServerLevel level))return true;var data=get(level);var a=end(host,port,power);String key=token(a,new CableLedger.End(position,remote,power),circuit);return circuit.isBlank()&&!data.ledger.cables.containsKey(key)||data.ledger.active(key,a);}
    public static void loaded(DeviceEntity host){
        if(!(host.getLevel() instanceof ServerLevel))return;
        for(int port:new ArrayList<>(host.links.keySet())){var p=host.links.get(port);if(!active(host,port,p.pos(),p.port(),false,p.circuit())){host.links.remove(port);host.connectionChanged(port);ackLocal(host,port,p.pos(),p.port(),false,p.circuit());host.changed();}}
        for(int port:new ArrayList<>(host.powerLinks.keySet())){var p=host.powerLinks.get(port);if(!active(host,port,p.pos(),p.port(),true,p.circuit())){host.powerLinks.remove(port);ackLocal(host,port,p.pos(),p.port(),true,p.circuit());host.changed();}}
    }
    private static void ackLocal(DeviceEntity host,int port,long position,int remote,boolean power,String circuit){var data=get((ServerLevel)host.getLevel());var a=end(host,port,power);data.ledger.acknowledge(token(a,new CableLedger.End(position,remote,power),circuit),a);data.setDirty();}
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20!=0)return;for(var level:event.getServer().getAllLevels()){
            var data=get(level);for(var record:new ArrayList<>(data.ledger.cables.entrySet()))if(record.getValue().closed)for(var end:new ArrayList<>(record.getValue().pending)){
                var pos=BlockPos.of(end.position());if(!level.hasChunkAt(pos))continue;var entity=level.getBlockEntity(pos);if(entity instanceof DeviceEntity host)loaded(host);
                data.ledger.acknowledge(record.getKey(),end);data.setDirty();
            }
        }
    }
}
