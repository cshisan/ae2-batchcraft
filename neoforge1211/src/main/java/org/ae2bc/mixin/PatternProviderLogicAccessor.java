package org.ae2bc.mixin;

import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {
    @Invoker("onStackReturnedToNetwork")
    void ae2bc$onStackReturnedToNetwork(GenericStack stack);
}
