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

/** Applies KRC form effects while preserving periodic callbacks and external potions. */
public final class FormEffects {
    public static void apply(Villager v, RiderDriverItem belt) {
        if (!belt.isTransformed(v) || v.level().isClientSide()) return;
        var stack = v.getItemBySlot(EquipmentSlot.FEET);
        double transforming = v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.IS_TRANSFORMING).getBaseValue();
        boolean saturated = false;
        for (int slot = 1; slot <= belt.numBaseFormItems; slot++) {
            var form = RiderDriverItem.getFormItem(stack, slot, transforming);
            var attack = RiderDriverItem.getFormItem(stack, slot);
            for (var effect : form.getPotionEffectList()) {
                refresh(v, effect);
                saturated |= effect.is(MobEffects.SATURATION);
            }
            if (attack.GetIsAttackForm()) for (var effect : attack.getPotionEffectList()) {
                refresh(v, effect);
                saturated |= effect.is(MobEffects.SATURATION);
            }
        }
        // Gaim cores grant SATURATION. Villagers have no FoodData: bridge a well-fed
        // player's natural regeneration cadence while an actual armor grants this effect.
        var d = v.getData(KrcVillagers.COMPANION);
        long now = v.level().getGameTime();
        if (d.lastSaturationTick != now) {
            d.lastSaturationTick = now;
            if (saturated && !d.downed && v.getHealth() < v.getMaxHealth()
                    && v.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION)) {
                if (++d.saturationHealTicks >= 10) { v.heal(1); d.saturationHealTicks = 0; }
            } else d.saturationHealTicks = 0;
        }
    }
    private static void refresh(Villager v, MobEffectInstance effect) {
        // Let periodic effects count down: refreshing a 45-tick regeneration effect every
        // tick prevents its 50/25/12-tick healing callback from ever running.
        int duration = Math.max(200, effect.getDuration());
        if (effect.is(MobEffects.REGENERATION)) duration = Math.max(1, 50 >> effect.getAmplifier()) * 4;
        if (effect.is(EffectCore.FORM_TIMEOUT) || effect.is(EffectCore.FORM_LOCK)) duration = effect.getDuration();
        else if (effect.is(MobEffects.NIGHT_VISION)) duration = 305;
        else if (effect.is(EffectCore.SMALL) || effect.is(EffectCore.BIG)) duration = 1;
        var d = v.getData(KrcVillagers.COMPANION);
        String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString();
        var current = v.getEffect(effect.getEffect());
        if (current != null && (current.getAmplifier() > effect.getAmplifier()
                || current.getAmplifier() == effect.getAmplifier() && (current.isInfiniteDuration() || current.getDuration() > duration))) return;
        int renewAt = effect.is(MobEffects.NIGHT_VISION) ? 300 : 1;
        if (effect.is(EffectCore.FORM_TIMEOUT) || effect.is(EffectCore.FORM_LOCK)) renewAt = duration;
        if (current != null && current.getAmplifier() == effect.getAmplifier() && current.getDuration() > renewAt) return;
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
                    && (!current.isInfiniteDuration() || expected.getInt("Duration") == -1)
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
        d.saturationHealTicks = 0;
    }
}
