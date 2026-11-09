package dev.krcvillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import java.util.Comparator;

/** Survival runs even when companion orders suspend the vanilla villager Brain. */
public final class WaterSafety {
    public static boolean dry(Villager v, BlockPos p) {
        if (!v.level().hasChunkAt(p) || !v.level().getFluidState(p).isEmpty()
                || !v.level().getFluidState(p.above()).isEmpty()
                || !v.level().getFluidState(p.below()).isEmpty()
                || !v.level().getBlockState(p.below()).isFaceSturdy(v.level(), p.below(), net.minecraft.core.Direction.UP)) return false;
        return v.level().noCollision(v, v.getBoundingBox().move(p.getX() + .5 - v.getX(), p.getY() - v.getY(), p.getZ() + .5 - v.getZ()));
    }
    /**
     * The dry block the villager stands on, or null. Uses the supporting block rather than the block under the
     * villager's centre: on the edge of a one-block bank the centre can still be over the water.
     */
    static BlockPos footing(Villager v) {
        if (!v.onGround() || v.isInWater() || v.isInLava()) return null;
        var below = v.getOnPos();
        var level = v.level();
        if (!level.getFluidState(below).isEmpty() || !level.getFluidState(below.above()).isEmpty()
                || !level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) return null;
        return below;
    }
    public static boolean tick(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        v.getNavigation().setCanFloat(true);
        if (!v.isInWater() && !v.isInLava()) {
            // Landing anywhere dry ends the escape. Following the old path to the planned shore after a
            // crowded exit pushed a villager off the bank and back across the water.
            var footing = d.shore == null ? null : footing(v);
            if (footing != null) {
                if (!v.level().getFluidState(d.guard).isEmpty()) Companions.setGuard(v, footing.above());
                d.shore = null;
                v.getNavigation().stop();
                v.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                v.setDeltaMovement(0, v.getDeltaMovement().y, 0);
            }
            // Crossing the water surface is not the same as reaching land. Keep the
            // escape path until the feet are on dry ground, including the final hop.
            return d.shore != null;
        }
        v.stopSleeping();
        v.getJumpControl().jump();
        // JumpControl alone is not enough below the surface with a suspended Brain.
        if (v.isEyeInFluid(FluidTags.WATER) || v.isInLava()) {
            var motion = v.getDeltaMovement();
            v.setDeltaMovement(motion.x, Math.max(motion.y, .08), motion.z);
        }
        if (d.shore != null && !dry(v, d.shore)) d.shore = null;
        if (v.tickCount % 10 == 0) {
            if (d.shore == null) {
                v.getNavigation().stop();
                var candidates = new java.util.ArrayList<BlockPos>();
                for (var pos : BlockPos.betweenClosed(v.blockPosition().offset(-8, -1, -8), v.blockPosition().offset(8, 6, 8)))
                    if (dry(v, pos)) candidates.add(pos.immutable());
                candidates.sort(Comparator.comparingDouble(p -> p.distSqr(v.blockPosition())));
                for (var pos : candidates) {
                    var path = v.getNavigation().createPath(pos, 0);
                    if (path != null && path.canReach()) {
                        d.shore = pos;
                        // createPath updates the navigation target cache. Install this exact
                        // path; asking again by coordinates can reuse an older escape path.
                        v.getNavigation().moveTo(path, 1.2);
                        break;
                    }
                }
            } else v.getNavigation().moveTo(d.shore.getX() + .5, d.shore.getY(), d.shore.getZ() + .5, 1.2);
        }
        // Do not let combat or follow orders replace the path to shore this tick.
        return true;
    }
}
