package org.ae2bc.part.unit.port.part;

import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.parts.automation.StackWorldBehaviors;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPlacementPortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;

public class Pp2mpUnitDropPortPart extends Pp2mpUnitPlacementPortPart {
    public Pp2mpUnitDropPortPart(IPartItem<?> partItem) {
        super(partItem, UnitPortType.DROP, Pp2mpUnitPortPartModel.MODEL_DROP, true);
    }

    @Override
    protected boolean supports(AEKey what) {
        return what != null && StackWorldBehaviors.withPlacementStrategy().contains(what.getType());
    }
}
