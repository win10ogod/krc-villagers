package dev.krcvillagers.client;

import dev.krcvillagers.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class CompanionScreen extends AbstractContainerScreen<CompanionMenu> {
    private List<String> skills=List.of("rider_punch","rider_kick");
    private final List<Button> ownedButtons=new ArrayList<>();
    private Button recruit,rescue,release,autoForms;
    private int skillOffset;
    private float uiScale=1;
    private final Map<String,Button> skillButtons=new LinkedHashMap<>();
    public CompanionScreen(CompanionMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=398;imageHeight=280;inventoryLabelX=16;inventoryLabelY=148;titleLabelX=16;titleLabelY=12;}
    private Component tr(String key){return Component.translatable("screen.krc_villagers."+key);}
    @Override protected void init(){
        int viewportWidth=minecraft.getWindow().getGuiScaledWidth(), viewportHeight=minecraft.getWindow().getGuiScaledHeight();
        uiScale=Math.min(1f,Math.min(viewportWidth/414f,viewportHeight/296f));
        width=(int)Math.ceil(viewportWidth/uiScale);height=(int)Math.ceil(viewportHeight/uiScale);
        super.init();ownedButtons.clear();skillButtons.clear();
        recruit=button("recruit",198,32,182,()->send("recruit",""));
        for(int i=0;i<3;i++){
            final int mode=i;
            ownedButtons.add(button("mode."+i,198+i*62,58,58,()->send("mode",VillagerCompanionData.Mode.values()[mode].name())));
            ownedButtons.add(button("policy."+i,198+i*62,84,58,()->send("policy",VillagerCompanionData.Policy.values()[mode].name())));
        }
        rescue=button("rescue",198,110,89,()->send("rescue",""));
        release=button("release",291,110,89,()->send("release",""));
        autoForms=button("auto_forms.on",60,78,116,()->send("auto_forms",""));
        autoForms.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("auto_forms.hint")));
        int y=154;
        for(String skill:skills.stream().skip(skillOffset).limit(5).toList()){
            var b=addRenderableWidget(Button.builder(Component.translatable("skill.krc_villagers."+skill),btn->send("toggle",skill)).bounds(leftPos+198,topPos+y,182,18).build());
            skillButtons.put(skill,b);y+=20;
        }
    }
    private Button button(String name,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(tr(name),b->action.run()).bounds(leftPos+x,topPos+y,width,20).build());}
    private void send(String action,String value){PacketDistributor.sendToServer(new Protocol.Action(menu.containerId,menu.villager.getId(),action,value));}
    public void updateSkills(List<String> next){if(!skills.equals(next)){skills=List.copyOf(next);skillOffset=Math.min(skillOffset,Math.max(0,skills.size()-5));rebuildWidgets();}}
    @Override protected void containerTick(){
        super.containerTick();boolean owner=menu.values.get(0)==1;
        autoForms.active=owner&&menu.values.get(4)==0;
        autoForms.setMessage(tr(menu.values.get(12)==1?"auto_forms.on":"auto_forms.off"));
        recruit.active=menu.values.get(0)==0&&!menu.villager.isBaby();
        recruit.setMessage(menu.values.get(0)==0?Component.translatable("screen.krc_villagers.recruit_cost",menu.values.get(8)):tr(owner?"owner":"other_owner"));
        for(var b:ownedButtons)b.active=owner&&menu.values.get(4)==0;
        rescue.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.krc_villagers.rescue_cost",menu.values.get(9))));
        rescue.active=owner&&menu.values.get(4)==1;release.active=owner&&menu.values.get(4)==0;
        for(var e:skillButtons.entrySet()){
            e.getValue().active=owner;
            e.getValue().setMessage(Component.literal(menu.companion.enabled(e.getKey())?"✓ ":"○ ").append(Component.translatable("skill.krc_villagers."+e.getKey())));
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        x/=uiScale;y/=uiScale;
        if(x>=leftPos+198&&x<=leftPos+380&&y>=topPos+140&&y<=topPos+253&&skills.size()>5){
            skillOffset=net.minecraft.util.Mth.clamp(skillOffset-(int)Math.signum(vertical),0,skills.size()-5);rebuildWidgets();return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean mouseClicked(double x,double y,int button){return super.mouseClicked(x/uiScale,y/uiScale,button);}
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(x/uiScale,y/uiScale,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return super.mouseDragged(x/uiScale,y/uiScale,button,dx/uiScale,dy/uiScale);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x/uiScale,y/uiScale);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF17252B);
        g.fill(leftPos+5,topPos+5,leftPos+imageWidth-5,topPos+imageHeight-5,0xFF26383D);
        g.fill(leftPos+188,topPos+26,leftPos+190,topPos+255,0xFF56776B);
        for(var slot:menu.slots){g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF8CA394);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xFF162329);}
        int mode=menu.values.get(1),policy=menu.values.get(2);
        g.fill(leftPos+198+mode*62,topPos+79,leftPos+256+mode*62,topPos+81,0xFF87D9AA);
        g.fill(leftPos+198+policy*62,topPos+105,leftPos+256+policy*62,topPos+107,0xFF87D9AA);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,titleLabelX,titleLabelY,0xFFE3EEE7,false);
        g.drawString(font,tr("belt"),16,32,0xFFA6C9B5,false);g.drawString(font,tr("forms"),52,32,0xFFA6C9B5,false);
        g.drawString(font,tr("hands"),16,68,0xFFA6C9B5,false);g.drawString(font,tr("supplies"),16,104,0xFFA6C9B5,false);
        g.drawString(font,playerInventoryTitle,16,148,0xFFA6C9B5,false);
        g.drawString(font,tr("skills"),198,140,0xFFA6C9B5,false);
        if(skills.size()>5)g.drawString(font,Component.literal((skillOffset+1)+"–"+Math.min(skillOffset+5,skills.size())+" / "+skills.size()),315,140,0xFFA6C9B5,false);
        g.drawString(font,Component.translatable("screen.krc_villagers.stats",menu.values.get(10)/10f,menu.values.get(11)/10f,menu.values.get(6)),16,241,0xFFE3EEE7,false);
        String status=menu.companion.status;
        Component hint=!status.isEmpty()?Component.translatable(status):tr(menu.values.get(5)==1||menu.values.get(3)>0?"unequip_hint":"equipment_hint");
        g.drawString(font,font.substrByWidth(hint,365).getString(),16,260,0xFFF0D89C,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        mx=(int)(mx/uiScale);my=(int)(my/uiScale);
        g.pose().pushPose();g.pose().scale(uiScale,uiScale,1);
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(mx>=leftPos+16&&mx<=leftPos+185&&my>=topPos+238&&my<=topPos+253)
            g.renderTooltip(font,Component.translatable("screen.krc_villagers.cooldown",menu.values.get(7)),mx,my);
        g.pose().popPose();
    }
}
