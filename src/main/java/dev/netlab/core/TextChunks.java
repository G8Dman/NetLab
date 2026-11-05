package dev.netlab.core;

import java.util.*;

/** NBT strings use modified UTF-8 with a 65535-byte limit; split by bounded UTF-16 units. */
public final class TextChunks {
    public static List<String> split(String value){var out=new ArrayList<String>();for(int i=0;i<value.length();i+=16000)out.add(value.substring(i,Math.min(value.length(),i+16000)));return out;}
}
