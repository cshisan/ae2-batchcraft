package cn.ae2bc.mixin;

import appeng.api.parts.IPart;
import appeng.api.util.AEPartLocation;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.parts.CableBusContainer;
import cn.ae2bc.item.PatternP2PUnitManagerItem;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the center manager shell while leaving AE2's cable topology untouched. */
@Mixin(CableBusContainer.class)
public abstract class PatternP2PUnitManagerCableBusMixin {
    @Inject(method = "canAddPart", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2bc$checkPatternP2PUnitManagerPlacement(ItemStack stack, AEPartLocation side,
            CallbackInfoReturnable<Boolean> callback) {
        CableBusContainer container = (CableBusContainer) (Object) this;
        if (container.getPart(AEPartLocation.INTERNAL) instanceof PatternP2PUnitManagerPart
                && !isAllowedManagerFace(stack)
                || stack.getItem() instanceof PatternP2PUnitManagerItem
                && !canInstallManager(container)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getRenderState", at = @At("RETURN"), remap = false)
    private void ae2bc$addPatternP2PUnitManagerModel(
            CallbackInfoReturnable<CableBusRenderState> callback) {
        CableBusContainer container = (CableBusContainer) (Object) this;
        IPart center = container.getPart(AEPartLocation.INTERNAL);
        if (!(center instanceof PatternP2PUnitManagerPart)) return;

        PatternP2PUnitManagerPart manager = (PatternP2PUnitManagerPart) center;
        CableBusRenderState state = callback.getReturnValue();
        state.getAttachments().put(Direction.NORTH, manager.getStaticModels());
        state.getPartModelData().put(Direction.NORTH, manager.getModelData());
    }

    private static boolean canInstallManager(CableBusContainer container) {
        if (!container.getFacadeContainer().isEmpty()) return false;
        for (AEPartLocation location : AEPartLocation.values()) {
            IPart part = container.getPart(location);
            if (part == null) continue;
            if (location != AEPartLocation.INTERNAL && isAllowedManagerFace(part.getItemStack(appeng.api.parts.PartItemStack.NETWORK))) continue;
            return false;
        }
        return true;
    }

    private static boolean isAllowedManagerFace(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) return false;
        net.minecraft.util.ResourceLocation id = stack.getItem().getRegistryName();
        return "ae2".equals(id.getNamespace()) && ("cable_anchor".equals(id.getPath()) || "quartz_fiber".equals(id.getPath()));
    }
}
