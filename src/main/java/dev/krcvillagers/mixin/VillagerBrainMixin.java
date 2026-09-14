package dev.krcvillagers.mixin;
import dev.krcvillagers.Companions;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Villager.class)
public abstract class VillagerBrainMixin {
    @Inject(method="customServerAiStep",at=@At("HEAD"),cancellable=true)
    private void kv$control(CallbackInfo ci) { if(Companions.controlBrain((Villager)(Object)this)) ci.cancel(); }
}
