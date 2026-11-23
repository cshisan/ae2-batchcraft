package org.ae2bc.part;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.AECapabilities;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.Pp2mpReceiveTarget;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Map;

class ReceiveTargetInvalidationTest {
    @SuppressWarnings("UnstableApiUsage")
    @Test
    void genericReturnsDoNotCreateStrategiesAndStableFallbackReusesThem() {
        IGrid grid = Mockito.mock(IGrid.class);
        Pp2mpGridService service = Mockito.mock(Pp2mpGridService.class);
        Pp2mpInputPart input = new Pp2mpInputPart(ItemRegistry.PP2MP_INPUT.get());
        ServerLevel level = Mockito.mock(ServerLevel.class);
        BarrelBlockEntity block = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        block.setLevel(level);
        IPartHost host = Mockito.mock(IPartHost.class);
        input.setPartHostInfo(Direction.EAST, host, block);
        GenericInternalInventory inventory = Mockito.mock(GenericInternalInventory.class);
        Mockito.when(grid.getService(Pp2mpGridService.class)).thenReturn(service);
        Mockito.when(service.findInput((short) 8)).thenReturn(input);
        Mockito.when(level.getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                BlockPos.ZERO.east(), Direction.EAST)).thenReturn(inventory);
        Pp2mpReceiveTarget target = new Pp2mpReceiveTarget(() -> grid, () -> (short) 8);
        for (int index = 0; index < 128; index++) {
            Assertions.assertSame(inventory, target.findInventory());
        }
        Mockito.verify(level, Mockito.never()).registerCapabilityListener(
                Mockito.any(BlockPos.class), Mockito.any(ICapabilityInvalidationListener.class));
        Map<AEKeyType, ExternalStorageStrategy> first = target.findStrategies();
        Mockito.clearInvocations(level);
        Assertions.assertSame(first, target.findStrategies());
        Mockito.verifyNoInteractions(level);
        input.setPartHostInfo(Direction.WEST, host, block);
        Assertions.assertNotSame(first, target.findStrategies());
        Mockito.when(service.findInput((short) 8)).thenReturn(null);
        Assertions.assertTrue(target.findStrategies().isEmpty());
        Assertions.assertNull(target.findInventory());
    }

    @SuppressWarnings("UnstableApiUsage")
    @Test
    void disconnectedGridDiscardsPreviouslyResolvedStrategies() throws ReflectiveOperationException {
        Pp2mpReceiveTarget target = new Pp2mpReceiveTarget(() -> null, () -> (short) 8);
        ExternalStorageStrategy stale = (extractableOnly, changed) -> null;
        Field strategies = Pp2mpReceiveTarget.class.getDeclaredField("storageStrategies");
        strategies.setAccessible(true);
        strategies.set(target, Map.of(AEKeyType.items(), stale));
        Assertions.assertTrue(target.findStrategies().isEmpty());
        Assertions.assertNull(strategies.get(target));
        Assertions.assertNull(target.findInventory());
    }
}
