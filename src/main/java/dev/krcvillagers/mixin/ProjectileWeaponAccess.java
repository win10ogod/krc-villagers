package dev.krcvillagers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;

@Mixin(ProjectileWeaponItem.class)
public interface ProjectileWeaponAccess {
    @Invoker("shoot") void kv$shoot(ServerLevel level, LivingEntity user, InteractionHand hand, ItemStack weapon,
            List<ItemStack> ammo, float velocity, float inaccuracy, boolean critical, LivingEntity target);
    @Invoker("draw") static List<ItemStack> kv$draw(ItemStack weapon, ItemStack ammo, LivingEntity user) { throw new AssertionError(); }
}
