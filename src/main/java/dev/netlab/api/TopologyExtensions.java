package dev.netlab.api;

import dev.netlab.*;
import dev.netlab.core.Network;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.*;

/** Addons add authenticated virtual links after the physical topology and clock are ready. */
public final class TopologyExtensions {
    public static final List<BiConsumer<Level,Network>> BUILD=new java.util.concurrent.CopyOnWriteArrayList<>();
    public interface Witness {BooleanSupplier get(ServerLevel level,Network.Traffic traffic);}
    public static final List<Witness> WITNESSES=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final List<Consumer<dev.netlab.core.Device>> PUBLIC_STATE=new java.util.concurrent.CopyOnWriteArrayList<>();
}
