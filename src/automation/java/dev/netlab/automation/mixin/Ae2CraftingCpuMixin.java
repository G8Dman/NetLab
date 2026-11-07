package dev.netlab.automation.mixin;

import dev.netlab.automation.compat.Ae2AutomationPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "appeng.crafting.execution.CraftingCpuLogic", remap = false)
public abstract class Ae2CraftingCpuMixin {
    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), cancellable = true, remap = false)
    private void netlab$pauseExistingJob(CallbackInfo callback) {
        if (Ae2AutomationPolicy.enabled()) callback.cancel();
    }
}
