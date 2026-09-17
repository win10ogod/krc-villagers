package dev.krcvillagers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Protocol {
    public record OpenManagement(int entity) implements CustomPacketPayload {
        public static final Type<OpenManagement> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(KrcVillagers.ID,"open_management"));
        public static final StreamCodec<RegistryFriendlyByteBuf,OpenManagement> CODEC=StreamCodec.of(
                (b,p)->b.writeVarInt(p.entity),b->new OpenManagement(b.readVarInt()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Action(int window,int entity,String action,String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(KrcVillagers.ID,"action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{
            b.writeVarInt(p.window); b.writeVarInt(p.entity); b.writeUtf(p.action,32); b.writeUtf(p.value,256);
        }, b->new Action(b.readVarInt(),b.readVarInt(),b.readUtf(32),b.readUtf(256)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record State(int entity,CompoundTag state) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(KrcVillagers.ID,"state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.entity);b.writeNbt(p.state);},b->new State(b.readVarInt(),b.readNbt()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Animation(int entity,String name,int tick) implements CustomPacketPayload {
        public static final Type<Animation> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(KrcVillagers.ID,"animation"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Animation> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.entity);b.writeUtf(p.name);b.writeVarInt(p.tick);},b->new Animation(b.readVarInt(),b.readUtf(),b.readVarInt()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("3");
        registrar.playToServer(OpenManagement.TYPE,OpenManagement.CODEC,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer p) openManagement(p,packet);
        }));
        registrar.playToServer(Action.TYPE,Action.CODEC,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer p) handle(p,packet);
        }));
        registrar.playToClient(State.TYPE,State.CODEC,(packet,context)->context.enqueueWork(()->dev.krcvillagers.client.ClientEvents.receive(packet)));
        registrar.playToClient(Animation.TYPE,Animation.CODEC,(packet,context)->context.enqueueWork(()->dev.krcvillagers.client.CompanionAnimations.receive(packet)));
    }
    public static void openManagement(ServerPlayer p,OpenManagement packet) {
        // Match vanilla entity interaction reach (including its latency allowance).
        // A key binding is client-local; reach, visibility and menu access remain server-authoritative.
        if(!p.isAlive()||p.isSpectator()||p.containerMenu!=p.inventoryMenu
                ||!(p.level().getEntity(packet.entity) instanceof Villager v)||!v.isAlive()
                ||!p.canInteractWithEntity(v,1.0)||!p.hasLineOfSight(v)
                ||v.getTradingPlayer()!=null&&v.getTradingPlayer()!=p) return;
        if(p.openMenu(new SimpleMenuProvider((id,inv,player)->new CompanionMenu(id,inv,v,true),
                Component.translatable("screen.krc_villagers.title")),buf->buf.writeVarInt(v.getId())).isPresent()) {
            v.setTradingPlayer(p);v.getNavigation().stop();
            sync(v);details(p,v);
        }
    }
    public static void handle(ServerPlayer p,Action packet) {
        if(!(p.containerMenu instanceof CompanionMenu menu)||menu.containerId!=packet.window||menu.villager.getId()!=packet.entity||!menu.stillValid(p)) return;
        var v=menu.villager; var d=v.getData(KrcVillagers.COMPANION);
        if(packet.action.equals("recruit")) {
            d.status=Companions.recruit(p,v)?"message.krc_villagers.recruited":v.isBaby()?"message.krc_villagers.adult_only":"message.krc_villagers.cannot_recruit";
        } else if(Companions.owns(p,v)) {
            switch(packet.action) {
                case "mode" -> {
                    d.status="";
                    try { d.mode=VillagerCompanionData.Mode.valueOf(packet.value); Companions.setGuard(v); v.setTarget(null); v.getNavigation().stop(); }
                    catch(IllegalArgumentException ignored) { return; }
                }
                case "policy" -> {
                    d.status="";
                    try { d.policy=VillagerCompanionData.Policy.valueOf(packet.value); }
                    catch(IllegalArgumentException ignored) { return; }
                    if(d.policy==VillagerCompanionData.Policy.OFF) Henshin.end(v);
                    else if(d.policy==VillagerCompanionData.Policy.ON) Henshin.begin(v);
                }
                case "rescue" -> d.status=Companions.rescue(p,v)?"message.krc_villagers.rescued":"message.krc_villagers.cannot_rescue";
                case "release" -> Companions.release(p,v);
                case "toggle" -> { if(Skills.available(v).contains(packet.value)) d.toggle(packet.value); }
                case "auto_forms" -> d.autoForms = !d.autoForms;
                default -> { return; }
            }
        } else return;
        menu.broadcastChanges(); sync(v); details(p,v);
    }
    private static CompoundTag snapshot(Villager v) {
        var d=v.getData(KrcVillagers.COMPANION); var n=new CompoundTag();
        if(d.owner!=null)n.putUUID("Owner",d.owner);
        n.putBoolean("Downed",d.downed);n.putBoolean("Equipped",d.equipped);n.putInt("Stage",d.stage);
        n.putString("Mode",d.mode.name());n.putString("Policy",d.policy.name());n.putString("Status",d.status);
        n.putBoolean("AutoForms",d.autoForms);
        var skills=new ListTag(); Skills.available(v).forEach(s->skills.add(StringTag.valueOf(s))); n.put("Skills",skills);
        var disabled=new ListTag(); d.disabledSkills.forEach(s->disabled.add(StringTag.valueOf(s)));n.put("Disabled",disabled);
        return n;
    }
    public static void sync(Villager v) {
        if(v.level().isClientSide())return;
        PacketDistributor.sendToPlayersTrackingEntity(v,new State(v.getId(),snapshot(v)));
    }
    public static void syncTo(ServerPlayer p,Villager v) {
        PacketDistributor.sendToPlayer(p,new State(v.getId(),snapshot(v)));
        var d=v.getData(KrcVillagers.COMPANION);
        if(d.stage>0&&!d.animation.isEmpty())PacketDistributor.sendToPlayer(p,new Animation(v.getId(),d.animation,d.stage));
    }
    public static void details(ServerPlayer p,Villager v) {syncTo(p,v);}
    public static void animate(Villager v,String name,int tick) {
        if(!v.level().isClientSide())PacketDistributor.sendToPlayersTrackingEntity(v,new Animation(v.getId(),name,tick));
    }
}
