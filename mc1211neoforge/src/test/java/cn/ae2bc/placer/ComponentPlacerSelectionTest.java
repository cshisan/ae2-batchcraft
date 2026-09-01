package cn.ae2bc.placer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentPlacerSelectionTest {
    @Test
    void pointSelectionProducesOneTarget() {
        var selection = new ComponentSelectionGeometry(3, 4, 5, 3, 4, 5);

        assertEquals(ComponentSelectionGeometry.Validation.VALID, selection.validate());
        assertEquals(1, selection.targetCount());
        assertEquals(new ComponentSelectionGeometry.Position(3, 4, 5), selection.positions(0, 0, 0).getFirst());
    }

    @Test
    void lineAndPlaneAreAllowedButVolumeIsRejected() {
        var line = new ComponentSelectionGeometry(0, 0, 0, 15, 0, 0);
        var plane = new ComponentSelectionGeometry(0, 0, 0, 15, 0, 15);
        var volume = new ComponentSelectionGeometry(0, 0, 0, 1, 1, 1);

        assertEquals(ComponentSelectionGeometry.Validation.VALID, line.validate());
        assertEquals(16, line.targetCount());
        assertEquals(ComponentSelectionGeometry.Validation.VALID, plane.validate());
        assertEquals(256, plane.targetCount());
        assertEquals(ComponentSelectionGeometry.Validation.VOLUME_NOT_ALLOWED, volume.validate());
    }

    @Test
    void axisSizeCannotExceedSixteen() {
        var selection = new ComponentSelectionGeometry(0, 0, 0, 16, 0, 0);
        assertEquals(ComponentSelectionGeometry.Validation.TOO_LARGE, selection.validate());
    }

    @Test
    void offsetsTranslateWithoutChangingSelectionSize() {
        var selection = new ComponentSelectionGeometry(1, 2, 3, 2, 2, 3);
        var positions = selection.positions(5, -5, 1);

        assertEquals(2, positions.size());
        assertEquals(new ComponentSelectionGeometry.Position(6, -3, 4), positions.getFirst());
        assertEquals(new ComponentSelectionGeometry.Position(7, -3, 4), positions.get(1));
    }

    @Test
    void extremeCoordinatesCannotOverflowValidationOrOffsets() {
        var selection = new ComponentSelectionGeometry(
                Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0);
        var offsetOverflow = new ComponentSelectionGeometry(
                Integer.MAX_VALUE - 1, 0, 0, Integer.MAX_VALUE, 0, 0);

        assertEquals(ComponentSelectionGeometry.Validation.TOO_LARGE, selection.validate());
        assertEquals(0, offsetOverflow.positions(2, 0, 0).size());
    }
}
