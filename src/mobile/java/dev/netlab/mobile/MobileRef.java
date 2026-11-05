package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.core.Device;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

public record MobileRef(DeviceEntity host,int bay,Device device) {
    public static MobileRef of(DeviceEntity host,int bay){var d=bay==0?host.device:host.upperDevice;return d==null?null:new MobileRef(host,bay,d);}
    public long id(){return host.slotId(bay);}
    public ServerLevel level(){return (ServerLevel)host.getLevel();}
    public MobileState state(){return MobileState.get(device);}
    public void changed(MobileState state){state.save(device);host.changed();}
    public boolean live(){return !host.isRemoved()&&host.portDevice(bay*128)==device&&host.getLevel()!=null&&host.getLevel().hasChunkAt(host.getBlockPos())&&host.getLevel().getBlockEntity(host.getBlockPos())==host;}
    public String ip(){return device.ports.isEmpty()?"":device.ports.getFirst().ip;}
    public static List<MobileRef> loaded(ServerLevel level){var out=new ArrayList<MobileRef>();for(var h:DeviceEntity.loaded(level))for(int slot=0;slot<2;slot++){var ref=of(h,slot);if(ref!=null)out.add(ref);}return out;}
    public static MobileRef core(ServerLevel level,String ip){var matches=loaded(level).stream().filter(r->r.device.kind.equals(dev.netlab.mobile.core.MobileHardware.CORE)&&r.ip().equals(ip)).toList();if(matches.size()!=1)throw new IllegalArgumentException("Нужно одно доступное ядро EPC с IP "+ip);return matches.getFirst();}
}
