package cn.ae2bc.integration;

import appeng.api.parts.IPart;
import appeng.api.util.AEPartLocation;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.parts.CableBusContainer;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.item.PatternP2PUnitManagerItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/** Integrates the unit manager with rv6's native center-cable implementation. */
public final class CableRenderStateBridge {
    private CableRenderStateBridge() {
    }

    public static CableBusRenderState addManagerModel(
            CableBusRenderState state, CableBusContainer container) {
        IPart center = container.getPart(AEPartLocation.INTERNAL);
        if (center instanceof PatternP2PUnitManagerPart) {
            PatternP2PUnitManagerPart manager = (PatternP2PUnitManagerPart) center;
            state.getAttachments().put(EnumFacing.NORTH, manager.getStaticModels());
            state.getPartFlags().put(EnumFacing.NORTH, manager.getRenderFlag());
        }
        return state;
    }

    public static boolean shouldRejectPart(CableBusContainer container, ItemStack stack) {
        if (container.getPart(AEPartLocation.INTERNAL) instanceof PatternP2PUnitManagerPart) return true;
        if (!(stack.getItem() instanceof PatternP2PUnitManagerItem)) return false;
        if (!container.getFacadeContainer().isEmpty()) return true;
        for (AEPartLocation location : AEPartLocation.values()) {
            if (container.getPart(location) != null) return true;
        }
        return false;
    }
}
