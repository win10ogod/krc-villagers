package dev.krcvillagers;

import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import com.kelco.kamenridercraft.item.base_items.RiderFormChangeItem;
import dev.krcvillagers.mixin.FormRequirements;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Applies KRC's declarative form transitions to the actual belt, atomically. */
public final class Forms {
    public static String install(VillagerCompanionData d, RiderDriverItem belt) {
        ItemStack candidate = d.items.getStackInSlot(0).copy();
        var pending = new ArrayList<RiderFormChangeItem>();
        for (int slot = 1; slot <= 5; slot++) {
            var stack = d.items.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof RiderFormChangeItem form) || !form.isCompatible(belt))
                return "message.krc_villagers.incompatible";
            if (form.getSlot() != slot) return "message.krc_villagers.wrong_slot";
            pending.add(form);
        }
        // A supplied prerequisite in another slot can be installed before its dependent form.
        while (!pending.isEmpty()) {
            boolean progressed = false;
            String problem = "message.krc_villagers.missing_form";
            for (var iterator = pending.iterator(); iterator.hasNext();) {
                var form = iterator.next();
                problem = requirements(d, candidate, belt, form);
                if (!problem.isEmpty()) continue;
                if (RiderDriverItem.getFormItem(candidate, form.getSlot()) != form) apply(candidate, belt, form);
                iterator.remove(); progressed = true;
            }
            if (!progressed) return problem;
        }
        d.items.setStackInSlot(0, candidate);
        return "";
    }
    static String requirements(VillagerCompanionData d, ItemStack stack, RiderDriverItem belt, RiderFormChangeItem form) {
        var req = (FormRequirements) form;
        for (Item item : req.kv$neededItems()) if (!contains(d, item)) return "message.krc_villagers.missing_items";
        for (Item item : form.needItemList) if (!contains(d, item)) return "message.krc_villagers.missing_items";
        // Already installed forms do not have to re-enter their previous form on every henshin.
        if (RiderDriverItem.getFormItem(stack, form.getSlot()) == form) return "";
        if (req.kv$needBase() && RiderDriverItem.getFormItem(stack, 1) != belt.baseFormItem)
            return "message.krc_villagers.missing_form";
        var needs = Arrays.asList(req.kv$needOne(), req.kv$needTwo(), req.kv$needThree(), req.kv$needFour());
        for (int i = 0; i < needs.size(); i++)
            if (needs.get(i) != null && RiderDriverItem.getFormItem(stack, i + 1) != needs.get(i))
                return "message.krc_villagers.missing_form";
        for (var bad : req.kv$incompatible()) for (int i = 1; i <= belt.numBaseFormItems; i++)
            if (RiderDriverItem.getFormItem(stack, i) == bad) return "message.krc_villagers.incompatible";
        return "";
    }
    public static void apply(ItemStack stack, RiderDriverItem belt, RiderFormChangeItem form) {
        var req = (FormRequirements) form;
        if (req.kv$reset() || req.kv$resetMain() && Objects.equals(belt.riderName, req.kv$rider()))
            RiderDriverItem.resetFormItem(stack);
        var others = Arrays.asList(req.kv$alsoOne(), req.kv$alsoTwo(), req.kv$alsoThree(), req.kv$alsoFour());
        for (int i = 0; i < others.size(); i++)
            if (others.get(i) != null) RiderDriverItem.setFormItem(stack, others.get(i), i + 1);
        if (req.kv$old() != null) RiderDriverItem.SetOldFormItem(stack, req.kv$old(), form.getSlot());
        if (req.kv$armor()) RiderDriverItem.setFormItem(stack, belt.armorFormItem, 1);
        RiderDriverItem.setFormItem(stack, form, form.getSlot());
        if (req.kv$alsoFive() != null) RiderDriverItem.setFormItem(stack, req.kv$alsoFive(), 5);
    }
    private static boolean contains(VillagerCompanionData d, Item item) {
        for (int i = 0; i < d.items.getSlots(); i++) {
            var stack = d.items.getStackInSlot(i);
            if (stack.is(item)) return true;
            var container = stack.get(DataComponents.CONTAINER);
            if (container != null) for (var held : container.nonEmptyItems()) if (held.is(item)) return true;
            var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) for (var held : bundle.items()) if (held.is(item)) return true;
        }
        return false;
    }
}
