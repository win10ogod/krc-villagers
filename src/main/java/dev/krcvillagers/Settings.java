package dev.krcvillagers;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class Settings {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue RECRUIT_BASE_COST, RECRUIT_INCREMENT, RANGED_INTERVAL, FOOD_INTERVAL, DETECT_RANGE, GUARD_RANGE, CALM_TICKS;
    static {
        var b = new ModConfigSpec.Builder();
        RECRUIT_BASE_COST = b.comment("First recruitment cost per player; replaces the old flat recruitCost.").defineInRange("recruitBaseCost", 16, 0, Integer.MAX_VALUE);
        RECRUIT_INCREMENT = b.comment("Additional emeralds per previous successful recruitment by this player.").defineInRange("recruitCostIncrement", 4, 0, Integer.MAX_VALUE);
        RANGED_INTERVAL = b.comment("Minimum ticks between companion shots. Longer native charge/reload times still apply.").defineInRange("rangedShotInterval", 20, 1, Integer.MAX_VALUE);
        FOOD_INTERVAL = b.comment("Minimum ticks between food servings consumed to heal an injured companion.").defineInRange("foodHealInterval", 100, 1, Integer.MAX_VALUE);
        DETECT_RANGE = b.defineInRange("detectionRange", 24, 1, 256);
        GUARD_RANGE = b.defineInRange("guardRadius", 16, 1, 256);
        CALM_TICKS = b.comment("Out-of-combat time before AUTO dehenshin, in ticks.").defineInRange("calmTicks", 400, 0, Integer.MAX_VALUE);
        SPEC = b.build();
    }
}
