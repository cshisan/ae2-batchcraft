package cn.ae2bc.client;

import cn.ae2bc.logic.DirectionLayout;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumFacing;

/** Formats absolute output faces and player-relative left/right hints. */
public final class DirectionText {
    private DirectionText() { }

    public static String name(EnumFacing direction, DirectionLayout layout) {
        String base = absoluteName(direction);
        if (direction == null) {
            return base;
        }
        if (direction == layout.left()) {
            return I18n.format("gui.ae2_batchcraft.direction.left", base);
        }
        if (direction == layout.right()) {
            return I18n.format("gui.ae2_batchcraft.direction.right", base);
        }
        return base;
    }

    private static String absoluteName(EnumFacing direction) {
        return I18n.format("gui.ae2_batchcraft.direction."
                + (direction == null ? "auto" : direction.getName2()));
    }
}
