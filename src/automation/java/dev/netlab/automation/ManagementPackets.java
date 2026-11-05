package dev.netlab.automation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class ManagementPackets {
    public record Request(BlockPos pc,String token,String action,String json) implements CustomPacketPayload{
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Automation.ID,"management_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,m)->{b.writeBlockPos(m.pc);b.writeUtf(m.token,64);b.writeUtf(m.action,32);b.writeUtf(m.json,8192);},b->new Request(b.readBlockPos(),b.readUtf(64),b.readUtf(32),b.readUtf(8192)));
        public Type<Request> type(){return TYPE;}
    }
    public record State(BlockPos pc,String json) implements CustomPacketPayload{
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Automation.ID,"management_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,m)->{b.writeBlockPos(m.pc);b.writeUtf(m.json,180000);},b->new State(b.readBlockPos(),b.readUtf(180000)));
        public Type<State> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("2");r.playToServer(Request.TYPE,Request.CODEC,(m,c)->ManagementService.handle((net.minecraft.server.level.ServerPlayer)c.player(),m));r.playToClient(State.TYPE,State.CODEC,(m,c)->dev.netlab.automation.client.AutomationClient.management(m));}
}
