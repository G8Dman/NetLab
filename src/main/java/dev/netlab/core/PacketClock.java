package dev.netlab.core;

import java.util.*;

/** Nanosecond link serialization/propagation clock. A directed egress queue models full duplex. */
public final class PacketClock {
    public record Slot(long start,long finish){}
    private final Map<Network.Endpoint,Long> busy=new HashMap<>();
    public Slot send(Network.Endpoint out,long ready,int bytes,double mbps,double length,double latencyMs,double speed){
        if(!Double.isFinite(speed)||speed<.000001||speed>1)throw new IllegalArgumentException("Speed must be 0.000001..1");
        long start=Math.max(ready,busy.getOrDefault(out,ready));
        long serialization=Math.max(1,(long)Math.ceil(Math.max(64,bytes)*8_000.0/Math.max(.001,mbps)/speed));
        long propagation=Math.max(1,(long)Math.ceil((Math.max(0,length)*5+Math.max(0,latencyMs)*1_000_000)/speed));
        long finish=Math.addExact(Math.addExact(start,serialization),propagation);if(finish-ready>3_600_000_000_000L)throw new IllegalArgumentException("Задержка пакета/очереди превышает час");busy.put(out,start+serialization);
        if(busy.size()>8192)busy.entrySet().removeIf(e->e.getValue()<ready-1_000_000_000L);
        return new Slot(start,finish);
    }
}
