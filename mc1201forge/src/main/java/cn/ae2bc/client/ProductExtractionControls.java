package cn.ae2bc.client;

import cn.ae2bc.logic.ProductExtractionSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

/** Shared interval and amount controls used by pattern P2P configuration screens. */
final class ProductExtractionControls {
    private final ValidatedIntegerField interval;
    private final ValidatedIntegerField amount;

    private ProductExtractionControls(ValidatedIntegerField interval, ValidatedIntegerField amount) {
        this.interval = interval;
        this.amount = amount;
    }

    static ProductExtractionControls create(Font font, int left, int top,
                                            UnaryOperator<ValidatedIntegerField> addWidget,
                                            IntConsumer setInterval, IntConsumer setAmount) {
        Component intervalLabel = Component.translatable("gui.ae2_batchcraft.product_extraction.interval");
        Component amountLabel = Component.translatable("gui.ae2_batchcraft.product_extraction.amount");
        int inputX = left + 16 + Math.max(font.width(intervalLabel), font.width(amountLabel));
        var interval = addWidget.apply(new ValidatedIntegerField(font, inputX, top + 92, 36, 16,
                intervalLabel, () -> ProductExtractionSettings.MIN_INTERVAL,
                () -> ProductExtractionSettings.MAX_INTERVAL, setInterval));
        var amount = addWidget.apply(new ValidatedIntegerField(font, inputX, top + 113, 36, 16,
                amountLabel, () -> ProductExtractionSettings.MIN_AMOUNT,
                () -> ProductExtractionSettings.MAX_AMOUNT, setAmount));
        return new ProductExtractionControls(interval, amount);
    }

    void setVisible(boolean visible) {
        interval.visible = visible;
        amount.visible = visible;
    }

    void setEditable(boolean editable) {
        interval.setEditable(editable);
        amount.setEditable(editable);
    }

    void sync(int intervalValue, int amountValue) {
        interval.syncValue(intervalValue);
        amount.syncValue(amountValue);
    }

    void drawUnits(GuiGraphics graphics, Font font, int left) {
        graphics.drawString(font, Component.translatable("gui.ae2_batchcraft.product_extraction.tick"),
                interval.getX() - left + interval.getWidth() + 3, 96, 0xFF404040, false);
        graphics.drawString(font, Component.translatable("gui.ae2_batchcraft.product_extraction.unit"),
                amount.getX() - left + amount.getWidth() + 3, 117, 0xFF404040, false);
    }
}
