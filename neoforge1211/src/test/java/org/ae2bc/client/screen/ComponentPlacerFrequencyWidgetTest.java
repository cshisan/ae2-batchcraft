package org.ae2bc.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

class ComponentPlacerFrequencyWidgetTest {
    @Test
    void leftClickClearsAndRightClickLoadsIncludingAe2ForwardedClicks() throws ReflectiveOperationException {
        ComponentPlacerScreen screen = Mockito.mock(
                ComponentPlacerScreen.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        AtomicInteger clears = new AtomicInteger();
        AtomicInteger loads = new AtomicInteger();
        Class<?> widgetType = Arrays.stream(ComponentPlacerScreen.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("FrequencyWidget"))
                .findFirst().orElseThrow();
        Constructor<?> constructor = widgetType.getDeclaredConstructor(
                ComponentPlacerScreen.class, Component.class, IntSupplier.class, Runnable.class, Runnable.class);
        constructor.setAccessible(true);
        AbstractWidget widget = Assertions.assertInstanceOf(AbstractWidget.class, constructor.newInstance(
                screen, Component.literal("Frequency"), (IntSupplier) () -> 1234,
                (Runnable) clears::incrementAndGet, (Runnable) loads::incrementAndGet));
        widget.setX(10);
        widget.setY(10);
        widget.setWidth(12);
        widget.setHeight(12);

        Minecraft minecraft = Mockito.mock(Minecraft.class);
        Mockito.when(minecraft.getSoundManager()).thenReturn(Mockito.mock(SoundManager.class));
        try (MockedStatic<Minecraft> mockedMinecraft = Mockito.mockStatic(
                Minecraft.class, Mockito.withSettings().mockMaker(MockMakers.INLINE))) {
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(minecraft);
            Assertions.assertFalse(widget.mouseClicked(15, 15, GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
            Assertions.assertFalse(widget.mouseClicked(30, 15, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
            Assertions.assertFalse(widget.mouseClicked(30, 15, GLFW.GLFW_MOUSE_BUTTON_LEFT));
            Assertions.assertEquals(0, clears.get());
            Assertions.assertEquals(0, loads.get());

            Assertions.assertTrue(widget.mouseClicked(15, 15, GLFW.GLFW_MOUSE_BUTTON_LEFT));
            Assertions.assertEquals(1, clears.get());
            Assertions.assertEquals(0, loads.get());
            Assertions.assertTrue(widget.mouseClicked(15, 15, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
            Assertions.assertEquals(1, clears.get());
            Assertions.assertEquals(1, loads.get());

            /* AE2 把右键转发为左键，但保留屏幕上的右键标记。 */
            Mockito.when(screen.isHandlingRightClick()).thenReturn(true);
            Assertions.assertTrue(widget.mouseClicked(15, 15, GLFW.GLFW_MOUSE_BUTTON_LEFT));
            Assertions.assertEquals(1, clears.get());
            Assertions.assertEquals(2, loads.get());
            Mockito.when(screen.isHandlingRightClick()).thenReturn(false);
            Assertions.assertTrue(widget.mouseClicked(15, 15, GLFW.GLFW_MOUSE_BUTTON_LEFT));
            Assertions.assertEquals(2, clears.get());
            Assertions.assertEquals(2, loads.get());
            widget.active = false;
            Assertions.assertFalse(widget.mouseClicked(15, 15, GLFW.GLFW_MOUSE_BUTTON_LEFT));
            Assertions.assertFalse(widget.mouseClicked(15, 15, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
            Assertions.assertEquals(2, clears.get());
            Assertions.assertEquals(2, loads.get());
        }
    }
}
