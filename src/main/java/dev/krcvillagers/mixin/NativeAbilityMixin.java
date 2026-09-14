package dev.krcvillagers.mixin;
import com.kelco.kamenridercraft.abilities.AbilityUtil;
import com.kelco.kamenridercraft.attachments.AttachmentTypes;
import dev.krcvillagers.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=AbilityUtil.class,remap=false)
public abstract class NativeAbilityMixin {
    @Inject(method="useAbility",at=@At("HEAD"),cancellable=true)
    private static void kv$native(LivingEntity e,CallbackInfo ci) {
        if(e instanceof Villager v && Companions.active(v) && !v.level().isClientSide() && Skills.adaptNative(v,v.getData(AttachmentTypes.USED_ABILITY))) ci.cancel();
    }
}
