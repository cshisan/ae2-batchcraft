package org.ae2bc.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PriorityScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.implementations.PriorityMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PriorityScreen.class)
public abstract class PriorityScreenMixin extends AEBaseScreen<PriorityMenu> {
    @Shadow
    @Final
    private NumberEntryWidget priority;

    protected PriorityScreenMixin(PriorityMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    /* 只为输出型端口约束原生数值组件，并替换不适用的存储总线说明。 */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2bc$configureOutputPortPriority(
            PriorityMenu menu, Inventory inventory, Component title, ScreenStyle style, CallbackInfo ci
    ) {
        if (menu.getHost() instanceof Pp2mpUnitOutputTypePortPart) {
            this.priority.setMinValue(Pp2mpUnitOutputTypePortPart.MIN_PRIORITY);
            this.priority.setMaxValue(Pp2mpUnitOutputTypePortPart.MAX_PRIORITY);
            this.setTextContent("priority_insertion_hint",
                    Component.translatable("gui.ae2_batchcraft.unit_port_output_type.priority_hint"));
            this.setTextContent("priority_extraction_hint", Component.translatable(
                    "gui.ae2_batchcraft.unit_port_output_type.priority_range",
                    Pp2mpUnitOutputTypePortPart.MIN_PRIORITY, Pp2mpUnitOutputTypePortPart.MAX_PRIORITY));
        }
    }
}
