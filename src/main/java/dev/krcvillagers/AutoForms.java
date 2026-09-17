package dev.krcvillagers;

import com.kelco.kamenridercraft.effects.EffectCore;
import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import com.kelco.kamenridercraft.item.base_items.RiderFormChangeItem;
import dev.krcvillagers.mixin.FormRequirements;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Chooses reachable forms from items actually entrusted to this villager. */
public final class AutoForms {
    private static List<RiderFormChangeItem> available(VillagerCompanionData d, RiderDriverItem belt) {
        var result = new LinkedHashSet<RiderFormChangeItem>();
        result.add(belt.baseFormItem);
        for (int i = 0; i < d.items.getSlots(); i++) {
            var stack = d.items.getStackInSlot(i);
            collect(stack, belt, result);
            var contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) for (var held : contents.nonEmptyItems()) collect(held, belt, result);
            var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) for (var held : bundle.items()) collect(held, belt, result);
        }
        return List.copyOf(result);
    }
    private static void collect(ItemStack stack, RiderDriverItem belt, Set<RiderFormChangeItem> result) {
        if (stack.getItem() instanceof RiderFormChangeItem form && form.isCompatible(belt) && !form.GetIsAttackForm()) result.add(form);
    }
    private static boolean reach(VillagerCompanionData d, ItemStack candidate, RiderDriverItem belt, RiderFormChangeItem form,
                                 List<RiderFormChangeItem> owned, Set<RiderFormChangeItem> visiting) {
        if (!owned.contains(form) || !form.isCompatible(belt) || !visiting.add(form)) return false;
        try {
            if (RiderDriverItem.getFormItem(candidate, form.getSlot()) == form)
                return Forms.requirements(d, candidate, belt, form).isEmpty();
            var req = (FormRequirements)form;
            if (req.kv$needBase() && RiderDriverItem.getFormItem(candidate, 1) != belt.baseFormItem
                    && !reach(d, candidate, belt, belt.baseFormItem, owned, visiting)) return false;
            var needs = Arrays.asList(req.kv$needOne(), req.kv$needTwo(), req.kv$needThree(), req.kv$needFour());
            for (int i = 0; i < needs.size(); i++) if (needs.get(i) != null && RiderDriverItem.getFormItem(candidate, i + 1) != needs.get(i)
                    && !reach(d, candidate, belt, needs.get(i), owned, visiting)) return false;
            if (!Forms.requirements(d, candidate, belt, form).isEmpty()) return false;
            Forms.apply(candidate, belt, form);
            return true;
        } finally { visiting.remove(form); }
    }
    private static List<MobEffectInstance> effects(ItemStack stack, RiderDriverItem belt) {
        var effects = new ArrayList<MobEffectInstance>();
        for (int slot = 1; slot <= belt.numBaseFormItems; slot++)
            effects.addAll(RiderDriverItem.getFormItem(stack, slot).getPotionEffectList());
        return effects;
    }
    private static boolean healing(Villager v, ItemStack stack, RiderDriverItem belt) {
        return effects(stack, belt).stream().anyMatch(e -> e.is(MobEffects.REGENERATION) || e.is(MobEffects.SATURATION)
                && v.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION));
    }
    public static boolean needsRecovery(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION); var belt = Henshin.belt(v);
        if (belt == null || v.getHealth() >= v.getMaxHealth()) return false;
        if (healing(v, d.items.getStackInSlot(0), belt)) return true;
        if (!d.autoForms) return false;
        var owned = available(d, belt);
        for (var form : owned) {
            var candidate = d.items.getStackInSlot(0).copy();
            if (reach(d, candidate, belt, form, owned, new HashSet<>()) && healing(v, candidate, belt)) return true;
        }
        return false;
    }
    private static double score(Villager v, ItemStack stack, RiderDriverItem belt) {
        double score = 0; boolean hurt = v.getHealth() < v.getMaxHealth(); boolean combat = v.getTarget() != null;
        var strongest = new HashMap<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, MobEffectInstance>();
        for (var effect : effects(stack, belt)) strongest.merge(effect.getEffect(), effect,
                (a, b) -> a.getAmplifier() >= b.getAmplifier() ? a : b);
        for (var e : strongest.values()) {
            int power = e.getAmplifier() + 1;
            if (e.is(MobEffects.REGENERATION) && hurt) score += 80 * power;
            else if (e.is(MobEffects.SATURATION) && hurt && v.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION)) score += 70;
            else if (e.is(MobEffects.WATER_BREATHING) && v.isInWater()) score += 180;
            else if (e.is(MobEffects.FIRE_RESISTANCE) && v.isOnFire()) score += 160;
            else if (e.is(MobEffects.DAMAGE_RESISTANCE) && combat) score += 12 * power;
            else if (e.is(MobEffects.DAMAGE_BOOST) && combat) score += 10 * power;
            else if (e.is(EffectCore.SHOT_BOOST) && combat && RangedCombat.weapon(v.getMainHandItem())) score += 16 * power;
            else if (e.is(MobEffects.MOVEMENT_SPEED) && combat) score += 2 * power;
            else if (!e.getEffect().value().isBeneficial()) score -= 30 * power;
        }
        return score;
    }
    public static boolean tick(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION); var belt = Henshin.belt(v);
        if (!d.autoForms || !Henshin.ready(v) || belt == null || d.timeStopOwned || v.level().getGameTime() < d.nextFormSwitch
                || v.hasEffect(EffectCore.FORM_LOCK)
                || !v.getData(com.kelco.kamenridercraft.attachments.AttachmentTypes.USED_ABILITY).isEmpty()) return false;
        var current = d.items.getStackInSlot(0); var best = current;
        double bestScore = score(v, current, belt);
        var owned = available(d, belt);
        for (var form : owned) {
            var candidate = current.copy();
            if (!reach(d, candidate, belt, form, owned, new HashSet<>())) continue;
            double score = score(v, candidate, belt);
            if (score > bestScore + .01) { best = candidate; bestScore = score; }
        }
        if (best == current) return false;
        FormEffects.clear(v); RangedCombat.stop(v); Skills.cleanup(v);
        d.items.setStackInSlot(0, best); v.setItemSlot(EquipmentSlot.FEET, best);
        RiderDriverItem.setUpdateForm(best);
        d.nextFormSwitch = v.level().getGameTime() + 100;
        d.stage = 1;
        var timing = com.example.generichenshin.config.HenshinTimingConfig.rider(Henshin.rider(v));
        d.animation = timing.animationId(); d.animationTicks = 0;
        Protocol.sync(v); Protocol.animate(v, d.animation, 0);
        return true;
    }
}
