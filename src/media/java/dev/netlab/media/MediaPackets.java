package dev.netlab.media;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class MediaPackets {
    public record Action(BlockPos pos,int bay,String action,String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Media.ID,"action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Action::pos,ByteBufCodecs.VAR_INT,Action::bay,ByteBufCodecs.stringUtf8(24),Action::action,ByteBufCodecs.stringUtf8(8192),Action::json,Action::new);public Type<Action> type(){return TYPE;}
    }
    public record State(BlockPos pos,int bay,String json,boolean open) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Media.ID,"state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,State::pos,ByteBufCodecs.VAR_INT,State::bay,ByteBufCodecs.stringUtf8(30000),State::json,ByteBufCodecs.BOOL,State::open,State::new);public Type<State> type(){return TYPE;}
    }
    public record Scene(String json) implements CustomPacketPayload {
        public static final Type<Scene> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Media.ID,"scene"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Scene> CODEC=StreamCodec.composite(ByteBufCodecs.stringUtf8(30000),Scene::json,Scene::new);public Type<Scene> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("1");r.playToServer(Action.TYPE,Action.CODEC,(q,c)->MediaService.action((ServerPlayer)c.player(),q));r.playToClient(State.TYPE,State.CODEC,(q,c)->dev.netlab.media.client.MediaClient.state(q));r.playToClient(Scene.TYPE,Scene.CODEC,(q,c)->dev.netlab.media.client.MediaClient.scene(q));}
}
