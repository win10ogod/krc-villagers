package dev.krcvillagers;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.*;

/** The villager remains the authoritative entity; no substitute mob or fake player is spawned. */
public final class VillagerCompanionData implements INBTSerializable<CompoundTag> {
    public enum Mode { FOLLOW, GUARD, LIFE }
    public enum Policy { AUTO, ON, OFF }
    public UUID owner;
    public Mode mode = Mode.FOLLOW;
    public Policy policy = Policy.AUTO;
    public BlockPos guard = BlockPos.ZERO;
    public String guardDimension = "minecraft:overworld";
    public boolean downed, equipped;
    public int stage, nextAttack, skillCursor, timeCharges;
    public long lastCombat, timeLockUntil;
    public int timeStopTicks;
    public boolean timeStopOwned, timeBullet, timePreviouslyWhitelisted, clearClockUp;
    public net.minecraft.resources.ResourceLocation timeTicket;
    public String lastAxelForm = "";
    public CompoundTag priorEffects = new CompoundTag(), appliedEffects = new CompoundTag();
    public BlockPos lastSafe;
    public boolean offlineGuard;
    public String status = "";
    public final Set<String> disabledSkills = new HashSet<>();
    public final ItemStackHandler items = new ItemStackHandler(17);
    public final ItemStack[] backup = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    public final Set<String> formEffects = new HashSet<>();
    // Synchronization counters are transient; transformation animation progress is persisted.
    public long lastSync;
    public int animationTicks;
    public String animation = "";
    public boolean active() { return owner != null; }
    public boolean enabled(String skill) { return !disabledSkills.contains(skill); }
    public void toggle(String skill) { if (!disabledSkills.remove(skill)) disabledSkills.add(skill); }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        var n = new CompoundTag();
        n.putInt("Version", 1);
        if (owner != null) n.putUUID("Owner", owner);
        n.putString("Mode", mode.name()); n.putString("Policy", policy.name());
        n.putLong("Guard", guard.asLong()); n.putString("Dimension", guardDimension);
        n.putBoolean("Downed", downed); n.putBoolean("Equipped", equipped);
        n.putString("Animation", animation); n.putInt("AnimationTicks", animationTicks);
        n.putString("LastAxelForm", lastAxelForm);
        n.putBoolean("TimeOwned", timeStopOwned); n.putBoolean("TimeBullet", timeBullet);
        n.putBoolean("TimeWhitelisted", timePreviouslyWhitelisted);
        n.putInt("TimeTicks", timeStopTicks);
        if (timeTicket != null) n.putString("TimeTicket", timeTicket.toString());
        n.put("PriorEffects", priorEffects.copy()); n.put("AppliedEffects", appliedEffects.copy());
        n.putInt("Stage", stage); n.putLong("LastCombat", lastCombat);
        n.putInt("TimeCharges", timeCharges); n.putLong("TimeLockUntil", timeLockUntil);
        if (lastSafe != null) n.putLong("LastSafe", lastSafe.asLong());
        n.put("Items", items.serializeNBT(registries));
        var b = new ListTag();
        for (var stack : backup) b.add(stack.saveOptional(registries));
        n.put("Backup", b);
        var skills = new ListTag();
        disabledSkills.stream().sorted().forEach(s -> skills.add(net.minecraft.nbt.StringTag.valueOf(s)));
        n.put("DisabledSkills", skills);
        var effects = new ListTag();
        formEffects.forEach(s -> effects.add(net.minecraft.nbt.StringTag.valueOf(s)));
        n.put("FormEffects", effects);
        return n;
    }
    @Override public void deserializeNBT(HolderLookup.Provider registries, CompoundTag n) {
        owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
        try { mode = Mode.valueOf(n.getString("Mode")); } catch (IllegalArgumentException ignored) { mode = Mode.FOLLOW; }
        try { policy = Policy.valueOf(n.getString("Policy")); } catch (IllegalArgumentException ignored) { policy = Policy.AUTO; }
        guard = BlockPos.of(n.getLong("Guard")); guardDimension = n.getString("Dimension");
        downed = n.getBoolean("Downed"); equipped = n.getBoolean("Equipped");
        animation = n.getString("Animation"); animationTicks = n.getInt("AnimationTicks");
        lastAxelForm = n.getString("LastAxelForm");
        timeStopOwned = n.getBoolean("TimeOwned"); timeBullet = n.getBoolean("TimeBullet");
        timePreviouslyWhitelisted = n.getBoolean("TimeWhitelisted"); timeStopTicks = n.getInt("TimeTicks");
        timeTicket = net.minecraft.resources.ResourceLocation.tryParse(n.getString("TimeTicket"));
        priorEffects = n.getCompound("PriorEffects").copy(); appliedEffects = n.getCompound("AppliedEffects").copy();
        stage = n.getInt("Stage"); lastCombat = n.getLong("LastCombat");
        timeCharges = n.getInt("TimeCharges"); timeLockUntil = n.getLong("TimeLockUntil");
        lastSafe = n.contains("LastSafe") ? BlockPos.of(n.getLong("LastSafe")) : null;
        items.deserializeNBT(registries, n.getCompound("Items"));
        var b = n.getList("Backup", 10);
        for (int i = 0; i < backup.length; i++) backup[i] = i < b.size() ? ItemStack.parseOptional(registries, b.getCompound(i)) : ItemStack.EMPTY;
        disabledSkills.clear(); n.getList("DisabledSkills", 8).forEach(s -> disabledSkills.add(s.getAsString()));
        formEffects.clear(); n.getList("FormEffects", 8).forEach(s -> formEffects.add(s.getAsString()));
    }
}
