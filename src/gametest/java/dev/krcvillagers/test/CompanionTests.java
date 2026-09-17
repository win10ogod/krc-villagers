package dev.krcvillagers.test;

import dev.krcvillagers.*;
import com.example.generichenshin.compat.KrcCompat;
import com.kelco.kamenridercraft.abilities.AbilityUtil;
import com.kelco.kamenridercraft.attachments.AttachmentTypes;
import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@Mod("krc_villagers_tests")
@GameTestHolder(KrcVillagers.ID)
@PrefixGameTestTemplate(false)
public final class CompanionTests {
    @net.minecraft.gametest.framework.BeforeBatch(batch="defaultBatch")
    public static void cleanTestWorldTime(net.minecraft.server.level.ServerLevel level) {
        // GameTest reuses its disposable world. Previous crashed fixtures may have persisted timers.
        io.github.suel_ki.timeclock.core.data.TimeData.get(level).ifPresent(time -> {
            ((dev.krcvillagers.mixin.TimeDataAccess)time).kv$tickets().clear();
            time.pauseTime(false);time.setVirtualTickrate(20);time.getWhitelist().clear();time.sync(level);
        });
    }
    static net.minecraft.server.level.ServerPlayer serverPlayer(GameTestHelper h) {
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"companion-test");
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {} // no negotiated remote client in a GameTest
            @Override public boolean hasChannel(ResourceLocation id) { return false; }
        };
        player.setGameMode(GameType.CREATIVE);return player;
    }
    static Villager villager(GameTestHelper h) {
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(2,2,2));
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(3));
        return v;
    }
    static void own(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION);d.owner=UUID.randomUUID();d.mode=VillagerCompanionData.Mode.LIFE;
        v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(300);
    }
    static RiderDriverItem belt(String name) {return (RiderDriverItem)BuiltInRegistries.ITEM.get(ResourceLocation.parse("kamenridercraft:"+name));}
    static void equip(Villager v,RiderDriverItem b) {
        var d=v.getData(KrcVillagers.COMPANION);d.items.setStackInSlot(0,new ItemStack(b));
        if(!Henshin.begin(v))throw new IllegalStateException(d.status);
        for(int i=0;i<200&&d.stage>0;i++)Henshin.tick(v);
        v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.IS_TRANSFORMING).setBaseValue(0);
        v.setOnGround(true);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void recruitmentChargesOnceAndKeepsIdentity(GameTestHelper h) {
        var v=villager(h);var id=v.getUUID();var p=h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().add(new ItemStack(Items.EMERALD,16));
        h.assertTrue(Companions.recruit(p,v),"first recruitment");
        h.assertTrue(!Companions.recruit(p,v),"duplicate recruitment denied");
        h.assertTrue(p.getInventory().countItem(Items.EMERALD)==8,"only one payment");
        h.assertTrue(v.getUUID().equals(id)&&v.getVillagerData().getLevel()==3&&v.getVillagerData().getProfession()==VillagerProfession.FARMER,"identity and profession retained");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void childAndInsufficientPaymentAreRejected(GameTestHelper h) {
        var v=villager(h);var p=h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().add(new ItemStack(Items.EMERALD,7));
        h.assertTrue(!Companions.recruit(p,v),"short payment rejected");
        p.getInventory().add(new ItemStack(Items.EMERALD,1));v.setAge(-24000);
        h.assertTrue(!Companions.recruit(p,v)&&p.getInventory().countItem(Items.EMERALD)==8,"child rejected without payment");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void everyRegisteredBeltTransformsTheSameVillager(GameTestHelper h) {
        var v=villager(h);own(v);int count=0;
        for(var item:BuiltInRegistries.ITEM)if(item instanceof RiderDriverItem b){
            equip(v,b);
            h.assertTrue(Henshin.transformed(v),"missing complete suit for "+item);
            Henshin.end(v);
            h.assertTrue(!Henshin.transformed(v)&&v.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"failed dehenshin: "+item);count++;
        }
        h.assertTrue(count>100,"actual KRC belt registry covered");KrcVillagers.LOG.info("ALL_BELTS_VERIFIED {}",count);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void equipmentAndOriginalArmorSurviveRoundTrip(GameTestHelper h) {
        var v=villager(h);own(v);var original=new ItemStack(Items.IRON_HELMET);original.setDamageValue(12);v.setItemSlot(EquipmentSlot.HEAD,original);
        var d=v.getData(KrcVillagers.COMPANION);var sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamageValue(30);d.items.setStackInSlot(6,sword);
        equip(v,belt("arcle"));v.getMainHandItem().setDamageValue(37);Henshin.end(v);
        h.assertTrue(v.getItemBySlot(EquipmentSlot.HEAD).getDamageValue()==12,"original helmet restored");
        h.assertTrue(d.items.getStackInSlot(6).getDamageValue()==37,"weapon wear retained");
        h.assertTrue(d.items.getStackInSlot(0).getItem()==belt("arcle"),"belt returned to escrow");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void persistentDataIncludesItemsPoliciesAndDownedState(GameTestHelper h) {
        var v=villager(h);own(v);var d=v.getData(KrcVillagers.COMPANION);
        d.downed=true;d.mode=VillagerCompanionData.Mode.GUARD;d.policy=VillagerCompanionData.Policy.OFF;d.guard=new BlockPos(10,64,20);d.toggle("rider_kick");
        d.items.setStackInSlot(0,new ItemStack(belt("arcle")));d.items.setStackInSlot(8,new ItemStack(Items.EMERALD,13));
        var copy=new VillagerCompanionData();copy.deserializeNBT(h.getLevel().registryAccess(),d.serializeNBT(h.getLevel().registryAccess()));
        h.assertTrue(copy.owner.equals(d.owner)&&copy.guard.equals(d.guard)&&copy.downed&&copy.mode==d.mode&&copy.policy==d.policy,"persistent state");
        h.assertTrue(!copy.enabled("rider_kick")&&copy.items.getStackInSlot(8).getCount()==13&&copy.items.getStackInSlot(0).getItem()==belt("arcle"),"items and settings persisted");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void hostileSelectionExcludesPlayersVillagersAndPets(GameTestHelper h) {
        var v=villager(h);own(v);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(3,2,2));var civilian=villager(h);
        var wolf=h.spawn(EntityType.WOLF,new BlockPos(1,2,2));wolf.setTame(true,true);
        h.assertTrue(Companions.hostile(v,zombie),"zombie targeted");
        h.assertTrue(!Companions.hostile(v,civilian)&&!Companions.hostile(v,wolf)&&!Companions.hostile(v,h.makeMockPlayer(GameType.SURVIVAL)),"allies excluded");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lethalHitDownsAndOwnerCanRescue(GameTestHelper h) {
        var v=villager(h);var p=h.makeMockPlayer(GameType.SURVIVAL);p.getInventory().add(new ItemStack(Items.EMERALD,12));Companions.recruit(p,v);
        v.hurt(v.damageSources().generic(),1000);
        h.assertTrue(v.isAlive()&&v.getData(KrcVillagers.COMPANION).downed,"lethal hit became downed");
        v.hurt(v.damageSources().generic(),1000);h.assertTrue(v.isAlive(),"downed protection");
        h.assertTrue(Companions.rescue(p,v),"owner rescue");h.assertTrue(v.getHealth()==v.getMaxHealth()*0.5f&&p.getInventory().countItem(Items.EMERALD)==0,"rescue health and cost");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void friendlyDamageIsCanceled(GameTestHelper h) {
        var v=villager(h);own(v);var target=villager(h);float health=target.getHealth();
        target.hurt(v.damageSources().mobAttack(v),5);
        h.assertTrue(target.getHealth()==health,"friendly fire canceled by source owner");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void failedAndDisabledSkillsDoNotDebitEnergy(GameTestHelper h) {
        var v=villager(h);own(v);equip(v,belt("arcle"));var target=h.spawn(EntityType.ZOMBIE,new BlockPos(3,2,2));v.setTarget(target);
        v.getData(KrcVillagers.COMPANION).toggle("rider_punch");
        h.assertTrue(!Skills.start(v,"rider_punch")&&KrcCompat.getAbilityMeter(v)==300,"disabled ability no debit");
        h.assertTrue(!Skills.start(v,"unregistered_fake_skill")&&KrcCompat.getAbilityMeter(v)==300,"unknown ability no debit");
        v.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(0);
        h.assertTrue(!Skills.start(v,"rider_kick")&&KrcCompat.getAbilityMeter(v)==0,"no energy no debit");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void punchDebitsOnceAndKrcExecutesNativeTicks(GameTestHelper h) {
        var v=villager(h);own(v);equip(v,belt("arcle"));var target=h.spawn(EntityType.ZOMBIE,new BlockPos(3,2,2));v.setTarget(target);
        h.assertTrue(KrcCompat.triggerAbility(v,"rider_punch"),"GH bridge starts punch");
        h.assertTrue(KrcCompat.getAbilityMeter(v)==200,"exactly 100 energy debit");
        h.assertTrue(!KrcCompat.triggerAbility(v,"rider_punch")&&KrcCompat.getAbilityMeter(v)==200,"no duplicate cast");
        AbilityUtil.useAbility(v);
        h.assertTrue(v.getData(AttachmentTypes.ABILITY_TICK)>0,"KRC owns ability execution");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void actualTickBrainAndTargetAcquisition(GameTestHelper h) {
        var v=villager(h);own(v);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(3,2,2));zombie.setNoAi(true);zombie.setInvulnerable(true);
        h.runAfterDelay(25,()->{h.assertTrue(v.getTarget()==zombie,"NPC acquired actual hostile target through ticks");h.assertTrue(Companions.controlBrain(v),"combat owns Brain");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void timeStopUsesTimeclockAndCleansUp(GameTestHelper h) {
        var v=villager(h);own(v);equip(v,belt("v_buckle_odin"));
        h.assertTrue(Skills.timeRider(v)!=null,"GH Odin time-stop matching");
        h.assertTrue(Skills.timeStop(v),"TimeClock starts for real villager");
        h.assertTrue(com.example.generichenshin.compat.TimeclockCompat.isTimePaused(v.level()),"time actually paused");
        h.assertTrue(KrcCompat.getAbilityMeter(v)==200,"time-stop cost");
        Skills.cleanup(v);
        h.assertTrue(!com.example.generichenshin.compat.TimeclockCompat.isTimePaused(v.level()),"time restored on cleanup");h.succeed();
    }

    static ItemStack item(String name) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kamenridercraft:"+name))); }
    @GameTest(template="empty",timeoutTicks=100)
    public static void linkedXtremeFormAndPrerequisiteTrialAreInstalled(GameTestHelper h) {
        var v=villager(h);own(v);var d=v.getData(KrcVillagers.COMPANION);
        d.items.setStackInSlot(0,item("wdriver"));
        RiderDriverItem.setFormItem(d.items.getStackInSlot(0), item("metal_memory").getItem(), 2);
        d.items.setStackInSlot(1,item("xtreme_memory"));
        h.assertTrue(Henshin.validateAndSetForms(v).isEmpty(),"Xtreme accepted");
        h.assertTrue(RiderDriverItem.getFormItem(d.items.getStackInSlot(0),2)==item("joker_memory").getItem(),"Xtreme installs linked Joker slot");
        d.items.setStackInSlot(0,item("acceldriver"));d.items.setStackInSlot(1,item("trial_memory"));
        h.assertTrue(Henshin.validateAndSetForms(v).isEmpty(),"Trial checks base BEFORE applying");
        h.assertTrue(Henshin.validateAndSetForms(v).isEmpty(),"installed Trial survives repeated henshin");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void incompatibleFormDoesNotPartiallyMutateBelt(GameTestHelper h) {
        var v=villager(h);own(v);var d=v.getData(KrcVillagers.COMPANION);d.items.setStackInSlot(0,item("arcle"));
        var before=d.items.getStackInSlot(0).copy();d.items.setStackInSlot(1,item("xtreme_memory"));
        h.assertTrue(!Henshin.begin(v),"incompatible form rejected");
        h.assertTrue(ItemStack.isSameItemSameComponents(before,d.items.getStackInSlot(0))&&!d.equipped,"original belt unmodified");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void realEntityReloadRetainsTradesAndWornItemWear(GameTestHelper h) {
        var v=villager(h);own(v);var d=v.getData(KrcVillagers.COMPANION);var id=v.getUUID();
        var trades=v.getOffers().copy();d.items.setStackInSlot(6,new ItemStack(Items.DIAMOND_SWORD));equip(v,belt("arcle"));
        v.getMainHandItem().setDamageValue(63);
        var tag=new net.minecraft.nbt.CompoundTag();v.save(tag);v.discard();
        var restored=(Villager)EntityType.loadEntityRecursive(tag,h.getLevel(),entity->entity);h.getLevel().addFreshEntity(restored);
        h.assertTrue(restored.getUUID().equals(id)&&Henshin.transformed(restored),"same saved entity and suit");
        h.assertTrue(restored.getOffers().size()==trades.size()&&restored.getVillagerData().getLevel()==3,"trades and profession restored");
        restored.getMainHandItem().setDamageValue(71);
        h.assertTrue(restored.getData(KrcVillagers.COMPANION).items.getStackInSlot(6).getDamageValue()==71,"equipment rebound after reload");
        Henshin.end(restored);h.assertTrue(restored.getData(KrcVillagers.COMPANION).items.getStackInSlot(6).getDamageValue()==71,"wear retained on revert");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void formPunchEffectAppliesAndExternalPotionSurvivesRevert(GameTestHelper h) {
        var v=villager(h);own(v);equip(v,belt("arcle"));
        belt("arcle").giveEffects(v);
        h.assertTrue(v.hasEffect(com.kelco.kamenridercraft.effects.EffectCore.PUNCH),"full form punch effect applies to villager");
        v.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.kelco.kamenridercraft.effects.EffectCore.PUNCH,6000,40));
        Henshin.end(v);
        h.assertTrue(v.getEffect(com.kelco.kamenridercraft.effects.EffectCore.PUNCH).getAmplifier()==40,"unrelated stronger potion preserved");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void clockUpCostsFiftyAndRemovesOnlyItsTimeTicket(GameTestHelper h) {
        var v=villager(h);own(v);equip(v,belt("arcle"));
        h.assertTrue(Skills.clockUp(v),"native Clock Up plus real time slowdown");
        h.assertTrue(KrcCompat.getAbilityMeter(v)==250&&com.example.generichenshin.compat.TimeclockCompat.getTimeScale(v.level())<1,"net 50 energy and slowed world");
        AbilityUtil.useAbility(v);Skills.tick(v);
        h.assertTrue(v.getData(AttachmentTypes.USED_ABILITY).isEmpty()&&v.getData(AttachmentTypes.ABILITY_TICK)==0,"casting lock clears for kick during acceleration");
        Skills.cleanup(v);
        h.assertTrue(com.example.generichenshin.compat.TimeclockCompat.getTimeScale(v.level())==1,"world speed restored");
        h.assertTrue(TimePowers.available(v),"native timer removed as well");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void timeSkillRefusesOverlapAndPreservesExistingWhitelist(GameTestHelper h) {
        var first=villager(h);own(first);equip(first,belt("v_buckle_odin"));
        var second=villager(h);own(second);equip(second,belt("v_buckle_odin"));
        var time=io.github.suel_ki.timeclock.core.data.TimeData.get(h.getLevel()).orElseThrow();time.addToWhitelist(first);
        h.assertTrue(Skills.timeStop(first),"first caster starts");
        h.assertTrue(!Skills.timeStop(second)&&KrcCompat.getAbilityMeter(second)==300,"overlap rejected without energy loss");
        Skills.cleanup(first);h.assertTrue(time.isInWhiteList(first),"pre-existing whitelist membership kept");time.removeFromWhitelist(first);
        h.assertTrue(Skills.timeStop(second),"second caster can start after cleanup");Skills.cleanup(second);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void twoPlayersCannotControlOrTakeEachOthersCompanion(GameTestHelper h) {
        var v=villager(h);var owner=serverPlayer(h);var stranger=serverPlayer(h);
        owner.teleportTo(v.getX(),v.getY(),v.getZ());stranger.teleportTo(v.getX(),v.getY(),v.getZ());
        Companions.recruit(owner,v);var d=v.getData(KrcVillagers.COMPANION);d.items.setStackInSlot(0,item("arcle"));
        var foreign=new CompanionMenu(8,stranger.getInventory(),v,true);stranger.containerMenu=foreign;
        Protocol.handle(stranger,new Protocol.Action(8,v.getId(),"mode","GUARD"));
        h.assertTrue(d.mode==VillagerCompanionData.Mode.FOLLOW&&!foreign.getSlot(0).mayPickup(stranger),"foreign settings and extraction denied");
        owner.containerMenu=new CompanionMenu(9,owner.getInventory(),v,true);
        Protocol.handle(owner,new Protocol.Action(8,v.getId(),"mode","GUARD"));h.assertTrue(d.mode==VillagerCompanionData.Mode.FOLLOW,"wrong window rejected");
        Protocol.handle(owner,new Protocol.Action(9,v.getId(),"mode","GUARD"));h.assertTrue(d.mode==VillagerCompanionData.Mode.GUARD,"owner can select guard");
        owner.teleportTo(v.getX()+100,v.getY(),v.getZ());Protocol.handle(owner,new Protocol.Action(9,v.getId(),"mode","LIFE"));
        h.assertTrue(d.mode==VillagerCompanionData.Mode.GUARD,"remote stale menu rejected");
        owner.containerMenu=owner.inventoryMenu;stranger.containerMenu=stranger.inventoryMenu;
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lifeModeReturnsControlToVanillaBrain(GameTestHelper h) {
        var v=villager(h);own(v);h.assertTrue(!Companions.controlBrain(v),"idle village life runs vanilla work and rest brain");
        v.getData(KrcVillagers.COMPANION).mode=VillagerCompanionData.Mode.GUARD;
        h.assertTrue(Companions.controlBrain(v),"guard mode takes movement control");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeRiderKickDebitsOnceAndBeginsJump(GameTestHelper h) {
        var v=villager(h);own(v);equip(v,belt("arcle"));
        var target=h.spawn(EntityType.HUSK,new BlockPos(3,2,2));target.setHealth(8);v.setTarget(target);
        h.assertTrue(KrcCompat.triggerAbility(v,"rider_kick"),"GH bridge starts real native kick");
        h.assertTrue(KrcCompat.getAbilityMeter(v)==150,"exactly 150 energy debit");
        AbilityUtil.useAbility(v);h.assertTrue(v.getData(AttachmentTypes.ABILITY_TICK)==1,"native jump sequence begins");
        h.assertTrue(!KrcCompat.triggerAbility(v,"rider_kick")&&KrcCompat.getAbilityMeter(v)==150,"no duplicate kick debit");h.succeed();
    }
}
