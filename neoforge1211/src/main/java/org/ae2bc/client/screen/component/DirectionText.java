package org.ae2bc.client.screen.component;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public final class DirectionText {
    private DirectionText() {
    }

    public static Component name(Direction direction, DirectionLayout layout) {
        Component absolute = direction == null
                ? Component.translatable("gui.ae2_batchcraft.direction.auto")
                : Component.translatable("gui.ae2_batchcraft.direction." + direction.getName());
        if (direction == layout.left()) {
            return Component.translatable("gui.ae2_batchcraft.direction.left", absolute);
        }
        if (direction == layout.right()) {
            return Component.translatable("gui.ae2_batchcraft.direction.right", absolute);
        }
        return absolute;
    }
}
