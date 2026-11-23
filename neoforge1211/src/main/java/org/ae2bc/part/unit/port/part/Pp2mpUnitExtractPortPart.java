package org.ae2bc.part.unit.port.part;

import appeng.api.parts.IPartItem;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.general.Pp2mpSendTarget;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitInputTypePortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;
import org.ae2bc.storage.ProductExtractor;

public class Pp2mpUnitExtractPortPart extends Pp2mpUnitInputTypePortPart {

    private final Pp2mpAdjacentTarget adjacentTarget;
    private final Pp2mpSendTarget sourceTarget;
    private final ProductExtractor.Cursor extractionCursor = new ProductExtractor.Cursor();

    public Pp2mpUnitExtractPortPart(IPartItem<?> partItem) {
        super(partItem, UnitPortType.EXTRACT, Pp2mpUnitPortPartModel.MODEL_EXTRACT);
        this.adjacentTarget = Pp2mpAdjacentTarget.forPart(this);
        this.sourceTarget = new Pp2mpSendTarget(this.adjacentTarget, this::alertManager);
    }

    public ProductExtractor.Result extractProducts(ProductExtractionParam param) {
        Pp2mpUnitManagerPart manager = this.findManager();
        if (manager == null || !this.isBoundTo(manager)
                || !manager.getLogic().isTaskOperational() || param == null) {
            return new ProductExtractor.Result(0, 0, false);
        }

        manager.getLogic().drainTaskRecovery();
        if (!manager.getData().getExtractionRecovery().isEmpty() || !manager.getLogic().isTaskOperational()) {
            return new ProductExtractor.Result(0, 0, true);
        }

        return ProductExtractor.extract(
                this.sourceTarget.findStorages(),
                this.receiveInventory,
                param.getQuantity(),
                Integer.MAX_VALUE,
                this.actionSource,
                this::allowsReceived,
                manager.getLogic()::retainExtracted, manager.getLogic().exactReturnKeys(), this.extractionCursor
        );
    }

    @Override
    protected void clearAdjacentTarget() {
        this.adjacentTarget.clear();
        this.sourceTarget.clear();
    }

}
