package dev.krcvillagers.test;

import dev.krcvillagers.KrcVillagers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(KrcVillagers.ID)
@PrefixGameTestTemplate(false)
public final class HenshinNetworkTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void dedicatedServerKeepsAllHenshinPacketRegistrations(GameTestHelper h) throws ReflectiveOperationException {
        String[] clientbound={"MobSkillAnimationPacket","PlayerAnimationPacket","StopPlayerAnimationPacket",
                "FormListSyncPacket","KickModeSyncPacket","FormSwitchResultPacket","AmazonShockwavePayload",
                "AmazonKickLockPayload","KuugaKickLockPayload","KuugaStabPosePacket"};
        String[] serverbound={"FormSwitchRequestPacket","FormDeleteRequestPacket","ActionRequestPacket",
                "FaizEmptySlashPacket","BladeEmptySlashPacket","AgitoEmptySlashPacket"};
        for(String name:clientbound)registered(h,name,PacketFlow.CLIENTBOUND);
        for(String name:serverbound)registered(h,name,PacketFlow.SERVERBOUND);
        h.succeed();
    }
    private static void registered(GameTestHelper h,String name,PacketFlow flow) throws ReflectiveOperationException {
        var packet=Class.forName("com.example.generichenshin.network."+name);
        var type=(CustomPacketPayload.Type<?>)packet.getField("TYPE").get(null);
        h.assertTrue(NetworkRegistry.getCodec(type.id(),ConnectionProtocol.PLAY,flow)!=null,
                "dedicated server retains "+flow+" codec for "+name);
    }
}
