import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.automation.*;
import dev.netlab.automation.core.Claim;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.*;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.*;

/** Uses real FluidTank/FluidStack/NBT and simulation clocks, never a client, server, Level or GameTest. */
public class FluidRuntimeCheck {
    static int checks;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    static void fails(Runnable action,String name){boolean rejected=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException e){rejected=true;}check(rejected,name);}
    public static void main(String[] args)throws Exception{
        MobileRuntimeCheck.main(args);
        // Standalone bootstrap needs only NeoForge's three vanilla fluid type registry entries.
        if(!BuiltInRegistries.REGISTRY.containsKey(NeoForgeRegistries.Keys.FLUID_TYPES.location())){((MappedRegistry<?>)BuiltInRegistries.REGISTRY).unfreeze();Registry.register((Registry)BuiltInRegistries.REGISTRY,NeoForgeRegistries.Keys.FLUID_TYPES.location(),NeoForgeRegistries.FLUID_TYPES);}
        for(String fluid:List.of("empty","water","lava"))if(!NeoForgeRegistries.FLUID_TYPES.containsKey(ResourceLocation.withDefaultNamespace(fluid)))Registry.register(NeoForgeRegistries.FLUID_TYPES,ResourceLocation.withDefaultNamespace(fluid),new FluidType(FluidType.Properties.create().descriptionId("block.minecraft."+fluid)));
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var source=new FluidTank(4000);source.fill(new FluidStack(Fluids.WATER,2000),IFluidHandler.FluidAction.EXECUTE);
        var snapshot=MachineFluids.snapshot(source,true);check(snapshot.size()==1&&snapshot.get(0).getAsJsonObject().get("amount").getAsInt()==2000&&snapshot.get(0).getAsJsonObject().get("drainable").getAsInt()==2000,"real tank snapshot reports volume and drain permission");check(source.getFluidAmount()==2000,"snapshot and simulated drain never consume fluid");
        check(MachineFluids.snapshot(source,false).get(0).getAsJsonObject().get("drainable").getAsInt()==0,"extract permission suppresses drain preview");
        fails(()->MachineFluids.preview(source,"minecraft:water",0,null),"zero volume rejected");fails(()->MachineFluids.preview(source,"minecraft:water",16001,null),"oversized volume rejected");check(MachineFluids.preview(source,"minecraft:lava",1000,null).isEmpty(),"wrong requested fluid cannot be drained");
        var water=MachineFluids.preview(source,"minecraft:water",1000,null);check(water.getAmount()==1000&&source.getFluidAmount()==2000,"preview reserves no real quantity");
        var job=job();job.liquid=true;job.fluid=MachineFluids.drain(source,water);job.claim=new Claim(job.amount());check(source.getFluidAmount()==1000&&job.hasCargo()&&job.amount()==1000,"real drain transfers exact quantity into escrow");
        var destination=new FluidTank(600);check(job.claim.begin()&&!job.claim.begin(),"fluid ticket has one exclusive redemption lock");job.fluid=MachineFluids.fill(destination,job.fluid);job.claim.finish(600);check(destination.getFluidAmount()==600&&job.amount()==400&&job.claim.remaining()==400,"partial fill preserves remainder in escrow");
        var full=MachineFluids.fill(destination,job.fluid);check(full.getAmount()==400&&destination.getFluidAmount()==600,"full receiver consumes no escrow");
        var lava=new FluidTank(1000);lava.fill(new FluidStack(Fluids.LAVA,300),IFluidHandler.FluidAction.EXECUTE);check(MachineFluids.fill(lava,job.fluid).getAmount()==400&&lava.getFluidAmount()==300,"water cannot mix into a lava tank");
        var interceptor=new FluidTank(1000);check(job.claim.begin(),"remaining fluid ticket can be resumed");job.fluid=MachineFluids.fill(interceptor,job.fluid);job.claim.finish(400);check(!job.hasCargo()&&!job.claim.begin()&&interceptor.getFluidAmount()==400,"redeemed fluid ticket cannot pay out again");check(source.getFluidAmount()+destination.getFluidAmount()+interceptor.getFluidAmount()==2000,"source + receivers + escrow conserve total volume");
        var flavored=new FluidStack(Fluids.WATER,1500);flavored.set(DataComponents.CUSTOM_NAME,Component.literal("Солёная вода"));source.setFluid(flavored);var typed=MachineFluids.preview(source,"minecraft:water",1000,flavored.getComponents().hashCode());check(typed.getAmount()==1000&&FluidStack.isSameFluidSameComponents(typed,flavored),"fluid components survive type-sensitive preview");
        check(MachineFluids.preview(source,"minecraft:water",1000,flavored.getComponents().hashCode()+1).isEmpty(),"selected fluid variant cannot silently change");var plain=new FluidTank(3000);plain.fill(new FluidStack(Fluids.WATER,100),IFluidHandler.FluidAction.EXECUTE);check(MachineFluids.fill(plain,typed).getAmount()==1000&&plain.getFluidAmount()==100,"different components are not mixed");
        var journal=new TransferJournal();var pending=job();pending.liquid=true;pending.fluid=typed.copy();pending.claim=new Claim(1000);journal.add(pending);var legacy=job();legacy.stack=new ItemStack(Items.COAL,8);legacy.claim=new Claim(8);journal.add(legacy);check(journal.audit.valid()&&journal.jobs.size()==2,"item and fluid jobs coexist in hash-chain journal");
        var saved=journal.save(new CompoundTag(),registries);var load=TransferJournal.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class);load.setAccessible(true);var restored=(TransferJournal)load.invoke(null,saved,registries);var resumed=restored.jobs.get(pending.ticket);check(resumed.liquid&&resumed.amount()==1000&&resumed.claim.remaining()==1000&&FluidStack.isSameFluidSameComponents(resumed.fluid,typed)&&restored.audit.valid(),"fluid escrow and components survive native journal NBT reload");check(restored.jobs.get(legacy.ticket).stack.is(Items.COAL)&&restored.jobs.get(legacy.ticket).amount()==8&&!restored.jobs.get(legacy.ticket).liquid,"item escrow remains compatible");
        for(var tag:saved.getList("jobs",Tag.TAG_COMPOUND))if(((CompoundTag)tag).getUUID("ticket").equals(legacy.ticket)){((CompoundTag)tag).remove("liquid");((CompoundTag)tag).remove("fluid");}restored=(TransferJournal)load.invoke(null,saved,registries);check(restored.jobs.get(legacy.ticket).amount()==8,"pre-fluid journal rows without new fields still load");
        var badFill=new FluidTank(1000){@Override public int fill(FluidStack stack,IFluidHandler.FluidAction action){return stack.getAmount()+1;}};fails(()->MachineFluids.fill(badFill,typed),"over-accepting fluid adapter rejected");
        var badDrain=new FluidTank(1000){@Override public FluidStack drain(FluidStack request,IFluidHandler.FluidAction action){return new FluidStack(Fluids.LAVA,request.getAmount());}};fails(()->MachineFluids.drain(badDrain,typed),"adapter returning a different fluid rejected");
        network(source,registries);
        System.out.println(checks+" fluid capability/escrow/network checks passed; no client, server or world launched.");
    }
    static TransferJournal.Job job(){var j=new TransferJournal.Job();j.sourceId=UUID.randomUUID();j.destinationId=UUID.randomUUID();j.controllerId=UUID.randomUUID();j.owner=UUID.randomUUID();return j;}
    static void network(FluidTank source,RegistryAccess registries){
        var n=new Network();var a=new Device("endpoint");var sw=new Device("switch");var b=new Device("endpoint");a.ports.get(0).ip="10.5.0.1";b.ports.get(0).ip="10.5.0.2";n.devices.put(1L,a);n.devices.put(2L,sw);n.devices.put(3L,b);n.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(2,0),8));n.wires.add(new Network.Wire(new Network.Endpoint(2,1),new Network.Endpoint(3,0),8));
        n.speedPolicy=(x,y,v,p)->p.endsWith("TCP")?.0001:1;byte[] data="{\"op\":\"FLUID_DATA\",\"fluid\":\"minecraft:water\",\"amount\":1000}".getBytes(java.nio.charset.StandardCharsets.UTF_8);var packet=n.transmit(1,"10.5.0.2","tcp",5420,data);check(packet.result().success()&&packet.destination()==3&&Arrays.equals(packet.payload(),data),"fluid manifest follows physical source-switch-destination path");
        source.setFluid(new FluidStack(Fluids.WATER,1000));var escrow=MachineFluids.drain(source,MachineFluids.preview(source,"minecraft:water",1000,null));var receiver=new FluidTank(2000);var queue=new TimedQueue();var complete=queue.submit(0,n.cursor,()->{check(MachineFluids.fill(receiver,escrow).isEmpty(),"arrived fluid fits actual receiver");return true;});queue.advance(n.cursor-1,10);check(!complete.isDone()&&receiver.isEmpty()&&source.isEmpty(),"fluid remains in escrow until the slow packet arrives");queue.advance(n.cursor,10);check(complete.join()&&receiver.getFluidAmount()==1000,"fluid is inserted at actual simulated arrival time");
        var trace=new TrafficMark("FLUID_DATA","minecraft:water",16000);var e=new Study.Event(1,0,3,0,"TCP",1,false,0,n.cursor,256,trace.phase(),trace.item(),trace.count());String json=new com.google.gson.Gson().toJson(new Study.Batch(0,Collections.nCopies(256,e)));var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),registries);Packets.Trace.CODEC.encode(buf,new Packets.Trace(json));check(Packets.Trace.CODEC.decode(buf).json().equals(json),"fluid mB trace hints fit real trace payload codec");buf.release();check(trace.hop("UDP",true).count()==0&&trace.hop("UDP",true).item().isEmpty(),"router VPN hides fluid trace type and amount");
    }
}
