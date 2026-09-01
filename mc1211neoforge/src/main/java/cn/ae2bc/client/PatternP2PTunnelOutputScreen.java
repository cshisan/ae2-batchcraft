package cn.ae2bc.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.menu.PatternP2PTunnelOutputMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.Map;

public final class PatternP2PTunnelOutputScreen extends AEBaseScreen<PatternP2PTunnelOutputMenu> {
    private final VerticallyAlignedCheckbox syncInputSettings;
    private final AE2Button resetTask;
    private final Map<ReturnMode, AE2Button> returnButtons = new EnumMap<>(ReturnMode.class);

    public PatternP2PTunnelOutputScreen(PatternP2PTunnelOutputMenu menu, Inventory playerInventory,
                                          Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        for (ReturnMode mode : ReturnMode.values()) {
            String modeName = mode.getSerializedName();
            var button = widgets.addButton("return" + Character.toUpperCase(modeName.charAt(0))
                            + modeName.substring(1),
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.return_mode." + modeName),
                    () -> menu.setReturnMode(mode));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.return_mode." + modeName + ".tooltip")));
            returnButtons.put(mode, button);
        }
        syncInputSettings = new VerticallyAlignedCheckbox(style,
                Component.translatable("gui.ae2_batchcraft.sync_input_settings"));
        widgets.add("syncInputSettings", syncInputSettings);
        syncInputSettings.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.sync_input_settings.tooltip")));
        syncInputSettings.setChangeListener(() -> menu.setSyncInputSettings(syncInputSettings.isSelected()));
        resetTask = widgets.addButton("resetTask",
                Component.translatable("gui.ae2_batchcraft.reset_task"),
                () -> TaskResetConfirmation.open(this, Component.translatable(
                        "gui.ae2_batchcraft.reset_task.confirm.output"), menu::resetTaskState));
        resetTask.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.reset_task.tooltip")));
    }

    @Override
    protected void init() {
        super.init();
        syncInputSettings.fitToMessage();
        syncInputSettings.setX(leftPos + 8);
        syncInputSettings.setY(topPos + 22);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        syncInputSettings.setSelected(menu.syncInputSettings);
        for (var entry : returnButtons.entrySet()) {
            entry.getValue().active = !menu.syncInputSettings && entry.getKey() != menu.returnMode;
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        DashedSectionRenderer.drawBackground(graphics, font,
                Component.translatable("gui.ae2_batchcraft.return_mode"),
                offsetX, offsetY, imageWidth, 43, 74);
        DashedSectionRenderer.drawBackground(graphics, font,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset"),
                offsetX, offsetY, imageWidth, 85, 116);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        graphics.hLine(7, imageWidth - 8, 17, 0xFF808080);
        graphics.hLine(7, imageWidth - 8, 18, 0xFFFFFFFF);
        DashedSectionRenderer.drawTitle(graphics, font,
                Component.translatable("gui.ae2_batchcraft.return_mode"), 39);
        DashedSectionRenderer.drawTitle(graphics, font,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset"), 81);
    }
}
