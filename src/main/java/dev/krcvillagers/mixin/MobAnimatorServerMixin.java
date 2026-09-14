package dev.krcvillagers.mixin;
import org.spongepowered.asm.mixin.Mixin;
/** Applies the narrowly versioned constructor patch in CompatibilityPlugin on servers. */
@Mixin(targets="me.Thelnfamous1.mobplayeranimator.MobPlayerAnimatorForge",remap=false)
public abstract class MobAnimatorServerMixin {}
