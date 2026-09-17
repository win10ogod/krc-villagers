package dev.krcvillagers;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Handed-over items own the hand slots; vanilla trading/farming displays are cosmetic. */
public final class CompanionEquipment {
    public static void bind(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        if (!d.active()) return;
        if (!d.handsManaged) {
            for (int i = 0; i < 2; i++) {
                d.originalHands[i] = d.equipped || d.stage > 0 ? d.backup[i + 4]
                        : v.getItemBySlot(i == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                d.backup[i + 4] = ItemStack.EMPTY;
            }
            d.handsManaged = true;
        }
        for (int i = 0; i < 2; i++) {
            var slot = i == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            var assigned = d.items.getStackInSlot(6 + i);
            if (v.getItemBySlot(slot) != assigned) {
                v.stopUsingItem();
                v.setItemSlot(slot, assigned);
            }
        }
    }
    public static void release(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        v.stopUsingItem();
        if (d.handsManaged) for (int i = 0; i < 2; i++) {
            v.setItemSlot(i == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, d.originalHands[i]);
            d.originalHands[i] = ItemStack.EMPTY;
        }
        d.handsManaged = false;
    }
}
