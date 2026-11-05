package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.world.chunk.*;
import java.util.*;

/** At most sixteen infrastructure anchors per dimension, never the radio coverage area. */
public final class MobileChunks extends SavedData {
    private static final UUID OWNER=UUID.fromString("d81ee58e-c92c-4662-901b-415dedc7a18f");
    public static final TicketController TICKETS=new TicketController(ResourceLocation.fromNamespaceAndPath("netlabmobile","infrastructure"),(level,helper)->{for(var id:new ArrayList<>(helper.getEntityTickets().keySet()))helper.removeAllTickets(id);});
    private final Set<Long> positions=new LinkedHashSet<>(),forced=new HashSet<>();
    public static MobileChunks get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(MobileChunks::new,MobileChunks::load),"netlab_mobile_sites");}
    private static MobileChunks load(CompoundTag tag,HolderLookup.Provider regs){var out=new MobileChunks();for(long position:tag.getLongArray("sites"))out.positions.add(position);return out;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider regs){tag.putLongArray("sites",positions.stream().mapToLong(Long::longValue).toArray());return tag;}
    public static void register(ServerLevel level,BlockPos pos){if(level.getBlockEntity(pos) instanceof DeviceEntity host&&anchor(host)&&get(level).positions.add(pos.asLong()))get(level).setDirty();}
    public static void remove(ServerLevel level,BlockPos pos){if(get(level).positions.remove(pos.asLong()))get(level).setDirty();}
    private static boolean anchor(DeviceEntity host){for(int bay=0;bay<2;bay++){var d=bay==0?host.device:host.upperDevice;if(d!=null&&Set.of(MobileHardware.BBU,MobileHardware.CORE,MobileHardware.RRU,MobileHardware.STATION).contains(d.kind)&&MobileState.get(d).keepLoaded)return true;}return false;}
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(event.getServer().getTickCount()%100!=0)return;for(var level:event.getServer().getAllLevels()){
        var data=get(level);var wanted=new HashSet<Long>();int used=0;
        for(long position:new ArrayList<>(data.positions)){var p=BlockPos.of(position);if(level.hasChunkAt(p)&&(!(level.getBlockEntity(p) instanceof DeviceEntity host)||!anchor(host))){data.positions.remove(position);data.setDirty();continue;}if(used++>=16)continue;var c=new ChunkPos(p);for(int x=c.x-1;x<=c.x+1;x++)for(int z=c.z-1;z<=c.z+1;z++)wanted.add(ChunkPos.asLong(x,z));}
        for(long chunk:new HashSet<>(data.forced))if(!wanted.contains(chunk))TICKETS.forceChunk(level,OWNER,ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false,true);
        for(long chunk:wanted)if(!data.forced.contains(chunk))TICKETS.forceChunk(level,OWNER,ChunkPos.getX(chunk),ChunkPos.getZ(chunk),true,true);
        data.forced.clear();data.forced.addAll(wanted);
    }}
}
