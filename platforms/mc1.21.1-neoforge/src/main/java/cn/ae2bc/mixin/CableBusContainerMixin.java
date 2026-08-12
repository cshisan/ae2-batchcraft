package cn.ae2bc.mixin;

import appeng.api.parts.IFacadeContainer;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.parts.CableBusContainer;
import appeng.client.render.cablebus.CableBusRenderState;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the center-mounted unit manager exclusive with face attachments and renders its frame. */
@Mixin(CableBusContainer.class)
public abstract class CableBusContainerMixin {
    @Shadow public abstract @Nullable IPart getPart(@Nullable Direction side);
    @Shadow public abstract IFacadeContainer getFacadeContainer();

    @Inject(method = "canAddPart", at = @At("HEAD"), cancellable = true)
    private void ae2bc$checkPatternP2PUnitManagerPlacement(ItemStack stack, Direction side,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (ae2bc$hasPatternP2PUnitManager()) {
            cir.setReturnValue(false);
        } else if (ModContent.isPatternP2PUnitManagerItem(stack.getItem()) && !ae2bc$canInstallPatternP2PUnitManager()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addPart", at = @At("HEAD"), cancellable = true)
    private <T extends IPart> void ae2bc$checkPatternP2PUnitManagerPlacement(
            IPartItem<T> partItem, Direction side, @Nullable Player player,
            CallbackInfoReturnable<T> cir) {
        // Stream and NBT reconstruction load face parts before the center cable.
        if (player == null) {
            return;
        }
        if (ae2bc$hasPatternP2PUnitManager()
                || ModContent.isPatternP2PUnitManagerItem(partItem.asItem())
                && !ae2bc$canInstallPatternP2PUnitManager()) {
            cir.setReturnValue(null);
        }
    }

    @Unique
    private boolean ae2bc$canInstallPatternP2PUnitManager() {
        if (getPart(null) != null || !getFacadeContainer().isEmpty()) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (getPart(direction) != null) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private boolean ae2bc$hasPatternP2PUnitManager() {
        return getPart(null) instanceof PatternP2PUnitManagerPart;
    }

    @Inject(method = "getRenderState", at = @At("RETURN"))
    private void ae2bc$renderPatternP2PUnitManagerFrame(CallbackInfoReturnable<CableBusRenderState> cir) {
        if (getPart(null) instanceof PatternP2PUnitManagerPart manager) {
            var renderState = cir.getReturnValue();
            // Center cables are normally rendered only by AE2's cable renderer. Add the manager's
            // symmetric frame as a north attachment so the existing part-model pipeline renders it once.
            renderState.getAttachments().put(Direction.NORTH, manager.getStaticModels());
            renderState.getPartModelData().put(Direction.NORTH, manager.getModelData());
        }
    }
}
