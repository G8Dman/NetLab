package dev.netlab.automation.core;

import dev.netlab.core.*;

/** Degrees C in game time; no destruction. Hysteresis prevents rapid stop/start cycling. */
public final class ServerHeat {
    public static final double AMBIENT=25,THROTTLE=70,STOP=90,RESUME=60;
    public double temperature=AMBIENT;
    public boolean protectedStop;
    public void normalize(){if(!Double.isFinite(temperature))temperature=AMBIENT;temperature=Math.clamp(temperature,AMBIENT,120);if(temperature>=STOP)protectedStop=true;else if(temperature<=RESUME)protectedStop=false;}
    public void advance(Device d,double seconds){
        normalize();boolean powered=Hardware.powered(d);double k=!powered?.08:switch(ServerHardware.cooling(d)){case 2->.30;case 1->.12;default->.018;};
        double heat=powered?(protectedStop?.25:ServerHardware.idleHeat(d)):0;double equilibrium=AMBIENT+heat/k;
        temperature=equilibrium+(temperature-equilibrium)*Math.exp(-k*Math.clamp(seconds,0,5));normalize();
    }
    public void work(Device d,double effort){if(Hardware.powered(d)&&!protectedStop){temperature+=Math.clamp(effort,0,32)*(1+(ServerHardware.has(d,ServerHardware.ACCEL)?.45:0));normalize();}}
    public double throttle(){normalize();return temperature>=THROTTLE?2:1;}
    public int offerTicks(Device d){return (int)((ServerHardware.has(d,ServerHardware.ACCEL)?100:400)*throttle());}
    public long processingNanos(Device d){return (long)((ServerHardware.has(d,ServerHardware.ACCEL)?50_000_000L:200_000_000L)*throttle());}
}
