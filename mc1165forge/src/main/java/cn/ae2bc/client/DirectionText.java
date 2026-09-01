package cn.ae2bc.client;

import cn.ae2bc.logic.DirectionLayout;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Formats absolute output faces and player-relative left/right hints. */
public final class DirectionText {
    private DirectionText() { }

    public static ITextComponent name(Direction direction, DirectionLayout layout) {
        ITextComponent base = absoluteName(direction);
        if (direction == null) {
            return base;
        }
        if (direction == layout.left()) {
            return new TranslationTextComponent("gui.ae2_batchcraft.direction.left", base);
        }
        if (direction == layout.right()) {
            return new TranslationTextComponent("gui.ae2_batchcraft.direction.right", base);
        }
        return base;
    }

    private static ITextComponent absoluteName(Direction direction) {
        return new TranslationTextComponent("gui.ae2_batchcraft.direction."
                + (direction == null ? "auto" : direction.getName()));
    }
}
