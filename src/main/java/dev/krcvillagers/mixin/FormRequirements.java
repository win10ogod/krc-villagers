package dev.krcvillagers.mixin;
import com.kelco.kamenridercraft.item.base_items.RiderFormChangeItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
@Mixin(value=RiderFormChangeItem.class,remap=false)
public interface FormRequirements {
    @Accessor("needItem") List<Item> kv$neededItems();
    @Accessor("needBaseForm") Boolean kv$needBase();
    @Accessor("needFormSlot1") RiderFormChangeItem kv$needOne();
    @Accessor("needFormSlot2") RiderFormChangeItem kv$needTwo();
    @Accessor("needFormSlot3") RiderFormChangeItem kv$needThree();
    @Accessor("needFormSlot4") RiderFormChangeItem kv$needFour();
    @Accessor("incompatibleForms") List<RiderFormChangeItem> kv$incompatible();
    @Accessor("resetForm") Boolean kv$reset();
    @Accessor("resetToMainForm") Boolean kv$resetMain();
    @Accessor("riderName") String kv$rider();
    @Accessor("setToArmorForm") Boolean kv$armor();
    @Accessor("alsoChange1stSlot") RiderFormChangeItem kv$alsoOne();
    @Accessor("alsoChange2ndSlot") RiderFormChangeItem kv$alsoTwo();
    @Accessor("alsoChange3rdSlot") RiderFormChangeItem kv$alsoThree();
    @Accessor("alsoChange4thSlot") RiderFormChangeItem kv$alsoFour();
    @Accessor("alsoChange5thSlot") RiderFormChangeItem kv$alsoFive();
    @Accessor("alsoUpdateOld") RiderFormChangeItem kv$old();
}
