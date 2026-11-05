package dev.netlab.client;

import com.google.gson.Gson;
import dev.netlab.Study;
import dev.netlab.core.StudyClock;
import java.util.*;

public final class StudyClient {
    public record Flight(Study.Event event,double start,double duration) {}
    private static final List<Flight> flights=new ArrayList<>();
    private static final StudyClock timing=new StudyClock();
    private static double clock;
    public static double speed=1;
    public static boolean paused, visible=true,synchronizedTime=true;
    private static Object world;
    public static double time(){
        Object current=net.minecraft.client.Minecraft.getInstance().level;
        long now=System.nanoTime(),tick=net.minecraft.client.Minecraft.getInstance().level==null?0:net.minecraft.client.Minecraft.getInstance().level.getGameTime();
        if(world!=current){flights.clear();world=current;timing.reset(tick,now);}
        clock=timing.advance(tick,now,net.minecraft.client.Minecraft.getInstance().isPaused(),synchronizedTime,paused,speed);
        flights.removeIf(f->clock-f.start()>f.duration()+.001);return clock;
    }
    public static void receive(String json){
        time();var batch=new Gson().fromJson(json,Study.Batch.class);
        for(var event:batch.events()){if(flights.size()>=4096)break;double start=synchronizedTime?event.start()/1e9:clock+(event.start()-batch.now())/1e9;double duration=Math.max(1e-9,(event.finish()-event.start())/1e9);if(start+duration>=clock)flights.add(new Flight(event,start,duration));}
    }
    public static List<Flight> flights(){time();return List.copyOf(flights);}
    public static void clear(){flights.clear();}
    public static void preview(double factor){time();synchronizedTime=false;speed=factor;}
    public static void pausePlayback(){if(synchronizedTime)preview(1);else time();paused=!paused;}
    public static void synchronize(){var live=flights.stream().map(f->new Flight(f.event(),f.event().start()/1e9,f.duration())).toList();flights.clear();flights.addAll(live);synchronizedTime=true;speed=1;paused=false;time();}
}
