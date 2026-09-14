package dev.krcvillagers.mixin;

import io.github.suel_ki.timeclock.core.data.AbilityTick;
import io.github.suel_ki.timeclock.core.data.TimeData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(value = TimeData.class, remap = false)
public interface TimeDataAccess {
    @Accessor("abilityTicks") List<AbilityTick> kv$tickets();
}
