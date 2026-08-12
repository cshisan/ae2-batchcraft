package cn.ae2bc.placer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;

public record ComponentPlacerSettings(
        Direction direction,
        int offsetX,
        int offsetY,
        int offsetZ) {
    public static final int MAX_OFFSET = 16;
    public static final ComponentPlacerSettings DEFAULT = new ComponentPlacerSettings(
            Direction.UP, 0, 1, 0);

    public static final Codec<ComponentPlacerSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Direction.CODEC.optionalFieldOf("direction", Direction.UP)
                    .forGetter(ComponentPlacerSettings::direction),
            Codec.intRange(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("offset_x", 0)
                    .forGetter(ComponentPlacerSettings::offsetX),
            Codec.intRange(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("offset_y", 1)
                    .forGetter(ComponentPlacerSettings::offsetY),
            Codec.intRange(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("offset_z", 0)
                    .forGetter(ComponentPlacerSettings::offsetZ)
    ).apply(instance, ComponentPlacerSettings::new));

    public ComponentPlacerSettings {
        direction = direction == null ? Direction.UP : direction;
        offsetX = clampOffset(offsetX);
        offsetY = clampOffset(offsetY);
        offsetZ = clampOffset(offsetZ);
    }

    public ComponentPlacerSettings withDirection(Direction newDirection) {
        return new ComponentPlacerSettings(newDirection, offsetX, offsetY, offsetZ);
    }

    public ComponentPlacerSettings withOffsets(int x, int y, int z) {
        return new ComponentPlacerSettings(direction, x, y, z);
    }

    public ComponentPlacerSettings resetOffsets() {
        return withOffsets(0, 1, 0);
    }

    public static int clampOffset(int value) {
        return Math.max(-MAX_OFFSET, Math.min(value, MAX_OFFSET));
    }
}
