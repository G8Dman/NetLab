import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.automation.*;
import dev.netlab.automation.core.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import java.util.*;

/** Real item/NBT/recipe fixture; never instantiates a client, server, Level or GameTest. */
public class ServerThermalRuntimeCheck {
    static int checks;
    static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);checks++;}
    static Device server(){var d=new Device("server");var h=Hardware.ensure(d);h.profile="Automation Server";h.powered=false;Hardware.install(d,"accel0",ServerHardware.ACCEL);Hardware.install(d,"buffer0",ServerHardware.BUFFER);Hardware.install(d,"cooling0",ServerHardware.PRO);h.powered=true;MemoryCards.fresh(d);return d;}
    static void heat(Device d,double value){var h=new ServerHeat();h.temperature=value;h.normalize();d.extensions.put(ServerThermal.KEY,new com.google.gson.Gson().toJson(h));}
    public static void main(String[] args)throws Exception {
        FluidRuntimeCheck.main(args);Automation.integrateThermal();var regs=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var cold=server();cold.ports.get(0).ip="10.20.0.2";MemoryCards.demo(cold);String image=cold.extensions.get("netlab.memory");
        var hot=server();heat(hot,95);hot.extensions.put("netlab.memory",image);MemoryCards.read(hot);
        check(ServerThermal.read(hot).temperature==95&&!ServerThermal.ready(hot),"reading a cold card cannot cool or restart a protected server");
        check(hot.ports.get(0).ip.equals("10.20.0.2")&&ServerHardware.itemLimit(hot)==256,"card still applies legitimate settings and keeps physical upgrades");
        var newServer=server();heat(cold,96);MemoryCards.sync(cold);newServer.extensions.put("netlab.memory",cold.extensions.get("netlab.memory"));MemoryCards.read(newServer);check(ServerThermal.read(newServer).temperature==25,"cloning a hot configuration card does not clone physical temperature");
        var cardless=server();heat(cardless,91);cardless.ports.get(0).ip="10.20.0.99";var blank=MemoryCards.saved(cardless);check(ServerThermal.read(blank).temperature==91&&!ServerThermal.ready(blank)&&blank.ports.get(0).ip.isEmpty(),"cardless restart retains heat and hardware while clearing volatile IP");check(ServerHardware.itemLimit(blank)==256&&ServerHardware.cooling(blank)==2,"cardless restart cannot erase paid modules");
        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(Automation.ID,"management_server"));var item=MemoryCards.deviceItem(block,cardless);check(!item.isEmpty(),"server has a real configured block item");var encoded=item.save(regs);var restored=RackDevices.restore(ItemStack.parse(regs,encoded).orElseThrow(),"server");check(ServerThermal.read(restored).temperature==91&&ServerHardware.itemLimit(restored)==256&&ServerHardware.cooling(restored)==2,"broken server's native ItemStack/NBT restores all modules and physical heat");
        var host=new FabricationRuntimeCheck.HeadlessDevice((DeviceBlock)block);host.device=server();var node=AutomationNode.of(host,0);heat(host.device,89);boolean stopped=false;try{ServerThermal.work(node,2);}catch(IllegalArgumentException expected){stopped=true;}
        check(stopped&&!ServerThermal.ready(host.device)&&ServerThermal.read(host.device).temperature>90,"production work guard stores heat before refusing a newly overheated operation");
        check(FrontPanel.extraStatus(host,0).contains("ПЕРЕГРЕВ")&&ServerThermal.snapshot(host.device).get("overheated").getAsBoolean(),"physical panel and management diagnostics expose the same protected state");
        stopped=false;try{ServerThermal.require(node);}catch(IllegalArgumentException expected){stopped=true;}check(stopped,"production operation guard refuses further work while protected");
        var mismatch=new Device("server");Hardware.ensure(mismatch).profile="Automation Server";mismatch.extensions.put("netlab.memory",image);boolean rejected=false;try{MemoryCards.read(mismatch);}catch(IllegalArgumentException expected){rejected=true;}check(rejected&&ServerHardware.itemLimit(mismatch)==64,"configuration card cannot create acceleration, memory or cooler hardware");
        for(String id:ServerHardware.MODULES){check(ModuleItem.find(id)==Automation.MODULE_ITEMS.get(id).get(),"removed module yields its actual addon item "+id);check(!ModuleFabrication.MODULES.contains(id),"expensive upgrade not present in cheap assembler "+id);}
        var journal=new TransferJournal();int left=256;while(left>0){int take=ServerHardware.part(left);var job=FluidRuntimeCheck.job();job.stack=new ItemStack(Items.COAL,take);job.claim=new Claim(take);journal.add(job);left-=take;}check(journal.jobs.size()==4&&journal.jobs.values().stream().allMatch(j->j.stack.getCount()<=j.stack.getMaxStackSize()),"256-item batch uses four legal native escrow stacks");
        var tag=journal.save(new CompoundTag(),regs);check(tag.getList("jobs",Tag.TAG_COMPOUND).size()==4&&journal.audit.valid(),"separate tickets survive native escrow serialization with valid audit chain");
        heat(hot,95);int before=journal.jobs.values().stream().mapToInt(TransferJournal.Job::amount).sum();check(!ServerThermal.ready(hot)&&before==256,"thermal protection does not destroy pending items");
        var load=TransferJournal.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class);load.setAccessible(true);var reloaded=(TransferJournal)load.invoke(null,tag,regs);check(reloaded.audit.valid()&&reloaded.jobs.values().stream().mapToInt(TransferJournal.Job::amount).sum()==256,"pending batch remains redeemable after journal reload");
        var typed=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(NetLab.ADVANCED_BOARD.get());check(typed.toString().equals("netlab:advanced_circuit_board"),"advanced board registered in main mod");
        System.out.println(checks+" server heat/module/card/escrow checks passed; no client, server or world launched.");
    }
}
