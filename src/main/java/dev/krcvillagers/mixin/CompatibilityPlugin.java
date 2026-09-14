package dev.krcvillagers.mixin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

public final class CompatibilityPlugin implements IMixinConfigPlugin {
    public void onLoad(String name){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){return true;}
    public void acceptTargets(Set<String> mine,Set<String> others){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){
        if(FMLEnvironment.dist!=Dist.DEDICATED_SERVER||!mixin.endsWith("MobAnimatorServerMixin"))return;
        // MPA 1.4.0's only common initialization is init(); its subsequent config-screen
        // invokedynamic eagerly resolves Minecraft Screen even before registerExtensionPoint.
        // Keep common initialization and leave the original client constructor intact.
        var ctor=node.methods.stream().filter(m->m.name.equals("<init>")&&m.desc.equals("(Lnet/neoforged/fml/ModContainer;)V")).findFirst().orElseThrow();
        long initCalls=Arrays.stream(ctor.instructions.toArray()).filter(i->i instanceof MethodInsnNode m && m.owner.equals("me/Thelnfamous1/mobplayeranimator/MobPlayerAnimator")&&m.name.equals("init")).count();
        if(initCalls!=1)throw new IllegalStateException("Unexpected Mob Player Animator constructor; update the compatibility adapter");
        ctor.instructions.clear();ctor.tryCatchBlocks.clear();if(ctor.localVariables!=null)ctor.localVariables.clear();
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"me/Thelnfamous1/mobplayeranimator/MobPlayerAnimator","init","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));ctor.maxStack=1;ctor.maxLocals=2;
    }
}
