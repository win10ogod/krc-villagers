package dev.krcvillagers.mixin;
import com.example.generichenshin.service.KickService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.UUID;
@Mixin(value=KickService.class,remap=false)
public interface KickAccess {
    @Invoker("clearMobAbility") static void kv$clear(UUID id) { throw new AssertionError(); }
}
