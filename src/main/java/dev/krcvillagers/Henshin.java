package dev.krcvillagers;

import com.example.generichenshin.compat.KrcCompat;
import com.example.generichenshin.config.HenshinTimingConfig;
import com.kelco.kamenridercraft.abilities.AbilityUtil;
import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import com.kelco.kamenridercraft.item.base_items.RiderFormChangeItem;
import dev.krcvillagers.mixin.FormRequirements;
import dev.krcvillagers.mixin.KickAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class Henshin {
    public static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    public static RiderDriverItem belt(Villager v) {
        return v.getData(KrcVillagers.COMPANION).items.getStackInSlot(0).getItem() instanceof RiderDriverItem b ? b : null;
    }
    public static boolean transformed(Villager v) {
        return v.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof RiderDriverItem b && b.isTransformed(v);
    }
    public static boolean ready(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        return d.active() && !d.downed && d.stage == 0 && transformed(v)
                && v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.IS_TRANSFORMING).getValue() <= 0;
    }
    public static String rider(Villager v) {
        var b = belt(v);
        if (b == null) return "";
        String name = b.riderName.toLowerCase(Locale.ROOT);
        if (name.contains("zi_o") || name.contains("zio")) return "zi_o";
        if (name.contains("ichigo")) return "ichigo";
        return name;
    }
    public static String validateAndSetForms(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        var b = belt(v);
        if (b == null) return "message.krc_villagers.no_belt";
        return Forms.install(d, b);
    }
    public static boolean begin(Villager v) {
        var d = v.getData(KrcVillagers.COMPANION);
        if (!d.active() || d.downed || d.stage>0 || d.equipped) return false;
        d.status = validateAndSetForms(v);
        if (!d.status.isEmpty()) return false;
        for (int i=0;i<EQUIPMENT.length;i++) d.backup[i] = v.getItemBySlot(EQUIPMENT[i]);
        d.stage=1;
        v.stopSleeping(); v.getNavigation().stop();
        v.setItemSlot(EquipmentSlot.FEET, d.items.getStackInSlot(0));
        v.setItemSlot(EquipmentSlot.MAINHAND, d.items.getStackInSlot(6));
        v.setItemSlot(EquipmentSlot.OFFHAND, d.items.getStackInSlot(7));
        var timing = HenshinTimingConfig.rider(rider(v));
        d.animation = timing.animationId(); d.animationTicks = 0;
        Protocol.sync(v); Protocol.animate(v, d.animation, 0);
        return true;
    }
    public static void tick(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        if (d.equipped && d.stage == 0 && !transformed(v)) { end(v); return; }
        if (d.stage<=0) return;
        var b=belt(v);
        if (b==null) { end(v); return; }
        var timing=HenshinTimingConfig.rider(rider(v));
        if (!d.equipped && d.stage >= Math.max(1,timing.armorAppearTick())) {
            v.setItemSlot(EquipmentSlot.HEAD,new ItemStack(b.helmet));
            v.setItemSlot(EquipmentSlot.CHEST,new ItemStack(b.chestplate));
            v.setItemSlot(EquipmentSlot.LEGS,new ItemStack(b.leggings));
            v.setItemSlot(EquipmentSlot.FEET,d.items.getStackInSlot(0));
            d.equipped=true;
            RiderDriverItem.setUpdateForm(d.items.getStackInSlot(0));

            Protocol.sync(v);
        }
        if (d.stage++ >= Math.max(2,timing.lockTicks())) { d.stage=0; Protocol.sync(v); }
    }
    public static void end(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        Skills.cleanup(v);
        KickAccess.kv$clear(v.getUUID());
        if (d.equipped || d.stage>0) {
            AbilityUtil.cancelAbility(v,"",0);
            // Weapon durability and all belt components remain in the same handed-over stacks.
            d.items.setStackInSlot(0,v.getItemBySlot(EquipmentSlot.FEET));
            d.items.setStackInSlot(6,v.getMainHandItem());
            d.items.setStackInSlot(7,v.getOffhandItem());
            for(int i=0;i<EQUIPMENT.length;i++) { v.setItemSlot(EQUIPMENT[i],d.backup[i]); d.backup[i]=ItemStack.EMPTY; }
            FormEffects.clear(v);
        }
        d.stage=0; d.equipped=false; d.animation="";
        d.lastAxelForm="";
        v.setInvulnerable(false);
        v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.IS_TRANSFORMING).setBaseValue(0);
        Protocol.sync(v); Protocol.animate(v,"",0);
    }
}
