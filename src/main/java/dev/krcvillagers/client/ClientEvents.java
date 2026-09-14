package dev.krcvillagers.client;
import dev.krcvillagers.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=KrcVillagers.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    @SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)r -> CompanionAnimations.invalidate());
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent e) {e.register(KrcVillagers.MENU.get(),CompanionScreen::new);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {e.registerEntityRenderer(EntityType.VILLAGER,CompanionRenderer::new);}
    public static void receive(Protocol.State p) {
        var level=Minecraft.getInstance().level;
        if(level==null||!(level.getEntity(p.entity()) instanceof Villager v)||p.state()==null)return;
        var d=v.getData(KrcVillagers.COMPANION);var n=p.state();
        d.owner=n.hasUUID("Owner")?n.getUUID("Owner"):null;d.downed=n.getBoolean("Downed");d.equipped=n.getBoolean("Equipped");d.stage=n.getInt("Stage");
        d.mode=VillagerCompanionData.Mode.valueOf(n.getString("Mode"));d.policy=VillagerCompanionData.Policy.valueOf(n.getString("Policy"));d.status=n.getString("Status");
        d.disabledSkills.clear();n.getList("Disabled",8).forEach(t->d.disabledSkills.add(t.getAsString()));
        if(Minecraft.getInstance().screen instanceof CompanionScreen screen&&screen.getMenu().villager==v)
            screen.updateSkills(n.getList("Skills",8).stream().map(t->t.getAsString()).toList());
    }
}
