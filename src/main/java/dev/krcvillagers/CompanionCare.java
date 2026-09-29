package dev.krcvillagers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class CompanionCare {
    private static final ResourceLocation MOVEMENT=ResourceLocation.fromNamespaceAndPath(KrcVillagers.ID,"companion_movement");
    public static final int MIN_RECOVERY_TICKS=3*60*20, MAX_RECOVERY_TICKS=5*60*20;
    public static void movement(Villager v) {
        var speed=v.getAttribute(Attributes.MOVEMENT_SPEED);
        if(Companions.active(v)) {
            if(!speed.hasModifier(MOVEMENT))speed.addTransientModifier(new AttributeModifier(MOVEMENT,-0.25,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else speed.removeModifier(MOVEMENT);
    }
    public static void scheduleRecovery(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        if(d.downed&&d.recoverAt==0)
            d.recoverAt=v.level().getGameTime()+MIN_RECOVERY_TICKS+v.getRandom().nextInt(MAX_RECOVERY_TICKS-MIN_RECOVERY_TICKS+1);
    }
    public static int recoverySeconds(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        if(!d.downed)return 0;
        long remaining=d.recoverAt==0?MIN_RECOVERY_TICKS:Math.max(0,d.recoverAt-v.level().getGameTime());
        return (int)Math.min(Integer.MAX_VALUE,(remaining+19)/20);
    }
    public static void recover(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);
        scheduleRecovery(v);
        if(!d.downed||v.level().getGameTime()<d.recoverAt)return;
        d.downed=false;d.recoverAt=0;d.status="";
        v.setHealth(v.getMaxHealth()*0.5f);v.invulnerableTime=40;
        Protocol.sync(v);
    }
    public static void eat(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);long now=v.level().getGameTime();
        if(!d.active()||d.downed||!v.isAlive()||v.getHealth()>=v.getMaxHealth()||now<d.nextFoodHeal)return;
        for(int slot=8;slot<d.items.getSlots();slot++) {
            var stack=d.items.getStackInSlot(slot);var food=stack.getFoodProperties(v);
            if(stack.isEmpty()||food==null)continue;
            d.nextFoodHeal=now+Settings.FOOD_INTERVAL.get();
            ItemStack serving=d.items.extractItem(slot,1,false);
            ItemStack remainder=serving.finishUsingItem(v.level(),v);
            // Ordinary food components return their bowls via Player.eat; villagers need that step too.
            if(remainder.isEmpty())remainder=food.usingConvertsTo().map(ItemStack::copy).orElse(ItemStack.EMPTY);
            remainder=d.items.insertItem(slot,remainder,false);
            for(int i=8;i<d.items.getSlots()&&!remainder.isEmpty();i++)remainder=d.items.insertItem(i,remainder,false);
            if(!remainder.isEmpty())v.spawnAtLocation(remainder);
            if(v.isAlive()&&!d.downed)v.heal(Math.max(1,food.nutrition()));
            return;
        }
    }
}
