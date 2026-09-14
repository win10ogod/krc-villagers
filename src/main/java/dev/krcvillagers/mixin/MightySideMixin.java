package dev.krcvillagers.mixin;
import com.example.generichenshin.service.MightyCombatService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** GH 0.1.139 calls Dist.CLIENT.isClient(), a constant true, on a dedicated server. */
@Mixin(value=MightyCombatService.class,remap=false)
public abstract class MightySideMixin {
    @Redirect(method="init",at=@At(value="INVOKE",target="Lnet/neoforged/api/distmarker/Dist;isClient()Z"))
    private static boolean kv$physicalSide(Dist ignored){return FMLEnvironment.dist==Dist.CLIENT;}
}
