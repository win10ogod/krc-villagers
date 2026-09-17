package dev.krcvillagers.test;

import dev.krcvillagers.client.CompanionKeys;
import dev.krcvillagers.client.CompanionScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Real key and mouse events inside the smoke test's private Xvfb display. Never loaded in release builds. */
final class KeyBindingsLive {
    private static int phase,ticks;
    private static volatile Throwable inputFailure;
    private static KeyBindsList.KeyEntry entry;

    static void defaultShortcut(Villager v) {
        aim(v);input("",3,"Shift_L");
    }

    static boolean tick(Villager v) {
        if(inputFailure!=null)throw new IllegalStateException("Isolated input failed",inputFailure);
        if(phase==3&&ticks==10){aim(v);input("n",0,"");}
        if(++ticks<30)return false;
        var mc=Minecraft.getInstance();var mapping=CompanionKeys.MANAGE;
        switch(phase) {
            case 0 -> {
                mc.getSingleplayerServer().execute(()->{
                    var npc=(Villager)mc.getSingleplayerServer().overworld().getEntity(v.getId());
                    npc.setNoAi(true);npc.setPos(.5,65,2.5);
                    mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().teleportTo(.5,65,6.5);
                });
                settings();next();
            }
            case 1 -> {
                check(mapping.isDefault(),"native controls lists default Shift + right mouse binding");
                LiveClient.capture("17-default-key-binding");
                clickButton(0);
                var screen=(KeyBindsScreen)mc.screen;
                check(screen.selectedKey==mapping,"native change-binding button selects management action");
                screen.keyPressed(GLFW.GLFW_KEY_N,0,0);screen.keyReleased(GLFW.GLFW_KEY_N,0,0);
                check(mapping.matches(GLFW.GLFW_KEY_N,0)&&mapping.getKeyModifier()==KeyModifier.NONE,"keyboard N replaces default modifier and mouse");
                mapping.setToDefault();mc.options.load();KeyMapping.resetMapping();
                check(mapping.matches(GLFW.GLFW_KEY_N,0)&&mapping.getKeyModifier()==KeyModifier.NONE,"custom key persists after options reload");
                next();
            }
            case 2 -> {
                LiveClient.capture("18-rebound-key-setting");mc.screen.onClose();next();
            }
            case 3 -> {
                System.out.println("LIVE_REBOUND_STATE screen="+mc.screen+" position="+mc.player.position()+" villager="+v.position()+" shift="+mc.player.isShiftKeyDown());
                check(mc.screen instanceof CompanionScreen&&!mc.player.isShiftKeyDown(),"physical rebound key opens management without sneaking");
                LiveClient.capture("19-rebound-management");mc.player.closeContainer();defaultShortcut(v);next();
            }
            case 4 -> {
                check(mc.screen==null,"old Shift + right click no longer opens management after rebinding");
                aim(v);input("",3,"");next();
            }
            case 5 -> {
                check(mc.player.containerMenu instanceof MerchantMenu,"plain right click still opens vanilla trades after rebinding");
                mc.player.closeContainer();settings();next();
            }
            case 6 -> {
                // Restore SHIFT first, then assign a plain mouse button through the actual input callback.
                clickButton(1);check(mapping.isDefault(),"native reset restores default key and modifier");
                clickButton(0);input("",2,"");next();
            }
            case 7 -> {
                check(mapping.matchesMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)&&mapping.getKeyModifier()==KeyModifier.NONE,
                        "plain mouse binding clears previous Shift modifier");
                LiveClient.capture("20-mouse-key-setting");mc.screen.onClose();aim(v);
                input("",2,"");next();
            }
            case 8 -> {
                check(mc.screen instanceof CompanionScreen,"physical middle mouse opens management");
                mc.player.closeContainer();settings();next();
            }
            case 9 -> {
                clickButton(0);var screen=(KeyBindsScreen)mc.screen;
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);screen.keyReleased(GLFW.GLFW_KEY_ESCAPE,0,0);
                check(mapping.isUnbound(),"Escape unbinds management");mc.screen.onClose();aim(v);input("",3,"");next();
            }
            case 10 -> {
                check(mc.player.containerMenu instanceof MerchantMenu,"unbound management leaves vanilla trading available");
                mc.player.closeContainer();settings();next();
            }
            case 11 -> {clickButton(1);check(mapping.isDefault(),"reset after unbinding restores Shift + right click");mc.screen.onClose();defaultShortcut(v);next();}
            case 12 -> {
                check(mc.screen instanceof CompanionScreen,"physical default shortcut works after native reset");
                mc.player.closeContainer();
                mc.hitResult=new net.minecraft.world.phys.BlockHitResult(mc.player.position(),net.minecraft.core.Direction.UP,mc.player.blockPosition(),false);
                check(!mapping.getKeyConflictContext().isActive(),"management binding does not claim non-villager interactions");
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                mc.hitResult=new net.minecraft.world.phys.EntityHitResult(v);
                check(!mapping.getKeyConflictContext().isActive(),"management binding inactive inside inventory screens");
                mc.player.closeContainer();return true;
            }
        }
        return false;
    }

    private static void settings() {
        var mc=Minecraft.getInstance();var screen=new KeyBindsScreen(null,mc.options);mc.setScreen(screen);
        var list=(KeyBindsList)screen.children().stream().filter(w->w instanceof KeyBindsList).findFirst().orElseThrow();
        try {
            var key=KeyBindsList.KeyEntry.class.getDeclaredField("key");key.setAccessible(true);
            for(var row:list.children())if(row instanceof KeyBindsList.KeyEntry candidate&&key.get(candidate)==CompanionKeys.MANAGE) {
                entry=candidate;list.setScrollAmount(Math.max(0,list.children().indexOf(row)*20-100));return;
            }
        } catch(ReflectiveOperationException ex) {throw new IllegalStateException(ex);}
        throw new IllegalStateException("Management binding missing from native controls");
    }

    private static void clickButton(int index) {
        var screen=Minecraft.getInstance().screen;var button=(Button)entry.children().get(index);
        check(button.active,"native binding button active");
        double x=button.getX()+button.getWidth()/2.0,y=button.getY()+button.getHeight()/2.0;
        check(screen.mouseClicked(x,y,0),"native binding button clicked");screen.mouseReleased(x,y,0);
    }

    private static void aim(Villager v) {
        var mc=Minecraft.getInstance();mc.player.lookAt(EntityAnchorArgument.Anchor.EYES,v.getEyePosition());
        mc.mouseHandler.grabMouse();
    }

    private static void input(String key,int mouse,String modifier) {
        new Thread(()->{
            try {
                // Minecraft forces AWT headless mode. Send native XTest events from a child
                // process inheriting only this smoke run's DISPLAY and XAUTHORITY instead.
                var process=new ProcessBuilder("python3","../scripts/live-input.py",key,Integer.toString(mouse),modifier).inheritIO().start();
                if(process.waitFor()!=0)throw new IllegalStateException("Native isolated input process failed");
            } catch(Throwable ex) {ex.printStackTrace();inputFailure=ex;}
        },"isolated-keybinding-input").start();
    }
    private static void next(){phase++;ticks=0;}
    private static void check(boolean condition,String message) {
        if(!condition)throw new IllegalStateException("LIVE FAILURE: "+message);
        System.out.println("LIVE_CHECK "+message);
    }
}
