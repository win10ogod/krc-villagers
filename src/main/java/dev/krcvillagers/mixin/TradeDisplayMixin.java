package dev.krcvillagers.mixin;

import dev.krcvillagers.Companions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShowTradesToPlayer.class)
public abstract class TradeDisplayMixin {
    @Redirect(method = {"clearHeldItem", "displayAsHeldItem"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/Villager;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void kv$keepWeapon(Villager v, EquipmentSlot slot, ItemStack stack) {
        if (!Companions.active(v)) v.setItemSlot(slot, stack);
    }
}
