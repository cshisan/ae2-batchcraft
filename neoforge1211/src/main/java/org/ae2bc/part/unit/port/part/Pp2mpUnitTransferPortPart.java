package org.ae2bc.part.unit.port.part;

import appeng.api.config.Actionable;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.general.Pp2mpSendTarget;
import org.ae2bc.part.general.Pp2mpInsertPlan;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;

public class Pp2mpUnitTransferPortPart extends Pp2mpUnitOutputTypePortPart {
    private final Pp2mpAdjacentTarget adjacentTarget;
    private final Pp2mpSendTarget sendTarget;

    public Pp2mpUnitTransferPortPart(IPartItem<?> partItem) {
        super(partItem, UnitPortType.TRANSFER, Pp2mpUnitPortPartModel.MODEL_TRANSFER);
        this.adjacentTarget = Pp2mpAdjacentTarget.forPart(this);
        this.sendTarget = new Pp2mpSendTarget(this.adjacentTarget, this::alertManager);
    }

    @Override
    protected long outputToTarget(AEKey what, long amount, Actionable mode) {
        return this.sendTarget.insert(what, amount, mode, this.actionSource);
    }

    /* 通过端口资格检查后只执行共享容量模拟，不提前投递材料。 */
    public long reserve(
            Pp2mpUnitManagerPart manager, GenericStack stack, MaterialOutputMode form, Pp2mpInsertPlan plan
    ) {
        return this.canOutput(manager, stack, form)
                ? this.sendTarget.reserve(plan, stack.what(), stack.amount(), this.actionSource) : 0;
    }

    @Override
    protected boolean supports(AEKey what) {
        return what != null;
    }

    @Override
    protected void clearAdjacentTarget() {
        this.adjacentTarget.clear();
        this.sendTarget.clear();
    }

}
