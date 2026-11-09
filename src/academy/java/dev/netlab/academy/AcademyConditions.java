package dev.netlab.academy;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.*;
final class AcademyConditions {
    private static final DeferredRegister<MapCodec<? extends ICondition>> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS,"netlabacademy");
    public record Ae2Policy() implements ICondition {
        static final MapCodec<Ae2Policy> CODEC=MapCodec.unit(Ae2Policy::new);
        public boolean test(IContext context){return Academy.policy().ae2();}
        public MapCodec<? extends ICondition> codec(){return CODEC;}
    }
    static void register(IEventBus bus){TYPES.register("ae2_policy",()->Ae2Policy.CODEC);TYPES.register(bus);}
}
