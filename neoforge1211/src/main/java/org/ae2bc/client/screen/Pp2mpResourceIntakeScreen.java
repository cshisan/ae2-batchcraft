package org.ae2bc.client.screen;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.ae2bc.client.screen.component.FeatureComponent;
import org.ae2bc.client.screen.component.ScrollingAE2Button;
import org.ae2bc.client.screen.component.ValidatedIntegerField;
import org.ae2bc.core.constant.ProductExtractionConfigConstants;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakeMenu;

public final class Pp2mpResourceIntakeScreen extends AEBaseScreen<Pp2mpResourceIntakeMenu> {
    private final AE2Button pullMode;
    private final AE2Button roundRobinMode;
    private final AE2Button priorityMode;
    private final AE2Button evenMode;
    private final AE2Button passiveMode;
    private final ValidatedIntegerField extractionInterval;
    private final ValidatedIntegerField extractionQuantity;
    private final FeatureComponent sourceSection = new FeatureComponent(
            Component.translatable("gui.ae2_batchcraft.resource_intake.section.source"),
            27, 89, Pp2mpResourceIntakeScreen::drawExtractionLabels
    );
    private final FeatureComponent distributionSection = new FeatureComponent(
            Component.translatable("gui.ae2_batchcraft.resource_intake.section.distribution"), 100, 131, null
    );
    private final FeatureComponent filterSection = new FeatureComponent(
            Component.translatable("gui.ae2_batchcraft.provider.section.configuration"), 142, 173, null
    );

    public Pp2mpResourceIntakeScreen(Pp2mpResourceIntakeMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        ScreenHeader.addTo(this.widgets);
        this.extractionInterval = new ValidatedIntegerField(
                style, Minecraft.getInstance().font, 0, 0, 36, 12,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.interval"),
                () -> ProductExtractionConfigConstants.INTERVAL_MIN,
                () -> ProductExtractionConfigConstants.INTERVAL_MAX,
                this.menu::setExtractionInterval
        );
        this.widgets.add("extraction_interval", this.extractionInterval);
        this.extractionInterval.syncValue(this.menu.getExtractionInterval());

        this.extractionQuantity = new ValidatedIntegerField(
                style, Minecraft.getInstance().font, 0, 0, 36, 12,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.quantity"),
                () -> ProductExtractionConfigConstants.QUANTITY_MIN,
                () -> ProductExtractionConfigConstants.QUANTITY_MAX,
                this.menu::setExtractionQuantity
        );
        this.widgets.add("extraction_quantity", this.extractionQuantity);
        this.extractionQuantity.syncValue(this.menu.getExtractionQuantity());
        this.passiveMode = this.widgets.addButton("passive", Component.translatable(
                "gui.ae2_batchcraft.resource_intake.mode.passive"), () -> {
            this.menu.setActivePull(false);
            this.updateButtons();
        });
        this.passiveMode.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.resource_intake.mode.passive.tooltip")));
        this.pullMode = this.widgets.addButton("active", Component.translatable(
                "gui.ae2_batchcraft.resource_intake.mode.active"), () -> {
            this.menu.setActivePull(true);
            this.updateButtons();
        });
        this.pullMode.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.resource_intake.mode.active.tooltip")));
        this.roundRobinMode = this.addDistributionButton("round_robin", 0);
        this.priorityMode = this.addDistributionButton("priority", 1);
        this.evenMode = this.addDistributionButton("even", 2);
        this.widgets.addButton("provider_filter", Component.translatable(
                "gui.ae2_batchcraft.provider_filter"), this::openProviderFilter);
        this.updateButtons();
    }

    /* 使用现有服务端动作直接选择分配模式。 */
    private AE2Button addDistributionButton(String name, int mode) {
        AE2Button button = new ScrollingAE2Button(Component.translatable(
                "gui.ae2_batchcraft.resource_intake.distribution." + name + ".button"),
                ignored -> this.menu.setDistributionMode(mode));
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.resource_intake.distribution." + name)));
        this.widgets.add("distribution_" + name, button);
        return button;
    }

    private static void drawExtractionLabels(GuiGraphics graphics, FeatureComponent.ContentBounds bounds) {
        int labelX = bounds.left() + 4;
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.interval"),
                labelX, bounds.top() + 25, 0xFF404040, false
        );
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.quantity"),
                labelX, bounds.top() + 39, 0xFF404040, false
        );
        int unitX = bounds.left() + 127;
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.tick"),
                unitX, bounds.top() + 25, 0xFF404040, false
        );
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.items"),
                unitX, bounds.top() + 39, 0xFF404040, false
        );
    }

    @Override
    protected void init() {
        super.init();
        this.updateButtons();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.extractionInterval.syncValue(this.menu.getExtractionInterval());
        this.extractionQuantity.syncValue(this.menu.getExtractionQuantity());
        this.updateButtons();
    }

    @Override
    public void onClose() {
        this.commitExtractionParameters();
        super.onClose();
    }

    @Override
    public void removed() {
        this.commitExtractionParameters();
        super.removed();
    }

    /* 提交当前输入后再切换到供应过滤子菜单。 */
    private void openProviderFilter() {
        this.commitExtractionParameters();
        this.menu.openProviderFilter();
    }

    /* 提交两个提取参数，避免离开页面时丢失尚未确认的输入。 */
    private void commitExtractionParameters() {
        this.extractionInterval.commitValue();
        this.extractionQuantity.commitValue();
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        this.sourceSection.drawBackground(graphics, this.font, offsetX, offsetY, this.imageWidth);
        this.distributionSection.drawBackground(graphics, this.font, offsetX, offsetY, this.imageWidth);
        this.filterSection.drawBackground(graphics, this.font, offsetX, offsetY, this.imageWidth);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        this.sourceSection.drawForeground(graphics, this.font, 0, 0, this.imageWidth);
        this.distributionSection.drawForeground(graphics, this.font, 0, 0, this.imageWidth);
        this.filterSection.drawForeground(graphics, this.font, 0, 0, this.imageWidth);
    }

    /* 根据服务端同步状态禁用当前资源获取和分配模式按钮。 */
    private void updateButtons() {
        this.passiveMode.active = this.menu.isActivePull();
        this.pullMode.active = !this.menu.isActivePull();
        int mode = this.menu.getDistributionMode();
        this.roundRobinMode.active = mode != 0;
        this.priorityMode.active = mode != 1;
        this.evenMode.active = mode != 2;
    }
}
