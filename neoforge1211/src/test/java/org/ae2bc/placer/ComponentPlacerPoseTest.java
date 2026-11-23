package org.ae2bc.placer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.ae2bc.core.placer.DataModel;
import org.ae2bc.core.placer.Pos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ComponentPlacerPoseTest {
    @Test
    void defaultsAndMissingSavedOffsetsAreZero() {
        Assertions.assertEquals(new Pos(0, 0, 0), ComponentPlacerPose.DEFAULT.pos());
        Assertions.assertEquals(ComponentPlacerPose.DEFAULT.pos(), DataModel.DEFAULT.pos1());
        Assertions.assertEquals(ComponentPlacerPose.DEFAULT,
                ComponentPlacerPose.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
    }

    @Test
    void resetKeepsDirectionAndUsesSelectedCoordinates() {
        ComponentPlacerPose pose = ComponentPlacerPose.DEFAULT
                .withDirection(Direction.NORTH).withOffsets(2, 3, 4).resetOffsets();
        Assertions.assertEquals(Direction.NORTH, pose.direction());
        Assertions.assertEquals(new Pos(0, 0, 0), pose.pos());
        ComponentPlacerSelection selection = ComponentPlacerSelection.start(
                Level.OVERWORLD.location(), new BlockPos(4, -12, 6)).complete(new BlockPos(4, -14, 6));
        Assertions.assertEquals(List.of(
                new BlockPos(4, -14, 6), new BlockPos(4, -13, 6), new BlockPos(4, -12, 6)
        ), selection.positions(pose));
    }

    @Test
    void explicitSavedOffsetsRemainAndRoundTrip() {
        for (int y : List.of(-16, 0, 1, 16)) {
            JsonObject saved = new JsonObject();
            saved.addProperty("offset_y", y);
            ComponentPlacerPose decoded = ComponentPlacerPose.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
            Assertions.assertEquals(new Pos(0, y, 0), decoded.pos());
            JsonElement encoded = ComponentPlacerPose.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
            Assertions.assertEquals(decoded, ComponentPlacerPose.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
            ComponentPlacerSelection selection = ComponentPlacerSelection.start(
                    Level.OVERWORLD.location(), BlockPos.ZERO).complete(BlockPos.ZERO);
            Assertions.assertEquals(List.of(new BlockPos(0, y, 0)), selection.positions(decoded));
        }
    }
}
