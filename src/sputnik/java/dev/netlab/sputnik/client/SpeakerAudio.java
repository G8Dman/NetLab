package dev.netlab.sputnik.client;

import dev.netlab.sputnik.SputnikPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.*;

/** PCM is emitted by CEF only while a page plays audio; no microphone is captured. */
public final class SpeakerAudio {
    private record Key(UUID player,long speaker){}
    private static final Map<Key,Track> TRACKS=new ConcurrentHashMap<>();
    private static final ExecutorService WORKERS=Executors.newVirtualThreadPerTaskExecutor();
    private static volatile double x,y,z,master;private static volatile boolean connected;private static volatile long lastFailure;
    private static final class Track {final BlockingQueue<SputnikPackets.Sound> queue=new ArrayBlockingQueue<>(12);volatile boolean closed;volatile SourceDataLine line;int last=Integer.MIN_VALUE;}
    public static void receive(SputnikPackets.Sound q){if(q.pcm().length==0||q.pcm().length>12000||(q.pcm().length&1)!=0||!Float.isFinite(q.volume()))return;var key=new Key(q.source(),q.speaker().asLong());var t=TRACKS.get(key);if(t==null){if(TRACKS.size()>=8||System.nanoTime()-lastFailure<3_000_000_000L)return;t=new Track();TRACKS.put(key,t);var track=t;WORKERS.submit(()->play(key,track));}if(q.sequence()<=t.last)return;t.last=q.sequence();if(!t.queue.offer(q)){t.queue.clear();t.queue.offer(q);}}
    public static void tick(){var mc=Minecraft.getInstance();connected=mc.level!=null&&mc.player!=null;if(!connected){clear();return;}x=mc.player.getX();y=mc.player.getY();z=mc.player.getZ();master=mc.options.getSoundSourceVolume(SoundSource.MASTER)*mc.options.getSoundSourceVolume(SoundSource.BLOCKS);}
    private static void play(Key key,Track t){try{var format=new AudioFormat(24000,16,1,true,false);var line=(SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class,format));t.line=line;line.open(format,24000);line.start();while(connected&&!t.closed){var q=t.queue.poll(3,TimeUnit.SECONDS);if(q==null)break;double distance=Math.sqrt(q.speaker().getCenter().distanceToSqr(new net.minecraft.world.phys.Vec3(x,y,z)));double gain=Math.clamp(q.volume()*master*(1-distance/32),0,1);byte[] data=q.pcm().clone();for(int i=0;i<data.length;i+=2){short sample=(short)((data[i]&255)|(data[i+1]<<8));short scaled=(short)Math.round(sample*gain);data[i]=(byte)scaled;data[i+1]=(byte)(scaled>>8);}line.write(data,0,data.length);}}catch(Exception error){if(connected&&!t.closed&&System.nanoTime()-lastFailure>3_000_000_000L){lastFailure=System.nanoTime();com.mojang.logging.LogUtils.getLogger().warn("Sputnik speaker audio output unavailable",error);var browser=BrowserSession.current;if(browser!=null)browser.status="Нет аудиовыхода JavaSound: "+error.getMessage();}}finally{t.closed=true;if(t.line!=null){t.line.stop();t.line.close();}TRACKS.remove(key,t);}}
    public static void clear(){for(var t:TRACKS.values()){t.closed=true;t.queue.clear();if(t.line!=null)t.line.close();}TRACKS.clear();}
}
