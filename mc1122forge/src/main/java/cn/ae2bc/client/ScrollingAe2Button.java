package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** Compact AE-style button whose overlong label scrolls without crossing the button border. */
final class ScrollingAe2Button extends Ae2Button {
    private static final long STEP_MILLIS = 350L;
    private static final int END_PAUSE_STEPS = 3;

    ScrollingAe2Button(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        String text = displayString;
        displayString = "";
        super.drawButton(minecraft, mouseX, mouseY, partialTicks);
        displayString = text;
        if (!visible) return;

        FontRenderer font = minecraft.fontRenderer;
        int availableWidth = Math.max(1, width - 8);
        int color = enabled ? (hovered ? 0xFFFFA0 : 0xFFFFFF) : 0xA0A0A0;
        int textWidth = font.getStringWidth(text);
        float textY = y + (height - 8) / 2.0F;
        if (textWidth <= availableWidth) {
            font.drawStringWithShadow(text, x + (width - textWidth) / 2.0F, textY, color);
            return;
        }

        int start = scrollingStart(font, text, availableWidth);
        String visibleText = fit(font, text.substring(start), availableWidth);
        font.drawStringWithShadow(visibleText, x + 4.0F, textY, color);
    }

    private static int scrollingStart(FontRenderer font, String text, int availableWidth) {
        int lastStart = 0;
        for (int start = 0; start < text.length();) {
            int next = text.offsetByCodePoints(start, 1);
            if (font.getStringWidth(text.substring(next)) <= availableWidth) {
                lastStart = next;
                break;
            }
            lastStart = next;
            start = next;
        }
        int steps = text.codePointCount(0, lastStart);
        if (steps <= 0) return 0;
        int cycle = steps * 2 + END_PAUSE_STEPS * 2;
        int phase = (int) ((System.currentTimeMillis() / STEP_MILLIS) % cycle);
        int index;
        if (phase < END_PAUSE_STEPS) index = 0;
        else if (phase < END_PAUSE_STEPS + steps) index = phase - END_PAUSE_STEPS;
        else if (phase < END_PAUSE_STEPS * 2 + steps) index = steps;
        else index = cycle - phase;
        return text.offsetByCodePoints(0, Math.min(steps, index));
    }

    private static String fit(FontRenderer font, String text, int availableWidth) {
        int end = 0;
        while (end < text.length()) {
            int next = text.offsetByCodePoints(end, 1);
            if (font.getStringWidth(text.substring(0, next)) > availableWidth) break;
            end = next;
        }
        return text.substring(0, end);
    }
}
