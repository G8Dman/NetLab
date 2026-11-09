package dev.netlab.media;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import net.minecraft.server.level.*;
import java.util.*;
public record MediaRef(DeviceEntity host,int bay,Device device){
    public long id(){return host.slotId(bay);}public ServerLevel level(){return (ServerLevel)host.getLevel();}public MediaState state(){return MediaState.get(device);}public String ip(){if(device.ports.isEmpty())return "";return device.ports.get(device.kind.equals("pc")?Wireless.clientPort(device):0).ip;}
    public void changed(MediaState state){String previous=device.extensions.get(MediaState.KEY),card=device.extensions.get("netlab.memory");try{String json=MediaState.JSON.toJson(state);if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>64000)throw new IllegalArgumentException("Медиаконфигурация превышает 64 KiB; сократи плейлисты");state.save(device);if(host instanceof PortableHosts.Host portable)PortableHosts.save(portable);else{MemoryCards.sync(device);host.changed();}}catch(RuntimeException error){if(previous==null)device.extensions.remove(MediaState.KEY);else device.extensions.put(MediaState.KEY,previous);if(card!=null)device.extensions.put("netlab.memory",card);throw error;}}
    public boolean live(){if(host instanceof PortableHosts.Host portable)return bay==0&&portable.online();return !host.isRemoved()&&host.portDevice(bay*128)==device&&host.getLevel() instanceof ServerLevel&&level().hasChunkAt(host.getBlockPos())&&level().getBlockEntity(host.getBlockPos())==host&&Hardware.powered(device);}
    public ApplicationTransport.Endpoint endpoint(){return new ApplicationTransport.Endpoint(host,bay);}
    public static MediaRef of(DeviceEntity host,int bay){var d=bay==0?host.device:host.upperDevice;return d==null?null:new MediaRef(host,bay,d);}
    public static List<MediaRef> loaded(ServerLevel level){var out=new ArrayList<MediaRef>();for(var host:DeviceEntity.loaded(level))for(int bay=0;bay<2;bay++){var r=of(host,bay);if(r!=null)out.add(r);}return out;}
    public static MediaRef address(ServerLevel level,String ip,String kind){Device.ip(ip);var matches=loaded(level).stream().filter(r->r.device.kind.equals(kind)&&r.ip().equals(ip)&&r.live()&&r.state().enabled).toList();if(matches.size()!=1)throw new IllegalArgumentException("Нужен один включённый "+kind+" с IP "+ip);return matches.getFirst();}
}
