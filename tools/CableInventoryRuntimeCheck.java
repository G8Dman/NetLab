import dev.netlab.*;
import dev.netlab.core.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.*;

/** Material transactions and actual saved peer NBT; never creates a level, player or game process. */
public final class CableInventoryRuntimeCheck {
    static int checks;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    static final class Host extends DeviceEntity {
        Host(BlockPos pos,DeviceBlock block){super(BlockEntityType.Builder.<DeviceEntity>of((p,s)->null,block).build(null),pos,block.defaultBlockState());}
    }
    static void pair(Host a,int ap,Host b,int bp,String type){a.links.put(ap,new DeviceEntity.Peer(b.getBlockPos().asLong(),bp,CableKind.color(type),"",type));b.links.put(bp,new DeviceEntity.Peer(a.getBlockPos().asLong(),ap,CableKind.color(type),"",type));}
    public static void main(String[] args)throws Exception{
        SputnikRuntimeCheck.main(args);var ledger=new CableLedger();var block=new DeviceBlock(BlockBehaviour.Properties.of(),"router",false);var a=new Host(new BlockPos(0,64,0),block);var b=new Host(new BlockPos(100,64,0),block);
        for(var entry:BuiltInRegistries.ITEM.entrySet())if(entry.getValue() instanceof RoutedCableItem&&!entry.getKey().location().getPath().equals("wall_power_cable")){
            var item=entry.getValue();check(item.getDefaultInstance().getMaxStackSize()==64,"usable cable stacks: "+entry.getKey().location());
            var stack=new ItemStack(item,8);boolean power=item instanceof PowerCableItem;int ap=power?300:0,bp=power?512:1;
            if(power){a.powerLinks.put(ap,new PowerSystem.Peer(b.getBlockPos().asLong(),bp,"a","b",true,List.of()));b.powerLinks.put(bp,new PowerSystem.Peer(a.getBlockPos().asLong(),ap,"b","a",true,List.of()));}
            else pair(a,ap,b,bp,((CableItem)item).cableType);
            CableInventory.install(ledger,a,ap,b,bp,stack,false);check(stack.getCount()==7,"successful connection consumes exactly one "+entry.getKey().location());
            String circuit=power?a.powerLinks.get(ap).circuit():a.links.get(ap).circuit();check(!circuit.isEmpty()&&ledger.active(circuit,CableInventory.end(b,bp,power)),"both physical endpoints share one material record");
            var local=CableInventory.end(a,ap,power);var remote=CableInventory.end(b,bp,power);
            ItemStack returned=CableInventory.claim(ledger,remote,local,circuit,power?"power":((CableItem)item).cableType,power?0:CableKind.color(((CableItem)item).cableType),"");
            check(returned.is(item)&&returned.getCount()==1,"disconnecting the far end refunds the original cable item");
            check(!returned.has(DataComponents.CUSTOM_DATA),"unpainted returned cable stacks with crafted stock");
            check(CableInventory.claim(ledger,local,remote,circuit,power?"power":((CableItem)item).cableType,0,"").isEmpty(),"a stale second endpoint cannot refund again");
            check(!ledger.cables.containsKey(circuit),"both removed ends release the claim entry");
            a.links.clear();b.links.clear();a.powerLinks.clear();b.powerLinks.clear();
        }
        var copper=CableInventory.item("copper");var stack=new ItemStack(copper,4);pair(a,0,b,1,"copper");CableInventory.install(ledger,a,0,b,1,stack,true);String creative=a.links.get(0).circuit();check(stack.getCount()==4&&CableInventory.claim(ledger,CableInventory.end(a,0,false),CableInventory.end(b,1,false),creative,"copper",0,"").isEmpty(),"creative cable neither spends nor generates survival material");
        try{CableInventory.install(ledger,a,0,b,1,stack,false);throw new AssertionError("occupied connection cannot settle twice");}catch(IllegalArgumentException expected){check(stack.getCount()==4,"repeated connection cannot charge for an occupied port");}
        a.links.clear();b.links.clear();pair(a,0,b,1,"serial_dte");var serial=new ItemStack(CableInventory.item("serial_dce"));CableInventory.install(ledger,a,0,b,1,serial,false);var serialPeer=b.links.get(1);var serialRefund=CableInventory.claim(ledger,CableInventory.end(b,1,false),CableInventory.end(a,0,false),serialPeer.circuit(),"serial_dte",CableKind.color("serial_dte"),"");check(serialRefund.is(CableInventory.item("serial_dce")),"canonical reversal of Serial DCE/DTE cannot change the refunded physical item");
        a.links.clear();b.links.clear();var before=stack.getCount();try{CableInventory.install(ledger,a,0,b,1,stack,false);throw new AssertionError("empty ports must reject settlement");}catch(IllegalArgumentException expected){check(stack.getCount()==before,"failed connection costs no item");}
        pair(a,0,b,1,"copper");CableInventory.install(ledger,a,0,b,1,stack,false);var p=a.links.get(0);var q=b.links.get(1);
        var painted=new DeviceEntity.Peer(p.pos(),p.port(),0xff33aa,"Backbone",p.cable(),List.of(new WallCablePath.Point(1,65,2)),p.circuit());
        var saved=DeviceEntity.saveCablePeer(0,painted);var restored=DeviceEntity.loadCablePeer(saved);check(restored.circuit().equals(p.circuit())&&restored.name().equals("Backbone")&&restored.anchors().size()==1,"connection identity, label and wall route survive real NBT");
        var serialized=new com.google.gson.Gson().toJson(ledger);ledger=new com.google.gson.Gson().fromJson(serialized,CableLedger.class);var material=CableInventory.claim(ledger,CableInventory.end(a,0,false),CableInventory.end(b,1,false),p.circuit(),"copper",painted.color(),painted.name());
        check(material.get(DataComponents.CUSTOM_DATA).copyTag().getInt("netlabCableColor")==0xff33aa&&material.get(DataComponents.CUSTOM_NAME).getString().equals("Backbone"),"refund keeps the cable's paint and label");
        ledger=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(ledger),CableLedger.class);check(CableInventory.claim(ledger,CableInventory.end(b,1,false),CableInventory.end(a,0,false),q.circuit(),"copper",q.color(),q.name()).isEmpty(),"saving/reloading a closed ledger cannot duplicate an unloaded cable");
        a.links.clear();b.links.clear();pair(a,0,b,1,"copper");CableInventory.install(ledger,a,0,b,1,material,false);check(material.isEmpty()&&a.links.get(0).name().equals("Backbone")&&a.links.get(0).color()==0xff33aa,"reinstalling returned wire consumes it and restores paint and label");
        var power=new PowerSystem.Peer(b.getBlockPos().asLong(),512,"left","right",true,List.of(new WallCablePath.Point(2,64,2)),UUID.randomUUID().toString());var powerCopy=PowerSystem.loadPeer(PowerSystem.savePeer(300,power));check(powerCopy.circuit().equals(power.circuit())&&powerCopy.anchors().equals(power.anchors()),"power cable material identity survives NBT with its route");
        System.out.println(checks+" cable material, stack, refund, saved-ledger and metadata checks passed; no game launched.");
    }
}
