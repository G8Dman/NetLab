package dev.netlab.automation.core;

import dev.netlab.core.*;
import java.util.*;

/** Real installed cards; configuration memory never creates an upgrade or a cooler. */
public final class ServerHardware {
    public static final String ACCEL="NL-SERVER-ACCELERATOR",BUFFER="NL-SERVER-BUFFER",FAN="NL-SERVER-COOLER",PRO="NL-SERVER-COOLER-PRO";
    public static final List<String> MODULES=List.of(ACCEL,BUFFER,FAN,PRO);
    private static boolean registered;
    public static synchronized void register(){if(registered)return;registered=true;
        for(String id:MODULES)Hardware.CATALOG.put(id,new Hardware.Module(id,id.equals(ACCEL)?"SERVER-CPU":id.equals(BUFFER)?"SERVER-BUFFER":"SERVER-COOLING","application",0,0,false,0));
        var slots=new LinkedHashMap<String,String>();slots.put("accel0","SERVER-CPU");slots.put("buffer0","SERVER-BUFFER");slots.put("cooling0","SERVER-COOLING");Hardware.EXTRA_SLOTS.put("Automation Server",slots);
        PowerModel.EXTRA_DEMAND.put("server",ServerHardware::energy);
    }
    public static boolean has(Device d,String module){return d.kind.equals("server")&&Hardware.ensure(d).modules.containsValue(module);}
    public static int itemLimit(Device d){return has(d,BUFFER)?256:64;}
    public static int cooling(Device d){return has(d,PRO)?2:has(d,FAN)?1:0;}
    public static int energy(Device d){return 12+(has(d,ACCEL)?8:0)+(has(d,BUFFER)?4:0)+(cooling(d)==2?6:cooling(d)==1?2:0);}
    public static double idleHeat(Device d){return 2.2+(has(d,ACCEL)?3.8:0)+(has(d,BUFFER)?2.4:0);}
    public static String cooler(Device d){return switch(cooling(d)){case 2->"улучшенный";case 1->"обычный";default->"не установлен";};}
    /** Split a logical batch into bounded escrow stacks, never create oversized ItemStacks. */
    public static int part(int remaining){return Math.clamp(remaining,0,64);}
}
