package dev.netlab.automation.mixin;

import dev.netlab.automation.compat.Ae2AutomationPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.parts.automation.FormationPlanePart", remap = false)
public abstract class Ae2FormationMixin {
    @Inject(method = "placeInWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void netlab$denyPlacement(CallbackInfoReturnable<Long> result) {
        if (Ae2AutomationPolicy.enabled()) result.setReturnValue(0L);
    }
}
