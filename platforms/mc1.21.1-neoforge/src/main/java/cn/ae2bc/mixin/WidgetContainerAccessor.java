package cn.ae2bc.mixin;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.ICompositeWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(WidgetContainer.class)
public interface WidgetContainerAccessor {
    @Accessor("compositeWidgets")
    Map<String, ICompositeWidget> ae2bc$getCompositeWidgetsById();
}
