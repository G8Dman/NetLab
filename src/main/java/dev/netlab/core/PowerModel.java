package dev.netlab.core;

import java.util.*;

/** Integer FE accounting, distinct from Ethernet; no energy is created by a cabinet or PSU. */
public final class PowerModel {
    public static final Map<String,java.util.function.ToIntFunction<Device>> EXTRA_DEMAND=new java.util.concurrent.ConcurrentHashMap<>();
    public static final int CAPACITY=1_000_000,INPUT_RATE=10_000,PSU_RATE=256;
    public static boolean board(Device d){return d!=null&&(Set.of("router","switch","traffic","server","endpoint","sniffer","ap").contains(d.kind)||DeviceKinds.energy(d.kind)>0);}
    public static int demand(Device d){if(!Hardware.ensure(d).powered)return 0;var extra=EXTRA_DEMAND.get(d.kind);if(extra!=null)return extra.applyAsInt(d);if(DeviceKinds.energy(d.kind)>0)return DeviceKinds.energy(d.kind);return switch(d.kind){case "router"->8;case "switch"->6;case "server"->12;case "endpoint","traffic"->2;case "ap"->4;default->3;};}
    public static int receive(int stored,int amount,int budget){if(amount<=0)return 0;return Math.max(0,Math.min(amount,Math.min(CAPACITY-Math.clamp(stored,0,CAPACITY),budget)));}
    public static final class Budget {
        public int energy;
        private final Map<String,Integer> used=new HashMap<>();
        public Budget(int energy){this.energy=Math.max(0,energy);}
        public int draw(String psu,int maximum){int value=Math.max(0,Math.min(maximum,Math.min(energy,PSU_RATE-used.getOrDefault(psu,0))));if(value>0){energy-=value;used.merge(psu,value,Integer::sum);}return value;}
        public boolean supply(String psu,int demand){int spent=used.getOrDefault(psu,0);if(demand<0||demand>PSU_RATE||energy<Math.max(1,demand)||spent+demand>PSU_RATE)return false;energy-=demand;used.put(psu,spent+demand);return true;}
    }
}
