package dev.netlab;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class TerminalPackets {
    public record Data(BlockPos pos,String token,String text,String history,boolean open,boolean replace,boolean busy,boolean connected) implements CustomPacketPayload {
        public static final Type<Data> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(NetLab.ID,"terminal_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Data> CODEC=StreamCodec.of((b,m)->{b.writeBlockPos(m.pos);b.writeUtf(m.token,64);b.writeUtf(m.text,160000);b.writeUtf(m.history,180000);b.writeBoolean(m.open);b.writeBoolean(m.replace);b.writeBoolean(m.busy);b.writeBoolean(m.connected);},b->new Data(b.readBlockPos(),b.readUtf(64),b.readUtf(160000),b.readUtf(180000),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean()));
        public Type<Data> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(Data.TYPE,Data.CODEC,(m,c)->dev.netlab.client.Client.terminal(m));}
}
