package dev.netlab.client;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
public final class DesktopApps {
    public record App(String module,String title,java.util.function.BiConsumer<BlockPos,Screen> open){}
    public static final List<App> APPS=new java.util.concurrent.CopyOnWriteArrayList<>();
}
