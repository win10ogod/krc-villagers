package dev.krcvillagers;

import com.kelco.kamenridercraft.effects.EffectCore;
import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;

/** Uses the same form effects and durations as a KRC player without the non-player filter. */
public final class FormEffects {
    public static void apply(Villager v, RiderDriverItem belt) {
        if (!belt.isTransformed(v) || v.level().isClientSide()) return;
        var stack = v.getItemBySlot(EquipmentSlot.FEET);
        double transforming = v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.IS_TRANSFORMING).getBaseValue();
        for (int slot = 1; slot <= belt.numBaseFormItems; slot++) {
            var form = RiderDriverItem.getFormItem(stack, slot, transforming);
            var attack = RiderDriverItem.getFormItem(stack, slot);
            for (var effect : form.getPotionEffectList()) refresh(v, effect);
            if (attack.GetIsAttackForm()) for (var effect : attack.getPotionEffectList()) refresh(v, effect);
        }
    }
    private static void refresh(Villager v, MobEffectInstance effect) {
        int duration = 45;
        if (effect.is(EffectCore.FORM_TIMEOUT) || effect.is(EffectCore.FORM_LOCK)) duration = effect.getDuration();
        else if (effect.is(MobEffects.NIGHT_VISION)) duration = 305;
        else if (effect.is(EffectCore.SMALL) || effect.is(EffectCore.BIG)) duration = 1;
        var d = v.getData(KrcVillagers.COMPANION);
        String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString();
        var current = v.getEffect(effect.getEffect());
        if (current != null && (current.getAmplifier() > effect.getAmplifier()
                || current.getAmplifier() == effect.getAmplifier() && current.getDuration() > duration)) return;
        if (d.formEffects.add(id) && current != null) {
            var saved = new CompoundTag(); saved.put("Effect", current.save());
            saved.putLong("CapturedAt", v.level().getGameTime()); d.priorEffects.put(id, saved);
        }
        v.addEffect(new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), true, false));
        var expected = new CompoundTag(); expected.putInt("Amplifier", effect.getAmplifier()); expected.putInt("Duration", duration);
        d.appliedEffects.put(id, expected);
    }
    public static void clear(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        for (String id : d.formEffects) BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(id)).ifPresent(holder -> {
            var current = v.getEffect(holder); var expected = d.appliedEffects.getCompound(id);
            if (current != null && current.getAmplifier() == expected.getInt("Amplifier")
                    && current.getDuration() <= expected.getInt("Duration") && current.isAmbient() && !current.isVisible())
                v.removeEffect(holder);
            if (d.priorEffects.contains(id)) {
                var saved = d.priorEffects.getCompound(id); var before = MobEffectInstance.load(saved.getCompound("Effect"));
                if (before != null) {
                    int remaining = before.getDuration() - (int)(v.level().getGameTime() - saved.getLong("CapturedAt"));
                    if (before.isInfiniteDuration() || remaining > 0) v.addEffect(new MobEffectInstance(holder,
                            before.isInfiniteDuration() ? -1 : remaining, before.getAmplifier(), before.isAmbient(), before.isVisible(), before.showIcon()));
                }
            }
        });
        d.formEffects.clear(); d.priorEffects = new CompoundTag(); d.appliedEffects = new CompoundTag();
    }
}
