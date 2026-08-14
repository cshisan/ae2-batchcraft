package cn.ae2bc.placer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Pure coordinate logic for placer selections, kept independent of Minecraft for unit testing. */
public final class ComponentSelectionGeometry {
    public static final int MAX_AXIS_SIZE = 16;
    public static final long MAX_TARGETS = (long) MAX_AXIS_SIZE * MAX_AXIS_SIZE;

    private final int firstX;
    private final int firstY;
    private final int firstZ;
    private final int secondX;
    private final int secondY;
    private final int secondZ;

    public ComponentSelectionGeometry(int firstX, int firstY, int firstZ,
                                      int secondX, int secondY, int secondZ) {
        this.firstX = firstX;
        this.firstY = firstY;
        this.firstZ = firstZ;
        this.secondX = secondX;
        this.secondY = secondY;
        this.secondZ = secondZ;
    }

    public int firstX() { return firstX; }
    public int firstY() { return firstY; }
    public int firstZ() { return firstZ; }
    public int secondX() { return secondX; }
    public int secondY() { return secondY; }
    public int secondZ() { return secondZ; }

    public Validation validate() {
        int varyingAxes = 0;
        if (firstX != secondX) varyingAxes++;
        if (firstY != secondY) varyingAxes++;
        if (firstZ != secondZ) varyingAxes++;
        if (varyingAxes == 3) return Validation.VOLUME_NOT_ALLOWED;
        if (sizeX() > MAX_AXIS_SIZE || sizeY() > MAX_AXIS_SIZE || sizeZ() > MAX_AXIS_SIZE
                || targetCount() > MAX_TARGETS) {
            return Validation.TOO_LARGE;
        }
        return Validation.VALID;
    }

    public long sizeX() { return Math.abs((long) secondX - firstX) + 1; }
    public long sizeY() { return Math.abs((long) secondY - firstY) + 1; }
    public long sizeZ() { return Math.abs((long) secondZ - firstZ) + 1; }
    public long targetCount() { return sizeX() * sizeY() * sizeZ(); }

    public List<Position> positions(int offsetX, int offsetY, int offsetZ) {
        if (validate() != Validation.VALID) return Collections.emptyList();

        long minX = (long) Math.min(firstX, secondX) + offsetX;
        long minY = (long) Math.min(firstY, secondY) + offsetY;
        long minZ = (long) Math.min(firstZ, secondZ) + offsetZ;
        long maxX = (long) Math.max(firstX, secondX) + offsetX;
        long maxY = (long) Math.max(firstY, secondY) + offsetY;
        long maxZ = (long) Math.max(firstZ, secondZ) + offsetZ;
        if (minX < Integer.MIN_VALUE || maxX > Integer.MAX_VALUE
                || minY < Integer.MIN_VALUE || maxY > Integer.MAX_VALUE
                || minZ < Integer.MIN_VALUE || maxZ > Integer.MAX_VALUE) {
            return Collections.emptyList();
        }
        List<Position> result = new ArrayList<>((int) targetCount());
        for (long y = minY; y <= maxY; y++) {
            for (long z = minZ; z <= maxZ; z++) {
                for (long x = minX; x <= maxX; x++) {
                    result.add(new Position((int) x, (int) y, (int) z));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ComponentSelectionGeometry)) return false;
        ComponentSelectionGeometry that = (ComponentSelectionGeometry) other;
        return firstX == that.firstX && firstY == that.firstY && firstZ == that.firstZ
                && secondX == that.secondX && secondY == that.secondY && secondZ == that.secondZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstX, firstY, firstZ, secondX, secondY, secondZ);
    }

    public enum Validation { VALID, VOLUME_NOT_ALLOWED, TOO_LARGE }

    public static final class Position {
        private final int x;
        private final int y;
        private final int z;

        public Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Position)) return false;
            Position that = (Position) other;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() { return Objects.hash(x, y, z); }
    }
}
