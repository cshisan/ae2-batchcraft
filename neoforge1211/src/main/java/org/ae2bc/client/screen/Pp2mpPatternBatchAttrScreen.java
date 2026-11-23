package org.ae2bc.client.screen;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.TabButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.ae2bc.core.pattern.PatternBatchCount;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;

public final class Pp2mpPatternBatchAttrScreen extends AESubScreen<Pp2mpEncodingTermMenu, PatternEncodingTermScreen<Pp2mpEncodingTermMenu>> {
    private EditBox countInput;
    private Component maximum = Component.empty();
    private Component correction = Component.empty();
    private boolean correctionVisible;

    public Pp2mpPatternBatchAttrScreen(
            PatternEncodingTermScreen<Pp2mpEncodingTermMenu> parent
    ) {
        super(parent, "/screens/ae2_batchcraft/pp2mp_pattern_batch_config.json");
        ScreenHeader.addTo(this.widgets);
        this.widgets.add("back", new TabButton(
                Icon.BACK,
                this.getMenu().getHost().getMainMenuIcon().getHoverName(),
                button -> this.returnToParent()
        ));
        this.widgets.add("confirm", new AE2Button(
                Component.translatable("gui.ae2_batchcraft.pattern_batch_count.confirm"),
                button -> this.commitValue()
        ));
    }

    @Override
    protected void init() {
        super.init();
        this.countInput = this.addRenderableWidget(new EditBox(
                this.font, this.leftPos + 12, this.topPos + 38, 96, 18,
                Component.translatable("gui.ae2_batchcraft.pattern_batch_count")
        ));
        this.countInput.setMaxLength(19);
        this.countInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        this.countInput.setResponder(value -> this.correctionVisible = false);
        this.countInput.setValue(Long.toString(this.getMenu().getBatchCount()));
        this.countInput.setTooltip(Tooltip.create(
                Component.translatable("gui.ae2_batchcraft.pattern_batch_count.tooltip")
        ));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.maximum = Component.translatable(
                "gui.ae2_batchcraft.pattern_batch_count.maximum",
                this.getMenu().getMaximumBatchCount()
        );
        if (!this.countInput.isFocused()) {
            String synced = Long.toString(this.getMenu().getBatchCount());
            if (!this.countInput.getValue().equals(synced)) {
                this.countInput.setValue(synced);
            }
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        Component status = this.correctionVisible ? this.correction : this.maximum;
        PaletteColor color = this.correctionVisible
                ? PaletteColor.ERROR : PaletteColor.DEFAULT_TEXT_COLOR;
        int lineY = 62;
        for (FormattedCharSequence line : this.font.split(status, 152)) {
            graphics.drawString(this.font, line, 12, lineY, this.style.getColor(color).toARGB(), false);
            lineY += this.font.lineHeight;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            this.commitValue();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitValue() {
        long requested;
        try {
            requested = Long.parseLong(this.countInput.getValue());
        } catch (NumberFormatException exception) {
            requested = 0;
        }
        PatternBatchCount.Validation validation = PatternBatchCount.validate(
                requested, this.getMenu().getMaximumBatchCount());
        this.getMenu().setBatchCount(validation.value());
        this.countInput.setValue(Long.toString(validation.value()));
        this.correction = switch (validation.result()) {
            case VALID -> Component.empty();
            case ABOVE_MAXIMUM -> Component.translatable(
                    "gui.ae2_batchcraft.pattern_batch_count.corrected_maximum",
                    this.getMenu().getMaximumBatchCount()
            );
            case INVALID, NOT_DIVISIBLE -> Component.translatable(
                    "gui.ae2_batchcraft.pattern_batch_count.corrected_one"
            );
        };
        this.correctionVisible = validation.result() != PatternBatchCount.Result.VALID;
        if (validation.result() == PatternBatchCount.Result.VALID) {
            this.returnToParent();
        }
    }

    @Override
    public void onClose() {
        this.returnToParent();
    }
}
