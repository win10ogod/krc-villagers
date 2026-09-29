package dev.krcvillagers;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-level UUID history survives player death, disconnects, dimension changes and releases. */
public final class RecruitmentHistory extends SavedData {
    private final Map<UUID,Long> counts=new HashMap<>();
    public static RecruitmentHistory get(Player player) {
        return ((ServerLevel)player.level()).getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(RecruitmentHistory::new,RecruitmentHistory::load),"krc_villagers_recruitment");
    }
    public long count(UUID player) { return counts.getOrDefault(player,0L); }
    public int cost(UUID player) {
        long count=count(player), base=Settings.RECRUIT_BASE_COST.get(), increment=Settings.RECRUIT_INCREMENT.get();
        // The menu and payment API use ints. Avoid overflow into a free/negative price.
        if(increment>0&&count>(Integer.MAX_VALUE-base)/increment)return Integer.MAX_VALUE;
        return (int)(base+count*increment);
    }
    public void recruited(UUID player) {
        counts.compute(player,(id,previous)->previous==null?1L:previous==Long.MAX_VALUE?previous:previous+1);
        setDirty();
    }
    public static RecruitmentHistory load(CompoundTag tag,HolderLookup.Provider registries) {
        var history=new RecruitmentHistory();var saved=tag.getCompound("Counts");
        for(String key:saved.getAllKeys()) {
            try { history.counts.put(UUID.fromString(key),Math.max(0,saved.getLong(key))); }
            catch(IllegalArgumentException ignored) { }
        }
        return history;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        var saved=new CompoundTag();counts.forEach((id,count)->saved.putLong(id.toString(),count));
        tag.put("Counts",saved);return tag;
    }
}
