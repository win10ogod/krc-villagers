package dev.krcvillagers.mixin;
import com.example.generichenshin.service.ZioTimeStopService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=ZioTimeStopService.class,remap=false)
public interface TimeStopAccess {
    @Invoker("matchByBeltAndForm") static ZioTimeStopService.StopRider kv$byBelt(String belt,String form) { throw new AssertionError(); }
    @Invoker("matchByForm") static ZioTimeStopService.StopRider kv$byForm(String form) { throw new AssertionError(); }
}
