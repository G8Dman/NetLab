package dev.netlab;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Packets {
  public record WifiRequest(BlockPos pos,int bay,String action,String json) implements CustomPacketPayload {
    public static final Type<WifiRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID,"wifi_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WifiRequest> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,WifiRequest::pos,ByteBufCodecs.VAR_INT,WifiRequest::bay,ByteBufCodecs.stringUtf8(16),WifiRequest::action,ByteBufCodecs.stringUtf8(2048),WifiRequest::json,WifiRequest::new);
    public Type<WifiRequest> type(){return TYPE;}
  }
  public record WifiData(BlockPos pos,int bay,String json,boolean open) implements CustomPacketPayload {
    public static final Type<WifiData> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID,"wifi_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WifiData> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,WifiData::pos,ByteBufCodecs.VAR_INT,WifiData::bay,ByteBufCodecs.stringUtf8(262144),WifiData::json,ByteBufCodecs.BOOL,WifiData::open,WifiData::new);
    public Type<WifiData> type(){return TYPE;}
  }
  public record PanelRequest(BlockPos pos,int bay,String action,String slot,String value) implements CustomPacketPayload {
    public static final Type<PanelRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID,"panel_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PanelRequest> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,PanelRequest::pos,ByteBufCodecs.VAR_INT,PanelRequest::bay,ByteBufCodecs.stringUtf8(16),PanelRequest::action,ByteBufCodecs.stringUtf8(64),PanelRequest::slot,ByteBufCodecs.stringUtf8(96),PanelRequest::value,PanelRequest::new);
    public Type<PanelRequest> type(){return TYPE;}
  }
  public record PanelData(BlockPos pos,int bay,String json,boolean open) implements CustomPacketPayload {
    public static final Type<PanelData> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID,"panel_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PanelData> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,PanelData::pos,ByteBufCodecs.VAR_INT,PanelData::bay,ByteBufCodecs.stringUtf8(262144),PanelData::json,ByteBufCodecs.BOOL,PanelData::open,PanelData::new);
    public Type<PanelData> type(){return TYPE;}
  }
  public record Trace(String json) implements CustomPacketPayload {
    public static final Type<Trace> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID,"trace"));
    public static final StreamCodec<RegistryFriendlyByteBuf,Trace> CODEC=StreamCodec.composite(ByteBufCodecs.stringUtf8(262144),Trace::json,Trace::new);
    public Type<Trace> type(){return TYPE;}
  }
  public record Command(BlockPos pos, String command,String token) implements CustomPacketPayload {
    public Command(BlockPos pos,String command){this(pos,command,"");}
    public static final Type<Command> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID, "command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Command> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, Command::pos, ByteBufCodecs.stringUtf8(256), Command::command,ByteBufCodecs.stringUtf8(64),Command::token, Command::new);
    @Override
    public Type<Command> type() {
      return TYPE;
    }
  }
  public record Output(BlockPos pos, String text, boolean open) implements CustomPacketPayload {
    public static final Type<Output> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID, "output"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Output> CODEC =
        StreamCodec.composite(BlockPos.STREAM_CODEC, Output::pos, ByteBufCodecs.stringUtf8(262144),
            Output::text, ByteBufCodecs.BOOL, Output::open, Output::new);
    @Override
    public Type<Output> type() {
      return TYPE;
    }
  }
  public record Complete(BlockPos pos, String prefix) implements CustomPacketPayload {
    public static final Type<Complete> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID, "complete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Complete> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, Complete::pos, ByteBufCodecs.stringUtf8(256), Complete::prefix, Complete::new);
    @Override
    public Type<Complete> type() {
      return TYPE;
    }
  }
  public record Completed(BlockPos pos, String query, String value, String choices)
      implements CustomPacketPayload {
    public static final Type<Completed> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID, "completed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Completed> CODEC =
        StreamCodec.composite(BlockPos.STREAM_CODEC, Completed::pos, ByteBufCodecs.stringUtf8(256),
            Completed::query, ByteBufCodecs.stringUtf8(256), Completed::value, ByteBufCodecs.STRING_UTF8,
            Completed::choices, Completed::new);
    @Override
    public Type<Completed> type() {
      return TYPE;
    }
  }
  public static void register(RegisterPayloadHandlersEvent e) {
    var r = e.registrar("11");
    r.playToServer(WifiRequest.TYPE,WifiRequest.CODEC,(msg,ctx)->WirelessMenu.handle((ServerPlayer)ctx.player(),msg));
    r.playToClient(WifiData.TYPE,WifiData.CODEC,(msg,ctx)->dev.netlab.client.Client.wifi(msg));
    r.playToServer(PanelRequest.TYPE,PanelRequest.CODEC,(msg,ctx)->FrontPanel.handle((ServerPlayer)ctx.player(),msg));
    r.playToClient(PanelData.TYPE,PanelData.CODEC,(msg,ctx)->dev.netlab.client.Client.panel(msg));
    r.playToClient(Trace.TYPE,Trace.CODEC,(msg,ctx)->dev.netlab.client.StudyClient.receive(msg.json()));
    r.playToServer(Command.TYPE,Command.CODEC,(msg,ctx)->ConsoleCommands.handle((ServerPlayer)ctx.player(),msg));
    r.playToServer(Complete.TYPE, Complete.CODEC, (msg, ctx) -> {
      var player = (ServerPlayer) ctx.player();
      if(msg.prefix.length()<=256&&PortableTerminals.complete(player,msg))return;
      var level = player.serverLevel();
      if (msg.prefix.length() > 256
          || player.distanceToSqr(msg.pos.getX() + .5, msg.pos.getY() + .5, msg.pos.getZ() + .5) > 64
          || !level.hasChunkAt(msg.pos))
        return;
      if (level.getBlockEntity(msg.pos) instanceof DeviceEntity device
          && device.selectedDevice(player.getUUID())!=null && !device.selectedDevice(player.getUUID()).kind.equals("rack")) {
        if(!device.device.kind.equals("pc"))return;
        if(ConsoleAccess.active(device,player.getUUID())){
          var connection=ConsoleAccess.target(device,player.getUUID(),false);
          if(connection==null)return;
          var extension=dev.netlab.api.ConsoleExtensions.HANDLERS.get(connection.device().kind);
          var memory=MemoryCards.complete(msg.prefix);var completion=memory!=null?memory:extension!=null?extension.complete(msg.prefix):connection.device().kind.equals("traffic")?TrafficControllers.complete(connection.cli(),msg.prefix):connection.cli().complete(connection.device(),msg.prefix);
          PacketDistributor.sendToPlayer(player,new Completed(msg.pos,msg.prefix,completion.value(),String.join("\n",completion.choices())));return;
        }
        var completion = device.session(player.getUUID()).complete(device.selectedDevice(player.getUUID()), msg.prefix);
        PacketDistributor.sendToPlayer(player,
            new Completed(msg.pos, msg.prefix, completion.value(), String.join("\n", completion.choices())));
      }
    });
    r.playToClient(Completed.TYPE, Completed.CODEC, (msg, ctx) -> dev.netlab.client.Client.complete(msg));
    r.playToClient(Output.TYPE, Output.CODEC, (msg, ctx) -> dev.netlab.client.Client.receive(msg));
  }
}
