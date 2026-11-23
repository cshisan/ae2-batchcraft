package org.ae2bc.client.screen.component;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.ae2bc.core.unit.OutputPortMode;

import java.util.Locale;
import java.util.function.Consumer;

public final class OutputPortModeButton extends ScrollingAE2Button {
    private final OutputPortMode mode;

    public OutputPortModeButton(OutputPortMode mode, Consumer<OutputPortMode> onPress) {
        super(Component.translatable("gui.ae2_batchcraft.feeding_mode." + mode.name().toLowerCase(Locale.ROOT)),
                button -> onPress.accept(mode));
        this.mode = mode;
        this.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.feeding_mode." + mode.name().toLowerCase(Locale.ROOT) + ".description")));
    }

    public String widgetId() {
        return "feeding_mode_" + this.mode.name().toLowerCase(Locale.ROOT);
    }

    public void update(OutputPortMode selected, boolean synced) {
        this.active = !synced && this.mode != selected;
    }
}
