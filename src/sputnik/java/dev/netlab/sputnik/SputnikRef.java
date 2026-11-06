package dev.netlab.sputnik;
import dev.netlab.*;
import dev.netlab.core.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
public record SputnikRef(DeviceEntity host,int bay,Device device){
    public long id(){return host.slotId(bay);}public ServerLevel level(){return (ServerLevel)host.getLevel();}public SputnikState state(){return SputnikState.get(device);}public String ip(){return device.ports.isEmpty()?"":device.ports.getFirst().ip;}
    public void changed(SputnikState s){s.save(device);host.changed();}
    public boolean live(){if(host instanceof dev.netlab.api.PortableHosts.Host portable)return bay==0&&portable.online();return !host.isRemoved()&&host.portDevice(bay*128)==device&&host.getLevel()!=null&&level().hasChunkAt(host.getBlockPos())&&level().getBlockEntity(host.getBlockPos())==host&&Hardware.powered(device);}
    public static SputnikRef of(DeviceEntity host,int bay){var d=bay==0?host.device:host.upperDevice;return d==null?null:new SputnikRef(host,bay,d);}
    public static List<SputnikRef> loaded(ServerLevel l){var result=new ArrayList<SputnikRef>();for(var h:DeviceEntity.loaded(l))for(int b=0;b<2;b++){var r=of(h,b);if(r!=null)result.add(r);}return result;}
}
