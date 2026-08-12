package cn.ae2bc.client;

import java.util.List;

import net.minecraft.util.text.TextComponentTranslation;

/** Native 1.12 tooltip helper; no TOP/Waila dependency is required. */
public final class TooltipSupport {
    private TooltipSupport() { }

    public static void add(List<String> tooltip, String key) {
        tooltip.add(new TextComponentTranslation(key).getUnformattedText());
    }
}
