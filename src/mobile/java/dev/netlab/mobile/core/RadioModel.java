package dev.netlab.mobile.core;

import java.util.*;

/** Bounded educational propagation model; values are game balance, not a field RF prediction. */
public final class RadioModel {
    public record Site(String id,double x,double y,double z,int band,int channel,double azimuth,double tilt,double height,double range,double power){}
    public record Signal(Site site,double distance,double rsrp,double sinr,int bars,double quality){}
    public static double received(Site s,double x,double y,double z,double obstruction){
        double dx=x-s.x,dz=z-s.z,d=Math.max(1,Math.sqrt(dx*dx+dz*dz+Math.pow(y-s.y,2)));double bearing=(Math.toDegrees(Math.atan2(-dx,dz))+360)%360;
        double angle=Math.abs((bearing-s.azimuth+540)%360-180);double vertical=Math.toDegrees(Math.atan2(s.y-y,Math.max(1,Math.hypot(dx,dz))));
        return -28-30*Math.log10(d)+12-Math.min(32,12*Math.pow(angle/65,2))-Math.min(22,12*Math.pow((vertical-s.tilt)/25,2))+Math.min(8,Math.max(0,s.height-12)/4)-(s.band==3?7:0)-Math.clamp(obstruction,0,45)+s.power;
    }
    public static Signal signal(Site s,List<Site> sites,double x,double y,double z,double obstruction){
        double distance=Math.sqrt(Math.pow(x-s.x,2)+Math.pow(y-s.y,2)+Math.pow(z-s.z,2));double rsrp=received(s,x,y,z,obstruction),interference=Math.pow(10,-120/10.0);
        for(var other:sites)if(!other.id.equals(s.id)&&other.band==s.band&&other.channel==s.channel)interference+=Math.pow(10,received(other,x,y,z,0)/10);
        double sinr=Math.clamp(rsrp-10*Math.log10(interference),-30,35);int bars=rsrp>=-80?5:rsrp>=-90?4:rsrp>=-100?3:rsrp>=-110?2:rsrp>=-118?1:0;
        if(s.height<12||distance>s.range||sinr< -8)bars=0;
        return new Signal(s,distance,rsrp,sinr,bars,bars==0?0:Math.clamp((sinr+8)/30,.08,1));
    }
    public static boolean shouldReselect(Signal current,Signal candidate,long betterTicks){return current==null||current.bars==0||!current.site.id.equals(candidate.site.id)&&candidate.rsrp>=current.rsrp+3&&betterTicks>=40;}
    public static double rate(Signal signal,boolean down,int active){return (down?5:1)*signal.quality/Math.max(1,active);}
}
