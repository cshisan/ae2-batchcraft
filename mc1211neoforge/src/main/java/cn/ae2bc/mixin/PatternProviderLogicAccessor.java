package cn.ae2bc.mixin;

import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {
    @Invoker("getActiveSides")
    Set<Direction> ae2bc$getActiveSides();

    @Invoker("onStackReturnedToNetwork")
    void ae2bc$onStackReturnedToNetwork(GenericStack stack);
}
