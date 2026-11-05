package dev.netlab.core;

import java.util.*;

/** Optional device capabilities, registered by addons before worlds are opened. */
public final class DeviceKinds {
    public record Features(boolean memory,boolean console,boolean ports,int energy,boolean router){}
    public static final Map<String,Features> EXTRA=new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean memory(String kind){return Set.of("router","switch","traffic","ap","server","endpoint","sniffer").contains(kind)||EXTRA.containsKey(kind)&&EXTRA.get(kind).memory();}
    public static boolean console(String kind){return Set.of("router","switch","traffic","pc","server","endpoint","sniffer").contains(kind)||EXTRA.containsKey(kind)&&EXTRA.get(kind).console();}
    public static boolean ports(String kind){return Set.of("router","switch","traffic","pc","server","endpoint","sniffer").contains(kind)||EXTRA.containsKey(kind)&&EXTRA.get(kind).ports();}
    public static boolean router(String kind){return kind.equals("router")||EXTRA.containsKey(kind)&&EXTRA.get(kind).router();}
    public static int energy(String kind){return EXTRA.containsKey(kind)?EXTRA.get(kind).energy():0;}
}
