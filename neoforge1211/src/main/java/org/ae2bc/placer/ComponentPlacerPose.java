package org.ae2bc.placer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.ae2bc.core.placer.Pos;

import java.util.Objects;

public record ComponentPlacerPose(Direction direction, Pos pos) {
    public static final int MAX_OFFSET = 16;
    public static final ComponentPlacerPose DEFAULT = new ComponentPlacerPose(Direction.UP, new Pos(0, 0, 0));
    public static final Codec<ComponentPlacerPose> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Direction.CODEC.optionalFieldOf("direction", Direction.UP).forGetter(ComponentPlacerPose::direction),
            Codec.intRange(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("offset_x", 0).forGetter(pose -> pose.pos().x()),
            Codec.intRange(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("offset_y", 0).forGetter(pose -> pose.pos().y()),
            Codec.intRange(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("offset_z", 0).forGetter(pose -> pose.pos().z())
    ).apply(instance, (direction, x, y, z) -> new ComponentPlacerPose(direction, new Pos(x, y, z))));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentPlacerPose> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeEnum(value.direction());
                buffer.writeByte(value.pos().x());
                buffer.writeByte(value.pos().y());
                buffer.writeByte(value.pos().z());
            },
            buffer -> new ComponentPlacerPose(buffer.readEnum(Direction.class),
                    new Pos(buffer.readByte(), buffer.readByte(), buffer.readByte()))
    );

    /* 规范化安装方向，并限制所有位置偏移量。 */
    public ComponentPlacerPose {
        direction = direction == null ? Direction.UP : direction;
        Objects.requireNonNull(pos, "pos");
        pos = new Pos(
                ComponentPlacerPose.clampOffset(pos.x()),
                ComponentPlacerPose.clampOffset(pos.y()),
                ComponentPlacerPose.clampOffset(pos.z())
        );
    }

    public static int clampOffset(int value) {
        return Math.clamp(value, -MAX_OFFSET, MAX_OFFSET);
    }

    public ComponentPlacerPose withDirection(Direction value) {
        return new ComponentPlacerPose(value, this.pos);
    }

    public ComponentPlacerPose withOffsets(int x, int y, int z) {
        return new ComponentPlacerPose(this.direction, new Pos(x, y, z));
    }

    public ComponentPlacerPose resetOffsets() {
        return this.withOffsets(0, 0, 0);
    }
}
