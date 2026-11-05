package dev.netlab.api;

import dev.netlab.core.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.List;

/** Server-only configuration, never exported in block entity update tags. */
public final class VpnStore extends SavedData {
    private final VpnPeers peers=new VpnPeers();
    public static VpnStore get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(VpnStore::new,(tag,regs)->{
        var data=new VpnStore();for(var key:tag.getAllKeys())data.peers.restore(key,tag.getString(key));return data;
    }),"netlab_vpn");}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider regs){peers.savedKeys().forEach(tag::putString);return tag;}
    public String configure(Network n,long id,String line){String result=peers.configure(id,line);if(!line.startsWith("show ")&&!result.startsWith("%")){setDirty();var d=n.devices.get(id);if(d!=null)d.extensions.put("netlab.vpn",new com.google.gson.Gson().toJson(peers.export(id)));}return result;}
    public void restore(long id,Device d){var values=new java.util.LinkedHashMap<String,String>();if(d.extensions.containsKey("netlab.vpn")){var obj=com.google.gson.JsonParser.parseString(d.extensions.get("netlab.vpn")).getAsJsonObject();obj.entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));}peers.replace(id,values);setDirty();}
    public void migrate(long id,Device d){if(!d.extensions.containsKey("netlab.vpn")&&!peers.export(id).isEmpty())d.extensions.put("netlab.vpn",new com.google.gson.Gson().toJson(peers.export(id)));}
    public boolean hasPolicy(Network n,List<Long> path){return peers.hasPolicy(n,path);}
    public VpnPeers.Tunnel tunnel(Network n,List<Long> path){return peers.tunnel(n,path);}
}
