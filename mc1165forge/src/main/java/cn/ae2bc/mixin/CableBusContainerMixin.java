package cn.ae2bc.mixin;

import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.tile.networking.CableBusTileEntity;
import cn.ae2bc.part.PatternP2PTunnelPart;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.util.Direction;
import org.spongepowered.asm.mixin.Mixin;

/** Bridges AE2 8's interface push callback to a part mounted on a cable bus. */
@Mixin(CableBusTileEntity.class)
public abstract class CableBusContainerMixin implements ICraftingMachine {
    private CableBusTileEntity ae2bc$self() {
        return (CableBusTileEntity) (Object) this;
    }

    @Override
    public boolean acceptsPlans() {
        for (Direction direction : Direction.values()) {
            if (ae2bc$self().getPart(direction) instanceof PatternP2PTunnelPart) {
                PatternP2PTunnelPart part = (PatternP2PTunnelPart) ae2bc$self().getPart(direction);
                if (part.acceptsPlans()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, CraftingInventory table, Direction direction) {
        Object part = ae2bc$self().getPart(direction);
        return part instanceof PatternP2PTunnelPart
                && ((PatternP2PTunnelPart) part).pushPattern(details, table, direction);
    }
}
