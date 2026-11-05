package dev.netlab;

import com.google.gson.Gson;
import dev.netlab.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DeviceEntity extends BlockEntity {
  private static final Gson GSON = new Gson();
  public Device device;
  public Device upperDevice;
  public int energy;public long energyReceivedTick=Long.MIN_VALUE;public int energyReceived;
  public boolean rackSupply,rackPowered;public String rackSupplyId="";
  public final Map<Integer,PowerSystem.Peer> powerLinks=new HashMap<>();
  private final Map<UUID,Integer> selectedSlots=new HashMap<>();
  private final Map<UUID,Console> upperSessions=new HashMap<>();
  public void selectSlot(UUID player,int slot){selectedSlots.put(player,((DeviceBlock)getBlockState().getBlock()).rack?slot:0);}
  public Device selectedDevice(UUID player){return selectedSlots.getOrDefault(player,0)==1?upperDevice:device;}
  public long selectedId(UUID player){return slotId(selectedSlots.getOrDefault(player,0));}
  public long slotId(int slot){return slot==0?worldPosition.asLong():worldPosition.below(1024).asLong();}
  public Device portDevice(int encoded){return encoded>=128?upperDevice:device;}
  public String portLabel(int encoded){Device d=portDevice(encoded);return d==null?"Empty":d.portName(encoded%128);}
  public static final int DEFAULT_CABLE_COLOR = 0x237de6;
  public record Peer(long pos, int port, int color, String name,String cable,List<WallCablePath.Point> anchors) {
    public Peer(long pos,int port,int color,String name,String cable){this(pos,port,color,name,cable,List.of());}
    public Peer(long pos, int port) {
      this(pos, port, DEFAULT_CABLE_COLOR, "","auto");
    }
    public Peer(long pos,int port,int color,String name){this(pos,port,color,name,"auto");}
    public Peer {
      color &= 0xffffff;
      name = cleanName(name);
      if(cable==null||cable.isBlank())cable="auto";
      anchors=List.copyOf(anchors);
    }
  }
  public static CompoundTag saveCablePeer(int port,Peer peer){
    var tag=new CompoundTag();tag.putInt("port",port);tag.putLong("target",peer.pos);tag.putInt("remote",peer.port);tag.putInt("color",peer.color);tag.putString("name",peer.name);tag.putString("cable",peer.cable);CableRouteNbt.write(tag,peer.anchors);return tag;
  }
  public static Peer loadCablePeer(CompoundTag tag){
    var anchors=CableRouteNbt.read(tag);return anchors==null?null:new Peer(tag.getLong("target"),tag.getInt("remote"),tag.contains("color",Tag.TAG_INT)?tag.getInt("color"):DEFAULT_CABLE_COLOR,tag.getString("name"),tag.getString("cable"),anchors);
  }
  public static String cleanName(String text) {
    String clean = text == null ? "" : text.replaceAll("[\\p{Cntrl}§]", "").strip();
    return clean.substring(0, Math.min(64, clean.length()));
  }
  private static final Map<net.minecraft.world.level.Level, Set<BlockPos>> LOADED = new WeakHashMap<>();
  @Override
  public void setLevel(net.minecraft.world.level.Level next) {
    synchronized (LOADED) {
      var old = LOADED.get(level);
      if (old != null)
        old.remove(worldPosition);
    }
    super.setLevel(next);
    synchronized (LOADED) {
      LOADED.computeIfAbsent(next, l -> new HashSet<>()).add(worldPosition);
    }
  }
  @Override
  public void onLoad() {
    super.onLoad();
    if(level instanceof ServerLevel server)for(int slot=0;slot<2;slot++){var d=slot==0?device:upperDevice;if(d!=null&&d.kind.equals("router")){var vpn=dev.netlab.api.VpnStore.get(server);if(MemoryCards.present(d)&&MemoryCards.card(d).active){vpn.migrate(slotId(slot),d);MemoryCards.sync(d);}vpn.restore(slotId(slot),d);}}
    PowerSystem.initialize(this);
    synchronized (LOADED) {
      LOADED.computeIfAbsent(level, l -> new HashSet<>()).add(worldPosition);
    }
  }
  @Override
  public void setRemoved() {
    synchronized (LOADED) {
      var set = LOADED.get(level);
      if (set != null)
        set.remove(worldPosition);
    }
    super.setRemoved();
  }
  /** Both logical sides have a separate level key. Call on the owning level thread. */
  public static List<DeviceEntity> loaded(net.minecraft.world.level.Level level) {
    List<BlockPos> positions;
    synchronized (LOADED) {
      positions = new ArrayList<>(LOADED.getOrDefault(level, Set.of()));
    }
    var result = new ArrayList<DeviceEntity>();
    for (var pos : positions)
      if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof DeviceEntity d && !d.isRemoved())
        result.add(d);
    return result;
  }
  /** Updates both copies atomically; unloaded or replaced peers cannot lose metadata. */
  public boolean decorate(int port, int color, String name) {
    Peer peer = links.get(port);
    if (peer == null || level == null || !level.hasChunkAt(BlockPos.of(peer.pos))
        || !(level.getBlockEntity(BlockPos.of(peer.pos)) instanceof DeviceEntity other))
      return false;
    Peer back = other.links.get(peer.port);
    if (back == null || back.pos != worldPosition.asLong() || back.port != port)
      return false;
    links.put(port, new Peer(peer.pos, peer.port, color, name,peer.cable,peer.anchors));
    other.links.put(peer.port, new Peer(worldPosition.asLong(), port, color, name,peer.cable,peer.anchors.reversed()));
    changed();
    other.changed();
    return true;
  }
  public final Map<Integer, Peer> links = new HashMap<>();
  private final Map<Integer,Long> portEpochs=new HashMap<>();
  public long portEpoch(int port){return portEpochs.getOrDefault(port,0L);}
  public void connectionChanged(int port){portEpochs.merge(port,1L,Long::sum);TerminalHistory.cut(this,port);ConsoleAccess.connectionChanged(this,port);}
  private final Map<UUID, Console> sessions = new HashMap<>();
  public DeviceEntity(BlockPos p, BlockState s) {
    this(NetLab.DEVICE.get(),p,s);
  }
  protected DeviceEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos p,BlockState s){
    super(type,p,s);
    device = new Device(((DeviceBlock) s.getBlock()).kind);
    if(device.kind.equals("pc"))Hardware.initializeHost(device,s.getBlock()==NetLab.LAPTOP.get());
    if(device.kind.equals("router")||device.kind.equals("switch")||device.kind.equals("traffic"))Hardware.ensure(device);
    PowerSystem.prepare(device);
    MemoryCards.fresh(device);
  }
  public Console session(UUID player) {
    if(selectedSlots.getOrDefault(player,0)==1)return upperSessions.computeIfAbsent(player,id->new Console());
    return sessions.computeIfAbsent(player, id -> new Console());
  }
  public void changed() {
    if(level!=null&&!level.isClientSide){MemoryCards.sync(device);if(upperDevice!=null)MemoryCards.sync(upperDevice);}
    setChanged();
    if (level != null)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
  }
  public String portSummary() {
    var s = new StringBuilder("Ports (front: "+getBlockState().getValue(DeviceBlock.FACING).getName()+"): ");
    for (int p = 0; p < device.ports.size(); p++)
      if(!device.ports.get(p).hidden&&!device.ports.get(p).virtual)s.append(device.portName(p)).append(links.containsKey(p) ? "[wired] " : "[free] ");
    if(upperDevice!=null){s.append("\nUpper slot: ");for(int p=0;p<upperDevice.ports.size();p++)s.append(upperDevice.portName(p)).append(links.containsKey(128+p)?"[wired] ":"[free] ");}
    return s.toString();
  }
  public void disconnect(int port) {
    var peer = links.remove(port);
    if(peer!=null)connectionChanged(port);
    if (peer != null && level != null && level.hasChunkAt(BlockPos.of(peer.pos))
        && level.getBlockEntity(BlockPos.of(peer.pos)) instanceof DeviceEntity other) {
      var reverse = other.links.get(peer.port);
      if (reverse != null && reverse.pos == worldPosition.asLong() && reverse.port == port) {
        {other.links.remove(peer.port);other.connectionChanged(peer.port);}
        other.changed();
      }
    }
    changed();
  }
  public void disconnectAll() {
    for (int port : new ArrayList<>(links.keySet())) disconnect(port);
  }
  public Network network() {
    return level==null?new Network():WirelessWorld.network(level);
  }
  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
    super.saveAdditional(tag, regs);
    StoredJson.put(tag,"device", GSON.toJson(MemoryCards.saved(device)));
    if(upperDevice!=null)StoredJson.put(tag,"upperDevice",GSON.toJson(MemoryCards.saved(upperDevice)));
    ListTag list = new ListTag();
    links.forEach((port, peer) -> list.add(saveCablePeer(port,peer)));
    tag.put("links", list);
    var epochs=new CompoundTag();portEpochs.forEach((port,epoch)->epochs.putLong("p"+port,epoch));tag.put("portEpochs",epochs);
    tag.putInt("energy",energy);tag.putBoolean("rackSupply",rackSupply);tag.putBoolean("rackPowered",rackPowered);tag.putString("rackSupplyId",rackSupplyId);
    var power=new ListTag();powerLinks.forEach((port,peer)->power.add(PowerSystem.savePeer(port,peer)));tag.put("powerLinks",power);
  }
  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
    super.loadAdditional(tag, regs);
    energy=Math.clamp(tag.getInt("energy"),0,PowerModel.CAPACITY);rackSupply=tag.getBoolean("rackSupply");rackPowered=tag.getBoolean("rackPowered");rackSupplyId=tag.getString("rackSupplyId");if(rackSupply&&rackSupplyId.isBlank())rackSupplyId=UUID.randomUUID().toString();powerLinks.clear();
    for(var entry:tag.getList("powerLinks",Tag.TAG_COMPOUND)){var t=(CompoundTag)entry;var peer=PowerSystem.loadPeer(t);if(peer!=null)powerLinks.put(t.getInt("port"),peer);}
    if (tag.contains("device"))
      device = GSON.fromJson(StoredJson.get(tag,"device"), Device.class);
    boolean missingIdentity=device.identity==null||device.identity.isBlank();boolean missingConsole=Set.of("server","endpoint","sniffer").contains(device.kind)&&device.ports.stream().noneMatch(port->port.console);device.normalize();if(missingIdentity||missingConsole)setChanged();
    if(device.kind.equals("pc")&&device.hardware==null)Hardware.ensure(device).profile=getBlockState().getBlock()==NetLab.LAPTOP.get()?"Laptop-PT":"PC-PT";
    if(device.kind.equals("pc")&&Hardware.migrateHostConsole(device))setChanged();
    if(device.kind.equals("router")||device.kind.equals("switch")||device.kind.equals("traffic"))Hardware.ensure(device);
    PowerSystem.prepare(device);
    upperDevice=tag.contains("upperDevice")?GSON.fromJson(StoredJson.get(tag,"upperDevice"),Device.class):null;
    if(upperDevice!=null)upperDevice.normalize();
    if(upperDevice!=null)Hardware.ensure(upperDevice);
    if(level==null||!level.isClientSide){MemoryCards.migrate(device);if(upperDevice!=null)MemoryCards.migrate(upperDevice);}
    links.clear();
    portEpochs.clear();var epochs=tag.getCompound("portEpochs");for(String key:epochs.getAllKeys())if(key.matches("p[0-9]{1,3}"))portEpochs.put(Integer.parseInt(key.substring(1)),epochs.getLong(key));
    for (var t : tag.getList("links", Tag.TAG_COMPOUND)) {
      var c = (CompoundTag) t;
      var peer=loadCablePeer(c);if(peer!=null)links.put(c.getInt("port"),peer);
    }
  }
  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider r) {
    var tag=saveWithoutMetadata(r);StoredJson.put(tag,"device",GSON.toJson(device));if(upperDevice!=null)StoredJson.put(tag,"upperDevice",GSON.toJson(upperDevice));return MemoryCards.publicTag(tag);
  }
  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
