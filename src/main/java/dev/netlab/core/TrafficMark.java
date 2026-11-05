package dev.netlab.core;

/** Public trace hints only. Never contains bearer tickets or item components. */
public record TrafficMark(String phase,String item,int count) {
    public static final TrafficMark NONE=new TrafficMark("","",0);
    public TrafficMark {
        if(phase==null||!phase.matches("[A-Z_]{0,32}"))throw new IllegalArgumentException("Invalid trace phase");
        if(item==null||item.length()>256||!item.isEmpty()&&!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid trace item");
        if(count<0||count>("FLUID_DATA".equals(phase)?16000:64)||item.isEmpty()&&count!=0)throw new IllegalArgumentException("Invalid trace count");
    }
    public TrafficMark hop(String protocol,boolean tunnel){
        if(!(protocol.endsWith("TCP")||protocol.endsWith("UDP")))return NONE;
        if(tunnel&&protocol.endsWith("UDP"))return new TrafficMark("VPN","",0);
        return this;
    }
}
