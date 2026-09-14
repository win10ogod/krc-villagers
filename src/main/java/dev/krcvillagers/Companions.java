package dev.krcvillagers;

import com.kelco.kamenridercraft.attachments.AttachmentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;

public final class Companions {
    public static boolean active(Entity e) { return e instanceof Villager v && v.hasData(KrcVillagers.COMPANION) && v.getData(KrcVillagers.COMPANION).active(); }
    public static boolean owns(Player p,Villager v) { return p.getUUID().equals(v.getData(KrcVillagers.COMPANION).owner); }
    public static boolean hostile(Villager v,LivingEntity other) {
        if(other==v || !other.isAlive() || other.isSpectator() || other instanceof Player || other instanceof Villager
                || other instanceof TamableAnimal pet && pet.isTame() || v.isAlliedTo(other) || other.getTeam()!=null && other.getTeam()==v.getTeam()) return false;
        // KRC summons inherit TamableAnimal. Also honor neutral mobs with a live attack target.
        var d=v.getData(KrcVillagers.COMPANION);
        boolean aggressor=other instanceof Mob m && m.getTarget()!=null &&
                (m.getTarget()==v || m.getTarget() instanceof Villager || m.getTarget().getUUID().equals(d.owner));
        if(other instanceof NeutralMob neutral) return aggressor || neutral.isAngry();
        return other instanceof Enemy || aggressor || v.getLastHurtByMob()==other;
    }
    public static boolean pay(Player p,int amount) {
        if(p.isCreative()) return true;
        int count=0;
        for(var item:p.getInventory().items) if(item.is(Items.EMERALD)) count+=item.getCount();
        if(count<amount) return false;
        int remain=amount;
        for(var item:p.getInventory().items) if(item.is(Items.EMERALD)) { int used=Math.min(remain,item.getCount()); item.shrink(used); remain-=used; if(remain==0) break; }
        p.getInventory().setChanged(); return true;
    }
    public static boolean recruit(Player p,Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        if(d.active() || v.isBaby() || !pay(p,Settings.RECRUIT_COST.get())) return false;
        d.owner=p.getUUID(); d.mode=VillagerCompanionData.Mode.FOLLOW;
        v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(
                v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.MAX_ABILITY_METER).getValue());
        setGuard(v); v.setPersistenceRequired(); Protocol.sync(v); return true;
    }
    public static void setGuard(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        d.guard=v.blockPosition(); d.guardDimension=v.level().dimension().location().toString();
    }
    public static void release(ServerPlayer p,Villager v) {
        if(!owns(p,v) || v.getData(KrcVillagers.COMPANION).downed) return;
        Henshin.end(v);
        var d=v.getData(KrcVillagers.COMPANION);
        for(int i=0;i<d.items.getSlots();i++) { var item=d.items.extractItem(i,Integer.MAX_VALUE,false); if(!p.getInventory().add(item)) p.drop(item,false); }
        d.owner=null; d.disabledSkills.clear(); v.setTarget(null); v.getNavigation().stop();
        Protocol.sync(v); p.closeContainer();
    }
    public static void down(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        Henshin.end(v); d.downed=true; v.setHealth(1); v.stopSleeping(); v.setTarget(null); v.getNavigation().stop();
        v.setDeltaMovement(Vec3.ZERO); v.clearFire(); Protocol.sync(v);
    }
    public static boolean rescue(Player p,Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        if(!owns(p,v)||!d.downed||!pay(p,Settings.RESCUE_COST.get())) return false;
        d.downed=false; v.setHealth(v.getMaxHealth()*0.5f); v.invulnerableTime=40;
        Protocol.sync(v); return true;
    }
    public static boolean controlBrain(Villager v) {
        if(!active(v)) return false;
        var d=v.getData(KrcVillagers.COMPANION);
        return d.downed || d.stage>0 || v.getTarget()!=null || d.mode!=VillagerCompanionData.Mode.LIFE;
    }
    public static void tick(Villager v) {
        if(!active(v)||!(v.level() instanceof ServerLevel level)) return;
        var d=v.getData(KrcVillagers.COMPANION);
        if(v.onGround() && !v.isInLava() && !v.isInWater() && level.getBlockState(v.blockPosition().below()).isSolid()) d.lastSafe=v.blockPosition();
        if(d.downed) {
            v.setTarget(null); v.getNavigation().stop(); v.setDeltaMovement(0,v.getDeltaMovement().y,0); v.clearFire();
            if(v.getY()<level.getMinBuildHeight() && d.lastSafe!=null) v.teleportTo(d.lastSafe.getX()+0.5,d.lastSafe.getY(),d.lastSafe.getZ()+0.5);
            return;
        }
        Henshin.tick(v); Skills.tick(v);
        if(v.tickCount%10==0) Protocol.sync(v);
        if(v.getTradingPlayer()!=null) { v.getNavigation().stop(); return; }
        var owner=level.getServer().getPlayerList().getPlayer(d.owner);
        if(d.mode==VillagerCompanionData.Mode.FOLLOW && owner==null && !d.offlineGuard) { setGuard(v); d.offlineGuard=true; }
        if(owner!=null) d.offlineGuard=false;
        var target=v.getTarget();
        if(target!=null && (!hostile(v,target) || !withinArea(v,target,owner))) { v.setTarget(null); target=null; }
        if(v.tickCount%10==0 && target==null) {
            target=level.getEntitiesOfClass(LivingEntity.class,v.getBoundingBox().inflate(Settings.DETECT_RANGE.get()), e->hostile(v,e) && withinArea(v,e,owner) && v.hasLineOfSight(e))
                    .stream().min(Comparator.comparingDouble(v::distanceToSqr)).orElse(null);
            v.setTarget(target);
        }
        if(target!=null) d.lastCombat=level.getGameTime();
        boolean want=d.policy==VillagerCompanionData.Policy.ON || d.policy==VillagerCompanionData.Policy.AUTO && target!=null;
        if(want && !d.equipped && d.stage==0 && v.tickCount%20==0) Henshin.begin(v);
        if((d.policy==VillagerCompanionData.Policy.OFF || d.policy==VillagerCompanionData.Policy.AUTO && target==null && level.getGameTime()-d.lastCombat>=Settings.CALM_TICKS.get()) && (d.equipped||d.stage>0)) Henshin.end(v);
        if(d.stage>0) { v.getNavigation().stop(); return; }
        if(target!=null) {
            v.stopSleeping();
            v.getLookControl().setLookAt(target,30,30);
            v.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,target.getEyePosition());
            String used=v.getData(AttachmentTypes.USED_ABILITY);
            if(!used.isEmpty() && !used.equals("clock_up")) { v.getNavigation().stop(); return; }
            double reach=2.3+target.getBbWidth()*0.5;
            if(v.distanceToSqr(target)>reach*reach) { if(v.tickCount%5==0) v.getNavigation().moveTo(target,1.15); }
            else {
                v.getNavigation().stop();
                if(v.tickCount>=d.nextAttack && v.hasLineOfSight(target)) {
                    d.nextAttack=v.tickCount+20; v.swing(InteractionHand.MAIN_HAND); v.doHurtTarget(target);
                    if(Henshin.ready(v)) Protocol.animate(v,"default.punch",0);
                }
            }
        } else if(d.mode!=VillagerCompanionData.Mode.LIFE && v.tickCount%10==0) {
            if(d.mode==VillagerCompanionData.Mode.FOLLOW && owner!=null && owner.level()==level) {
                double distance=v.distanceToSqr(owner);
                if(distance>32*32) teleportNear(v,owner.blockPosition());
                else if(distance>9) v.getNavigation().moveTo(owner,1.0);
                else v.getNavigation().stop();
            } else if(d.guardDimension.equals(level.dimension().location().toString()) && v.blockPosition().distSqr(d.guard)>4)
                v.getNavigation().moveTo(d.guard.getX()+0.5,d.guard.getY(),d.guard.getZ()+0.5,1.0);
        }
    }
    private static boolean withinArea(Villager v,LivingEntity target,ServerPlayer owner) {
        var d=v.getData(KrcVillagers.COMPANION);
        if(d.mode==VillagerCompanionData.Mode.GUARD || d.offlineGuard)
            return target.blockPosition().distSqr(d.guard)<=Math.pow(Settings.GUARD_RANGE.get(),2);
        if(d.mode==VillagerCompanionData.Mode.FOLLOW && owner!=null && owner.level()==v.level())
            return target.distanceToSqr(owner)<=Math.pow(Settings.DETECT_RANGE.get(),2);
        return target.distanceToSqr(v)<=Math.pow(Settings.DETECT_RANGE.get(),2);
    }
    private static void teleportNear(Villager v,BlockPos center) {
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
            if(Math.abs(x)<2&&Math.abs(z)<2) continue;
            var p=center.offset(x,0,z);
            if(!v.level().hasChunkAt(p) || !v.level().getBlockState(p.below()).isSolid() || !v.level().getFluidState(p).isEmpty()) continue;
            var box=v.getBoundingBox().move(p.getX()+0.5-v.getX(),p.getY()-v.getY(),p.getZ()+0.5-v.getZ());
            if(v.level().noCollision(v,box)) { v.teleportTo(p.getX()+0.5,p.getY(),p.getZ()+0.5); v.getNavigation().stop(); return; }
        }
    }
}
