package org.ae2bc.part;

import appeng.api.util.AEColor;
import appeng.client.render.cablebus.CubeBuilder;

public final class Pp2mpIndicatorColor {
    private Pp2mpIndicatorColor() {
    }

    /* 频率与单元标识共用相同的通电色和断电色。 */
    public static void apply(CubeBuilder builder, AEColor color, boolean active) {
        builder.setColorRGB(Pp2mpIndicatorColor.getRgb(color, active));
    }

    /* 同时供立方体生成器和已有烘焙面的动态着色使用。 */
    public static int getRgb(AEColor color, boolean active) {
        if (active) {
            return color.mediumVariant;
        }
        float scale = 0.3f / 255.0f;
        int red = (int) ((color.blackVariant >> 16 & 0xff) * scale * 255);
        int green = (int) ((color.blackVariant >> 8 & 0xff) * scale * 255);
        int blue = (int) ((color.blackVariant & 0xff) * scale * 255);
        return red << 16 | green << 8 | blue;
    }
}
