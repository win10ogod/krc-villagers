package dev.krcvillagers;

import com.example.generichenshin.compat.KrcCompat;
import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import com.kelco.kamenridercraft.item.base_items.RiderFormChangeItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class CompanionMenu extends AbstractContainerMenu {
    public final Villager villager;
    public final VillagerCompanionData companion;
    public final ContainerData values;
    public CompanionMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, (Villager)inventory.player.level().getEntity(extra.readVarInt()), false);
    }
    public CompanionMenu(int id, Inventory inventory, Villager villager, boolean server) {
        super(KrcVillagers.MENU.get(), id);
        this.villager = villager;
        companion = villager.getData(KrcVillagers.COMPANION);
        values = server ? new ContainerData() {
            public int getCount() { return 13; }
            public int get(int index) {
                return switch (index) {
                    case 0 -> companion.owner == null ? 0 : companion.owner.equals(inventory.player.getUUID()) ? 1 : 2;
                    case 1 -> companion.mode.ordinal(); case 2 -> companion.policy.ordinal();
                    case 3 -> companion.stage; case 4 -> companion.downed ? 1 : 0;
                    case 5 -> companion.equipped ? 1 : 0;
                    case 6 -> (int)KrcCompat.getAbilityMeter(villager);
                    case 7 -> KrcCompat.getAbilityCooldown(villager);
                    case 8 -> Settings.RECRUIT_COST.get(); case 9 -> Settings.RESCUE_COST.get();
                    case 10 -> (int)(villager.getHealth() * 10); case 11 -> (int)(villager.getMaxHealth() * 10);
                    case 12 -> companion.autoForms ? 1 : 0;
                    default -> 0;
                };
            }
            public void set(int index, int value) {}
        } : new SimpleContainerData(13);
        addDataSlots(values);
        for (int i = 0; i < 17; i++) {
            final int slot = i;
            int x = i == 0 ? 16 : i <= 5 ? 52 + (i-1)*18 : i <= 7 ? 16+(i-6)*18 : 16+(i-8)*18;
            int y = i <= 5 ? 44 : i <= 7 ? 80 : 116;
            addSlot(new SlotItemHandler(companion.items, i, x, y) {
                @Override public boolean mayPlace(ItemStack stack) { return editable(inventory.player, slot) && accepts(slot, stack); }
                @Override public boolean mayPickup(Player player) { return editable(player, slot); }
                @Override public void setChanged() {
                    super.setChanged();
                    if (!villager.level().isClientSide()) CompanionEquipment.bind(villager);
                }
                @Override public int getMaxStackSize() { return slot <= 5 ? 1 : 64; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col+row*9+9, 16+col*18, 160+row*18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 16+col*18, 218));
    }
    public boolean editable(Player player) {
        return editable(player, 0);
    }
    private boolean editable(Player player, int slot) {
        return stillValid(player) && values.get(0) == 1 && values.get(4) == 0
                && (slot >= 8 || values.get(3) == 0 && values.get(5) == 0);
    }
    private boolean accepts(int slot, ItemStack stack) {
        if (slot == 0) return stack.getItem() instanceof RiderDriverItem;
        if (slot <= 5) return stack.getItem() instanceof RiderFormChangeItem form
                && companion.items.getStackInSlot(0).getItem() instanceof RiderDriverItem belt
                && form.getSlot() == slot && form.isCompatible(belt);
        return true;
    }
    @Override public boolean stillValid(Player player) {
        return villager != null && villager.isAlive() && villager.level() == player.level() && player.distanceToSqr(villager) <= 64;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if(villager.getTradingPlayer()==player) villager.setTradingPlayer(null);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || values.get(0) != 1 || values.get(4) != 0 || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var original = slot.getItem(); var copy = original.copy();
        if (index < 17) {
            if (!moveItemStackTo(original, 17, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved;
            if (original.getItem() instanceof RiderDriverItem) moved = moveItemStackTo(original, 0, 1, false);
            else if (original.getItem() instanceof RiderFormChangeItem) moved = moveItemStackTo(original, 1, 6, false);
            else if (RangedCombat.weapon(original) || original.getItem() instanceof net.minecraft.world.item.SwordItem
                    || original.getItem() instanceof com.kelco.kamenridercraft.item.base_items.BaseSwordItem)
                moved = moveItemStackTo(original, 6, 8, false);
            else moved = false;
            if (!original.isEmpty()) moved |= moveItemStackTo(original, 8, 17, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, original);
        return copy;
    }
}
