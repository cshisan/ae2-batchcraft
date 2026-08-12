package cn.ae2bc.mixin;

import appeng.api.storage.AEKeySlotFilter;
import appeng.helpers.externalstorage.GenericStackInv;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GenericStackInv.class)
public interface GenericStackInvAccessor {
    @Invoker("setFilter")
    void ae2bc$setFilter(@Nullable AEKeySlotFilter filter);
}
