package org.ae2bc.part.unit.port.part;

import appeng.api.parts.IPartItem;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.unit.port.base.Pp2mpUnitInputTypePortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;

public class Pp2mpUnitReturnPortPart extends Pp2mpUnitInputTypePortPart {
    public Pp2mpUnitReturnPortPart(IPartItem<?> partItem) {
        super(partItem, UnitPortType.RETURN, Pp2mpUnitPortPartModel.MODEL_RETURN);
    }
}
