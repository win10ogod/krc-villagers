package dev.krcvillagers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.krcvillagers.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public final class CompanionRenderer extends EntityRenderer<Villager> {
    private final VillagerRenderer ordinary;
    private final RiderRenderer rider;
    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context); ordinary=new VillagerRenderer(context);rider=new RiderRenderer(context);shadowRadius=0.5f;
    }
    private boolean rider(Villager v) {var d=v.getData(KrcVillagers.COMPANION);return d.active()&&(d.equipped||d.stage>0);}
    @Override public void render(Villager v,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        if(v.getData(KrcVillagers.COMPANION).downed) {pose.translate(0,0.25,0);pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(75));}
        if(rider(v))rider.render(v,yaw,partial,pose,buffers,light);else ordinary.render(v,yaw,partial,pose,buffers,light);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(Villager v){return ordinary.getTextureLocation(v);}
    private static final class RiderRenderer extends HumanoidMobRenderer<Villager,HumanoidModel<Villager>> {
        RiderRenderer(EntityRendererProvider.Context c){
            super(c,new HumanoidModel<>(c.bakeLayer(ModelLayers.PLAYER)),0.5f);
            addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<>(c.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),new HumanoidModel<>(c.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),c.getModelManager()));
        }
        @Override public ResourceLocation getTextureLocation(Villager v){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/villager/villager.png");}
        @Override protected void setupRotations(Villager v,PoseStack pose,float age,float yaw,float partial,float scale){
            super.setupRotations(v,pose,age,yaw,partial,scale);
        }
    }
}
