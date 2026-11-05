package dev.netlab.automation.core;

import dev.netlab.core.Device;
import java.util.*;

/** Successful server addresses only: bounded, deduplicated, and never a credential store. */
public final class ServerHistory {
    public static final int LIMIT=16;
    public static List<String> clean(Collection<String> addresses){
        var result=new LinkedHashSet<String>();
        for(String address:addresses){if(result.size()==LIMIT)break;try{Device.ip(address);result.add(address);}catch(IllegalArgumentException|NullPointerException ignored){}}
        return List.copyOf(result);
    }
    public static List<String> success(Collection<String> previous,String address){Device.ip(address);var all=new ArrayList<String>();all.add(address);all.addAll(previous);return clean(all);}
}
