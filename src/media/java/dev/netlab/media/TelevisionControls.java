package dev.netlab.media;

import dev.netlab.DeviceEntity;
import dev.netlab.media.core.MediaHardware;
import dev.netlab.media.core.MediaPlaylist;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Switches the connected subscriber receiver, using the same saved tuning as its console. */
public final class TelevisionControls extends Item {
    public TelevisionControls(Properties properties){super(properties);}

    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();
        if(!(level.getBlockEntity(context.getClickedPos()) instanceof DeviceEntity host)
                || !(host.device.kind.equals(MediaHardware.TV)||host.device.kind.equals(MediaHardware.STB)))return InteractionResult.PASS;
        if(context.getPlayer() instanceof ServerPlayer player){
            try{
                var receiver=MediaRef.of(host,0);
                if(host.device.kind.equals(MediaHardware.TV))receiver=MediaService.peer(receiver,"HDMIIn","HDMIOut");
                if(receiver==null||!receiver.live()||!receiver.state().enabled)throw new IllegalArgumentException("Подключи включённую приставку по HDMI");
                if(!player.mayBuild()||!receiver.state().owns(player.getUUID()))throw new IllegalArgumentException("Настрой свою IPTV-приставку");
                var state=receiver.state();
                var headend=MediaRef.address(receiver.level(),state.server,MediaHardware.HEADEND);
                var network=receiver.host().network();
                if(!network.routePath(receiver.id(),headend.ip()).success()||!network.routePath(headend.id(),receiver.ip()).success())
                    throw new IllegalArgumentException("Нет связи со станцией IPTV");
                long now=level.getGameTime();
                var channels=headend.state().channels.entrySet().stream()
                        .filter(e->{var ch=e.getValue();return ch.running&&MediaPlaylist.at(ch.playlist,ch.started,now,ch.loop)!=null;})
                        .map(java.util.Map.Entry::getKey).sorted().toList();
                if(channels.isEmpty())throw new IllegalArgumentException("Нет каналов в эфире");
                int selected=state.channel;
                if(player.isShiftKeyDown())state.channel=channels.reversed().stream().filter(c->c<selected).findFirst().orElse(channels.getLast());
                else state.channel=channels.stream().filter(c->c>selected).findFirst().orElse(channels.getFirst());
                receiver.changed(state);
                player.displayClientMessage(Component.literal("Канал "+state.channel+" · "+headend.state().channels.get(state.channel).name),true);
            }catch(IllegalArgumentException error){player.displayClientMessage(Component.literal(error.getMessage()),true);}
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
