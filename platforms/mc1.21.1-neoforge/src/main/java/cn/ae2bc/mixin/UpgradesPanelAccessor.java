package cn.ae2bc.mixin;

import appeng.client.gui.widgets.UpgradesPanel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.Supplier;

@Mixin(UpgradesPanel.class)
public interface UpgradesPanelAccessor {
    @Accessor("tooltipSupplier")
    Supplier<List<Component>> ae2bc$getTooltipSupplier();

    @Mutable
    @Accessor("tooltipSupplier")
    void ae2bc$setTooltipSupplier(Supplier<List<Component>> tooltipSupplier);
}
