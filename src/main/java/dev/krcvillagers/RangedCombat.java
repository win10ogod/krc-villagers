package dev.krcvillagers;

import com.kelco.kamenridercraft.item.base_items.BaseBlasterItem;
import com.kelco.kamenridercraft.item.base_items.NeoBaseBlasterItem;
import dev.krcvillagers.mixin.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;

/** Native projectiles, ammo and enchantments; no player proxy or shared gun magazine. */
public final class RangedCombat {
    private static final Set<String> LEGACY_PROJECTILES = Set.of("ARROW", "SMALL_FIREBALL", "LARGE_FIREBALL",
            "DRAGON_FIREBALL", "EGG", "ENDER_PEARL", "WIND_CHARGE", "WITHER_SKULL", "FIREWORK");
    public static boolean weapon(ItemStack stack) {
        if (stack.getItem() instanceof NeoBaseBlasterItem gun) return ((NeoBlasterAccess)gun).kv$gunMode();
        if (stack.getItem() instanceof BaseBlasterItem gun) return LEGACY_PROJECTILES.contains(gun.getProjectile().name());
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }
    private static ItemStack ammo(Villager v, ItemStack stack, ProjectileWeaponItem weapon) {
        var d = v.getData(KrcVillagers.COMPANION);
        if (weapon.getSupportedHeldProjectiles(stack).test(d.items.getStackInSlot(7))) return d.items.getStackInSlot(7);
        for (int i = 8; i < 17; i++) if (weapon.getAllSupportedProjectiles(stack).test(d.items.getStackInSlot(i))) return d.items.getStackInSlot(i);
        return ItemStack.EMPTY;
    }
    public static void stop(Villager v) {
        v.getData(KrcVillagers.COMPANION).rangedCharge = 0;
        v.stopUsingItem();
    }
    public static boolean tick(Villager v, LivingEntity target) {
        var d = v.getData(KrcVillagers.COMPANION);
        var stack = v.getMainHandItem(); var item = stack.getItem();
        if (!weapon(stack)) { stop(v); return false; }
        boolean krc = item instanceof BaseBlasterItem || item instanceof NeoBaseBlasterItem;
        var ammo = !krc && item instanceof ProjectileWeaponItem projectile ? ammo(v, stack, projectile) : ItemStack.EMPTY;
        if (!krc && ammo.isEmpty() && !(item instanceof CrossbowItem && CrossbowItem.isCharged(stack))) { stop(v); return false; }
        if (d.rangedWeapon != stack) { stop(v); d.rangedWeapon = stack; }
        double distance = v.distanceToSqr(target);
        if (distance > 18 * 18 || !v.hasLineOfSight(target)) {
            stop(v);
            if (v.tickCount % 5 == 0 && target.level().getFluidState(target.blockPosition()).isEmpty()) v.getNavigation().moveTo(target, 1.1);
            return true;
        }
        if (distance >= 16) v.getNavigation().stop();
        // Keep a little space while still facing the enemy.
        if (distance < 16 && v.tickCount % 5 == 0) {
            var away = v.position().subtract(target.position()).normalize().scale(3).add(v.position());
            var pos = net.minecraft.core.BlockPos.containing(away);
            if (WaterSafety.dry(v, pos)) v.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            else v.getNavigation().stop();
        }
        if (v.tickCount < d.rangedCooldown || !clearShot(v, target)) { stop(v); return true; }
        long now = v.level().getGameTime();
        var state = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("krc_villagers_gun");
        if (krc && now < state.getLong("ReadyAt")) { stop(v); return true; }
        int charge = item instanceof NeoBaseBlasterItem gun ? ((NeoBlasterAccess)gun).kv$drawTime()
                : item instanceof BaseBlasterItem ? 1 : item instanceof CrossbowItem ? CrossbowItem.getChargeDuration(stack, v) : 20;
        boolean charged = item instanceof CrossbowItem && CrossbowItem.isCharged(stack);
        // KRC's onUseTick mutates fields on the global Item singleton. Do not run it for NPCs.
        if (!krc && !charged && !v.isUsingItem()) v.startUsingItem(InteractionHand.MAIN_HAND);
        if (!charged && ++d.rangedCharge < Math.max(1, charge)) return true;
        boolean firework = item instanceof CrossbowItem && (ammo.is(Items.FIREWORK_ROCKET)
                || stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).contains(Items.FIREWORK_ROCKET));
        boolean arrow = item instanceof BaseBlasterItem gun ? gun.getProjectile().name().equals("ARROW")
                : item instanceof BowItem || item instanceof CrossbowItem && !firework
                || item instanceof NeoBaseBlasterItem gun && ((NeoBlasterAccess)gun).kv$projectile().contains("arrow");
        float velocity = item instanceof CrossbowItem ? firework ? 1.6f : 3.15f : 3;
        var boost = v.getEffect(com.kelco.kamenridercraft.effects.EffectCore.SHOT_BOOST);
        if (boost != null && item instanceof BaseBlasterItem) velocity = 2 * (boost.getAmplifier() + 1);
        else if (boost != null && item instanceof NeoBaseBlasterItem) velocity = 4;
        aim(v, target, arrow, velocity);
        if (item instanceof NeoBaseBlasterItem gun) {
            var access = (NeoBlasterAccess)gun;
            int oldDraw = access.kv$drawTick();
            try { access.kv$drawTick(Math.max(1, access.kv$drawTime())); gun.fire(v, v.getLookAngle()); }
            finally { access.kv$drawTick(oldDraw); }
            magazine(stack, now, Math.max(1, access.kv$ammo()), access.kv$rate(), access.kv$reload());
            stack.hurtAndBreak(1, v, EquipmentSlot.MAINHAND);
        } else if (item instanceof BaseBlasterItem gun) {
            var access = (BlasterAccess)gun;
            if (gun.getProjectile().name().equals("ARROW")) {
                var arrowStack = new ItemStack(Items.ARROW); arrowStack.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
                ((ProjectileWeaponAccess)gun).kv$shoot((ServerLevel)v.level(), v, InteractionHand.MAIN_HAND, stack, List.of(arrowStack), velocity, 1 + 2 * access.kv$accuracy(), true, target);
            } else { gun.fire(v, v.getLookAngle()); stack.hurtAndBreak(1, v, EquipmentSlot.MAINHAND); }
            int capacity = access.kv$fireType().equals("burst") ? 2 : access.kv$fireType().equals("hold") ? 9 : 1;
            magazine(stack, now, capacity, 3, access.kv$cooldown());
            v.playSound(SoundEvents.BLAZE_SHOOT, 1, 1);
        } else if (item instanceof CrossbowItem crossbow) {
            if (!charged) {
                var loaded = ProjectileWeaponAccess.kv$draw(stack, ammo, v);
                if (loaded.isEmpty()) { stop(v); return true; }
                stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(loaded));
            }
            // The native mob-target overload compensates for slower pillager shots.
            // Use our aimed rotation with normal crossbow velocity instead.
            crossbow.performShooting(v.level(), v, InteractionHand.MAIN_HAND, stack, velocity, 1, null);
            d.rangedCooldown = v.tickCount + 10;
        } else if (item instanceof BowItem bow) {
            ((ProjectileWeaponAccess)bow).kv$shoot((ServerLevel)v.level(), v, InteractionHand.MAIN_HAND, stack,
                    ProjectileWeaponAccess.kv$draw(stack, ammo, v), 3, 1, true, target);
            v.playSound(SoundEvents.ARROW_SHOOT, 1, 1); d.rangedCooldown = v.tickCount + 10;
        }
        stop(v); v.swing(InteractionHand.MAIN_HAND);
        return true;
    }
    private static void magazine(ItemStack stack, long now, int capacity, int rate, int reload) {
        var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var state = custom.getCompound("krc_villagers_gun");
        int remaining = (state.contains("Ammo") ? state.getInt("Ammo") : capacity) - 1;
        state.putInt("Ammo", remaining <= 0 ? capacity : remaining);
        state.putLong("ReadyAt", now + Math.max(1, remaining <= 0 ? reload : rate));
        custom.put("krc_villagers_gun", state); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
    private static void aim(Villager v, LivingEntity target, boolean arrow, float velocity) {
        Vec3 delta = target.position().add(0, target.getBbHeight() * .55, 0).subtract(v.getEyePosition());
        double horizontal = delta.horizontalDistance();
        // Compensate gravity for native arrows at velocity 3.
        double rise = arrow ? horizontal * horizontal * .05 / (2 * velocity * velocity) : 0;
        float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        v.setYRot(yaw); v.setYHeadRot(yaw);
        v.setXRot((float)-Math.toDegrees(Math.atan2(delta.y + rise, horizontal)));
    }
    private static boolean clearShot(Villager v, LivingEntity target) {
        var start = v.getEyePosition(); var end = target.getBoundingBox().getCenter();
        for (var other : v.level().getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().expandTowards(end.subtract(start)).inflate(1)))
            if (other != v && other != target && !Companions.hostile(v, other)
                    && other.getBoundingBox().inflate(.3).clip(start, end).isPresent()) return false;
        return true;
    }
}
