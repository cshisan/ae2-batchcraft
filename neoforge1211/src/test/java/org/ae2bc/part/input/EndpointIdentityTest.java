package org.ae2bc.part.input;

import appeng.api.parts.IPartHost;
import appeng.api.util.AEColor;
import appeng.parts.AEBasePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.ae2bc.part.task.Pp2mpTaskEndpoint;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class EndpointIdentityTest {
    @Test
    void endpointIdentityDistinguishesDimensionSideAndEndpointKind() {
        Pp2mpOutputPart overworld = EndpointIdentityTest.output(Level.OVERWORLD, Direction.NORTH);
        Pp2mpOutputPart nether = EndpointIdentityTest.output(Level.NETHER, Direction.NORTH);
        Pp2mpOutputPart south = EndpointIdentityTest.output(Level.OVERWORLD, Direction.SOUTH);
        Pp2mpOutputPart reloaded = EndpointIdentityTest.output(Level.OVERWORLD, Direction.NORTH);
        Pp2mpUnitManagerPart manager = new Pp2mpUnitManagerPart(
                ItemRegistry.PP2MP_UNIT_MANAGERS.get(AEColor.WHITE).get());
        EndpointIdentityTest.install(manager, Level.OVERWORLD, Direction.NORTH);
        String id = overworld.getLogic().getEndpointId();
        Assertions.assertNotNull(id);
        Assertions.assertNotEquals(id, nether.getLogic().getEndpointId());
        Assertions.assertNotEquals(id, south.getLogic().getEndpointId());
        Assertions.assertNotEquals(id, manager.getLogic().getEndpointId());
        Assertions.assertEquals(id, reloaded.getLogic().getEndpointId());
        Assertions.assertSame(nether.getLogic(), Pp2mpInputLogic.findEndpoint(
                List.of(overworld.getLogic(), nether.getLogic()), nether.getLogic().getEndpointId()));
    }

    @Test
    void absentDuplicateAndOldIdentitiesNeverGuessADeliveryTarget() {
        Pp2mpTaskEndpoint first = EndpointIdentityTest.output(Level.OVERWORLD, Direction.NORTH).getLogic();
        Pp2mpTaskEndpoint duplicate = EndpointIdentityTest.output(Level.OVERWORLD, Direction.NORTH).getLogic();
        Assertions.assertNull(Pp2mpInputLogic.findEndpoint(List.of(first, duplicate), first.getEndpointId()));
        Assertions.assertNull(Pp2mpInputLogic.findEndpoint(List.of(), first.getEndpointId()));
        Assertions.assertNull(Pp2mpInputLogic.findEndpoint(List.of(first), null));
        Pp2mpTaskEndpoint old = Mockito.mock(Pp2mpTaskEndpoint.class);
        Mockito.when(old.getEndpointId()).thenReturn("output:0:north");
        Assertions.assertNull(Pp2mpInputLogic.findEndpoint(List.of(old), "output:0:north"));
        Assertions.assertNull(new Pp2mpOutputPart(ItemRegistry.PP2MP_OUTPUT.get()).getLogic().getEndpointId());
    }

    private static Pp2mpOutputPart output(ResourceKey<Level> dimension, Direction side) {
        Pp2mpOutputPart part = new Pp2mpOutputPart(ItemRegistry.PP2MP_OUTPUT.get());
        EndpointIdentityTest.install(part, dimension, side);
        return part;
    }

    private static void install(AEBasePart part, ResourceKey<Level> dimension, Direction side) {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        Mockito.when(level.dimension()).thenReturn(dimension);
        ChestBlockEntity host = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        host.setLevel(level);
        part.setPartHostInfo(side, Mockito.mock(IPartHost.class), host);
    }
}
