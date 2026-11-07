package dev.netlab.mixin;

import dev.netlab.DeviceEntity;
import dev.netlab.compat.RackCarry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.BiFunction;

/** Carry On itself saves the block entity and removes it before removing the block (no duplicate drops). */
@Pseudo
@Mixin(targets="tschipp.carryon.common.carry.PickupHandler",remap=false)
public abstract class CarryOnPickupMixin {
    // Keep Carry On's distance and empty-hands checks before looking up any block entity.
    @Inject(method="tryPickUpBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",remap=false),cancellable=true,remap=false)
    private static void netlab$checkRack(ServerPlayer player, BlockPos pos, Level level,
            BiFunction<BlockState,BlockPos,Boolean> protection, CallbackInfoReturnable<Boolean> result) {
        if (level.getBlockEntity(pos) instanceof DeviceEntity host && RackCarry.rack(host)) {
            String reason=RackCarry.refusal(player,host);
            if (reason!=null) {
                player.displayClientMessage(Component.literal(reason),true);
                result.setReturnValue(false);
            }
        }
    }
}
