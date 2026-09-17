package dev.krcvillagers;

import com.example.generichenshin.compat.*;
import com.example.generichenshin.config.HenshinConfig;
import com.example.generichenshin.service.ZioTimeStopService;
import com.example.generichenshin.service.KabutoBulletService;
import com.kelco.kamenridercraft.abilities.AbilityUtil;
import com.kelco.kamenridercraft.attachments.AttachmentTypes;
import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import dev.krcvillagers.mixin.TimeStopAccess;
import io.github.suel_ki.timeclock.core.data.TimeData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import java.util.*;

public final class Skills {
    public static List<String> available(Villager v) {
        var list=new LinkedHashSet<String>();
        list.add("rider_punch"); list.add("rider_kick");
        if(Henshin.transformed(v)) for(int slot=1;slot<=2;slot++) {
            var choices=AbilityUtil.getAbility(v,slot);
            if(!choices.isEmpty()) { String id=choices.getFirst().replaceFirst("^-?\\d+", ""); if(!id.isEmpty()) list.add(id); }
        }
        if (KabutoBulletService.isKabutoSeriesBelt(v.getData(KrcVillagers.COMPANION).items.getStackInSlot(0))) list.add("clock_up");
        if (axelForm(v).contains("axel")) list.add("axel_acceleration");
        if(timeRider(v)!=null) list.add("time_stop");
        return List.copyOf(list);
    }
    public static boolean idle(Villager v) { return v.getData(AttachmentTypes.USED_ABILITY).isEmpty() && v.getData(AttachmentTypes.ABILITY_COOLDOWN)==0; }
    public static boolean canStart(Villager v,String skill) {
        var d=v.getData(KrcVillagers.COMPANION);
        return Henshin.ready(v) && !v.isInWater() && !v.isInLava() && idle(v) && d.enabled(skill) && v.getTarget()!=null
                && Companions.hostile(v,v.getTarget()) && v.hasLineOfSight(v.getTarget()) && available(v).contains(skill);
    }
    public static boolean start(Villager v,String skill) {
        if(!canStart(v,skill)) return false;
        if(skill.equals("time_stop")) return timeStop(v);
        if(skill.equals("clock_up")) return clockUp(v);
        if(skill.equals("axel_acceleration")) return axel(v);
        double meter=KrcCompat.getAbilityMeter(v);
        AbilityUtil.calculateAbility(v,skill);
        // KRC declares shrink but does not dispatch it in 1.1.4. Its villager adapter below completes that ability.
        boolean started=skill.equals(v.getData(AttachmentTypes.USED_ABILITY));
        if(!started && (skill.equals("wizard_kick_flame")||skill.equals("joker_memory_kick"))) {
            // Undo a rejected native precondition debit before using GH's special-form dispatch.
            v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(meter);
            started=FormAbilitySpecial.trigger(v,skill);
        }
        if(!started) {
            v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(meter);
            return false;
        }
        var d=v.getData(KrcVillagers.COMPANION);
        d.status="";
        if(!skill.equals("rider_punch")&&!skill.equals("rider_kick")) Protocol.animate(v,animation(skill),0);
        return true;
    }
    public static void tick(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        TimePowers.tick(v);
        if (d.clearClockUp && v.getData(AttachmentTypes.ABILITY_TICK) > 0) {
            // GH clears the casting lock next tick so Rider Kick can be used during acceleration.
            AbilityUtil.cancelAbility(v, "", 0); v.setData(AttachmentTypes.ABILITY_COOLDOWN, 0); d.clearClockUp = false;
        }
        String form = axelForm(v);
        if (Henshin.ready(v) && form.contains("axel") && !d.lastAxelForm.contains("axel") && d.enabled("axel_acceleration")) axel(v);
        if (Henshin.ready(v)) d.lastAxelForm = form;
        if(d.timeLockUntil>0 && v.level().getGameTime()>=d.timeLockUntil) { d.timeCharges=0; d.timeLockUntil=0; }
        String used=v.getData(AttachmentTypes.USED_ABILITY);
        if(used.isEmpty() && !d.animation.isEmpty() && d.stage==0 && ++d.animationTicks>30) { d.animation=""; Protocol.animate(v,"",0); }
        if(!Henshin.ready(v) || !idle(v) || v.getTarget()==null || v.tickCount%10!=0) return;
        var candidates=available(v).stream().filter(s->!s.equals("rider_punch")&&!s.equals("rider_kick")).toList();
        for(int i=0;i<candidates.size();i++) {
            int idx=Math.floorMod(d.skillCursor+i,candidates.size()); String skill=candidates.get(idx);
            if(appropriate(v,skill) && start(v,skill)) { d.skillCursor=idx+1; return; }
        }
    }
    private static boolean appropriate(Villager v,String skill) {
        double distance=v.distanceToSqr(v.getTarget());
        return switch(skill) {
            case "time_stop" -> distance<144;
            case "clock_up" -> distance>9 && !v.getData(KrcVillagers.COMPANION).timeStopOwned
                    && (v.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED) == null
                    || v.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED).getAmplifier() < 20);
            case "axel_acceleration" -> false; // GH triggers this once on entering Axel form.
            case "gatling","cannon" -> distance>=9 && distance<=576;
            case "warp","flight_boost" -> distance>64;
            case "fish" -> v.getHealth()<v.getMaxHealth()*0.6f;
            case "grow","wonder_grow","special_turbo" -> !v.hasEffect(com.kelco.kamenridercraft.effects.EffectCore.BIG);
            case "shrink","wonder_shrink" -> v.getHealth()<v.getMaxHealth()*0.5f;
            default -> distance<=64 && (skill.contains("punch") || v.getTarget().getHealth()<=v.getTarget().getMaxHealth()*0.6f);
        };
    }
    public static boolean adaptNative(Villager v,String skill) {
        switch(skill) {
            case "fish" -> {
                v.spawnAtLocation(v.isOnFire()?Items.COOKED_SALMON:Items.SALMON);
                v.setData(AttachmentTypes.ABILITY_COOLDOWN,100);
            }
            case "warp" -> {
                var pearl=new ThrownEnderpearl(v.level(),v);
                pearl.setPos(v.getX(),v.getY(0.5)+0.5,v.getZ()); pearl.addDeltaMovement(v.getLookAngle().scale(3)); v.level().addFreshEntity(pearl);
                v.setData(AttachmentTypes.ABILITY_COOLDOWN,150);
            }
            case "shrink" -> {
                v.addEffect(new MobEffectInstance(com.kelco.kamenridercraft.effects.EffectCore.SMALL,300,2,true,false));
                v.setData(AttachmentTypes.ABILITY_COOLDOWN,50);
            }
            default -> { return false; }
        }
        AbilityUtil.cancelAbility(v,"",0); return true;
    }
    public static ZioTimeStopService.StopRider timeRider(Villager v) {
        var belt=v.getData(KrcVillagers.COMPANION).items.getStackInSlot(0);
        if(!(belt.getItem() instanceof RiderDriverItem b)) return null;
        var beltId=BuiltInRegistries.ITEM.getKey(b).getPath();
        var bases=KrcCompat.getBeltBaseFormIds(b);
        String form=KrcCompat.getFormItemId(belt,1);
        for(int i=1;i<=b.numBaseFormItems;i++) { String id=KrcCompat.getFormItemId(belt,i); if(id!=null&&!bases.contains(id)) { form=id; break; } }
        if(ZioTimeStopService.isDecadeBelt(belt)) return TimeStopAccess.kv$byForm(form);
        return TimeStopAccess.kv$byBelt(beltId,form);
    }
    public static boolean timeStop(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        var rider=timeRider(v);
        if(rider==null || d.timeLockUntil>v.level().getGameTime() || d.timeCharges>=ZioTimeStopService.MAX_CHARGES) return false;
        if(!TimeclockCompat.ensureInit()) { d.status="message.krc_villagers.timeclock_missing"; return false; }
        if(!TimePowers.available(v)) return false;
        double cost=rider==ZioTimeStopService.StopRider.OHMA_ZIO_DRIVER?0:ZioTimeStopService.ENERGY_COST;
        double meter=KrcCompat.getAbilityMeter(v);
        if(meter<cost) return false;
        if(!TimePowers.start(v,true,ZioTimeStopService.STOP_DURATION)) { d.status="message.krc_villagers.timeclock_failed"; return false; }
        v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(meter-cost);
        d.timeCharges++; v.setData(AttachmentTypes.ABILITY_COOLDOWN, ZioTimeStopService.COOLDOWN_TICKS);
        sound(v,"the_world_time_stop");
        Protocol.animate(v,"default.punch",0); return true;
    }
    static void sound(Villager v,String name) {
        if(!HenshinConfig.isTimeStopSoundEnabled()) return;
        var key=ResourceLocation.fromNamespaceAndPath("generic_henshin",name);
        if(BuiltInRegistries.SOUND_EVENT.containsKey(key)) v.level().playSound(null,v.blockPosition(),BuiltInRegistries.SOUND_EVENT.get(key),SoundSource.NEUTRAL,1,1);
    }
    public static boolean clockUp(Villager v) {
        if (!Henshin.ready(v) || !idle(v) || !TimePowers.available(v)) return false;
        double meter = KrcCompat.getAbilityMeter(v);
        // Native KRC requires 100 energy to initiate; GH settles the successful cast to a net 50.
        AbilityUtil.calculateAbility(v, "clock_up");
        if (!v.getData(AttachmentTypes.USED_ABILITY).equals("clock_up")) return false;
        if (!TimePowers.start(v, false, KabutoBulletService.BULLET_DURATION_TICKS)) {
            AbilityUtil.cancelAbility(v, "", 0);
            v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(meter);
            return false;
        }
        v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(Math.max(0, meter - 50));
        v.getData(KrcVillagers.COMPANION).clearClockUp = true;
        return true;
    }
    private static String axelForm(Villager v) {
        var stack = v.getData(KrcVillagers.COMPANION).items.getStackInSlot(0);
        if (!(stack.getItem() instanceof RiderDriverItem)) return "";
        return String.valueOf(KrcCompat.getFormItemId(stack, 1)).toLowerCase(Locale.ROOT);
    }
    private static boolean axel(Villager v) {
        return Henshin.ready(v) && axelForm(v).contains("axel")
                && TimePowers.start(v, false, KabutoBulletService.FAIZ_AXEL_BULLET_DURATION_TICKS);
    }
    public static void cleanup(Villager v) { TimePowers.finish(v); v.getData(KrcVillagers.COMPANION).clearClockUp = false; }
    private static String animation(String skill) {
        return skill.contains("kick") ? "default.rider_floor_jump" : "default.punch";
    }
}
