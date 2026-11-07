package dev.netlab.automation.mixin;

import dev.netlab.automation.compat.Ae2AutomationPolicy;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE2's external item capability; GUI slots use InternalInventory directly. */
@Pseudo
@Mixin(targets = "appeng.api.inventories.InternalInventoryItemHandler", remap = false)
public abstract class Ae2ExternalItemsMixin {
    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void netlab$rejectInsertion(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> result) {
        if (Ae2AutomationPolicy.enabled()) result.setReturnValue(stack);
    }
    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void netlab$rejectExtraction(CallbackInfoReturnable<ItemStack> result) {
        if (Ae2AutomationPolicy.enabled()) result.setReturnValue(ItemStack.EMPTY);
    }
}
