package dev.krcvillagers.mixin;

import com.kelco.kamenridercraft.item.base_items.BaseBlasterItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BaseBlasterItem.class, remap = false)
public interface BlasterAccess {
    @Accessor("cooldown") int kv$cooldown();
    @Accessor("firetype") String kv$fireType();
    @Accessor("accuracyMod") float kv$accuracy();
}
