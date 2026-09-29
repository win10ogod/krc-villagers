package dev.krcvillagers.test;

import dev.krcvillagers.*;
import dev.krcvillagers.client.CompanionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;

/** Connected-client checks for the new recruitment and food rules after the combat smoke flow. */
final class BalanceLive {
    private static int phase,ticks;
    private static volatile boolean verified;
    static boolean tick(Villager v) {
        if(phase==8)return true;
        if(++ticks<30)return false;
        var mc=Minecraft.getInstance();
        switch(phase) {
            case 0 -> {
                mc.getSingleplayerServer().execute(()->{
                    var level=mc.getSingleplayerServer().overworld();var npc=(Villager)level.getEntity(v.getId());
                    for(var enemy:level.getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class,npc.getBoundingBox().inflate(40)))enemy.discard();
                    npc.setNoAi(true);npc.setPos(.5,65,2.5);npc.setHealth(npc.getMaxHealth());
                    npc.getData(KrcVillagers.COMPANION).policy=VillagerCompanionData.Policy.OFF;
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    p.teleportTo(.5,65,5.0);p.setGameMode(GameType.SURVIVAL);
                    p.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));
                    p.getInventory().setItem(2,new ItemStack(Items.BREAD,10));p.getInventory().setItem(3,new ItemStack(Items.DIAMOND_SWORD));
                    p.inventoryMenu.broadcastChanges();
                });next();
            }
            case 1 -> {KeyBindingsLive.defaultShortcut(v);next();}
            case 2 -> {
                check(mc.screen instanceof CompanionScreen,"survival management opens");
                var menu=((CompanionScreen)mc.screen).getMenu();
                check(menu.values.get(8)==24,"third recruitment shown as 24 emeralds after two earlier recruitments");
                LiveClient.capture("22-progressive-recruitment");
                LiveClient.press(Component.translatable("screen.krc_villagers.recruit_cost",24).getString());next();
            }
            case 3 -> {
                var menu=((CompanionScreen)mc.screen).getMenu();
                check(menu.values.get(0)==1&&menu.values.get(8)==28,"successful recruitment advances displayed next price to 28");
                check(mc.player.getInventory().countItem(Items.EMERALD)==40,"server charged exactly 24 emeralds");
                mc.gameMode.handleInventoryMouseClick(menu.containerId,46,0,ClickType.QUICK_MOVE,mc.player);
                mc.gameMode.handleInventoryMouseClick(menu.containerId,47,0,ClickType.QUICK_MOVE,mc.player);
                mc.getSingleplayerServer().execute(()->((Villager)mc.getSingleplayerServer().overworld().getEntity(v.getId())).setHealth(8));next();
            }
            case 4 -> {
                var menu=((CompanionScreen)mc.screen).getMenu();
                check(menu.getSlot(8).getItem().is(Items.BREAD)&&menu.getSlot(8).getItem().getCount()==9,"injured villager consumes one transferred bread");
                check(menu.values.get(10)==130&&menu.getSlot(6).getItem().is(Items.DIAMOND_SWORD),"food heals 5 HP while keeping weapon");
                LiveClient.capture("23-food-healing");next();
            }
            case 5 -> {
                if(ticks<210)return false;
                var menu=((CompanionScreen)mc.screen).getMenu();
                check(menu.values.get(10)==200&&menu.getSlot(8).getItem().getCount()==7,"food heals at intervals and stops when full");
                mc.player.closeContainer();next();
            }
            case 6 -> {mc.gameMode.attack(mc.player,v);next();}
            case 7 -> {
                if(!verified) {
                    mc.getSingleplayerServer().execute(()->{
                        var npc=(Villager)mc.getSingleplayerServer().overworld().getEntity(v.getId());
                        check(npc.getHealth()==npc.getMaxHealth(),"connected player attack cannot damage recruited villager");
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        check(RecruitmentHistory.get(p).count(p.getUUID())==3,"server keeps recruitment history through releases");
                        p.setGameMode(GameType.CREATIVE);verified=true;
                    });return false;
                }
                LiveClient.capture("24-protected-companion");
                System.out.println("KRC_VILLAGERS_BALANCE_OK: progressive recruitment, food consumption/healing and player protection verified");next();return true;
            }
        }
        return false;
    }
    private static void next(){phase++;ticks=0;}
    private static void check(boolean condition,String message) {
        if(!condition)throw new IllegalStateException("LIVE FAILURE: "+message);
        System.out.println("LIVE_CHECK "+message);
    }
}
