package cn.ae2bc.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PatternP2PTunnelEnergyScreen extends AEBaseScreen<PatternP2PTunnelEnergyMenu> {
    private final AE2Button passiveButton;
    private final AE2Button activeButton;
    private final AE2Button energyDistributionMode;

    public PatternP2PTunnelEnergyScreen(PatternP2PTunnelEnergyMenu menu, Inventory playerInventory,
                                          Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        passiveButton = widgets.addButton("passive", Component.translatable(
                "gui.ae2_batchcraft.energy.mode.passive"), () -> menu.setPullEnabled(false));
        passiveButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.energy.mode.passive.tooltip")));
        activeButton = widgets.addButton("active", Component.translatable(
                "gui.ae2_batchcraft.energy.mode.active"), () -> menu.setPullEnabled(true));
        activeButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.energy.mode.active.tooltip")));
        energyDistributionMode = widgets.addButton("energyDistributionMode", Component.empty(),
                () -> menu.setEnergyDistributionMode(menu.energyDistributionMode.next()));
        energyDistributionMode.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.energy_distribution_mode.tooltip")));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        passiveButton.active = menu.pullEnabled;
        activeButton.active = !menu.pullEnabled;
        energyDistributionMode.setMessage(Component.translatable(
                "gui.ae2_batchcraft.energy_distribution_mode." +
                        menu.energyDistributionMode.getSerializedName()));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        DashedSectionRenderer.drawBackground(graphics, font,
                Component.translatable("gui.ae2_batchcraft.energy.section.input"),
                intervalText(), offsetX, offsetY, imageWidth, 28, 59);
        DashedSectionRenderer.drawBackground(graphics, font,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration"),
                offsetX, offsetY, imageWidth, 70, 101);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY) {
        graphics.hLine(7, imageWidth - 8, 18, 0xFF808080);
        graphics.hLine(7, imageWidth - 8, 19, 0xFFFFFFFF);
        DashedSectionRenderer.drawTitle(graphics, font,
                Component.translatable("gui.ae2_batchcraft.energy.section.input"),
                intervalText(), 24, imageWidth);
        DashedSectionRenderer.drawTitle(graphics, font,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration"), 66);
    }

    private Component intervalText() {
        return Component.translatable("gui.ae2_batchcraft.energy.pull_interval", menu.pullInterval);
    }
}
