package dev.netlab.automation.mixin;

import dev.netlab.automation.compat.Ae2AutomationPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"appeng.parts.automation.IOBusPart", "appeng.helpers.InterfaceLogic$Ticker",
        "appeng.helpers.patternprovider.PatternProviderLogic$Ticker",
        "appeng.blockentity.crafting.MolecularAssemblerBlockEntity",
        "appeng.blockentity.storage.IOPortBlockEntity", "appeng.parts.automation.AnnihilationPlanePart"}, remap = false)
public abstract class Ae2TickMixin {
    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true, remap = false)
    private void netlab$pause(CallbackInfoReturnable<Object> result) {
        // SLOWER, rather than SLEEP: turning the pack rule off also resumes existing nodes.
        if (Ae2AutomationPolicy.enabled()) result.setReturnValue(Ae2AutomationPolicy.slowTick());
    }
}
