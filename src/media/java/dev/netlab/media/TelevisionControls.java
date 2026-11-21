package dev.netlab.media;

import dev.netlab.DeviceEntity;
import dev.netlab.core.Device;
import dev.netlab.media.core.MediaHardware;
import dev.netlab.media.core.MediaPlaylist;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;

/** Each remote is paired to the physical receiver, independent of its IP or channel. */
public final class TelevisionControls extends Item {
    private static final String RECEIVER = "netlabMediaReceiver";

    public TelevisionControls(Properties properties){super(properties);}

    static void pair(ItemStack remote,Device receiver){
        if(!receiver.kind.equals(MediaHardware.STB)||receiver.identity.isBlank())throw new IllegalArgumentException("Нужна IPTV-приставка");
        CustomData.update(DataComponents.CUSTOM_DATA,remote,tag->tag.putString(RECEIVER,receiver.identity));
    }

    private static String pairedReceiver(ItemStack remote){
        return remote.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString(RECEIVER);
    }

    static boolean pairedWith(ItemStack remote,Device receiver){
        String identity=pairedReceiver(remote);
        return !identity.isBlank()&&receiver.kind.equals(MediaHardware.STB)&&identity.equals(receiver.identity);
    }

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable(pairedReceiver(stack).isBlank()?"item.netlabmedia.tv_remote.unpaired":"item.netlabmedia.tv_remote.paired"));
        lines.add(Component.translatable("item.netlabmedia.tv_remote.pair_hint"));
        lines.add(Component.translatable("item.netlabmedia.tv_remote.power_hint"));
    }

    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();
        if(!(level.getBlockEntity(context.getClickedPos()) instanceof DeviceEntity host)
                || !(host.device.kind.equals(MediaHardware.TV)||host.device.kind.equals(MediaHardware.STB)))return InteractionResult.PASS;
        if(context.getPlayer() instanceof ServerPlayer player){
            try{
                String message=operate(player,context.getItemInHand(),MediaRef.of(host,0));
                player.displayClientMessage(Component.literal(message),true);
            }catch(IllegalArgumentException error){player.displayClientMessage(Component.literal(error.getMessage()),true);}
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static String operate(ServerPlayer player,ItemStack remote,MediaRef target){
        boolean television=target.device().kind.equals(MediaHardware.TV);
        boolean shift=player.isShiftKeyDown();
        if(!player.mayBuild())throw new IllegalArgumentException("Нет прав управления IPTV");
        if(shift&&!television){
            if(!target.state().owns(player.getUUID()))throw new IllegalArgumentException("Настрой свою IPTV-приставку");
            pair(remote,target.device());
            return "Пульт привязан к приставке";
        }
        if(pairedReceiver(remote).isBlank())throw new IllegalArgumentException("Привяжи пульт через Shift+ПКМ по приставке");
        var receiver=television?MediaService.connectedPeer(target,"HDMIIn","HDMIOut"):target;
        if(receiver==null||!receiver.device().kind.equals(MediaHardware.STB))throw new IllegalArgumentException("Подключи приставку по HDMI");
        if(!pairedWith(remote,receiver.device()))throw new IllegalArgumentException("Пульт привязан к другой приставке");
        var state=receiver.state();
        if(!state.owns(player.getUUID()))throw new IllegalArgumentException("Настрой свою IPTV-приставку");
        if(shift){
            var settings=target.state();
            if(!settings.owns(player.getUUID()))throw new IllegalArgumentException("Телевизор принадлежит другому игроку");
            String message=TelevisionBlock.togglePower(settings);
            target.changed(settings);
            return message;
        }
        if(!receiver.live()||!state.enabled)throw new IllegalArgumentException("Включи IPTV-приставку");
        var headend=MediaRef.address(receiver.level(),state.server,MediaHardware.HEADEND);
        var network=receiver.host().network();
        if(!network.routePath(receiver.id(),headend.ip()).success()||!network.routePath(headend.id(),receiver.ip()).success())
            throw new IllegalArgumentException("Нет связи со станцией IPTV");
        long now=receiver.level().getGameTime();
        var station=headend.state();
        var channels=station.channels.entrySet().stream()
                .filter(entry->{var channel=entry.getValue();return channel.running&&MediaPlaylist.at(channel.playlist,channel.started,now,channel.loop)!=null;})
                .map(java.util.Map.Entry::getKey).sorted().toList();
        if(channels.isEmpty())throw new IllegalArgumentException("Нет каналов в эфире");
        int selected=state.channel;
        state.channel=channels.stream().filter(c->c>selected).findFirst().orElse(channels.getFirst());
        receiver.changed(state);
        return "Канал "+state.channel+" · "+station.channels.get(state.channel).name;
    }
}
