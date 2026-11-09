package dev.netlab.client;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import dev.netlab.Packets;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Optional learning acknowledgement after an actual client audio line accepted audible samples. */
public final class LearningAudio {
    private static final Map<String,Long> LAST=new HashMap<>();
    public static void played(UUID source,BlockPos speaker){var mc=Minecraft.getInstance();mc.execute(()->{if(mc.player==null||mc.getConnection()==null)return;long now=mc.level.getGameTime();String key=source+":"+speaker.asLong();if(now-LAST.getOrDefault(key,now-100)<40)return;if(LAST.size()>128)LAST.clear();LAST.put(key,now);PacketDistributor.sendToServer(new Packets.LearningHint(speaker,"audio.played",source.toString()));});}
    private LearningAudio(){}
}
