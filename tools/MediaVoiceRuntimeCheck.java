import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.*;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.packets.*;
import dev.netlab.media.*;
import dev.netlab.media.voice.MediaVoice;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
/** Invoke the actual plugin using Voice Chat's published API contracts, without audio hardware. */
public final class MediaVoiceRuntimeCheck {
    static int checks;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,BiFunction<String,Object[],Object> call){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{if(m.getName().equals("toString"))return type.getSimpleName();return call.apply(m.getName(),a);});}
    public static void run()throws Exception {
        var handlers=new HashMap<Class<?>,Consumer<?>>();var plugin=new MediaVoice();plugin.registerEvents(new EventRegistration(){public <T extends Event>void registerEvent(Class<T> type,Consumer<T> handler,int priority){handlers.put(type,handler);}});
        check(handlers.containsKey(MicrophonePacketEvent.class),"plugin registers microphone handler");UUID id=UUID.randomUUID();boolean[] cancelled={false};var player=proxy(de.maxhenkel.voicechat.api.ServerPlayer.class,(n,a)->n.equals("getUuid")?id:null);var connection=proxy(VoicechatConnection.class,(n,a)->switch(n){case "getPlayer"->player;case "isConnected","isInstalled"->true;case "isDisabled"->false;default->null;});var mic=proxy(MicrophonePacket.class,(n,a)->n.equals("getOpusEncodedData")?new byte[]{1,2,3}:null);var event=proxy(MicrophonePacketEvent.class,(n,a)->switch(n){case "getSenderConnection"->connection;case "getPacket"->mic;case "cancel"->{cancelled[0]=true;yield true;}default->null;});
        @SuppressWarnings("unchecked")var onMic=(Consumer<MicrophonePacketEvent>)handlers.get(MicrophonePacketEvent.class);onMic.accept(event);check(!cancelled[0],"ordinary proximity voice is untouched outside calls");MediaService.VOICE_USERS.add(id);onMic.accept(event);check(cancelled[0],"active phone microphone cannot leak through default proximity routing");MediaService.VOICE_USERS.clear();
        int[] created={0},sent={0},flushed={0};var audio=proxy(StaticAudioChannel.class,(n,a)->{if(n.equals("send"))sent[0]++;if(n.equals("flush"))flushed[0]++;return null;});var api=proxy(VoicechatServerApi.class,(n,a)->switch(n){case "getConnectionOf"->connection;case "createStaticAudioChannel"->{created[0]++;yield audio;}default->null;});
        var started=proxy(VoicechatServerStartedEvent.class,(n,a)->n.equals("getVoicechat")?api:null);@SuppressWarnings("unchecked")var onStart=(Consumer<VoicechatServerStartedEvent>)handlers.get(VoicechatServerStartedEvent.class);onStart.accept(started);check(MediaVoice.ready(),"voice lifecycle supplies API");
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var target=(net.minecraft.server.level.ServerPlayer)((sun.misc.Unsafe)field.get(null)).allocateInstance(net.minecraft.server.level.ServerPlayer.class);target.setUUID(UUID.randomUUID());UUID call=UUID.randomUUID();for(int i=0;i<3;i++)MediaVoice.deliver(call,id,target,new byte[]{4,5,6},false,net.minecraft.core.BlockPos.ZERO);
        check(created[0]==1&&sent[0]==3,"one stable channel maintains packet sequence across consecutive microphone frames");MediaVoice.deliver(UUID.randomUUID(),id,target,new byte[]{7},false,net.minecraft.core.BlockPos.ZERO);check(created[0]==2,"new call creates a fresh audio stream");MediaVoice.clear();check(flushed[0]==2,"shutdown flushes audio streams");
        System.out.println(checks+" actual Voice Chat API lifecycle/privacy/channel checks passed; no microphone or server started.");
    }
}
