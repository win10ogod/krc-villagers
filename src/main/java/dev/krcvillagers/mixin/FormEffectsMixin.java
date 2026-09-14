package dev.krcvillagers.mixin;

import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import dev.krcvillagers.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RiderDriverItem.class, remap = false)
public abstract class FormEffectsMixin {
    @Inject(method = "giveEffects", at = @At("HEAD"), cancellable = true)
    private void kv$effects(LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof Villager v && Companions.active(v)) {
            FormEffects.apply(v, (RiderDriverItem)(Object)this); ci.cancel();
        }
    }
}
