package dev.netlab;

import dev.netlab.core.TextChunks;
import net.minecraft.nbt.*;

/** Large saved transcripts/policy registries must not overflow a single NBT string. */
public final class StoredJson {
    public static void put(CompoundTag tag,String key,String json){var list=new ListTag();for(String part:TextChunks.split(json))list.add(StringTag.valueOf(part));tag.put(key,list);}
    public static String get(CompoundTag tag,String key){if(tag.contains(key,Tag.TAG_STRING))return tag.getString(key);var out=new StringBuilder();for(var part:tag.getList(key,Tag.TAG_STRING))out.append(part.getAsString());return out.toString();}
}
