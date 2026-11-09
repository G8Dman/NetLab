package dev.netlab.academy.mixin;
import dev.netlab.academy.IntegrationEvidence;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="appeng.menu.me.common.MEStorageMenu",remap=false)
public abstract class Ae2ManualLearningMixin {
    @Unique private Object netlab$manualBefore;
    @Inject(method="handleNetworkInteraction",at=@At("HEAD"),remap=false,require=0)
    private void netlab$begin(ServerPlayer player,@Coerce Object key,@Coerce Object action,CallbackInfo ci){netlab$manualBefore=IntegrationEvidence.manualStart(this,player,key);}
    @Inject(method="handleNetworkInteraction",at=@At("RETURN"),remap=false,require=0)
    private void netlab$end(ServerPlayer player,@Coerce Object key,@Coerce Object action,CallbackInfo ci){IntegrationEvidence.manualEnd(this,player,key,netlab$manualBefore);netlab$manualBefore=null;}
}
