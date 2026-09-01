package cn.ae2bc.client;

import appeng.client.gui.Icon;

/** An IconButton used by the attached right-side action toolbar. */
final class RightToolbarIconButton extends ScaledIconButton {
    RightToolbarIconButton(Icon icon, float scaled, int iconOffsetX, int iconOffsetY, OnPress onPress) {
        super(icon, scaled, iconOffsetX, iconOffsetY, onPress);
    }
}
