package org.ae2bc.core.placer;

import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public final class DataModel {
    private final boolean frequencyMode;
    private final short frequency;
    private final short unitId;
    private final Pos pos1;
    private final Pos pos2;
    private final SelectionStatus selection;
    private final boolean hasCable;
    private final boolean hasPart;
    private final boolean hasSelection;
    private final boolean aeConnected;

    public static final DataModel DEFAULT = new DataModel(
            false, (short) 0, (short) 0,
            new Pos(0, 0, 0),
            new Pos(0, 0, 0),
            SelectionStatus.SELECTION_NOT_ALLOWED,
            false, false, false, false
    );

    public DataModel(
            boolean frequencyMode, short frequency, short unitId,
            Pos pos1, Pos pos2,
            SelectionStatus selection, boolean hasCable,
            boolean hasPart, boolean hasSelection, boolean aeConnected
    ) {
        this.frequencyMode = frequencyMode;
        this.frequency = frequency;
        this.unitId = unitId;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.selection = selection;
        this.hasCable = hasCable;
        this.hasPart = hasPart;
        this.hasSelection = hasSelection;
        this.aeConnected = aeConnected;
    }

    public static Builder builder() {
        return new Builder();
    }

    @NoArgsConstructor
    public static class Builder {
        private boolean frequencyMode;
        private short frequency;
        private short unitId;
        private Pos pos1;
        private Pos pos2;
        private SelectionStatus selection;
        private boolean hasCable;
        private boolean hasPart;
        private boolean hasSelection;
        private boolean aeConnected;

        public Builder(DataModel model) {
            this.frequencyMode = model.frequencyMode;
            this.frequency = model.frequency;
            this.unitId = model.unitId;
            this.pos1 = model.pos1;
            this.pos2 = model.pos2;
            this.selection = model.selection;
            this.hasCable = model.hasCable;
            this.hasPart = model.hasPart;
            this.hasSelection = model.hasSelection;
            this.aeConnected = model.aeConnected;
        }

        public Builder frequencyMode(boolean frequencyMode) {
            this.frequencyMode = frequencyMode;
            return this;
        }

        public Builder frequency(short frequency) {
            this.frequency = frequency;
            return this;
        }

        public Builder unitId(short unitId) {
            this.unitId = unitId;
            return this;
        }

        public Builder pos1(Pos range1) {
            this.pos1 = range1;
            return this;
        }

        public Builder pos2(Pos range2) {
            this.pos2 = range2;
            return this;
        }

        public Builder selection(SelectionStatus selection) {
            this.selection = selection;
            return this;
        }

        public Builder hasCable(boolean hasCable) {
            this.hasCable = hasCable;
            return this;
        }

        public Builder hasPart(boolean hasPart) {
            this.hasPart = hasPart;
            return this;
        }

        public Builder hasSelection(boolean hasSelection) {
            this.hasSelection = hasSelection;
            return this;
        }

        public Builder aeConnected(boolean aeConnected) {
            this.aeConnected = aeConnected;
            return this;
        }

        public DataModel build() {
            return new DataModel(
                    this.frequencyMode, this.frequency, this.unitId,
                    this.pos1, this.pos2, this.selection, this.hasCable,
                    this.hasPart, this.hasSelection, this.aeConnected
            );
        }
    }
}
