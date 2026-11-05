package dev.netlab.automation;

import dev.netlab.DeviceEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class AutomationEntity extends DeviceEntity {
    public UUID identity=UUID.randomUUID(),owner;
    public Direction target=Direction.DOWN;
    public String manager="";
    public boolean extract=true,insert=true,control=false,requireVpn=false,running=false;
    public int signal;
    public final List<Rule> rules=new ArrayList<>();
    public final Deque<String> captures=new ArrayDeque<>();
    public String last="Готово",remoteSnapshot="",capturedTicket="";
    public record Rule(String source,String destination,String item,int minimum,int batch){}
    public AutomationEntity(BlockPos p,BlockState s){super(Automation.ENTITY.get(),p,s);device.hostname=s.getBlock()==Automation.INTERFACE.get()?"Multiinterface":s.getBlock()==Automation.SERVER.get()?"ManagementServer":"PacketAnalyzer";}
    public String ip(){return device.ports.getFirst().ip;}
    public boolean owns(UUID player){return owner==null||owner.equals(player);}
    public boolean managedBy(AutomationEntity server){return device.kind.equals("endpoint")&&!manager.isBlank()&&manager.equals(server.ip())&&owner!=null&&owner.equals(server.owner);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider regs){
        var app=AutomationNode.of(this,0);if(app!=null)app.persist();
        super.saveAdditional(tag,regs);tag.putUUID("automationId",identity);if(owner!=null)tag.putUUID("automationOwner",owner);
        tag.putString("target",target.getName());tag.putString("manager",manager);tag.putBoolean("extract",extract);tag.putBoolean("insert",insert);tag.putBoolean("control",control);tag.putBoolean("requireVpn",requireVpn);tag.putBoolean("running",running);tag.putInt("signal",signal);
        var list=new ListTag();for(var r:rules){var t=new CompoundTag();t.putString("source",r.source);t.putString("destination",r.destination);t.putString("item",r.item);t.putInt("minimum",r.minimum);t.putInt("batch",r.batch);list.add(t);}tag.put("rules",list);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider regs){
        super.loadAdditional(tag,regs);if(tag.hasUUID("automationId"))identity=tag.getUUID("automationId");owner=tag.hasUUID("automationOwner")?tag.getUUID("automationOwner"):null;
        Direction d=Direction.byName(tag.getString("target"));target=d==null?Direction.DOWN:d;manager=tag.getString("manager");
        extract=!tag.contains("extract")||tag.getBoolean("extract");insert=!tag.contains("insert")||tag.getBoolean("insert");control=tag.getBoolean("control");requireVpn=tag.getBoolean("requireVpn");running=tag.getBoolean("running");signal=Math.clamp(tag.getInt("signal"),0,15);
        rules.clear();var list=tag.getList("rules",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(16,list.size());i++){var r=list.getCompound(i);rules.add(new Rule(r.getString("source"),r.getString("destination"),r.getString("item"),Math.clamp(r.getInt("minimum"),1,4096),Math.clamp(r.getInt("batch"),1,256)));}
    }
}
