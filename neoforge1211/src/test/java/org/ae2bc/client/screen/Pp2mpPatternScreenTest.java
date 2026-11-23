package org.ae2bc.client.screen;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.TabButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.lang.reflect.Field;

class Pp2mpPatternScreenTest {
    /* 即使父类绘制异常，也不能把同步的任务数永久改成隐藏值。 */
    @Test
    void foregroundFailureStillRestoresJobCount() throws ReflectiveOperationException {
        Pp2mpEncodingTermMenu menu = Mockito.mock(
                Pp2mpEncodingTermMenu.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        Pp2mpPatternScreen screen = Pp2mpPatternScreenTest.screen(menu);
        menu.activeCraftingJobs = 7;
        IllegalStateException failure = new IllegalStateException("Foreground rendering failed");
        Mockito.when(menu.getLinkStatus()).thenThrow(failure);
        Assertions.assertSame(failure, Assertions.assertThrows(IllegalStateException.class,
                () -> screen.drawFG(Mockito.mock(GuiGraphics.class), 0, 0, 0, 0)));
        Assertions.assertEquals(7, menu.activeCraftingJobs);
    }

    private static Pp2mpPatternScreen screen(Pp2mpEncodingTermMenu menu) throws ReflectiveOperationException {
        Pp2mpPatternScreen screen = Mockito.mock(Pp2mpPatternScreen.class);
        Pp2mpPatternScreenTest.setField(AbstractContainerScreen.class, screen, "menu", menu);
        Pp2mpPatternScreenTest.setField(MEStorageScreen.class, screen, "repo",
                Mockito.mock(Repo.class, Mockito.withSettings().mockMaker(MockMakers.INLINE)));
        TabButton status = Mockito.mock(TabButton.class);
        status.visible = false;
        Pp2mpPatternScreenTest.setField(MEStorageScreen.class, screen, "craftingStatusBtn", status);
        Mockito.when(screen.getMenu()).thenReturn(menu);
        Mockito.doCallRealMethod().when(screen).drawFG(
                Mockito.any(GuiGraphics.class), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
        return screen;
    }

    private static void setField(Class<?> owner, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
