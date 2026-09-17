package dev.krcvillagers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
@EventBusSubscriber(modid=KrcVillagers.ID)
public final class Events {
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract e) {
        if(!(e.getTarget() instanceof Villager v) || !e.getEntity().isShiftKeyDown()) return;
        e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS);
        if(e.getHand()!=InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) return;
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new CompanionMenu(id,inv,v,true),Component.translatable("screen.krc_villagers.title")),buf->buf.writeVarInt(v.getId()));
        v.setTradingPlayer(p); v.getNavigation().stop();
        Protocol.sync(v); Protocol.details(p,v);
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post e) { if(e.getEntity() instanceof Villager v && !v.level().isClientSide()) Companions.tick(v); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void incoming(LivingIncomingDamageEvent e) {
        var attacker=e.getSource().getEntity();
        if(attacker==null && e.getSource().getDirectEntity() instanceof Projectile projectile) attacker=projectile.getOwner();
        if(attacker instanceof Villager v && Companions.active(v) && !Companions.hostile(v,e.getEntity())) { e.setCanceled(true); return; }
        if(e.getEntity() instanceof Villager v && Companions.active(v) && (v.getData(KrcVillagers.COMPANION).downed || v.getData(KrcVillagers.COMPANION).timeStopOwned && v.getData(KrcVillagers.COMPANION).timeBullet)
                && !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void lethal(LivingDamageEvent.Pre e) {
        if(e.getEntity() instanceof Villager v && Companions.active(v) && e.getNewDamage()>=v.getHealth()
                && !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) { e.setNewDamage(0); Companions.down(v); }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e) {
        if(e.getEntity() instanceof Villager v && Companions.active(v) && !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) { e.setCanceled(true); Companions.down(v); }
    }
    @SubscribeEvent public static void track(PlayerEvent.StartTracking e) {
        if(e.getTarget() instanceof Villager v && e.getEntity() instanceof ServerPlayer p && Companions.active(v)) Protocol.syncTo(p,v);
    }
    @SubscribeEvent public static void join(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof Villager v && !v.level().isClientSide() && Companions.active(v)) {
            var d = v.getData(KrcVillagers.COMPANION);
            Skills.cleanup(v);
            com.kelco.kamenridercraft.abilities.AbilityUtil.cancelAbility(v, "", 0);
            if (d.equipped || d.stage > 0) {
                // The belt receives native component updates; hands belong to the inventory.
                d.items.setStackInSlot(0, v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            }
            CompanionEquipment.bind(v);
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent e) {
        if(e.getEntity() instanceof Villager v && !v.level().isClientSide() && Companions.active(v)) Skills.cleanup(v);
    }
}
