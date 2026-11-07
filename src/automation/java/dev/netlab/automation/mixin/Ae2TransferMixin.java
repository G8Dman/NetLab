package dev.netlab.automation.mixin;

import dev.netlab.automation.compat.Ae2AutomationPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers items and fluids, subnetworks and machine requests from other mods. */
@Pseudo
@Mixin(targets = {"appeng.me.storage.NetworkStorage", "appeng.blockentity.storage.MEChestBlockEntity$ChestMonitorHandler",
        "appeng.helpers.InterfaceLogic$InterfaceInventory"}, remap = false)
public abstract class Ae2TransferMixin {
    @Inject(method = {"insert", "extract"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void netlab$manualOnly(@Coerce Object key, long amount, @Coerce Object mode,
                                  @Coerce Object source, CallbackInfoReturnable<Long> result) {
        if (Ae2AutomationPolicy.denyTransfer(source)) result.setReturnValue(0L);
    }
}
