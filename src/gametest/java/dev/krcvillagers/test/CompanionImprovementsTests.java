package dev.krcvillagers.test;

import dev.krcvillagers.*;
import dev.krcvillagers.mixin.NeoBlasterAccess;
import com.kelco.kamenridercraft.item.base_items.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.ai.behavior.UseBonemeal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.krcvillagers.test.CompanionTests.*;

@GameTestHolder(KrcVillagers.ID)
@PrefixGameTestTemplate(false)
public final class CompanionImprovementsTests {
    private static Villager stationary(GameTestHelper h) {
        var v = villager(h); own(v); v.setNoAi(true);
        var d = v.getData(KrcVillagers.COMPANION); d.policy = VillagerCompanionData.Policy.ON; d.autoForms = false;
        return v;
    }
    static class Farming extends UseBonemeal {
        void display(ServerLevel level, Villager v) { super.start(level, v, 0); }
        void finish(ServerLevel level, Villager v) { super.stop(level, v, 0); }
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void allRegisteredWeaponsSurviveTradeAndFarmingDisplays(GameTestHelper h) {
        var v = stationary(h); var d = v.getData(KrcVillagers.COMPANION);
        var trade = new ShowTradesToPlayer(10, 20); var farming = new Farming(); int count = 0;
        for (var item : BuiltInRegistries.ITEM) if (item instanceof SwordItem || item instanceof DiggerItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof NeoBaseBlasterItem || item instanceof BaseSwordItem) {
            var weapon = new ItemStack(item); if (weapon.isDamageableItem()) weapon.setDamageValue(7);
            d.items.setStackInSlot(6, weapon); CompanionEquipment.bind(v);
            trade.stop(h.getLevel(), v, 0);
            h.assertTrue(v.getMainHandItem() == weapon, "trade stop erased " + item);
            farming.display(h.getLevel(), v); farming.finish(h.getLevel(), v);
            h.assertTrue(v.getMainHandItem() == weapon && d.items.getStackInSlot(6) == weapon, "farm display erased " + item);
            if (weapon.isDamageableItem()) h.assertTrue(weapon.getDamageValue() == 7, "display reset wear " + item);
            count++;
        }
        h.assertTrue(count > 100, "actual weapon registry covered"); KrcVillagers.LOG.info("WEAPONS_RETAINED {}", count); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualTradePreviewKeepsWeaponAndOrdinaryVillagersStillDisplay(GameTestHelper h) {
        var v = stationary(h); var p = serverPlayer(h); p.teleportTo(v.getX(), v.getY(), v.getZ());
        var offer = v.getOffers().getFirst(); p.setItemSlot(EquipmentSlot.MAINHAND, offer.getCostA().copy());
        var sword = new ItemStack(Items.DIAMOND_SWORD); v.getData(KrcVillagers.COMPANION).items.setStackInSlot(6, sword); CompanionEquipment.bind(v);
        var trade = new ShowTradesToPlayer(20, 40); v.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, p);
        trade.start(h.getLevel(), v, 0); trade.tick(h.getLevel(), v, 1);
        h.assertTrue(v.getMainHandItem() == sword, "actual offer display preserves assigned hand");
        trade.stop(h.getLevel(), v, 2);
        var civilian = villager(h); civilian.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD));
        trade.stop(h.getLevel(), civilian, 3);
        h.assertTrue(civilian.getMainHandItem().isEmpty(), "unrecruited vanilla display still clears"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void staleEmptyHandReloadAndReleaseReturnWeaponExactlyOnce(GameTestHelper h) {
        var v = villager(h); var p = serverPlayer(h); p.teleportTo(v.getX(), v.getY(), v.getZ());
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD)); Companions.recruit(p, v);
        var d = v.getData(KrcVillagers.COMPANION); var sword = new ItemStack(Items.DIAMOND_SWORD); sword.setDamageValue(41);
        d.items.setStackInSlot(6, sword); equip(v, belt("arcle"));
        v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        var n = new net.minecraft.nbt.CompoundTag(); v.save(n); v.discard();
        var restored = (Villager)EntityType.loadEntityRecursive(n, h.getLevel(), entity -> entity); h.getLevel().addFreshEntity(restored);
        h.assertTrue(restored.getMainHandItem().is(Items.DIAMOND_SWORD) && restored.getMainHandItem().getDamageValue() == 41, "escrow survives stale empty entity hand");
        Companions.down(restored); h.assertTrue(restored.getMainHandItem().is(Items.DIAMOND_SWORD), "downed retains weapon");
        Companions.rescue(p, restored); Companions.release(p, restored); Companions.release(p, restored);
        h.assertTrue(p.getInventory().countItem(Items.DIAMOND_SWORD) == 1 && restored.getMainHandItem().is(Items.BREAD), "one return and original hand restored"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void brokenAssignedWeaponIsNotResurrected(GameTestHelper h) {
        var v = stationary(h); var d = v.getData(KrcVillagers.COMPANION); var sword = new ItemStack(Items.WOODEN_SWORD);
        d.items.setStackInSlot(6, sword); equip(v, belt("arcle")); sword.setDamageValue(sword.getMaxDamage()-1);
        sword.hurtAndBreak(2, v, EquipmentSlot.MAINHAND); CompanionEquipment.bind(v); Henshin.end(v);
        h.assertTrue(v.getMainHandItem().isEmpty() && d.items.getStackInSlot(6).isEmpty(), "actual break consumes weapon exactly once"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void gaimSaturationAndGoldenRegenerationActuallyHeal(GameTestHelper h) {
        var basic = stationary(h); var golden = stationary(h); var plain = stationary(h);
        for (var v : java.util.List.of(basic, golden, plain)) {
            v.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100); v.setHealth(10);
        }
        equip(basic, belt("sengoku_driver_gaim"));
        golden.getData(KrcVillagers.COMPANION).items.setStackInSlot(1, item("golden_ringo_lockseed")); equip(golden, belt("sengoku_driver_gaim"));
        equip(plain, belt("arcle"));
        h.runAfterDelay(100, () -> {
            h.assertTrue(basic.getHealth() > 10, "base Gaim saturation heals an actual villager over time: " + basic.getHealth());
            h.assertTrue(golden.getHealth() > basic.getHealth(), "regeneration adds healing beyond saturation: " + golden.getHealth()+" / "+basic.getHealth());
            h.assertTrue(plain.getHealth() == 10, "armor without healing receives no free regeneration");
            Henshin.end(golden); golden.getData(KrcVillagers.COMPANION).policy = VillagerCompanionData.Policy.OFF;
            float after = golden.getHealth();
            h.runAfterDelay(30, () -> { h.assertTrue(golden.getHealth() == after, "armor healing stops after dehenshin"); h.succeed(); });
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void reserveFormsSelectHealingAndHonorCooldownAndOffSwitch(GameTestHelper h) {
        var v = stationary(h); var d = v.getData(KrcVillagers.COMPANION); d.autoForms = true;
        d.items.setStackInSlot(8, item("golden_ringo_lockseed")); d.items.setStackInSlot(9, item("black_ringo_lockseed"));
        equip(v, belt("sengoku_driver_gaim")); v.setHealth(5);
        d.timeStopOwned = true;
        h.assertTrue(!AutoForms.tick(v), "automatic form switch must not interrupt a time ability");
        d.timeStopOwned = false;
        h.assertTrue(AutoForms.tick(v), "health selects a reserve form");
        h.assertTrue(RiderDriverItem.getFormItem(d.items.getStackInSlot(0), 1) == item("black_ringo_lockseed").getItem(), "stronger owned regeneration chosen");
        h.assertTrue(d.items.getStackInSlot(8).getCount() == 1 && d.items.getStackInSlot(9).getCount() == 1, "switch never consumes or duplicates form items");
        h.assertTrue(!AutoForms.tick(v), "animation and cooldown prevent oscillation");
        d.autoForms = false; d.nextFormSwitch = 0; d.stage = 0;
        h.assertTrue(!AutoForms.tick(v), "off switch holds current form");
        var copy = new VillagerCompanionData(); copy.deserializeNBT(h.getLevel().registryAccess(), d.serializeNBT(h.getLevel().registryAccess()));
        h.assertTrue(!copy.autoForms && copy.handsManaged && copy.items.getStackInSlot(9).getCount() == 1, "preferences and reserves persist"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void kiwamiRequiresEntrustedKachidokiPrerequisite(GameTestHelper h) {
        var v = stationary(h); var d = v.getData(KrcVillagers.COMPANION); d.autoForms = true; v.setHealth(5);
        d.items.setStackInSlot(8, item("kiwami_lockseed")); equip(v, belt("sengoku_driver_gaim"));
        h.assertTrue(!AutoForms.tick(v), "cannot invent Kachidoki prerequisite");
        d.items.setStackInSlot(9, item("kachidoki_lockseed"));
        h.assertTrue(AutoForms.tick(v), "owned prerequisite chain reaches Kiwami");
        h.assertTrue(RiderDriverItem.getFormItem(d.items.getStackInSlot(0), 1) == item("kiwami_lockseed").getItem(), "native Kiwami transition installed"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void transformedOwnerCanAddMultipleSameSlotFormsAndAmmo(GameTestHelper h) {
        var v = villager(h); var p = serverPlayer(h); p.teleportTo(v.getX(), v.getY(), v.getZ()); Companions.recruit(p, v);
        equip(v, belt("sengoku_driver_gaim")); var menu = new CompanionMenu(4, p.getInventory(), v, true); p.containerMenu = menu;
        for (String name : java.util.List.of("golden_ringo_lockseed", "black_ringo_lockseed")) {
            p.getInventory().setItem(9, item(name)); h.assertTrue(!menu.quickMoveStack(p, 17).isEmpty(), "shift-transfer reserve " + name);
        }
        var d = v.getData(KrcVillagers.COMPANION);
        h.assertTrue(d.items.getStackInSlot(8).is(item("golden_ringo_lockseed").getItem()) && d.items.getStackInSlot(9).is(item("black_ringo_lockseed").getItem()), "same-slot alternatives stored separately");
        h.assertTrue(!menu.getSlot(6).mayPickup(p) && menu.getSlot(8).mayPickup(p), "equipped gear locked; reserves accessible");
        v.setTradingPlayer(p); v.setHealth(5); v.tickCount = 10; Companions.tick(v);
        h.assertTrue(RiderDriverItem.getFormItem(d.items.getStackInSlot(0), 1) == item("black_ringo_lockseed").getItem(),
                "open management window must not block automatic healing form selection");
        Protocol.handle(p, new Protocol.Action(4, v.getId(), "auto_forms", "")); h.assertTrue(!d.autoForms, "owner toggle reaches server");
        p.containerMenu = p.inventoryMenu; h.succeed();
    }
    private static void ranged(GameTestHelper h, ItemStack weapon, boolean supplies) {
        var v = h.spawn(EntityType.VILLAGER, new BlockPos(4,2,5)); own(v);
        var d = v.getData(KrcVillagers.COMPANION); d.mode = VillagerCompanionData.Mode.GUARD; d.policy = VillagerCompanionData.Policy.OFF;
        Companions.setGuard(v); d.items.setStackInSlot(6, weapon); if (supplies) d.items.setStackInSlot(8, new ItemStack(Items.ARROW, 16));
        var target = h.spawn(EntityType.HUSK, new BlockPos(13,2,5)); target.setNoAi(true); target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
        h.runAfterDelay(100, () -> {
            h.assertTrue(target.getHealth() < 200, "actual native projectile hit with " + weapon + " health=" + target.getHealth());
            h.assertTrue(v.distanceToSqr(target) > 16, "ranged shot did not rely on melee");
            h.assertTrue(weapon.getDamageValue() > 0 && v.getMainHandItem() == d.items.getStackInSlot(6), "native durability retained");
            if (supplies) h.assertTrue(d.items.getStackInSlot(8).getCount() < 16, "real ammo consumed");
            h.succeed();
        });
    }
    @GameTest(template="arena",timeoutTicks=150)
    public static void bowFiresActualArrowsAndConsumesAmmo(GameTestHelper h) { ranged(h, new ItemStack(Items.BOW), true); }
    @GameTest(template="arena",timeoutTicks=150)
    public static void crossbowLoadsAndFiresActualArrows(GameTestHelper h) { ranged(h, new ItemStack(Items.CROSSBOW), true); }
    @GameTest(template="arena",timeoutTicks=150)
    public static void gaimMusouSaberUsesNativeBlasterProjectiles(GameTestHelper h) { ranged(h, item("musou_saber"), false); }
    @GameTest(template="arena",timeoutTicks=150)
    public static void neoBlasterUsesItsRealLaserOrArrow(GameTestHelper h) {
        var gun = BuiltInRegistries.ITEM.stream().filter(i -> i instanceof NeoBaseBlasterItem && ((NeoBlasterAccess)i).kv$gunMode()
                && ((NeoBlasterAccess)i).kv$projectile().equals("laser")).findFirst().orElseThrow();
        KrcVillagers.LOG.info("NEO_BLASTER_TEST {}", gun); ranged(h, new ItemStack(gun), false);
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void noAmmoAndAlliesBlockRangedFire(GameTestHelper h) {
        var v = stationary(h); var d = v.getData(KrcVillagers.COMPANION); d.policy = VillagerCompanionData.Policy.OFF;
        d.items.setStackInSlot(6, new ItemStack(Items.BOW)); CompanionEquipment.bind(v);
        var target = h.spawn(EntityType.HUSK, new BlockPos(12,2,2)); target.setNoAi(true);
        h.assertTrue(!RangedCombat.tick(v, target), "empty bow cannot create free ammunition");
        var ally = h.spawn(EntityType.VILLAGER, new BlockPos(7,2,2)); ally.setNoAi(true);
        d.items.setStackInSlot(8, new ItemStack(Items.ARROW, 4));
        for (int i = 0; i < 30; i++) RangedCombat.tick(v, target);
        h.assertTrue(d.items.getStackInSlot(8).getCount() == 4 && v.getMainHandItem().getDamageValue() == 0, "friendly in firing line blocks shots"); h.succeed();
    }
    @GameTest(template="arena",timeoutTicks=320)
    public static void followGuardAndLifeVillagersEscapeDeepWater(GameTestHelper h) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 1; y <= 4; y++)
            h.setBlock(new BlockPos(x,y,z), x == 1 || x == 11 || z == 1 || z == 11 ? Blocks.STONE : Blocks.WATER);
        var swimmers = new java.util.ArrayList<Villager>(); int x = 4;
        for (var mode : VillagerCompanionData.Mode.values()) {
            var v = h.spawn(EntityType.VILLAGER, new BlockPos(x++,1,6)); own(v); var d = v.getData(KrcVillagers.COMPANION);
            d.mode = mode; d.policy = VillagerCompanionData.Policy.OFF; Companions.setGuard(v); v.setAirSupply(100); swimmers.add(v);
        }
        h.runAfterDelay(240, () -> {
            for (var v : swimmers) {
                h.assertTrue(v.getHealth() == v.getMaxHealth() && !v.getData(KrcVillagers.COMPANION).downed, "swimmer avoided drowning in " + v.getData(KrcVillagers.COMPANION).mode);
                h.assertTrue(!v.isInWater() && v.getAirSupply() == v.getMaxAirSupply(), "swimmer reached dry shore: " + v.position());
            }
            h.succeed();
        });
    }
    @GameTest(template="arena",timeoutTicks=100)
    public static void neoMagazinesArePerStackAndSurviveSerialization(GameTestHelper h) {
        var gun = BuiltInRegistries.ITEM.stream().filter(i -> i instanceof NeoBaseBlasterItem && ((NeoBlasterAccess)i).kv$gunMode()
                && ((NeoBlasterAccess)i).kv$projectile().equals("laser") && ((NeoBlasterAccess)i).kv$drawTime() == 0
                && ((NeoBlasterAccess)i).kv$ammo() > 1).findFirst().orElseThrow();
        var first = stationary(h); var second = h.spawn(EntityType.VILLAGER, new BlockPos(2,2,6)); own(second); second.setNoAi(true);
        var target = h.spawn(EntityType.HUSK, new BlockPos(12,2,4)); target.setNoAi(true);
        var a = new ItemStack(gun); var b = new ItemStack(gun);
        first.getData(KrcVillagers.COMPANION).items.setStackInSlot(6, a); second.getData(KrcVillagers.COMPANION).items.setStackInSlot(6, b);
        CompanionEquipment.bind(first); CompanionEquipment.bind(second);
        int draw = ((NeoBlasterAccess)gun).kv$drawTick();
        RangedCombat.tick(first, target); RangedCombat.tick(first, target);
        h.assertTrue(a.getDamageValue() == 1 && b.getDamageValue() == 0, "first gun fires once, respects cooldown and leaves second gun untouched");
        RangedCombat.tick(second, target);
        h.assertTrue(b.getDamageValue() == 1 && ((NeoBlasterAccess)gun).kv$drawTick() == draw, "second villager can fire independently; global draw state restored");
        var loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag)a.saveOptional(h.getLevel().registryAccess()));
        h.assertTrue(loaded.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("krc_villagers_gun").getInt("Ammo") == ((NeoBlasterAccess)gun).kv$ammo()-1,
                "native magazine capacity and remaining rounds persist with weapon"); h.succeed();
    }
    @GameTest(template="empty",batch="healingRules",timeoutTicks=100)
    public static void saturationHealingHonorsNaturalRegenerationRule(GameTestHelper h) {
        var rule = h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION);
        boolean before = rule.get(); rule.set(false, h.getLevel().getServer());
        var v = stationary(h); equip(v, belt("sengoku_driver_gaim")); v.setHealth(5);
        h.runAfterDelay(60, () -> {
            try {
                h.assertTrue(v.getHealth() == 5 && !AutoForms.needsRecovery(v), "saturation respects disabled natural regeneration"); h.succeed();
            } finally { rule.set(before, h.getLevel().getServer()); }
        });
    }
}
