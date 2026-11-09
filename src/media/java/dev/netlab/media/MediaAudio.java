package dev.netlab.media;
import com.google.gson.*;
import dev.netlab.media.core.*;
import java.util.*;
/** Directed, cycle-safe audio distribution. A splitter never connects two outputs backwards. */
public final class MediaAudio {
    public static boolean browser(net.minecraft.server.level.ServerPlayer player,dev.netlab.sputnik.SputnikPackets.Audio packet){var host=dev.netlab.api.PortableHosts.resolve(player,packet.pc());if(host==null)return false;var ref=new MediaRef(host,0,host.device);var outs=outputs(ref);if(outs.isEmpty())return false;dev.netlab.sputnik.SputnikPackets.audioLearning(player,packet.pc(),outs.stream().map(o->o.get("pos").getAsLong()).toList(),false);for(var output:outs){var pos=net.minecraft.core.BlockPos.of(output.get("pos").getAsLong());var sound=new dev.netlab.sputnik.SputnikPackets.Sound(player.getUUID(),pos,packet.sequence(),output.get("volume").getAsFloat(),packet.pcm());for(var listener:player.serverLevel().players())if(listener.distanceToSqr(pos.getCenter())<32*32)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(listener,sound);}return true;}
    public static List<JsonObject> outputs(MediaRef tv){var result=new ArrayList<JsonObject>();var seen=new HashSet<String>();walk(tv,result,seen,0,1);return result;}
    private static void walk(MediaRef node,List<JsonObject> out,Set<String> seen,int depth,double gain){if(depth>8||out.size()>=16||!node.live()||!node.state().enabled||!seen.add(node.device().identity))return;var s=node.state();gain*=s.volume;
        boolean tv=node.device().kind.equals(MediaHardware.TV),speaker=node.device().kind.equals(MediaHardware.SPEAKER)||node.device().kind.equals(dev.netlab.sputnik.core.SputnikHardware.SPEAKER);
        if(speaker&&node.device().kind.equals(dev.netlab.sputnik.core.SputnikHardware.SPEAKER))gain*=dev.netlab.sputnik.SputnikState.get(node.device()).volume;
        if(speaker||tv&&s.speakers){var row=new JsonObject();row.addProperty("pos",node.host().getBlockPos().asLong());row.addProperty("volume",Math.clamp(gain,0,1));out.add(row);}
        for(int i=0;i<node.device().ports.size();i++){String name=node.device().portName(i);if(!name.startsWith("AudioOut"))continue;var next=MediaService.peer(node,name,"AudioIn");if(next!=null)walk(next,out,seen,depth+1,gain);}
        // A receiver's analog audio output is usable alongside the HDMI display.
        if(tv){var receiver=MediaService.peer(node,"HDMIIn","HDMIOut");if(receiver!=null){var next=MediaService.peer(receiver,"AudioOut","AudioIn");if(next!=null)walk(next,out,seen,depth+1,gain);}}
    }
}
