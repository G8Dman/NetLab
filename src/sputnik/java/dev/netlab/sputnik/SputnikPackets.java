package dev.netlab.sputnik;

import com.google.gson.*;
import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.sputnik.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

public final class SputnikPackets {
    public static java.util.function.BiPredicate<net.minecraft.server.level.ServerPlayer,Audio> EXTRA_AUDIO;
    public static final java.util.List<java.util.function.Consumer<Reply>> EXTRA_REPLIES=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static void reply(Reply q){dev.netlab.sputnik.client.BrowserSession.receive(q);for(var hook:EXTRA_REPLIES)hook.accept(q);}

    public record Action(BlockPos pos,int bay,String action,String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Sputnik.ID,"action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Action::pos,ByteBufCodecs.VAR_INT,Action::bay,ByteBufCodecs.stringUtf8(32),Action::action,ByteBufCodecs.stringUtf8(256),Action::value,Action::new);
        public Type<Action> type(){return TYPE;}
    }
    public record State(BlockPos pos,int bay,String json,boolean open) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Sputnik.ID,"state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,State::pos,ByteBufCodecs.VAR_INT,State::bay,ByteBufCodecs.stringUtf8(12000),State::json,ByteBufCodecs.BOOL,State::open,State::new);
        public Type<State> type(){return TYPE;}
    }
    public record Tunnel(BlockPos pc,int id,String op,String gateway,String host,int port,byte[] data) implements CustomPacketPayload {
        public static final Type<Tunnel> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Sputnik.ID,"tunnel"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Tunnel> CODEC=new StreamCodec<>(){public Tunnel decode(RegistryFriendlyByteBuf b){return new Tunnel(b.readBlockPos(),b.readVarInt(),b.readUtf(8),b.readUtf(64),b.readUtf(253),b.readVarInt(),b.readByteArray(SocksProxy.CHUNK));}public void encode(RegistryFriendlyByteBuf b,Tunnel q){b.writeBlockPos(q.pc);b.writeVarInt(q.id);b.writeUtf(q.op,8);b.writeUtf(q.gateway,64);b.writeUtf(q.host,253);b.writeVarInt(q.port);if(q.data.length>SocksProxy.CHUNK)throw new IllegalArgumentException("Tunnel chunk");b.writeByteArray(q.data);}};
        public Type<Tunnel> type(){return TYPE;}
    }
    public record Reply(int id,String op,String status,byte[] data) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Sputnik.ID,"reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=new StreamCodec<>(){public Reply decode(RegistryFriendlyByteBuf b){return new Reply(b.readVarInt(),b.readUtf(8),b.readUtf(512),b.readByteArray(SocksProxy.CHUNK));}public void encode(RegistryFriendlyByteBuf b,Reply q){b.writeVarInt(q.id);b.writeUtf(q.op,8);b.writeUtf(q.status,512);if(q.data.length>SocksProxy.CHUNK)throw new IllegalArgumentException("Reply chunk");b.writeByteArray(q.data);}};
        public Type<Reply> type(){return TYPE;}
    }
    public record Audio(BlockPos pc,int sequence,byte[] pcm) implements CustomPacketPayload {
        public static final Type<Audio> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Sputnik.ID,"audio"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Audio> CODEC=new StreamCodec<>(){public Audio decode(RegistryFriendlyByteBuf b){return new Audio(b.readBlockPos(),b.readVarInt(),b.readByteArray(12000));}public void encode(RegistryFriendlyByteBuf b,Audio a){b.writeBlockPos(a.pc);b.writeVarInt(a.sequence);if(a.pcm.length>12000)throw new IllegalArgumentException("Audio chunk");b.writeByteArray(a.pcm);}};
        public Type<Audio> type(){return TYPE;}
    }
    public record Sound(UUID source,BlockPos speaker,int sequence,float volume,byte[] pcm) implements CustomPacketPayload {
        public static final Type<Sound> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Sputnik.ID,"sound"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Sound> CODEC=new StreamCodec<>(){public Sound decode(RegistryFriendlyByteBuf b){return new Sound(b.readUUID(),b.readBlockPos(),b.readVarInt(),b.readFloat(),b.readByteArray(12000));}public void encode(RegistryFriendlyByteBuf b,Sound a){b.writeUUID(a.source);b.writeBlockPos(a.speaker);b.writeVarInt(a.sequence);b.writeFloat(a.volume);if(a.pcm.length>12000)throw new IllegalArgumentException("Audio chunk");b.writeByteArray(a.pcm);}};
        public Type<Sound> type(){return TYPE;}
    }
    private record Lease(BlockPos pos,String identity,long epoch,long expiry){}
    private static final Map<UUID,Lease> BROWSERS=new HashMap<>();
    private static final Map<UUID,long[]> AUDIO_BUDGET=new HashMap<>();
    public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("1");r.playToServer(Action.TYPE,Action.CODEC,(q,c)->handle((ServerPlayer)c.player(),q));r.playToClient(State.TYPE,State.CODEC,(q,c)->dev.netlab.sputnik.client.SputnikClient.state(q));r.playToServer(Tunnel.TYPE,Tunnel.CODEC,(q,c)->InternetBridge.receive((ServerPlayer)c.player(),q));r.playToClient(Reply.TYPE,Reply.CODEC,(q,c)->reply(q));r.playToServer(Audio.TYPE,Audio.CODEC,(q,c)->audio((ServerPlayer)c.player(),q));r.playToClient(Sound.TYPE,Sound.CODEC,(q,c)->dev.netlab.sputnik.client.SpeakerAudio.receive(q));}
    public static boolean browserAllowed(ServerPlayer p,BlockPos pos){
        if(InternetBridge.externalAllowed(p,pos))return true;var lease=BROWSERS.get(p.getUUID());var h=dev.netlab.api.PortableHosts.resolve(p,pos);
        if(lease==null||!lease.pos.equals(pos)||p.serverLevel().getGameTime()>lease.expiry||h==null)return false;
        try{dev.netlab.api.PortableHosts.require(p,h,"browser");}catch(IllegalArgumentException error){return false;}
        return lease.identity.equals(h.device.identity)&&lease.epoch==Hardware.ensure(h.device).epoch;
    }
    public static void panel(ServerPlayer p,SputnikRef r,boolean open,String status){var out=new JsonObject();var s=r.state();out.addProperty("kind",r.device().kind);out.addProperty("name",r.device().hostname);out.addProperty("ip",r.ip());out.addProperty("id",r.device().identity);out.addProperty("network",s.network);out.addProperty("satellite",s.satellite);out.addProperty("azimuth",s.azimuth);out.addProperty("elevation",s.elevation);out.addProperty("volume",s.volume);out.addProperty("enabled",s.enabled);out.addProperty("power",Hardware.powered(r.device()));out.addProperty("status",status.isBlank()?SatelliteLinks.status(r):status);out.addProperty("memory",MemoryCards.status(r.device()));PacketDistributor.sendToPlayer(p,new State(r.host().getBlockPos(),r.bay(),out.toString(),open));}
    private static void handle(ServerPlayer p,Action q){
        if(q.bay<0||q.bay>1)return;
        if(q.action.equals("browserClose")){var old=BROWSERS.get(p.getUUID());if(old!=null&&old.pos.equals(q.pos)){BROWSERS.remove(p.getUUID());InternetBridge.closePlayer(p);}return;}
        var h=dev.netlab.api.PortableHosts.resolve(p,q.pos);if(h==null)return;
        if(q.action.equals("heartbeat")){if(browserAllowed(p,q.pos)){var lease=BROWSERS.get(p.getUUID());BROWSERS.put(p.getUUID(),new Lease(lease.pos,lease.identity,lease.epoch,p.serverLevel().getGameTime()+80));}return;}
        if(q.action.equals("browserClose")){BROWSERS.remove(p.getUUID());InternetBridge.closePlayer(p);return;}
        if(h instanceof dev.netlab.api.PortableHosts.Host){if(!q.action.equals("browser")||q.bay!=0)return;try{dev.netlab.api.PortableHosts.require(p,h,"browser");}catch(IllegalArgumentException error){var out=new JsonObject();out.addProperty("kind","pc");out.addProperty("status",error.getMessage());PacketDistributor.sendToPlayer(p,new State(q.pos,0,out.toString(),false));return;}}else if(p.distanceToSqr(q.pos.getCenter())>64)return;int previousPorts=h.device.ports.size();SputnikHardware.audio(h.device);if(previousPorts!=h.device.ports.size())h.changed();var r=SputnikRef.of(h,q.bay);if(r==null)return;
        if(q.action.equals("browser")){if(!r.device().kind.equals("pc")||!r.live())return;BROWSERS.put(p.getUUID(),new Lease(q.pos,r.device().identity,Hardware.ensure(r.device()).epoch,p.serverLevel().getGameTime()+80));panel(p,r,true,"Браузер готов. Нужен маршрут к интернет-шлюзу провайдера");return;}
        if(!SputnikHardware.KINDS.contains(r.device().kind)||!r.state().owns(p.getUUID()))return;String status="";try{var s=r.state();if(!q.action.equals("refresh")&&!p.mayBuild())return;switch(q.action){case "refresh"->{}case "hardware"->{FrontPanel.send(p,h,q.bay,"");return;}case "point"->{if(!Set.of(SputnikHardware.DISH,SputnikHardware.VSAT).contains(r.device().kind))throw new IllegalArgumentException("Нужна антенна");String[] v=q.value.split(" ");if(v.length!=3)throw new IllegalArgumentException("Спутник, азимут, угол");SatelliteModel.find(v[0]);s.satellite=v[0];s.azimuth=Addressing.number(v[1],0,359);s.elevation=Addressing.number(v[2],5,85);r.changed(s);h.getLevel().setBlockAndUpdate(q.pos,h.getBlockState().setValue(DeviceBlock.FACING,net.minecraft.core.Direction.fromYRot(s.azimuth)));SatelliteLinks.clear();}case "volume"->{if(!r.device().kind.equals(SputnikHardware.SPEAKER))throw new IllegalArgumentException("Нужна колонка");s.volume=Addressing.number(q.value,0,100)/100.0;r.changed(s);}case "toggle"->{s.enabled=!s.enabled;r.changed(s);SatelliteLinks.clear();}default->throw new IllegalArgumentException("Неизвестное действие");}}catch(IllegalArgumentException e){status=e.getMessage();}panel(p,r,false,status);
    }
    private static void audio(ServerPlayer p,Audio q){if(!browserAllowed(p,q.pc)||q.pcm.length==0||q.pcm.length>12000||(q.pcm.length&1)!=0)return;long now=p.serverLevel().getGameTime();long[] budget=AUDIO_BUDGET.computeIfAbsent(p.getUUID(),k->new long[]{now,0});if(now-budget[0]>=20){budget[0]=now;budget[1]=0;}if((budget[1]+=q.pcm.length)>60000)return;var host=dev.netlab.api.PortableHosts.resolve(p,q.pc);if(host==null||host instanceof dev.netlab.api.PortableHosts.Host)return;if(EXTRA_AUDIO!=null&&EXTRA_AUDIO.test(p,q))return;var ref=new SputnikRef(host,0,host.device);int port=-1;for(int i=0;i<host.device.ports.size();i++)if(host.device.ports.get(i).name.equals("AudioOut"))port=i;if(port<0)return;var speaker=SatelliteLinks.peer(ref,port,"audio");if(speaker==null||!speaker.device().kind.equals(SputnikHardware.SPEAKER)||!speaker.state().enabled)return;
        var packet=new Sound(p.getUUID(),speaker.host().getBlockPos(),q.sequence,(float)Math.clamp(speaker.state().volume,0,1),q.pcm);for(var listener:p.serverLevel().players())if(listener.distanceToSqr(speaker.host().getBlockPos().getCenter())<32*32)PacketDistributor.sendToPlayer(listener,packet);
    }
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){if(e.getServer().getTickCount()%20!=0)return;InternetBridge.tick();for(var l:e.getServer().getAllLevels())for(var h:DeviceEntity.loaded(l))if(h.device.kind.equals("pc")){int size=h.device.ports.size();SputnikHardware.audio(h.device);if(size!=h.device.ports.size())h.changed();}BROWSERS.entrySet().removeIf(x->e.getServer().getPlayerList().getPlayer(x.getKey())==null);}
    public static void clear(){BROWSERS.clear();AUDIO_BUDGET.clear();}
}
