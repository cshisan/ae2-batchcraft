package org.ae2bc.client.screen;

import appeng.client.gui.WidgetContainer;
import appeng.api.util.AEColor;
import net.minecraft.network.chat.Component;

public final class ScreenHeader {
    private ScreenHeader() {
    }

    /* 标题由样式文本绘制，下划线复用 AE2 样式背景面板。 */
    public static void addTo(WidgetContainer widgets) {
        widgets.addBackgroundPanel("title_separator_dark");
        widgets.addBackgroundPanel("title_separator_light");
    }

    /* 界面使用短标题并保留颜色信息，物品完整名称保持不变。 */
    public static Component unitManagerTitle(AEColor color) {
        return color == AEColor.TRANSPARENT
                ? Component.translatable("gui.ae2_batchcraft.unit_manager.title")
                : Component.translatable("gui.ae2_batchcraft.unit_manager.title.colored",
                        Component.translatable(color.translationKey));
    }
}
