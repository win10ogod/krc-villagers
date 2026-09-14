package dev.krcvillagers.mixin;
import com.example.generichenshin.service.KickService;
import dev.krcvillagers.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=KickService.class,remap=false)
public abstract class KickEligibilityMixin {
    @Inject(method="isRiderMob",at=@At("HEAD"),cancellable=true)
    private static void kv$eligible(LivingEntity entity,CallbackInfoReturnable<Boolean> cir) {
        if(entity instanceof Villager v) cir.setReturnValue(Companions.active(v)&&Henshin.ready(v));
    }
}
