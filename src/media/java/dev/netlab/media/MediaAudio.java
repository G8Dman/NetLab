package dev.netlab.media;
import com.google.gson.*;
import dev.netlab.media.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Directed, cycle-safe audio distribution. A splitter never connects two outputs backwards. */
public final class MediaAudio {
    public static final int RANGE_SQUARED=32*32;

    public record Output(BlockPos pos,float volume) {
        public boolean audibleAt(Vec3 listener){return volume>0&&pos.getCenter().distanceToSqr(listener)<RANGE_SQUARED;}
        public JsonObject json(){var row=new JsonObject();row.addProperty("pos",pos.asLong());row.addProperty("volume",volume);return row;}
        public static Output fromJson(JsonObject row){return new Output(BlockPos.of(row.get("pos").getAsLong()),row.get("volume").getAsFloat());}
    }

    public static double audienceDistanceSquared(BlockPos tv,List<Output> outputs,Vec3 listener){
        double distance=tv.getCenter().distanceToSqr(listener);
        for(var output:outputs)if(output.volume()>0)distance=Math.min(distance,output.pos().getCenter().distanceToSqr(listener));
        return distance;
    }

    public static boolean browser(net.minecraft.server.level.ServerPlayer player,dev.netlab.sputnik.SputnikPackets.Audio packet){var host=dev.netlab.api.PortableHosts.resolve(player,packet.pc());if(host==null)return false;var ref=new MediaRef(host,0,host.device);var outs=outputs(ref);if(outs.isEmpty())return false;dev.netlab.sputnik.SputnikPackets.audioLearning(player,packet.pc(),outs.stream().map(o->o.pos().asLong()).toList(),false);for(var output:outs){var sound=new dev.netlab.sputnik.SputnikPackets.Sound(player.getUUID(),output.pos(),packet.sequence(),output.volume(),packet.pcm());for(var listener:player.serverLevel().players())if(output.audibleAt(listener.position()))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(listener,sound);}return true;}
    public static List<Output> outputs(MediaRef tv){var result=new ArrayList<Output>();var seen=new HashSet<String>();walk(tv,result,seen,0,1);return List.copyOf(result);}
    private static void walk(MediaRef node,List<Output> out,Set<String> seen,int depth,double gain){if(depth>8||out.size()>=16||!node.live()||!node.state().enabled||!seen.add(node.device().identity))return;var s=node.state();gain*=s.volume;
        boolean tv=node.device().kind.equals(MediaHardware.TV),speaker=node.device().kind.equals(MediaHardware.SPEAKER)||node.device().kind.equals(dev.netlab.sputnik.core.SputnikHardware.SPEAKER);
        if(speaker&&node.device().kind.equals(dev.netlab.sputnik.core.SputnikHardware.SPEAKER))gain*=dev.netlab.sputnik.SputnikState.get(node.device()).volume;
        if(speaker||tv&&s.speakers)out.add(new Output(node.host().getBlockPos(),(float)Math.clamp(gain,0,1)));
        for(int i=0;i<node.device().ports.size();i++){String name=node.device().portName(i);if(!name.startsWith("AudioOut"))continue;var next=MediaService.peer(node,name,"AudioIn");if(next!=null)walk(next,out,seen,depth+1,gain);}
        // A receiver's analog audio output is usable alongside the HDMI display.
        if(tv){var receiver=MediaService.peer(node,"HDMIIn","HDMIOut");if(receiver!=null){var next=MediaService.peer(receiver,"AudioOut","AudioIn");if(next!=null)walk(next,out,seen,depth+1,gain);}}
    }
}
