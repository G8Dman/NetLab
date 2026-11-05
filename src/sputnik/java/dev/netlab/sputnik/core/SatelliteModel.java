package dev.netlab.sputnik.core;

import java.util.*;

/** GEO geometry is an explicit Minecraft abstraction; rates and latency use real units. */
public final class SatelliteModel {
    public record Satellite(String id,double azimuth,double elevation){}
    public static final List<Satellite> SKY=List.of(new Satellite("GEO-1",180,45),new Satellite("GEO-2",90,35),new Satellite("GEO-3",270,55));
    public static Satellite find(String id){return SKY.stream().filter(s->s.id.equals(id.toUpperCase(Locale.ROOT))).findFirst().orElseThrow(()->new IllegalArgumentException("GEO-1 | GEO-2 | GEO-3"));}
    public record Reception(double quality,double marginDb,String state){}
    public static double angle(double a,double b){double delta=Math.abs(a-b)%360;return Math.min(delta,360-delta);}
    public static Reception signal(Satellite sat,double azimuth,double elevation,double cable,double size,boolean obstructed,boolean storm){
        if(!Double.isFinite(azimuth)||!Double.isFinite(elevation)||!Double.isFinite(cable)||!Double.isFinite(size)||cable<0||size<=0)return new Reception(0,-100,"Некорректная антенна");
        double error=Math.hypot(angle(sat.azimuth,azimuth)*Math.cos(Math.toRadians(elevation)),sat.elevation-elevation);
        double margin=14+10*Math.log10(size)-error*2.5-cable*.10-(storm?7:0)-(obstructed?80:0);
        return new Reception(margin<=0?0:Math.clamp(margin/14,.05,1),margin,obstructed?"Препятствие в направлении спутника":margin<=0?"Нет синхронизации":"LOCK");
    }
    /** Two Ku carriers translated by the virtual GEO transponder by 2 GHz. */
    public static boolean carriers(int hubTx,int hubRx,int terminalTx,int terminalRx){return hubTx-terminalRx==2000&&terminalTx-hubRx==2000&&hubTx!=terminalTx&&hubTx>=13750&&hubTx<=14500&&terminalTx>=13750&&terminalTx<=14500;}
    public static final double DOWN_MBPS=25,UP_MBPS=3,ONE_WAY_MS=375;
}
