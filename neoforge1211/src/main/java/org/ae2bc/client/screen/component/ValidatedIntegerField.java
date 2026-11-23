package org.ae2bc.client.screen.component;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ValidatedIntegerField extends AETextField {
    private final IntSupplier minimum;
    private final IntSupplier maximum;
    private final IntConsumer changeListener;
    private String pendingValue;
    private String committedValue;

    public ValidatedIntegerField(
            ScreenStyle style, Font font, int x, int y, int width, int height, Component narration,
            IntSupplier minimum, IntSupplier maximum, IntConsumer changeListener
    ) {
        super(style, font, x, y, width, height);
        this.setBordered(false);
        this.setMessage(narration);
        this.minimum = minimum;
        this.maximum = maximum;
        this.changeListener = changeListener;
        this.setMaxLength(Math.max(4, Integer.toString(maximum.getAsInt()).length()));
        this.setTextColorUneditable(0xE0E0E0);
        this.setFilter(this::isValidInput);
    }

    public void syncValue(int value) {
        String normalizedValue = Integer.toString(value);

        if (this.pendingValue != null) {
            if (!this.pendingValue.equals(normalizedValue)) {
                return;
            }
            this.pendingValue = null;
        }

        if (this.isFocused() || this.getValue().equals(normalizedValue)) {
            return;
        }

        this.committedValue = normalizedValue;
        this.setValue(normalizedValue);
    }

    private boolean isValidInput(String value) {
        if (value.isEmpty()) {
            return true;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= this.minimum.getAsInt() && parsed <= this.maximum.getAsInt();
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public void commitValue() {
        String value = this.getValue();
        if (value.isEmpty() || value.equals("-")) {
            if (this.committedValue != null) {
                this.setValue(this.committedValue);
            }
            return;
        }

        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            if (this.committedValue != null) {
                this.setValue(this.committedValue);
            }
            return;
        }

        long minimumValue = this.minimum.getAsInt();
        long maximumValue = this.maximum.getAsInt();
        int clampedValue = (int) Math.clamp(parsed, minimumValue, maximumValue);
        String normalizedValue = Long.toString(clampedValue);

        if (!normalizedValue.equals(this.committedValue)) {
            this.pendingValue = normalizedValue;
            this.committedValue = normalizedValue;
            this.changeListener.accept(clampedValue);
        }

        this.setValue(normalizedValue);
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused && this.isFocused()) {
            this.commitValue();
        }
        super.setFocused(focused);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.commitValue();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
