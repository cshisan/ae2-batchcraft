package cn.ae2bc.placer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

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

    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentPlacerSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeEnum(value.direction);
                buffer.writeByte(value.offsetX);
                buffer.writeByte(value.offsetY);
                buffer.writeByte(value.offsetZ);
            },
            buffer -> new ComponentPlacerSettings(
                    buffer.readEnum(Direction.class),
                    buffer.readByte(),
                    buffer.readByte(),
                    buffer.readByte()));

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
        return Math.clamp(value, -MAX_OFFSET, MAX_OFFSET);
    }
}
