package dev.netlab.core;

import java.util.*;

/** Three physical peers, most recently opened last. Authentication is never retained. */
public final class PhoneTerminalMemory {
    public static final int LIMIT=3,TEXT_LIMIT=24_000;
    public Map<String,ConsoleTranscript> devices=new LinkedHashMap<>();
    public ConsoleTranscript open(String identity){
        UUID.fromString(identity);if(devices==null)devices=new LinkedHashMap<>();
        var history=devices.remove(identity);if(history==null)history=new ConsoleTranscript();devices.put(identity,history);
        while(devices.size()>LIMIT)devices.remove(devices.keySet().iterator().next());return history;
    }
    public static void trim(ConsoleTranscript entry){if(entry.text.length()>TEXT_LIMIT)entry.text=entry.text.substring(entry.text.length()-TEXT_LIMIT);while(entry.history.size()>60)entry.history.removeFirst();}
}
