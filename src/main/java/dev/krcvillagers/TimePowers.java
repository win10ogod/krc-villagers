package dev.krcvillagers;

import com.example.generichenshin.compat.TimeclockCompat;
import dev.krcvillagers.mixin.TimeDataAccess;
import io.github.suel_ki.timeclock.core.data.TimeData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/** TimeClock is level-wide: never replace another caster's running ability. */
public final class TimePowers {
    public static boolean available(Villager v) {
        return TimeData.get(v.level()).map(t -> !t.isTimePaused() && t.getTimeScale() == 1.0f
                && ((TimeDataAccess)t).kv$tickets().isEmpty()).orElse(false);
    }
    public static boolean start(Villager v, boolean pause, int ticks) {
        if (!available(v)) return false;
        var time = TimeData.get(v.level()).orElse(null);
        if (time == null) return false;
        var d = v.getData(KrcVillagers.COMPANION);
        d.timeTicket = ResourceLocation.fromNamespaceAndPath(KrcVillagers.ID, v.getUUID().toString());
        d.timePreviouslyWhitelisted = time.isInWhiteList(v);
        d.timeStopOwned = true; d.timeBullet = !pause; d.timeStopTicks = ticks;
        time.setTimeManipulator(v);
        time.addToWhitelist(v);
        time.addAbilityTick(ticks, pause ? TimeData.AbilityType.PAUSE : TimeData.AbilityType.BULLET, d.timeTicket);
        if (pause) time.pauseTime(true); else time.setVirtualTickrate(2.0f);
        time.sync(v.level());
        if (pause) TimeclockCompat.sendDesaturateShaderStart(v.level());
        return true;
    }
    public static void tick(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        if (!d.timeStopOwned) return;
        var time = TimeData.get(v.level()).orElse(null);
        if (--d.timeStopTicks <= 0 || time == null || !time.isTimeManipulator(v)
                || (d.timeBullet ? time.getTimeScale() >= 1.0f : !time.isTimePaused())) finish(v);
    }
    public static void finish(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        if (!d.timeStopOwned) return;
        TimeData.get(v.level()).ifPresent(time -> {
            // TimeClock 4.7's public removeAbilityTick compares ResourceLocations by identity.
            // Remove only this persisted ticket by value, including after an entity reload.
            ((TimeDataAccess)time).kv$tickets().removeIf(ticket -> ticket.getId().equals(d.timeTicket));
            if (time.isTimeManipulator(v)) {
                if (d.timeBullet) time.setVirtualTickrate(20.0f);
                else { time.pauseTime(false); TimeclockCompat.sendDesaturateShaderEnd(v.level()); }
            }
            if (!d.timePreviouslyWhitelisted) time.removeFromWhitelist(v);
            time.sync(v.level());
        });
        if (!d.timeBullet) {
            if (d.timeCharges >= com.example.generichenshin.service.ZioTimeStopService.MAX_CHARGES)
                d.timeLockUntil = v.level().getGameTime() + com.example.generichenshin.service.ZioTimeStopService.LOCK_TICKS;
            Skills.sound(v, "the_world_time_stop_reverse");
        }
        d.timeStopOwned = false; d.timeStopTicks = 0; d.timeBullet = false; d.timeTicket = null;
    }
}
