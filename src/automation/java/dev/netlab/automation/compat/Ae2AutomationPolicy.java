package dev.netlab.automation.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/** Optional pack rule. No AE2 classes are linked when AE2 is absent. */
public final class Ae2AutomationPolicy {
    public static final ModConfigSpec CONFIG;
    public static final ModConfigSpec.BooleanValue DISABLE;
    public static final String MESSAGE = "Нет, используй CISCO :)";
    private static final Set<String> AUTOMATION_ITEMS = Set.of(
            "import_bus", "export_bus", "interface", "cable_interface", "pattern_provider", "cable_pattern_provider", "molecular_assembler",
            "io_port", "formation_plane", "annihilation_plane", "crafting_unit",
            "crafting_accelerator", "1k_crafting_storage", "4k_crafting_storage",
            "16k_crafting_storage", "64k_crafting_storage", "256k_crafting_storage");
    private static final Set<String> AUTOMATION_PARTS = Set.of(
            "ImportBusPart", "ExportBusPart", "InterfacePart", "PatternProviderPart",
            "FormationPlanePart", "AnnihilationPlanePart", "IdentityAnnihilationPlanePart");
    static {
        var builder = new ModConfigSpec.Builder();
        DISABLE = builder.comment("Pack rule: disable AE2 autocrafting and automatic item/fluid transfer.",
                "Manual ME terminals, cells, drives and manual terminal crafting remain available.",
                "Existing automation is paused without deleting its inventory. Restart after changing.",
                "AE2 is optional. Verified against AE2 19.2.18 for Minecraft 1.21.1.")
                .define("disableAe2Automation", false);
        CONFIG = builder.build();
    }
    private Ae2AutomationPolicy() {}

    public static boolean enabled() { return CONFIG.isLoaded() && DISABLE.get(); }

    public static void notifyPlayer(Player player) {
        if (player != null && !player.level().isClientSide)
            player.displayClientMessage(Component.literal(MESSAGE), false);
    }

    public static boolean automationItem(net.minecraft.resources.ResourceLocation id) {
        return id.getNamespace().equals("ae2") && AUTOMATION_ITEMS.contains(id.getPath());
    }

    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!enabled()) return;
        if (automationItem(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()))) {
            notifyPlayer(event.getEntity());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (event.getLevel().isClientSide) return;
        var state = event.getLevel().getBlockState(event.getPos());
        boolean blocked = automationItem(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        var host = event.getLevel().getBlockEntity(event.getPos());
        if (!blocked && host != null && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("ae2")) {
            try {
                // Hit-test the actual cable part; a terminal on the same cable stays usable.
                Class<?> type = Class.forName("appeng.api.parts.IPartHost");
                if (type.isInstance(host)) {
                    Object selected = type.getMethod("selectPartWorld", net.minecraft.world.phys.Vec3.class)
                            .invoke(host, event.getHitVec().getLocation());
                    Object part = selected.getClass().getField("part").get(selected);
                    for (Class<?> c = part == null ? null : part.getClass(); c != null; c = c.getSuperclass())
                        if (AUTOMATION_PARTS.contains(c.getSimpleName())) { blocked = true; break; }
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported AE2 part API", exception);
            }
        }
        // Keep wrench removal and inventory recovery available on existing machines.
        if (blocked) notifyPlayer(event.getEntity());
    }

    public static void opened(PlayerContainerEvent.Open event) {
        if (enabled() && event.getContainer().getClass().getName().startsWith("appeng.menu.me.crafting.Craft"))
            notifyPlayer(event.getEntity());
    }

    public static Player player(Object source) {
        if (source == null) return null;
        try { return (Player) ((Optional<?>) Ae2Api.PLAYER.invoke(source)).orElse(null); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException("Unsupported AE2 action source", exception); }
    }

    public static boolean denyTransfer(Object source) { return enabled() && player(source) == null; }
    public static Object slowTick() { return Ae2Api.SLOWER; }
    public static Object deniedJob() { return Ae2Api.CPU_OFFLINE; }

    /** Loaded only from an AE2 hook, never by ordinary NetLab startup without AE2. */
    private static final class Ae2Api {
        static final Method PLAYER;
        static final Object SLOWER, CPU_OFFLINE;
        static {
            try {
                PLAYER = Class.forName("appeng.api.networking.security.IActionSource").getMethod("player");
                SLOWER = Class.forName("appeng.api.networking.ticking.TickRateModulation").getField("SLOWER").get(null);
                CPU_OFFLINE = Class.forName("appeng.crafting.execution.CraftingSubmitResult").getField("CPU_OFFLINE").get(null);
            } catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
        }
    }
}
