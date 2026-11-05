package dev.netlab.core;

import java.util.*;

/** Server and screen use the same catalogue and material costs. */
public final class ModuleFabrication {
    public static final List<String> MODULES=Hardware.BUILTIN_MODULES;
    public record Cost(int boards,int copper,int redstone){
        public boolean available(int boards,int copper,int redstone){return boards>=this.boards&&copper>=this.copper&&redstone>=this.redstone;}
    }
    public static Cost cost(String id){
        var m=Hardware.CATALOG.get(id);if(m==null)throw new IllegalArgumentException("Unknown module: "+id);
        int size=switch(m.format()){case "NM","NIM"->2;default->1;};
        int boards=Math.min(8,size+(Math.max(1,m.ports())-1)/4+m.wicSlots());
        int copper=Math.min(16,1+(m.ports()+1)/2+(m.mbps()>=1000?1:0));
        int redstone=Math.min(16,1+boards+(m.switching()?1:0)+(m.medium().equals("wireless")?1:0));
        return new Cost(boards,copper,redstone);
    }
}
