package dev.krcvillagers.mixin;
import com.example.generichenshin.compat.KrcCompat;
import com.kelco.kamenridercraft.world.attribute.KRCAttributes;
import dev.krcvillagers.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Field;
@Mixin(value=KrcCompat.class,remap=false)
public abstract class KrcCompatMixin {
    @Shadow private static Field abilityMeterField;
    @Shadow private static Field maxAbilityMeterField;
    /**
     * GH 0.1.139 finds KRC's ability meter reflectively as {@code world.attribute.Attributes}; KRC 1.1.5 renamed
     * that class to {@link KRCAttributes}. Without these fields GH skips its meter for every mob and rider skill.
     */
    @Inject(method="init",at=@At("TAIL"))
    private static void kv$renamedAttributes(CallbackInfo ci) {
        try {
            if(abilityMeterField==null) abilityMeterField=KRCAttributes.class.getField("ABILITY_METER");
            if(maxAbilityMeterField==null) maxAbilityMeterField=KRCAttributes.class.getField("MAX_ABILITY_METER");
        } catch(NoSuchFieldException e) {
            throw new IllegalStateException("Unexpected KRC attribute layout; update the compatibility adapter",e);
        }
    }
    @Inject(method="triggerAbility",at=@At("HEAD"),cancellable=true)
    private static void kv$ability(LivingEntity e,String skill,CallbackInfoReturnable<Boolean> cir) {
        if(e instanceof Villager v && Companions.active(v)) cir.setReturnValue(Skills.start(v,skill));
    }
    @Inject(method="ensureAbilityMeter",at=@At("HEAD"),cancellable=true)
    private static void kv$meter(LivingEntity e,CallbackInfoReturnable<Boolean> cir) {
        if(e instanceof Villager v && Companions.active(v)) cir.setReturnValue(v.getAttribute(KRCAttributes.ABILITY_METER)!=null);
    }
}
