package dev.netlab.automation.mixin;

import dev.netlab.automation.compat.Ae2AutomationPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.me.service.CraftingService", remap = false)
public abstract class Ae2CraftingMixin {
    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, remap = false)
    private void netlab$denyJob(@Coerce Object plan, @Coerce Object requester, @Coerce Object cpu,
                               boolean prioritizePower, @Coerce Object source, CallbackInfoReturnable<Object> result) {
        if (Ae2AutomationPolicy.enabled()) {
            Ae2AutomationPolicy.notifyPlayer(Ae2AutomationPolicy.player(source));
            result.setReturnValue(Ae2AutomationPolicy.deniedJob());
        }
    }
}
