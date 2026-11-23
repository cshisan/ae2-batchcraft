package org.ae2bc.client.screen;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.TabButton;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.client.screen.component.TextSwitch;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Pp2mpProviderFilterScreen extends Pp2mpFilterScreen<Pp2mpProviderFilterMenu> {
    private final @Nullable TextSwitch syncSwitch;
    private final ItemStack virtualInverter = AEItems.INVERTER_CARD.stack();

    /* 仅供应单元端口主界面创建同步开关，过滤子页沿用主页面状态。 */
    public Pp2mpProviderFilterScreen(
            Pp2mpProviderFilterMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style
    ) {
        super(menu, playerInventory, title, style);
        if (this.menu.hasSyncSwitch()) {
            this.syncSwitch = new TextSwitch(
                    style, () -> this.menu.setSynced(!this.menu.isSynced()),
                    Component.translatable("gui.ae2_batchcraft.sync_switch")
            );
            this.widgets.add("sync", this.syncSwitch);
        } else {
            this.syncSwitch = null;
        }
        TabButton back = new TabButton(Icon.BACK, this.menu.getHostTitle(), ignored -> this.returnToParent());
        back.visible = this.menu.hasParent();
        this.widgets.add("back", back);
    }

    /* 同步开关文字与资源标记标题保持在同一行。 */
    @Override
    protected void init() {
        super.init();
        if (this.syncSwitch != null) {
            int width = this.syncSwitch.fitToMessage();
            this.syncSwitch.setX(this.leftPos + this.imageWidth - 8 - width);
        }
        this.updateSyncSwitch();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.updateSyncSwitch();
    }

    private void updateSyncSwitch() {
        if (this.syncSwitch != null) {
            this.syncSwitch.active = this.menu.isInitialized();
            this.syncSwitch.setSelected(this.menu.isSynced());
        }
    }

    /* 返回实际所属菜单，并恢复打开过滤界面前的分页。 */
    private void returnToParent() {
        Pp2mpPagedScreen.rememberReturnRoute(this.menu.getTarget(), this.menu.getParentRoute());
        this.menu.returnToParent();
    }

    @Override
    public void onClose() {
        if (this.menu.hasParent()) {
            this.returnToParent();
        } else {
            super.onClose();
        }
    }

    /* 中键打开已标记资源的目标数量配置。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance().options.keyPickItem.matchesMouse(button)) {
            Slot slot = this.getSlotUnderMouse();
            int markerSlot = this.menu.getSlots(SlotSemantics.CONFIG).indexOf(slot);
            AEKey key = markerSlot < 0 ? null : this.menu.getMarkerKey(markerSlot);
            if (key != null && this.menu.isEditable() && this.menu.isWhitelist()) {
                GenericStack marker = new GenericStack(
                        key,
                        Math.max(1, this.menu.getLimit(markerSlot))
                );
                this.switchToScreen(new Pp2mpProviderAmountScreen(this, marker, markerSlot));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /* 同步时只替换升级槽的显示，其余槽位仍使用原生渲染和数量标记。 */
    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        if (this.isUpgradeSlot(slot) && (!this.menu.isInitialized() || this.menu.isSynced())) {
            this.renderSyncedUpgrade(graphics, slot);
            return;
        }
        super.renderSlot(graphics, slot);
        int markerSlot = this.menu.getSlots(SlotSemantics.CONFIG).indexOf(slot);
        if (markerSlot < 0 || !slot.isActive() || !this.menu.isWhitelist()) {
            return;
        }

        AEKey key = this.menu.getMarkerKey(markerSlot);
        long amount = this.menu.getLimit(markerSlot);
        if (key == null || amount <= 0) {
            return;
        }

        boolean largeFonts = AEConfig.instance().isUseLargeFonts();
        AmountFormat format = largeFonts ? AmountFormat.SLOT_LARGE_FONT : AmountFormat.SLOT;
        graphics.pose().pushPose();
        try {
            // 对齐普通槽位绘制物品时的 Z 偏移，与 AE2 编码终端的数量标记保持一致。
            graphics.pose().translate(0, 0, 100);
            StackSizeRenderer.renderSizeLabel(graphics, this.font, slot.x, slot.y,
                    key.formatAmount(amount, format), largeFonts);
        } finally {
            graphics.pose().popPose();
        }
    }

    private boolean isUpgradeSlot(@Nullable Slot slot) {
        return slot != null && slot.isActive() && this.menu.getSlotSemantic(slot) == SlotSemantics.UPGRADE;
    }

    /* 虚拟反相卡仅用于绘制，不写入菜单槽位或宿主的真实升级库存。 */
    private void renderSyncedUpgrade(GuiGraphics graphics, Slot slot) {
        if (this.menu.isInitialized() && !this.menu.isWhitelist()) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 0, 100);
                graphics.renderFakeItem(this.virtualInverter, slot.x, slot.y);
            } finally {
                graphics.pose().popPose();
            }
        } else if (slot instanceof AppEngSlot upgradeSlot && upgradeSlot.getIcon() != null) {
            upgradeSlot.getIcon().getBlitter().dest(slot.x, slot.y)
                    .opacity(upgradeSlot.getOpacityOfIcon()).blit(graphics);
        }
    }

    /* 槽位提示由当前生效模式生成，避免空槽的面板提示覆盖虚拟卡说明。 */
    @Override
    protected List<Component> getUpgradePanelTooltip() {
        if (this.isUpgradeSlot(this.getSlotUnderMouse())) {
            return List.of();
        }
        return List.of(GuiText.CompatibleUpgrades.text(), this.virtualInverter.getHoverName());
    }

    /* 同步时显示虚拟规则；关闭同步后说明与本地实物卡不同的保留模式。 */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Slot slot = this.getSlotUnderMouse();
        if (slot == null || !this.isUpgradeSlot(slot)) {
            super.renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (!this.menu.isInitialized() || !this.menu.getCarried().isEmpty()) {
            return;
        }

        ItemStack localUpgrade = slot.getItem();
        boolean whitelist = this.menu.isWhitelist();
        Component mode = Component.translatable(whitelist
                ? "gui.ae2_batchcraft.provider.filter.whitelist"
                : "gui.ae2_batchcraft.provider.filter.blacklist");
        List<Component> lines = new ArrayList<>();
        if (this.menu.isSynced()) {
            lines.add(whitelist ? Component.translatable("gui.ae2_batchcraft.provider_filter")
                    : this.virtualInverter.getHoverName());
            lines.add(Component.translatable("gui.ae2_batchcraft.provider.filter.inherited", mode));
            lines.add(Component.translatable(whitelist
                    ? "gui.ae2_batchcraft.provider.filter.read_only"
                    : "gui.ae2_batchcraft.provider.filter.virtual_upgrade"));
            if (!localUpgrade.isEmpty()) {
                lines.add(Component.translatable("gui.ae2_batchcraft.provider.filter.local_upgrade_preserved"));
            }
        } else {
            boolean localWhitelist = !AEItems.INVERTER_CARD.is(localUpgrade);
            if (whitelist == localWhitelist) {
                if (localUpgrade.isEmpty()) {
                    this.drawTooltipWithHeader(graphics, mouseX, mouseY,
                            List.of(GuiText.CompatibleUpgrades.text(), this.virtualInverter.getHoverName()));
                } else {
                    super.renderTooltip(graphics, mouseX, mouseY);
                }
                return;
            }
            if (localUpgrade.isEmpty()) {
                lines.add(Component.translatable("gui.ae2_batchcraft.provider_filter"));
            } else {
                lines.addAll(super.getTooltipFromContainerItem(localUpgrade));
            }
            lines.add(Component.translatable("gui.ae2_batchcraft.provider.filter.retained", mode));
            lines.add(Component.translatable("gui.ae2_batchcraft.provider.filter.retained_reset"));
        }
        this.drawTooltipWithHeader(graphics, mouseX, mouseY, lines);
    }

    /* 在资源原有提示后显示完整目标数量及黑白名单语义。 */
    @Override
    protected @NonNull List<Component> getTooltipFromContainerItem(@NonNull ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        int markerSlot = this.menu.getSlots(SlotSemantics.CONFIG).indexOf(this.getSlotUnderMouse());
        if (markerSlot < 0) {
            return lines;
        }
        AEKey key = this.menu.getMarkerKey(markerSlot);
        if (key == null) {
            return lines;
        }

        lines = new ArrayList<>(lines);
        if (!this.menu.isWhitelist()) {
            lines.add(Component.translatable(
                    "gui.ae2_batchcraft.provider.target_amount.ignored"
            ).withStyle(ChatFormatting.GRAY));
            return lines;
        }
        long amount = this.menu.getLimit(markerSlot);
        lines.add((amount == 0 ? Component.translatable(
                "gui.ae2_batchcraft.provider.target_amount.unlimited"
        ) : Component.translatable(
                "gui.ae2_batchcraft.provider.target_amount.value",
                key.formatAmount(amount, AmountFormat.FULL)
        )).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(this.menu.isEditable()
                ? "gui.ae2_batchcraft.provider.target_amount.configure"
                : "gui.ae2_batchcraft.provider.filter.read_only"
        ).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

}
