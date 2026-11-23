package org.ae2bc.part;

import appeng.api.config.Actionable;
import appeng.api.parts.IPartHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.part.general.Pp2mpInsertPlan;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitTransferPortPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;

class TransferPortSimulationTest {
    private static final GenericStack STONE = new GenericStack(AEItemKey.of(Items.STONE), 2);

    @Test
    void realPortSimulatesOnceAndRetainsSharedSlotReservation() {
        Fixture fixture = new Fixture();
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertEquals(2, fixture.port.reserve(
                fixture.manager, TransferPortSimulationTest.STONE, MaterialOutputMode.NORMAL, plan));
        Mockito.verify(fixture.handler).insertItem(Mockito.eq(0), Mockito.any(ItemStack.class), Mockito.eq(true));
        Assertions.assertEquals(0, fixture.port.reserve(fixture.manager,
                new GenericStack(AEItemKey.of(Items.DIRT), 2), MaterialOutputMode.NORMAL, plan));
        Assertions.assertTrue(fixture.handler.getStackInSlot(0).isEmpty());
    }

    @Test
    void reservationAndDeliveryShareBindingModeAndWhitelistChecks() {
        Fixture fixture = new Fixture();
        fixture.port.bound = false;
        Assertions.assertEquals(0, fixture.reserve(MaterialOutputMode.NORMAL));
        fixture.port.bound = true;
        Assertions.assertEquals(0, fixture.reserve(MaterialOutputMode.DROP));
        fixture.port.getFilterMarkers().setStack(0, new GenericStack(AEItemKey.of(Items.DIRT), 0));
        Assertions.assertEquals(0, fixture.reserve(MaterialOutputMode.NORMAL));
        Assertions.assertEquals(0, fixture.port.output(fixture.manager,
                TransferPortSimulationTest.STONE, MaterialOutputMode.NORMAL, Actionable.SIMULATE));
        Mockito.verifyNoInteractions(fixture.handler);
        fixture.port.getFilterMarkers().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 0));
        Assertions.assertEquals(2, fixture.reserve(MaterialOutputMode.NORMAL));
        Assertions.assertEquals(0, fixture.port.output(fixture.manager,
                TransferPortSimulationTest.STONE, MaterialOutputMode.NORMAL, Actionable.MODULATE));
        fixture.data.begin(Map.of(AEItemKey.of(Items.DIAMOND), 1L), null, UUID.randomUUID(), null);
        Assertions.assertEquals(2, fixture.port.output(fixture.manager,
                TransferPortSimulationTest.STONE, MaterialOutputMode.NORMAL, Actionable.MODULATE));
        Assertions.assertEquals(2, fixture.handler.getStackInSlot(0).getCount());
    }

    @Test
    void invertedFilterRejectsMarkedMaterialDuringReservation() {
        Fixture fixture = new Fixture();
        fixture.port.getFilterMarkers().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 0));
        fixture.port.getUpgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        Assertions.assertEquals(0, fixture.reserve(MaterialOutputMode.NORMAL));
        Assertions.assertEquals(2, fixture.port.reserve(fixture.manager,
                new GenericStack(AEItemKey.of(Items.DIRT), 2), MaterialOutputMode.NORMAL, new Pp2mpInsertPlan()));
        Assertions.assertTrue(fixture.handler.getStackInSlot(0).isEmpty());
    }

    private static final class Fixture {
        private final Pp2mpUnitManagerPart manager = Mockito.mock(Pp2mpUnitManagerPart.class);
        private final Pp2mpUnitManagerData data = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
        private final ItemStackHandler handler = Mockito.spy(new ItemStackHandler(1));
        private final TestPort port = new TestPort();

        private Fixture() {
            ServerLevel level = Mockito.mock(ServerLevel.class);
            ChestBlockEntity entity = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            entity.setLevel(level);
            this.port.setPartHostInfo(Direction.NORTH, Mockito.mock(IPartHost.class), entity);
            Mockito.when(this.manager.getData()).thenReturn(this.data);
            Pp2mpUnitManagerLogic logic = new Pp2mpUnitManagerLogic(Mockito.mock(IManagedGridNode.class), this.manager);
            Mockito.when(this.manager.getLogic()).thenReturn(logic);
            Mockito.when(level.isLoaded(Mockito.any(BlockPos.class))).thenReturn(true);
            Mockito.when(level.getCapability(Mockito.eq(Capabilities.ItemHandler.BLOCK),
                    Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenReturn(this.handler);
        }

        private long reserve(MaterialOutputMode mode) {
            return this.port.reserve(this.manager, TransferPortSimulationTest.STONE, mode, new Pp2mpInsertPlan());
        }
    }

    private static final class TestPort extends Pp2mpUnitTransferPortPart {
        private boolean bound = true;

        private TestPort() {
            super(ItemRegistry.PP2MP_UNIT_PORT_TRANSFER.get());
        }

        @Override
        public boolean isBoundTo(Pp2mpUnitManagerPart manager) {
            return this.bound;
        }
    }
}
