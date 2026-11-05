package dev.netlab.core;

/** Client trace time: interpolate at most one game tick, never invent progress during a stall. */
public final class StudyClock {
    private long lastWall,lastTick,tickWall;
    private double value;
    public void reset(long tick,long wall){lastWall=wall;tickWall=wall;lastTick=tick;value=tick*.05;}
    public double advance(long tick,long wall,boolean worldPaused,boolean synchronizedTime,boolean playbackPaused,double speed){
        double elapsed=Math.max(0,(wall-lastWall)/1e9);
        if(elapsed>.25)elapsed=Math.max(0,tick-lastTick)*.05;
        if(tick!=lastTick)tickWall=wall;
        if(!worldPaused){
            if(synchronizedTime)value=tick*.05+Math.clamp((wall-tickWall)/1e9,0,.05);
            else if(!playbackPaused)value+=elapsed*speed;
        }
        lastWall=wall;lastTick=tick;return value;
    }
}
