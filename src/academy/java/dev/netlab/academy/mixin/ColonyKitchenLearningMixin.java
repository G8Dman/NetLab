package dev.netlab.academy.mixin;
import dev.netlab.academy.IntegrationEvidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.minecolonies.core.colony.buildings.AbstractBuilding",remap=false)
public abstract class ColonyKitchenLearningMixin {
    @Inject(method="onUpgradeComplete",at=@At("RETURN"),remap=false,require=0)
    private void netlab$kitchen(@Coerce Object blueprint,int level,CallbackInfo ci){IntegrationEvidence.kitchen(this);}
}
