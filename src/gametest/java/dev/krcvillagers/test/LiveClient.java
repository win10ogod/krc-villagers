package dev.krcvillagers.test;

import dev.krcvillagers.*;
import dev.krcvillagers.client.CompanionScreen;
import com.kelco.kamenridercraft.attachments.AttachmentTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

/** Runs a real integrated world in a separate X display. Excluded from release JARs. */
@EventBusSubscriber(modid="krc_villagers_tests",value=Dist.CLIENT)
public final class LiveClient {
    private static int phase,ticks;
    private static boolean creating;
    private static volatile int villagerId=-1;
    private static volatile boolean serverSetup;
    private static volatile String ability="";
    private static int captures;
    private static volatile boolean observedTimeStop, verifiedCleanup;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("krcvillagers.uiSmoke"))return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("krc-villagers-live-"+System.currentTimeMillis(),
                    new LevelSettings("KRC Villagers Live Verification",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(123456789L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            return;
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(phase==0){
            phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();level.setDayTime(6000);
                var p=level.getServer().getPlayerList().getPlayers().getFirst();
                for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++)level.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE_BRICKS.defaultBlockState());
                p.teleportTo(0.5,65,6.5);p.setYRot(180);p.setXRot(7);
                p.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));p.getInventory().setItem(1,new ItemStack(CompanionTests.belt("arcle")));p.getInventory().setChanged();
                var v=EntityType.VILLAGER.create(level);v.setPos(0.5,65,2.5);v.setYRot(0);v.setNoAi(true);
                v.setCustomName(Component.literal("Rider Villager"));v.setCustomNameVisible(true);
                v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));level.addFreshEntity(v);villagerId=v.getId();serverSetup=true;
            });return;
        }
        if(!serverSetup||!(mc.level.getEntity(villagerId) instanceof Villager v))return;
        if(++ticks<30)return;
        switch(phase){
            case 1 -> {mc.player.setYRot(180);mc.player.setXRot(7);mc.gameMode.interact(mc.player,v,InteractionHand.MAIN_HAND);next();}
            case 2 -> {
                require(mc.player.containerMenu instanceof MerchantMenu,"ordinary right click opens vanilla trades");capture("01-vanilla-trading");mc.player.closeContainer();
                mc.options.keyShift.setDown(true);mc.player.input.shiftKeyDown=true;mc.player.setShiftKeyDown(true);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                mc.gameMode.interact(mc.player,v,InteractionHand.MAIN_HAND);next();
            }
            case 3 -> {
                require(mc.screen instanceof CompanionScreen,"Shift right click opens companion menu");capture("02-recruitment");press(Component.translatable("screen.krc_villagers.recruit_cost",((CompanionScreen)mc.screen).getMenu().values.get(8)).getString());next();
            }
            case 4 -> {
                require(mc.screen instanceof CompanionScreen&&((CompanionScreen)mc.screen).getMenu().values.get(0)==1,"recruitment confirmed by server");
                int window=mc.player.containerMenu.containerId;
                mc.gameMode.handleInventoryMouseClick(window,45,0,ClickType.PICKUP,mc.player);
                mc.gameMode.handleInventoryMouseClick(window,0,0,ClickType.PICKUP,mc.player);next();
            }
            case 5 -> {capture("03-equipment-and-controls");mc.options.guiScale().set(3);mc.resizeDisplay();capture("09-compact-gui-scale-3");press(Component.translatable("screen.krc_villagers.mode.1").getString());press(Component.translatable("screen.krc_villagers.policy.1").getString());next();}
            case 6 -> {
                if(ticks<140)return;require(((CompanionScreen)mc.screen).getMenu().values.get(1)==1,"guard selected through scaled mouse coordinates");mc.options.guiScale().set(2);mc.resizeDisplay();capture("04-transformed-menu");mc.player.closeContainer();mc.options.keyShift.setDown(false);mc.player.input.shiftKeyDown=false;mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                mc.getSingleplayerServer().execute(()->{
                    var level=mc.getSingleplayerServer().overworld();var npc=(Villager)level.getEntity(villagerId);npc.setNoAi(false);npc.setYRot(0);
                });next();
            }
            case 7 -> {
                capture("05-full-rider-armor");mc.getSingleplayerServer().execute(()->{
                    var level=mc.getSingleplayerServer().overworld();var npc=(Villager)level.getEntity(villagerId);
                    var enemy=EntityType.HUSK.create(level);enemy.setPos(0.5,65,0.0);enemy.setNoAi(true);enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(5000);enemy.setHealth(5000);level.addFreshEntity(enemy);
                    npc.setTarget(enemy);
                });next();
            }
            case 8 -> {
                mc.getSingleplayerServer().execute(()->{var npc=(Villager)mc.getSingleplayerServer().overworld().getEntity(villagerId);ability=npc.getData(AttachmentTypes.USED_ABILITY);
                    if(ticks%100==0)System.out.println("LIVE_COMBAT_STATE phase="+phase+" position="+npc.position()+" ready="+Henshin.ready(npc)+" down="+npc.getData(KrcVillagers.COMPANION).downed+" ground="+npc.onGround()+" target="+(npc.getTarget()==null?"none":npc.getTarget().position()+" hp="+npc.getTarget().getHealth())+" energy="+com.example.generichenshin.compat.KrcCompat.getAbilityMeter(npc)+" ability="+ability+" tick="+npc.getData(AttachmentTypes.ABILITY_TICK)+" cooldown="+npc.getData(AttachmentTypes.ABILITY_COOLDOWN));});
                if(!ability.isEmpty()&&captures==0){capture("06-native-combat-"+ability);captures++;}
                if(ticks<180)return;
                mc.getSingleplayerServer().execute(()->{var npc=(Villager)mc.getSingleplayerServer().overworld().getEntity(villagerId);if(npc.getTarget()!=null)npc.getTarget().setHealth(400);npc.setData(AttachmentTypes.ABILITY_COOLDOWN,0);npc.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(300);});next();
            }
            case 9 -> {
                mc.getSingleplayerServer().execute(()->{var npc=(Villager)mc.getSingleplayerServer().overworld().getEntity(villagerId);ability=npc.getData(AttachmentTypes.USED_ABILITY);
                    if(ticks%100==0)System.out.println("LIVE_COMBAT_STATE phase="+phase+" position="+npc.position()+" ready="+Henshin.ready(npc)+" down="+npc.getData(KrcVillagers.COMPANION).downed+" ground="+npc.onGround()+" target="+(npc.getTarget()==null?"none":npc.getTarget().position()+" hp="+npc.getTarget().getHealth())+" energy="+com.example.generichenshin.compat.KrcCompat.getAbilityMeter(npc)+" ability="+ability+" tick="+npc.getData(AttachmentTypes.ABILITY_TICK)+" cooldown="+npc.getData(AttachmentTypes.ABILITY_COOLDOWN));});
                if(!ability.equals("rider_kick")) { if(ticks>=700)require(false,"automatic Rider Kick timeout");return; }
                require(true,"automatic Rider Kick observed from GH AI");capture("07-rider-kick");next();
            }
            case 10 -> {
                if(ticks==30)capture("10-rider-kick-strike");
                if(ticks<160)return;
                mc.getSingleplayerServer().execute(()->{var npc=(Villager)mc.getSingleplayerServer().overworld().getEntity(villagerId);Companions.down(npc);});next();
            }
            case 11 -> {
                capture("08-downed-companion");
                mc.options.keyShift.setDown(true);mc.player.input.shiftKeyDown=true;mc.player.setShiftKeyDown(true);
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                mc.gameMode.interact(mc.player,v,InteractionHand.MAIN_HAND);next();
            }
            case 12 -> {
                require(mc.screen instanceof CompanionScreen,"downed management opens");
                press(Component.translatable("screen.krc_villagers.rescue").getString());next();
            }
            case 13 -> {
                require(((CompanionScreen)mc.screen).getMenu().values.get(4)==0,"owner rescue confirmed by server");
                capture("11-rescued-companion");mc.player.closeContainer();
                mc.options.keyShift.setDown(false);mc.player.input.shiftKeyDown=false;mc.player.setShiftKeyDown(false);
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                mc.getSingleplayerServer().execute(()->{
                    var level=mc.getSingleplayerServer().overworld();var npc=(Villager)level.getEntity(villagerId);
                    Henshin.end(npc);CompanionTests.equip(npc,CompanionTests.belt("v_buckle_odin"));
                    npc.setData(AttachmentTypes.ABILITY_COOLDOWN,0);npc.getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(300);
                    var d=npc.getData(KrcVillagers.COMPANION);d.policy=VillagerCompanionData.Policy.ON;
                    d.disabledSkills.add("rider_punch");d.disabledSkills.add("rider_kick");
                    if(npc.getTarget()==null){var enemy=EntityType.HUSK.create(level);enemy.setPos(npc.getX()+2,65,npc.getZ());enemy.setNoAi(true);level.addFreshEntity(enemy);npc.setTarget(enemy);}
                });next();
            }
            case 14 -> {
                mc.getSingleplayerServer().execute(()->{
                    var level=mc.getSingleplayerServer().overworld();var npc=(Villager)level.getEntity(villagerId);
                    if(npc.getData(KrcVillagers.COMPANION).timeStopOwned&&!npc.getData(KrcVillagers.COMPANION).timeBullet)
                        observedTimeStop=com.example.generichenshin.compat.TimeclockCompat.isTimePaused(level);
                });
                if(!observedTimeStop||!com.example.generichenshin.compat.TimeclockCompat.isTimePaused(mc.level)){if(ticks>300)require(false,"automatic Odin time stop timeout");return;}
                require(true,"automatic Odin time stop reached real connected client");capture("12-odin-time-stop");next();
            }
            case 15 -> {
                require(com.example.generichenshin.compat.TimeclockCompat.isTimePaused(mc.level),"client TimeClock state is paused");
                capture("12-odin-time-stop");
                mc.getSingleplayerServer().execute(()->{
                    var level=mc.getSingleplayerServer().overworld();var npc=(Villager)level.getEntity(villagerId);
                    var p=level.getServer().getPlayerList().getPlayers().getFirst();Companions.release(p,npc);
                    require(!com.example.generichenshin.compat.TimeclockCompat.isTimePaused(level),"release restores world time");
                    require(!Companions.active(npc)&&npc.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty(),"release restores original villager");
                    require(p.getInventory().countItem(CompanionTests.belt("v_buckle_odin"))==1,"release returns exactly one handed-over belt");verifiedCleanup=true;
                });next();
            }
            case 16 -> {
                if(!verifiedCleanup)return;
                require(!com.example.generichenshin.compat.TimeclockCompat.isTimePaused(mc.level),"client TimeClock state restored after release");
                capture("13-released-villager");System.out.println("KRC_VILLAGERS_LIVE_OK: real trade, Shift interaction, recruitment, equipment, scaled mouse controls, full armor, GH automatic punch and kick, rescue, Odin time stop and release verified");mc.stop();phase=17;
            }
            default -> {}
        }
    }
    private static void next(){phase++;ticks=0;}
    private static void press(String label){
        var screen=Minecraft.getInstance().screen;
        var b=screen.children().stream().filter(w->w instanceof Button button&&button.getMessage().getString().startsWith(label)).map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("Missing button "+label));
        require(b.active,"button active: "+label);
        var window=Minecraft.getInstance().getWindow();float scale=Math.min(1f,Math.min(window.getGuiScaledWidth()/414f,window.getGuiScaledHeight()/296f));
        double x=(b.getX()+b.getWidth()/2.0)*scale,y=(b.getY()+b.getHeight()/2.0)*scale;
        require(screen.mouseClicked(x,y,0),"mouse click accepted: "+label);screen.mouseReleased(x,y,0);
    }
    private static void require(boolean condition,String name){if(!condition)throw new IllegalStateException("LIVE FAILURE: "+name);System.out.println("LIVE_CHECK "+name);}
    private static void capture(String name){
        try{var mc=Minecraft.getInstance();Path p=Path.of("../evidence/"+name+".png");Files.createDirectories(p.toAbsolutePath().getParent());try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(p);}System.out.println("LIVE_CAPTURE "+p.toAbsolutePath());}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
