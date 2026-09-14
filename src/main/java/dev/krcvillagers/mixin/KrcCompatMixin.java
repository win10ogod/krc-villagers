package dev.krcvillagers.mixin;
import com.example.generichenshin.compat.KrcCompat;
import dev.krcvillagers.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=KrcCompat.class,remap=false)
public abstract class KrcCompatMixin {
    @Inject(method="triggerAbility",at=@At("HEAD"),cancellable=true)
    private static void kv$ability(LivingEntity e,String skill,CallbackInfoReturnable<Boolean> cir) {
        if(e instanceof Villager v && Companions.active(v)) cir.setReturnValue(Skills.start(v,skill));
    }
    @Inject(method="ensureAbilityMeter",at=@At("HEAD"),cancellable=true)
    private static void kv$meter(LivingEntity e,CallbackInfoReturnable<Boolean> cir) {
        if(e instanceof Villager v && Companions.active(v)) cir.setReturnValue(v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER)!=null);
    }
}
