package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.util.DimensionalBlockPos;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortMenu;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitDropPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitPlacePortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitTransferPortPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

class OutputPortPriorityTest {
    @Test
    void nativePriorityMenuEditsAllOutputPortTypesAndReturnsToTheirMainMenu() {
        List<Pp2mpUnitOutputTypePortPart> ports = List.of(
                new Pp2mpUnitTransferPortPart(ItemRegistry.PP2MP_UNIT_PORT_TRANSFER.get()),
                new Pp2mpUnitDropPortPart(ItemRegistry.PP2MP_UNIT_PORT_DROP.get()),
                new Pp2mpUnitPlacePortPart(ItemRegistry.PP2MP_UNIT_PORT_PLACE.get()));
        for (Pp2mpUnitOutputTypePortPart port : ports) {
            Fixture fixture = new Fixture(port);
            fixture.port.setPriority(42);
            PriorityMenu menu = new PriorityMenu(1, new Inventory(fixture.player), fixture.port);
            Assertions.assertEquals(42, menu.getPriorityValue());
            Assertions.assertSame(fixture.port, menu.getHost());
            menu.setPriority(-42);
            Assertions.assertEquals(-42, fixture.port.getPriority());
            Assertions.assertEquals(-42, menu.getPriorityValue());
            PriorityMenu reopened = new PriorityMenu(2, new Inventory(fixture.player), fixture.port);
            Assertions.assertEquals(-42, reopened.getPriorityValue());
            Assertions.assertSame(port.getPartItem().asItem(), fixture.port.getMainMenuIcon().getItem());
            Mockito.verify(fixture.service, Mockito.times(2)).portOrderChanged();
            Mockito.verify(fixture.port, Mockito.times(2)).wakeManager();
            Mockito.verify(fixture.host, Mockito.times(2)).markForSave();
            Mockito.verify(fixture.host, Mockito.times(2)).markForUpdate();
            Mockito.verify(fixture.service, Mockito.never()).topologyChanged();

            MenuHostLocator locator = MenuLocators.forPart(fixture.port);
            try (MockedStatic<MenuOpener> opener = Mockito.mockStatic(
                    MenuOpener.class, Mockito.withSettings().mockMaker(MockMakers.INLINE))) {
                fixture.port.returnToMainMenu(fixture.player, reopened);
                opener.verify(() -> MenuOpener.returnTo(
                        Pp2mpUnitOutputTypePortMenu.TYPE, fixture.player, locator));
            }
        }
    }

    @Test
    void priorityLimitsAndUnchangedValuesAvoidRepeatedSaveAndInvalidation() {
        Fixture fixture = new Fixture(new Pp2mpUnitTransferPortPart(ItemRegistry.PP2MP_UNIT_PORT_TRANSFER.get()));
        fixture.port.setPriority(0);
        Mockito.verify(fixture.host, Mockito.never()).markForSave();
        Mockito.verifyNoInteractions(fixture.service);
        Mockito.verify(fixture.port, Mockito.never()).wakeManager();

        fixture.port.setPriority(Integer.MAX_VALUE);
        Assertions.assertEquals(Pp2mpUnitOutputTypePortPart.MAX_PRIORITY, fixture.port.getPriority());
        fixture.port.setPriority(Integer.MAX_VALUE);
        fixture.port.setPriority(Pp2mpUnitOutputTypePortPart.MAX_PRIORITY);
        fixture.port.setPriority(Integer.MIN_VALUE);
        Assertions.assertEquals(Pp2mpUnitOutputTypePortPart.MIN_PRIORITY, fixture.port.getPriority());
        fixture.port.setPriority(Integer.MIN_VALUE);
        fixture.port.setPriority(Pp2mpUnitOutputTypePortPart.MIN_PRIORITY);

        Mockito.verify(fixture.host, Mockito.times(2)).markForSave();
        Mockito.verify(fixture.host, Mockito.times(2)).markForUpdate();
        Mockito.verify(fixture.service, Mockito.times(2)).portOrderChanged();
        Mockito.verify(fixture.port, Mockito.times(2)).wakeManager();
        Mockito.verify(fixture.service, Mockito.never()).topologyChanged();
    }

    @Test
    void disconnectedPortStillSavesPriorityWithoutAccessingTheGridService() {
        Fixture fixture = new Fixture(new Pp2mpUnitTransferPortPart(ItemRegistry.PP2MP_UNIT_PORT_TRANSFER.get()));
        Mockito.when(fixture.node.getGrid()).thenReturn(null);
        fixture.port.setPriority(-7);
        Assertions.assertEquals(-7, fixture.port.getPriority());
        Mockito.verify(fixture.host).markForSave();
        Mockito.verify(fixture.host).markForUpdate();
        Mockito.verify(fixture.port).wakeManager();
        Mockito.verifyNoInteractions(fixture.service);
    }

    private static final class Fixture {
        private final IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
        private final IPartHost host = Mockito.mock(IPartHost.class);
        private final Pp2mpGridService service = Mockito.mock(Pp2mpGridService.class);
        private final ServerPlayer player = Mockito.mock(ServerPlayer.class);
        private final Pp2mpUnitOutputTypePortPart port;

        private Fixture(Pp2mpUnitOutputTypePortPart port) {
            ServerLevel level = Mockito.mock(ServerLevel.class);
            ChestBlockEntity entity = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            entity.setLevel(level);
            port.setPartHostInfo(Direction.NORTH, this.host, entity);
            this.port = Mockito.spy(port);
            Mockito.when(this.host.getLocation()).thenReturn(new DimensionalBlockPos(level, BlockPos.ZERO));
            Mockito.when(this.player.getCommandSenderWorld()).thenReturn(level);
            IGrid grid = Mockito.mock(IGrid.class);
            Mockito.when(this.node.getGrid()).thenReturn(grid);
            Mockito.when(grid.getService(Pp2mpGridService.class)).thenReturn(this.service);
            Mockito.doReturn(this.node).when(this.port).getMainNode();
            Mockito.doNothing().when(this.port).wakeManager();
        }
    }
}
