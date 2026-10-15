package dev.krcvillagers.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** The plugin defers client handler linkage on physical dedicated servers. */
@Mixin(targets="com.example.generichenshin.network.NetworkHandler",remap=false)
public abstract class HenshinNetworkServerMixin { }
