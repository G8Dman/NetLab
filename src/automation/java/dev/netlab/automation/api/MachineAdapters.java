package dev.netlab.automation.api;

import com.google.gson.JsonObject;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/** Optional integrations add telemetry/actions without linking NetLab to a specific machine mod. */
public final class MachineAdapters {
    public interface Adapter {
        boolean matches(ServerLevel level,BlockPos pos,Direction face);
        JsonObject describe(ServerLevel level,BlockPos pos,Direction face);
        default String act(ServerLevel level,BlockPos pos,Direction face,String action){throw new IllegalArgumentException("Unsupported machine action");}
    }
    public static final List<Adapter> ADAPTERS=new CopyOnWriteArrayList<>();
    private MachineAdapters(){}
}
