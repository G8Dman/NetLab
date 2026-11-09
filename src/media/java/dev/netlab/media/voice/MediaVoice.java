package dev.netlab.media.voice;
import dev.netlab.media.*;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
/** Opus transport is supplied by Simple Voice Chat; NetLab controls routing and recipients. */
@ForgeVoicechatPlugin
public final class MediaVoice implements VoicechatPlugin {
    private static volatile VoicechatServerApi api;
    private record Key(UUID call,UUID from,UUID target,boolean speaker){}
    private static final class Channel {de.maxhenkel.voicechat.api.audiochannel.AudioChannel audio;long touched;Channel(de.maxhenkel.voicechat.api.audiochannel.AudioChannel audio){this.audio=audio;touch();}void touch(){touched=System.nanoTime();}}
    private static final Map<Key,Channel> CHANNELS=new HashMap<>();
    public static void clear(){for(var channel:CHANNELS.values())channel.audio.flush();CHANNELS.clear();}
    public static void tick(){long now=System.nanoTime();var iterator=CHANNELS.values().iterator();while(iterator.hasNext()){var channel=iterator.next();if(now-channel.touched>3_000_000_000L){channel.audio.flush();iterator.remove();}}}

    public String getPluginId(){return Media.ID;}
    public static boolean ready(){return api!=null;}
    @Override public void registerEvents(EventRegistration events){
        events.registerEvent(VoicechatServerStartedEvent.class,e->api=e.getVoicechat());
        events.registerEvent(VoicechatServerStoppedEvent.class,e->{clear();api=null;});
        events.registerEvent(MicrophonePacketEvent.class,e->{var from=e.getSenderConnection();if(from==null)return;UUID id=from.getPlayer().getUuid();if(!MediaService.VOICE_USERS.contains(id))return;e.cancel();byte[] opus=e.getPacket().getOpusEncodedData().clone();var server=MediaService.server;if(server!=null&&opus.length<=1275)server.execute(()->MediaService.voice(id,opus));});
    }
    public static boolean deliver(UUID call,UUID from,ServerPlayer target,byte[] opus,boolean speakerphone,BlockPos phone){var voice=api;if(voice==null)return false;var connection=voice.getConnectionOf(target.getUUID());if(connection==null||!connection.isConnected()||connection.isDisabled())return false;
        var key=new Key(call,from,target.getUUID(),speakerphone);var channel=CHANNELS.get(key);
        if(channel==null){if(CHANNELS.size()>=128)return false;var id=UUID.randomUUID();de.maxhenkel.voicechat.api.audiochannel.AudioChannel output;
            if(speakerphone){var located=voice.createLocationalAudioChannel(id,voice.fromServerLevel(target.serverLevel()),voice.createPosition(phone.getX()+.5,phone.getY()+.5,phone.getZ()+.5));located.setDistance(12);output=located;}
            else output=voice.createStaticAudioChannel(id,voice.fromServerLevel(target.serverLevel()),connection);
            if(output==null)return false;channel=new Channel(output);CHANNELS.put(key,channel);
        }
        if(channel.audio instanceof de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel located)located.updateLocation(voice.createPosition(phone.getX()+.5,phone.getY()+.5,phone.getZ()+.5));
        channel.touch();channel.audio.send(opus);return true;
    }
}
