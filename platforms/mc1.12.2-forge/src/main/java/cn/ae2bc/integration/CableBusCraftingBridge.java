package cn.ae2bc.integration;

import cn.ae2bc.part.PatternP2PTunnelPart;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPart;
import appeng.tile.networking.TileCableBus;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.util.EnumFacing;

/** Static target used by the 1.12 coremod-injected ICraftingMachine methods. */
public final class CableBusCraftingBridge {
    private CableBusCraftingBridge() { }

    public static boolean acceptsPlans(TileCableBus cableBus) {
        for (EnumFacing side : EnumFacing.values()) {
            IPart part = cableBus.getPart(side);
            if (part instanceof PatternP2PTunnelPart
                    && ((PatternP2PTunnelPart) part).acceptsPlans()) {
                return true;
            }
        }
        return false;
    }

    public static boolean pushPattern(TileCableBus cableBus, ICraftingPatternDetails details,
            InventoryCrafting table, EnumFacing direction) {
        IPart part = cableBus.getPart(direction);
        return part instanceof PatternP2PTunnelPart
                && ((PatternP2PTunnelPart) part).pushPattern(details, table, direction);
    }
}
