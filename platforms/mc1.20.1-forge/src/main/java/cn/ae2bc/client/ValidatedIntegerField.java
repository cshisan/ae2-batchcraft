package cn.ae2bc.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

final class ValidatedIntegerField extends EditBox {
    private final IntSupplier minimum;
    private final IntSupplier maximum;
    private final IntConsumer changeListener;
    private boolean syncing;

    ValidatedIntegerField(Font font, int x, int y, int width, int height, Component narration,
                          IntSupplier minimum, IntSupplier maximum, IntConsumer changeListener) {
        super(font, x, y, width, height, narration);
        this.minimum = minimum;
        this.maximum = maximum;
        this.changeListener = changeListener;
        setMaxLength(4);
        setTextColorUneditable(0xE0E0E0);
        setFilter(this::isValidInput);
        setResponder(this::valueChanged);
    }

    void syncValue(int value) {
        if (isFocused() || getValue().equals(Integer.toString(value))) {
            return;
        }
        syncing = true;
        setValue(Integer.toString(value));
        syncing = false;
    }

    private boolean isValidInput(String value) {
        if (value.isEmpty()) {
            return true;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= minimum.getAsInt() && parsed <= maximum.getAsInt();
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void valueChanged(String value) {
        if (syncing || value.isEmpty()) {
            return;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= minimum.getAsInt() && parsed <= maximum.getAsInt()) {
                changeListener.accept(parsed);
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
