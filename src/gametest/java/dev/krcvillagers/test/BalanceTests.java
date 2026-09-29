package dev.krcvillagers.test;

import dev.krcvillagers.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.krcvillagers.test.CompanionTests.*;

@GameTestHolder(KrcVillagers.ID)
@PrefixGameTestTemplate(false)
public final class BalanceTests {
    private static Villager stationary(GameTestHelper h) {
        var v=villager(h);own(v);v.setNoAi(true);v.getData(KrcVillagers.COMPANION).policy=VillagerCompanionData.Policy.OFF;
        return v;
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void recruitmentPricesAreIndependentAndSurviveReleaseAndSerialization(GameTestHelper h) {
        var a=serverPlayer(h);var b=serverPlayer(h);a.setGameMode(GameType.SURVIVAL);b.setGameMode(GameType.SURVIVAL);
        a.getInventory().add(new ItemStack(Items.EMERALD,64));b.getInventory().add(new ItemStack(Items.EMERALD,64));
        var history=RecruitmentHistory.get(a);var v=villager(h);a.setPos(v.position());
        h.assertTrue(history.cost(a.getUUID())==16&&history.cost(b.getUUID())==16,"first cost per UUID");
        h.assertTrue(Companions.recruit(a,v)&&a.getInventory().countItem(Items.EMERALD)==48,"first costs 16");
        h.assertTrue(!Companions.recruit(a,v)&&history.count(a.getUUID())==1,"duplicate does not advance history");
        Companions.release(a,v);
        h.assertTrue(Companions.recruit(a,v)&&a.getInventory().countItem(Items.EMERALD)==28,"release does not reset second cost of 20");
        var third=villager(h);h.assertTrue(Companions.recruit(a,third)&&a.getInventory().countItem(Items.EMERALD)==4,"third costs 24");
        h.assertTrue(Companions.recruit(b,villager(h))&&b.getInventory().countItem(Items.EMERALD)==48,"another player still pays 16");
        var loaded=RecruitmentHistory.load(history.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.count(a.getUUID())==3&&loaded.cost(a.getUUID())==28&&loaded.cost(b.getUUID())==20,"UUID history survives saved-data round trip");
        var menu=new CompanionMenu(1,a.getInventory(),villager(h),true);
        h.assertTrue(menu.values.get(8)==28,"menu uses this player's current price");
        h.succeed();
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void failedRecruitmentDoesNotRaisePriceAndCreativeCounts(GameTestHelper h) {
        var p=serverPlayer(h);p.setGameMode(GameType.SURVIVAL);var history=RecruitmentHistory.get(p);var v=villager(h);
        p.getInventory().add(new ItemStack(Items.EMERALD,15));
        h.assertTrue(!Companions.recruit(p,v)&&history.cost(p.getUUID())==16,"insufficient funds do not raise cost");
        p.getInventory().add(new ItemStack(Items.EMERALD));v.setAge(-100);
        h.assertTrue(!Companions.recruit(p,v)&&history.count(p.getUUID())==0,"child does not advance history");
        v.setAge(0);p.setGameMode(GameType.CREATIVE);
        h.assertTrue(Companions.recruit(p,v)&&history.cost(p.getUUID())==20&&p.getInventory().countItem(Items.EMERALD)==16,"creative is free but still counts a successful recruitment");
        h.succeed();
    }
    @GameTest(template="arena",timeoutTicks=6200)
    public static void downedCompanionActuallyWaitsThreeToFiveMinutes(GameTestHelper h) {
        var v=stationary(h);Companions.down(v);var d=v.getData(KrcVillagers.COMPANION);
        long now=h.getLevel().getGameTime(),wait=d.recoverAt-now;
        h.assertTrue(wait>=3600&&wait<=6000,"random deadline is 3 to 5 game minutes");
        d.items.setStackInSlot(8,new ItemStack(Items.BREAD,4));
        h.runAfterDelay(wait-1,()->{
            h.assertTrue(d.downed&&v.getHealth()==1,"still down before actual deadline");
            h.assertTrue(d.items.getStackInSlot(8).getCount()==4,"food cannot bypass downed recovery");
        });
        h.runAfterDelay(wait+1,()->{
            h.assertTrue(!d.downed&&v.isAlive(),"entity tick stands up after real deadline");
            h.assertTrue(v.getHealth()>=v.getMaxHealth()*0.5f,"automatic recovery gives half health");
            KrcVillagers.LOG.info("AUTO_RECOVERY_WAIT_VERIFIED {} ticks",wait);h.succeed();
        });
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void recoveryDeadlineSurvivesReloadAndOldRescuePacketCannotSkipIt(GameTestHelper h) {
        var p=serverPlayer(h);p.setGameMode(GameType.SURVIVAL);p.getInventory().add(new ItemStack(Items.EMERALD,64));
        var v=villager(h);p.setPos(v.position());Companions.recruit(p,v);Companions.down(v);
        long deadline=v.getData(KrcVillagers.COMPANION).recoverAt;
        var saved=new CompoundTag();v.save(saved);v.discard();
        var restored=(Villager)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);h.getLevel().addFreshEntity(restored);
        var d=restored.getData(KrcVillagers.COMPANION);
        h.assertTrue(d.downed&&d.recoverAt==deadline,"reload preserves remaining recovery time");
        p.containerMenu=new CompanionMenu(8,p.getInventory(),restored,true);
        Protocol.handle(p,new Protocol.Action(8,restored.getId(),"rescue",""));
        h.assertTrue(d.downed&&d.recoverAt==deadline&&p.getInventory().countItem(Items.EMERALD)==48,"old rescue packet has no effect or cost");
        Companions.down(restored);h.assertTrue(d.recoverAt==deadline,"repeat down does not restart timer");
        p.closeContainer();h.succeed();
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void legacyDownedVillagerGetsOneRecoveryDeadline(GameTestHelper h) {
        var v=stationary(h);var d=v.getData(KrcVillagers.COMPANION);d.downed=true;d.recoverAt=0;v.setHealth(1);
        CompanionCare.recover(v);long deadline=d.recoverAt;
        CompanionCare.recover(v);
        h.assertTrue(d.downed&&deadline==d.recoverAt&&deadline>=h.getLevel().getGameTime()+3600,"old saves schedule once without instant revival");h.succeed();
    }
    @GameTest(template="arena",timeoutTicks=140)
    public static void foodHealsAtIntervalsWithoutReplacingWeapon(GameTestHelper h) {
        var v=stationary(h);var d=v.getData(KrcVillagers.COMPANION);d.items.setStackInSlot(6,new ItemStack(Items.DIAMOND_SWORD));
        d.items.setStackInSlot(8,new ItemStack(Items.BREAD,3));CompanionEquipment.bind(v);
        CompanionCare.eat(v);h.assertTrue(d.items.getStackInSlot(8).getCount()==3,"full health does not consume food");
        v.setHealth(5);CompanionCare.eat(v);
        h.assertTrue(v.getHealth()==10&&d.items.getStackInSlot(8).getCount()==2,"one bread heals its nutrition of 5 HP");
        CompanionCare.eat(v);h.assertTrue(v.getHealth()==10&&d.items.getStackInSlot(8).getCount()==2,"no immediate second serving");
        var copy=new VillagerCompanionData();copy.deserializeNBT(h.getLevel().registryAccess(),d.serializeNBT(h.getLevel().registryAccess()));
        h.assertTrue(copy.nextFoodHeal==d.nextFoodHeal,"food cooldown persists");
        h.runAfterDelay(99,()->h.assertTrue(d.items.getStackInSlot(8).getCount()==2,"cooldown lasts 5 seconds"));
        h.runAfterDelay(101,()->{
            h.assertTrue(v.getHealth()==15&&d.items.getStackInSlot(8).getCount()==1,"next serving via entity tick");
            h.assertTrue(v.getMainHandItem().is(Items.DIAMOND_SWORD),"eating does not replace weapon");h.succeed();
        });
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void foodKeepsNativeEffectsAndContainerRemainders(GameTestHelper h) {
        var v=stationary(h);var d=v.getData(KrcVillagers.COMPANION);v.setHealth(2);
        d.items.setStackInSlot(8,new ItemStack(Items.MUSHROOM_STEW));CompanionCare.eat(v);
        h.assertTrue(d.items.getStackInSlot(8).is(Items.BOWL)&&v.getHealth()>2,"stew returns one bowl");
        d.nextFoodHeal=0;d.items.setStackInSlot(8,new ItemStack(Items.GOLDEN_APPLE));CompanionCare.eat(v);
        h.assertTrue(d.items.getStackInSlot(8).isEmpty()&&v.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),"golden apple retains native effects");
        d.nextFoodHeal=0;v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100));
        d.items.setStackInSlot(8,new ItemStack(Items.HONEY_BOTTLE));CompanionCare.eat(v);
        h.assertTrue(d.items.getStackInSlot(8).is(Items.GLASS_BOTTLE)&&!v.hasEffect(net.minecraft.world.effect.MobEffects.POISON),"honey keeps poison cure and returns exactly one bottle");h.succeed();
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void allPlayersAndTheirProjectilesCannotHurtRecruitedVillagers(GameTestHelper h) {
        var owner=serverPlayer(h);var other=serverPlayer(h);var v=villager(h);Companions.recruit(owner,v);
        float health=v.getHealth();owner.setPos(v.position().add(1,0,0));other.setPos(owner.position());
        owner.attack(v);other.attack(v);
        h.assertTrue(v.getHealth()==health,"owner and other player melee prevented");
        var arrow=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),other,new ItemStack(Items.ARROW),null);
        v.hurt(v.damageSources().arrow(arrow,other),10);
        v.hurt(v.damageSources().playerAttack(other),10);
        h.assertTrue(v.getHealth()==health&&!v.getData(KrcVillagers.COMPANION).downed,"player projectiles and direct damage prevented");
        var civilian=villager(h);civilian.hurt(civilian.damageSources().playerAttack(other),4);
        h.assertTrue(civilian.getHealth()<civilian.getMaxHealth(),"unrecruited villager damage unchanged");
        var enemy=h.spawn(EntityType.HUSK,new BlockPos(5,2,2));v.hurt(v.damageSources().mobAttack(enemy),4);
        h.assertTrue(v.getHealth()<health,"hostile mob damage still works");h.succeed();
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void movementIsReducedOnceAndRestoredOnRelease(GameTestHelper h) {
        var p=serverPlayer(h);var v=villager(h);double base=v.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Companions.recruit(p,v);for(int i=0;i<20;i++)CompanionCare.movement(v);
        h.assertTrue(Math.abs(v.getAttributeValue(Attributes.MOVEMENT_SPEED)-base*.75)<1e-6,"25 percent reduction without stacking");
        equip(v,belt("arcle"));CompanionCare.movement(v);
        var speed=v.getAttribute(Attributes.MOVEMENT_SPEED);
        Henshin.end(v);Companions.release(p,v);
        h.assertTrue(Math.abs(speed.getValue()-base)<1e-6,"release removes only companion speed modifier");
        var civilian=villager(h);h.assertTrue(Math.abs(civilian.getAttributeValue(Attributes.MOVEMENT_SPEED)-base)<1e-6,"civilian speed unchanged");h.succeed();
    }
    @GameTest(template="arena",timeoutTicks=160)
    public static void blasterFireRateHasOneSecondFloorAndSurvivesWeaponSwap(GameTestHelper h) { blasterRate(h,item("musou_saber")); }
    @GameTest(template="arena",timeoutTicks=160)
    public static void neoBlasterAlsoRespectsOneSecondFloor(GameTestHelper h) { blasterRate(h,item("gm_01_scorpion")); }
    private static void blasterRate(GameTestHelper h,ItemStack first) {
        var v=stationary(h);v.setTradingPlayer(serverPlayer(h));var d=v.getData(KrcVillagers.COMPANION);var second=first.copy();
        d.items.setStackInSlot(6,first);CompanionEquipment.bind(v);
        var target=h.spawn(EntityType.HUSK,new BlockPos(12,2,2));target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000);target.setHealth(5000);
        long[] last={Long.MIN_VALUE};int[] shots={0};
        h.onEachTick(()->{
            if(h.getTick()>=120)return;
            int before=first.getDamageValue()+second.getDamageValue();
            RangedCombat.tick(v,target);
            int after=first.getDamageValue()+second.getDamageValue();
            if(after>before) {
                long now=h.getLevel().getGameTime();
                h.assertTrue(last[0]==Long.MIN_VALUE||now-last[0]>=20,"at least 20 game ticks between actual shots");
                last[0]=now;shots[0]++;
                var replacement=v.getMainHandItem()==first?second:first;d.items.setStackInSlot(6,replacement);CompanionEquipment.bind(v);
                RangedCombat.tick(v,target);
                h.assertTrue(first.getDamageValue()+second.getDamageValue()==after,"swapping guns cannot bypass interval");
                var copy=new VillagerCompanionData();copy.deserializeNBT(h.getLevel().registryAccess(),d.serializeNBT(h.getLevel().registryAccess()));
                h.assertTrue(copy.nextRangedAttack==d.nextRangedAttack,"shot deadline persists through serialization");
            }
        });
        h.runAfterDelay(121,()->{h.assertTrue(shots[0]>=2&&shots[0]<=6,"sustained shooting must be 2 to 6 shots in 120 ticks; observed="+shots[0]);h.succeed();});
    }
}
