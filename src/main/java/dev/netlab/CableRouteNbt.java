package dev.netlab;

import dev.netlab.core.WallCablePath;
import net.minecraft.nbt.*;
import java.util.*;

/** The same bounded route format is used by data and power cables. */
public final class CableRouteNbt {
    public static void write(CompoundTag tag,List<WallCablePath.Point> anchors){
        var points=new ListTag();for(var p:anchors){var t=new CompoundTag();t.putDouble("x",p.x());t.putDouble("y",p.y());t.putDouble("z",p.z());points.add(t);}tag.put("anchors",points);
    }
    /** Missing route data is the compatible direct connection; null denotes corrupt data. */
    public static List<WallCablePath.Point> read(CompoundTag tag){
        if(tag.contains("anchors")&&!tag.contains("anchors",Tag.TAG_LIST))return null;
        var list=tag.getList("anchors",Tag.TAG_COMPOUND);if(list.size()>WallCablePath.MAX_ANCHORS)return null;
        var points=new ArrayList<WallCablePath.Point>();for(var entry:list){var t=(CompoundTag)entry;
            if(!t.contains("x",Tag.TAG_ANY_NUMERIC)||!t.contains("y",Tag.TAG_ANY_NUMERIC)||!t.contains("z",Tag.TAG_ANY_NUMERIC))return null;
            var point=new WallCablePath.Point(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));if(!point.finite())return null;points.add(point);
        }return List.copyOf(points);
    }
}
