package dev.netlab.mixin;

import dev.netlab.client.DisconnectMessages;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla disconnect constructor delegates here, including login failures. */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin {
    @Shadow @Final @Mutable private DisconnectionDetails details;

    @Inject(method = "<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/DisconnectionDetails;Lnet/minecraft/network/chat/Component;)V", at = @At("RETURN"))
    private void netlab$readableServiceError(Screen parent, Component title,
            DisconnectionDetails details, Component buttonText, CallbackInfo callback) {
        this.details = DisconnectMessages.readable(details);
    }
}
