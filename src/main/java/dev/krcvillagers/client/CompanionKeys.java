package dev.krcvillagers.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.krcvillagers.KrcVillagers;
import dev.krcvillagers.Protocol;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Only claims the shortcut while pointing at a villager; building and other interactions keep their keys. */
@EventBusSubscriber(modid=KrcVillagers.ID,value=Dist.CLIENT)
public final class CompanionKeys {
    private static final IKeyConflictContext TARGET_VILLAGER = new IKeyConflictContext() {
        public boolean isActive() { return target()!=null; }
        public boolean conflicts(IKeyConflictContext other) {
            return other==this||other==KeyConflictContext.IN_GAME||other==KeyConflictContext.UNIVERSAL;
        }
    };
    public static final KeyMapping MANAGE = new KeyMapping("key.krc_villagers.manage",
            TARGET_VILLAGER,KeyModifier.SHIFT,InputConstants.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            "key.categories.krc_villagers");

    private static Villager target() {
        var mc=Minecraft.getInstance();
        if(mc.screen!=null||mc.getOverlay()!=null||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()) return null;
        return mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof Villager v
                &&v.isAlive()&&mc.player.canInteractWithEntity(v,0)?v:null;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        boolean clicked=false;
        while(MANAGE.consumeClick()) clicked=true;
        // The modifier may already be released by this tick, so use the queued click, not isDown().
        var v=target();
        if(clicked&&v!=null)
            PacketDistributor.sendToServer(new Protocol.OpenManagement(v.getId()));
    }

    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        // A binding without a modifier can also trigger vanilla use/attack/pick. Claim only this villager.
        if(MANAGE.isActiveAndMatches(event.getKeyMapping().getKey())) {
            event.setCanceled(true);event.setSwingHand(false);
        }
    }

    @SubscribeEvent public static void mouseBinding(ScreenEvent.MouseButtonPressed.Pre event) {
        // NeoForge 21.1's mouse assignment keeps the previous modifier. Record the actual held
        // modifier for this binding before the native screen saves it, including NONE to clear it.
        if(event.getScreen() instanceof KeyBindsScreen screen&&screen.selectedKey==MANAGE) {
            var modifiers=KeyModifier.getActiveModifiers();
            MANAGE.setKeyModifierAndCode(modifiers.isEmpty()?KeyModifier.NONE:modifiers.getFirst(),
                    InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
        }
    }
}
