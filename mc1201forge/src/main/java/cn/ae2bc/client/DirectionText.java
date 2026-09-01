package cn.ae2bc.client;

import cn.ae2bc.logic.DirectionLayout;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class DirectionText {
    private DirectionText() {
    }

    public static Component name(@Nullable Direction direction, DirectionLayout layout) {
        Component base = absoluteName(direction);
        if (direction == null) {
            return base;
        }
        if (direction == layout.left()) {
            return Component.translatable("gui.ae2_batchcraft.direction.left", base);
        }
        if (direction == layout.right()) {
            return Component.translatable("gui.ae2_batchcraft.direction.right", base);
        }
        return base;
    }

    public static Component absoluteName(@Nullable Direction direction) {
        return direction == null
                ? Component.translatable("gui.ae2_batchcraft.direction.auto")
                : Component.translatable("gui.ae2_batchcraft.direction." + direction.getName());
    }
}
