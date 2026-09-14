package dev.krcvillagers;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

@Mod(KrcVillagers.ID)
public final class KrcVillagers {
    public static final String ID = "krc_villagers";
    public static final Logger LOG = LoggerFactory.getLogger(ID);
    private static final DeferredRegister<AttachmentType<?>> DATA = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ID);
    public static final Supplier<AttachmentType<VillagerCompanionData>> COMPANION = DATA.register("companion",
            () -> AttachmentType.serializable(VillagerCompanionData::new).build());
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final Supplier<MenuType<CompanionMenu>> MENU = MENUS.register("companion",
            () -> IMenuTypeExtension.create(CompanionMenu::new));
    public KrcVillagers(IEventBus bus, ModContainer container) {
        DATA.register(bus);
        MENUS.register(bus);
        bus.addListener(Protocol::register);
        bus.addListener(KrcVillagers::attributes);
        container.registerConfig(ModConfig.Type.SERVER, Settings.SPEC);
    }
    private static void attributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.VILLAGER, Attributes.ATTACK_DAMAGE, 2);
        event.add(EntityType.VILLAGER, Attributes.FOLLOW_RANGE, 24);
        event.add(EntityType.VILLAGER, Attributes.ATTACK_KNOCKBACK, 0);
    }
}
