package org.ae2bc.part.unit.port.part;

import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.BlockItem;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPlacementPortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;

public class Pp2mpUnitPlacePortPart extends Pp2mpUnitPlacementPortPart {
    public Pp2mpUnitPlacePortPart(IPartItem<?> partItem) {
        super(partItem, UnitPortType.PLACE, Pp2mpUnitPortPartModel.MODEL_PLACE, false);
    }

    @Override
    protected boolean supports(AEKey what) {
        return what instanceof AEFluidKey || what instanceof AEItemKey itemKey && itemKey.getItem() instanceof BlockItem;
    }
}
