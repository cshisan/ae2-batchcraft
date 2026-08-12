package cn.ae2bc.client;

import java.util.function.Consumer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;

/** Sliding boolean switch used by the configuration screens. */
final class ToggleSwitch extends Button {
    static final int LABEL_OFFSET = 26;
    private boolean selected;
    private final Consumer<Boolean> changeListener;

    ToggleSwitch(int x, int y, int width, ITextComponent message, boolean selected,
            Consumer<Boolean> changeListener) {
        super(x, y, width, 14, message, button -> { });
        this.selected = selected;
        this.changeListener = changeListener;
    }

    boolean selected() {
        return selected;
    }

    @Override
    public void onPress() {
        selected = !selected;
        changeListener.accept(selected);
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        int trackY = y + 1;
        boolean hovered = isHovered;
        int border = !active ? 0xff3a3a3a : hovered ? 0xffd0d0d0 : 0xff777777;
        int track = !active ? 0xff252525 : selected ? 0xff244f6b : 0xff3f3f3f;
        int inner = !active ? 0xff303030 : selected ? 0xff347299 : hovered ? 0xff5b5b5b : 0xff4b4b4b;
        // AE2's compact controls use a one-pixel dark frame and a top highlight.
        AbstractGui.fill(matrices, x, trackY, x + 22, trackY + 12, border);
        AbstractGui.fill(matrices, x + 1, trackY + 1, x + 21, trackY + 11, track);
        AbstractGui.fill(matrices, x + 2, trackY + 2, x + 20, trackY + 3, inner);
        int knobX = selected ? x + 12 : x + 2;
        int knobBorder = !active ? 0xff555555 : hovered ? 0xffffffff : 0xffa0a0a0;
        int knob = !active ? 0xff6b6b6b : 0xffd8d8d8;
        AbstractGui.fill(matrices, knobX, trackY + 2, knobX + 8, trackY + 10, knobBorder);
        AbstractGui.fill(matrices, knobX + 1, trackY + 3, knobX + 7, trackY + 9, knob);
        int textColor = active ? (hovered ? 0xff202020 : 0xff404040) : 0xff8a8a8a;
        Minecraft.getInstance().font.draw(matrices, getMessage(), x + LABEL_OFFSET, y + 3, textColor);
    }
}
