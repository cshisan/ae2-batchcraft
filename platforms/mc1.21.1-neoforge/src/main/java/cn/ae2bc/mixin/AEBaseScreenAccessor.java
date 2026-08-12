package cn.ae2bc.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AEBaseScreen.class)
public interface AEBaseScreenAccessor {
    @Accessor("widgets")
    WidgetContainer ae2bc$getWidgets();

    @Invoker("addToLeftToolbar")
    <B extends Button> B ae2bc$addToLeftToolbar(B button);
}
