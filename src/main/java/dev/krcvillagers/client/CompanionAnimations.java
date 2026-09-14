package dev.krcvillagers.client;

import dev.krcvillagers.*;
import dev.kosmx.playerAnim.api.layered.*;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import me.Thelnfamous1.mobplayeranimator.api.MobAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import java.util.*;

public final class CompanionAnimations {
    private static final Map<String,KeyframeAnimation> CACHE=new HashMap<>();
    private static final Map<Villager,KeyframeAnimationPlayer> ACTIVE=new WeakHashMap<>();
    private static Object resourceManager;
    public static void invalidate() { CACHE.clear(); resourceManager=null; }
    public static void receive(Protocol.Animation p){
        var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(p.entity()) instanceof Villager v))return;
        var layer=MobAnimationAccess.getMobAnimLayer(v);var previous=ACTIVE.remove(v);if(previous!=null)layer.removeLayer(previous);
        if(p.name().isEmpty())return;
        if(resourceManager!=mc.getResourceManager()){
            CACHE.clear();resourceManager=mc.getResourceManager();
        }
        if(!CACHE.containsKey(p.name())){
            var resources=mc.getResourceManager().listResources("player_animations",r->r.getPath().endsWith(".json"));
            for(var entry:resources.entrySet()){
                if(!Set.of("generic_henshin","kamenridercraft").contains(entry.getKey().getNamespace()))continue;
                try(var reader=entry.getValue().openAsReader()){
                    var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    var definitions=json.getAsJsonObject("animations");
                    if(definitions==null||!definitions.has(p.name()))continue;
                    // KRC also ships assets for its newer animation library. Read only the
                    // requested animation, so an unrelated format cannot break a GH action.
                    var selected=new com.google.gson.JsonObject();selected.add(p.name(),definitions.get(p.name()));
                    json.add("animations",selected);
                    for(var anim:AnimationSerializing.deserializeAnimation(new java.io.StringReader(json.toString())))CACHE.put(anim.getName(),anim);
                    if(CACHE.containsKey(p.name()))break;
                }catch(Exception ex){KrcVillagers.LOG.warn("Cannot read animation {}: {}",entry.getKey(),ex.toString());}
            }
        }
        var animation=CACHE.get(p.name());
        if(animation==null){KrcVillagers.LOG.warn("Missing companion animation {}",p.name());return;}
        var player=new KeyframeAnimationPlayer(animation,p.tick(),false);ACTIVE.put(v,player);layer.addAnimLayer(1,player);
    }
}
