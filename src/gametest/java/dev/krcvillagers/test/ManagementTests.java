package dev.krcvillagers.test;

import dev.krcvillagers.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KrcVillagers.ID)
@PrefixGameTestTemplate(false)
public final class ManagementTests {
    @GameTest(template="arena",timeoutTicks=100)
    public static void reboundManagementOpensWithoutSneakingAndRetainsOwnership(GameTestHelper h) {
        var v=CompanionTests.villager(h);var p=CompanionTests.serverPlayer(h);
        p.setPos(v.position().add(0,0,2));
        Protocol.openManagement(p,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(!p.isShiftKeyDown()&&p.containerMenu instanceof CompanionMenu,"key request opens without sneaking");
        h.assertTrue(v.getTradingPlayer()==p,"management pauses villager trading and AI");
        var menu=p.containerMenu;
        Protocol.openManagement(p,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(p.containerMenu==menu,"repeated key packet does not replace existing menu");
        Protocol.handle(p,new Protocol.Action(menu.containerId,v.getId(),"recruit",""));
        h.assertTrue(Companions.owns(p,v),"recruit still uses authenticated menu actions");
        p.closeContainer();
        var visitor=CompanionTests.serverPlayer(h);visitor.setPos(p.position());
        Protocol.openManagement(visitor,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(visitor.containerMenu instanceof CompanionMenu,"other owner can view management");
        var d=v.getData(KrcVillagers.COMPANION);var mode=d.mode;
        Protocol.handle(visitor,new Protocol.Action(visitor.containerMenu.containerId,v.getId(),"mode","GUARD"));
        h.assertTrue(d.mode==mode&&Companions.owns(p,v),"opening by key does not bypass owner permissions");
        visitor.closeContainer();h.succeed();
    }

    @GameTest(template="arena",timeoutTicks=100)
    public static void managementRejectsInvalidAndDistantTargets(GameTestHelper h) {
        var v=CompanionTests.villager(h);var p=CompanionTests.serverPlayer(h);
        p.setPos(v.position().add(0,0,12));
        Protocol.openManagement(p,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(p.containerMenu==p.inventoryMenu,"remote villager request rejected");
        p.setPos(v.position().add(0,0,2));
        Protocol.openManagement(p,new Protocol.OpenManagement(Integer.MAX_VALUE));
        var cow=h.spawn(EntityType.COW,new BlockPos(3,2,2));
        Protocol.openManagement(p,new Protocol.OpenManagement(cow.getId()));
        v.discard();Protocol.openManagement(p,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(p.containerMenu==p.inventoryMenu,"missing, non-villager and removed targets rejected");h.succeed();
    }

    @GameTest(template="arena",timeoutTicks=100)
    public static void managementRejectsWallsSpectatorsAndBusyVillagers(GameTestHelper h) {
        var v=CompanionTests.villager(h);v.setNoAi(true);
        var p=CompanionTests.serverPlayer(h);p.setPos(v.position().add(0,0,3));
        for(int x=1;x<=3;x++)for(int y=2;y<=4;y++)h.setBlock(new BlockPos(x,y,3),Blocks.STONE);
        Protocol.openManagement(p,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(p.containerMenu==p.inventoryMenu,"wall blocks management request");
        for(int x=1;x<=3;x++)for(int y=2;y<=4;y++)h.setBlock(new BlockPos(x,y,3),Blocks.AIR);
        p.setGameMode(GameType.SPECTATOR);Protocol.openManagement(p,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(p.containerMenu==p.inventoryMenu,"spectator rejected");
        p.setGameMode(GameType.CREATIVE);
        var trader=CompanionTests.serverPlayer(h);v.setTradingPlayer(trader);
        Protocol.openManagement(p,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(p.containerMenu==p.inventoryMenu&&v.getTradingPlayer()==trader,"existing trader retained");
        v.setTradingPlayer(null);p.setHealth(0);Protocol.openManagement(p,new Protocol.OpenManagement(v.getId()));
        h.assertTrue(p.containerMenu==p.inventoryMenu,"dead player rejected");h.succeed();
    }
}
