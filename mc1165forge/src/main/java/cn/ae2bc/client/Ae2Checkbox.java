package cn.ae2bc.client;

import java.util.function.Consumer;

import net.minecraft.client.gui.widget.button.CheckboxButton;
import net.minecraft.util.text.ITextComponent;

final class Ae2Checkbox extends CheckboxButton {
    private final Consumer<Boolean> changeListener;

    Ae2Checkbox(int x, int y, int width, ITextComponent message, boolean selected,
            Consumer<Boolean> changeListener) {
        super(x, y, width, 14, message, selected);
        this.changeListener = changeListener;
    }

    @Override
    public void onPress() {
        super.onPress();
        changeListener.accept(selected());
    }
}
