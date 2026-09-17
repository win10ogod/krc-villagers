package dev.krcvillagers.mixin;

import com.kelco.kamenridercraft.item.base_items.NeoBaseBlasterItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NeoBaseBlasterItem.class, remap = false)
public interface NeoBlasterAccess {
    @Accessor("firingRate") int kv$rate();
    @Accessor("reloadTime") int kv$reload();
    @Accessor("maxAmmo") int kv$ammo();
    @Accessor("drawTime") int kv$drawTime();
    @Accessor("drawTick") int kv$drawTick();
    @Accessor("drawTick") void kv$drawTick(int value);
    @Accessor("gunMode") boolean kv$gunMode();
    @Accessor("projectile") String kv$projectile();
}
