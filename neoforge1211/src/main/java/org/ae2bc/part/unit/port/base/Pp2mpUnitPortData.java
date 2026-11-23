package org.ae2bc.part.unit.port.base;

public class Pp2mpUnitPortData {
    private final Runnable markForSave;
    private final Runnable markForUpdate;

    public Pp2mpUnitPortData(Runnable markForSave, Runnable markForUpdate) {
        this.markForSave = markForSave;
        this.markForUpdate = markForUpdate;
    }

    public void update(Runnable update) {
        update.run();
        this.markForSave.run();
        this.markForUpdate.run();
    }
}
