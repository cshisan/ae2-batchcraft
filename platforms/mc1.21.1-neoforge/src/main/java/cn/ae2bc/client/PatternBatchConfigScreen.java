package cn.ae2bc.client;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.style.PaletteColor;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.logic.PatternBatchCount;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class PatternBatchConfigScreen<C extends PatternEncodingTermMenu>
        extends AESubScreen<C, PatternEncodingTermScreen<C>> {
    private static final String STYLE = "/screens/ae2_batchcraft/pattern_batch_config.json";
    private static final int TEXT_LEFT = 12;
    private static final int TEXT_WIDTH = 152;
    private EditBox batchCountInput;
    private Component maximum = Component.empty();
    private Component correction = Component.empty();
    private boolean correctionVisible;

    public PatternBatchConfigScreen(PatternEncodingTermScreen<C> parent) {
        super(parent, STYLE);
        var icon = getMenu().getHost().getMainMenuIcon();
        widgets.add("back", new TabButton(Icon.BACK, icon.getHoverName(), button -> returnToParent()));
        widgets.add("confirm", new AE2Button(Component.translatable(
                "gui.ae2_batchcraft.pattern_batch_count.confirm"), button -> commitValue()));
    }

    @Override
    protected void init() {
        super.init();
        batchCountInput = addRenderableWidget(new EditBox(font, leftPos + 12, topPos + 42, 96, 18,
                Component.translatable("gui.ae2_batchcraft.pattern_batch_count")));
        batchCountInput.setMaxLength(19);
        batchCountInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        batchCountInput.setResponder(value -> correctionVisible = false);
        batchCountInput.setValue(Long.toString(extension().ae2bc$getPatternBatchCount()));
        batchCountInput.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.pattern_batch_count.tooltip")));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        maximum = Component.translatable("gui.ae2_batchcraft.pattern_batch_count.maximum",
                extension().ae2bc$getMaximumPatternBatchCount());
        if (!batchCountInput.isFocused()) {
            String synced = Long.toString(extension().ae2bc$getPatternBatchCount());
            if (!batchCountInput.getValue().equals(synced)) {
                batchCountInput.setValue(synced);
            }
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        Component status = correctionVisible ? correction : maximum;
        PaletteColor color = correctionVisible ? PaletteColor.ERROR : PaletteColor.DEFAULT_TEXT_COLOR;
        drawWrapped(graphics, status, 66, style.getColor(color).toARGB());
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int y, int color) {
        int lineY = y;
        for (var line : font.split(text, TEXT_WIDTH)) {
            graphics.drawString(font, line, TEXT_LEFT, lineY, color, false);
            lineY += font.lineHeight;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            commitValue();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitValue() {
        long requested;
        try {
            requested = Long.parseLong(batchCountInput.getValue());
        } catch (NumberFormatException ignored) {
            requested = 0;
        }
        long maximum = extension().ae2bc$getMaximumPatternBatchCount();
        PatternBatchCount.Validation validation = PatternBatchCount.validate(requested, maximum);
        extension().ae2bc$setPatternBatchCount(validation.getValue());
        batchCountInput.setValue(Long.toString(validation.getValue()));
        correction = switch (validation.getResult()) {
            case VALID -> Component.empty();
            case ABOVE_MAXIMUM -> Component.translatable(
                    "gui.ae2_batchcraft.pattern_batch_count.corrected_maximum", maximum);
            case INVALID, NOT_DIVISIBLE -> Component.translatable(
                    "gui.ae2_batchcraft.pattern_batch_count.corrected_one");
        };
        correctionVisible = validation.getResult() != PatternBatchCount.Result.VALID;
    }

    private PatternEncodingTermMenuExtension extension() {
        return (PatternEncodingTermMenuExtension) getMenu();
    }

    @Override
    public void onClose() {
        returnToParent();
    }
}
