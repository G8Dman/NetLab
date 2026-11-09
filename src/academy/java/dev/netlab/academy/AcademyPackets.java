package dev.netlab.academy;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class AcademyPackets {
    public record Page(String article) implements CustomPacketPayload {
        public static final Type<Page> TYPE=new Type<>(ResourceLocation.parse("netlabacademy:page"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Page> CODEC=StreamCodec.composite(ByteBufCodecs.stringUtf8(160),Page::article,Page::new);
        public Type<Page> type(){return TYPE;}
    }
    public record State(String json,boolean open) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.parse("netlabacademy:state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.composite(ByteBufCodecs.stringUtf8(65536),State::json,ByteBufCodecs.BOOL,State::open,State::new);
        public Type<State> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("1");r.playToServer(Page.TYPE,Page.CODEC,(q,c)->Academy.page((ServerPlayer)c.player(),q.article()));r.playToClient(State.TYPE,State.CODEC,(q,c)->dev.netlab.academy.client.AcademyClient.state(q));}
}
