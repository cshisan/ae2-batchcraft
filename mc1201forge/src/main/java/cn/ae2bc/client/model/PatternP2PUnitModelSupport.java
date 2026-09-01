package cn.ae2bc.client.model;

import appeng.api.util.AEColor;
import appeng.client.render.cablebus.CubeBuilder;
import appeng.core.AppEng;
import appeng.util.Platform;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.inventory.InventoryMenu;

final class PatternP2PUnitModelSupport {
    static final Material FREQUENCY_TEXTURE = new Material(
            InventoryMenu.BLOCK_ATLAS, AppEng.makeId("part/p2p_tunnel_frequency"));

    private PatternP2PUnitModelSupport() {
    }

    static AEColor[] colors(long flags) {
        return Platform.p2p().toColors((short) (flags & 0xffff));
    }

    static boolean isActive(long flags) {
        return (flags & 0x10000L) != 0;
    }

    static void setColor(CubeBuilder builder, AEColor color, boolean active) {
        if (active) {
            builder.setColorRGB(color.mediumVariant);
            return;
        }
        float scale = 0.3f / 255.0f;
        builder.setColorRGB((color.blackVariant >> 16 & 0xff) * scale,
                (color.blackVariant >> 8 & 0xff) * scale,
                (color.blackVariant & 0xff) * scale);
    }
}
