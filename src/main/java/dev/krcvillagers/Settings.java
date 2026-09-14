package dev.krcvillagers;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class Settings {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue RECRUIT_COST, RESCUE_COST, DETECT_RANGE, GUARD_RANGE, CALM_TICKS;
    static {
        var b = new ModConfigSpec.Builder();
        RECRUIT_COST = b.comment("Emeralds required to recruit an adult villager.").defineInRange("recruitCost", 8, 0, Integer.MAX_VALUE);
        RESCUE_COST = b.comment("Emeralds required to revive a downed companion.").defineInRange("rescueCost", 4, 0, Integer.MAX_VALUE);
        DETECT_RANGE = b.defineInRange("detectionRange", 24, 1, 256);
        GUARD_RANGE = b.defineInRange("guardRadius", 16, 1, 256);
        CALM_TICKS = b.comment("Out-of-combat time before AUTO dehenshin, in ticks.").defineInRange("calmTicks", 400, 0, Integer.MAX_VALUE);
        SPEC = b.build();
    }
}
