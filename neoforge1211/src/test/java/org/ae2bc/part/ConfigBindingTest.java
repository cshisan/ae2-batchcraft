package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.parts.AEBasePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.part.OutputConfig;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.output.Pp2mpOutputLogic;
import org.ae2bc.part.output.Pp2mpOutputTicker;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakePart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitProviderPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitTransferPortPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockMakers;

import java.util.List;
import java.util.Set;

class ConfigBindingTest {
    @Test
    void taskAcceptanceAndRunningTicksCalibrateBeforeUsingLocalSettings() {
        Fixture fixture = new Fixture();
        IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
        Mockito.when(node.getGrid()).thenReturn(fixture.grid);
        Pp2mpOutputPart realOutput = new Pp2mpOutputPart(ItemRegistry.PP2MP_OUTPUT.get());
        fixture.install(realOutput, 4);
        Pp2mpOutputPart output = Mockito.spy(realOutput);
        Mockito.when(output.getMainNode()).thenReturn(node);
        Mockito.when(output.getData()).thenReturn(fixture.output.getData());
        Pp2mpOutputLogic outputLogic = new Pp2mpOutputLogic(output, fixture.output.getData());
        Mockito.when(output.getLogic()).thenReturn(outputLogic);
        Pp2mpOutputTicker ticker = new Pp2mpOutputTicker(output);
        Pp2mpUnitManagerLogic managerLogic = new Pp2mpUnitManagerLogic(node, fixture.manager);
        List<Runnable> boundaries = List.of(
                outputLogic::canAcceptTask, managerLogic::canAcceptTask,
                () -> ticker.tickingRequest(Mockito.mock(IGridNode.class), 1),
                () -> managerLogic.tick(1)
        );

        for (Runnable boundary : boundaries) {
            fixture.inputs(fixture.input(false, OutputPortMode.SINGLE));
            Pp2mpInputPart replacement = fixture.input(true, OutputPortMode.SAME_TYPE);
            Mockito.when(fixture.grid.getMachines(Pp2mpInputPart.class)).thenReturn(Set.of(replacement));
            fixture.output.onTunnelNetworkChange();
            boundary.run();
            fixture.assertConfig(replacement.getData().getConfig());
        }
    }

    @Test
    void passiveNodeBurstsRebuildAndCalibrateOnlyOnceAtEndTick() {
        Fixture fixture = new Fixture();
        fixture.inputs(fixture.input(false, OutputPortMode.SINGLE));
        Pp2mpInputPart replacement = fixture.input(true, OutputPortMode.SAME_TYPE);
        Mockito.when(fixture.grid.getMachines(Pp2mpInputPart.class)).thenReturn(Set.of(replacement));
        Mockito.clearInvocations(fixture.grid);

        for (int notification = 0; notification < 64; notification++) {
            fixture.output.nodeStateChanged();
            fixture.output.onTunnelNetworkChange();
            fixture.transfer.nodeStateChanged();
            fixture.manager.nodeStateChanged();
        }
        Mockito.verify(fixture.grid, Mockito.never()).getMachines(Pp2mpInputPart.class);
        Assertions.assertEquals(OutputPortMode.SINGLE, fixture.transfer.getData().getOutputPortMode());

        fixture.service.onServerEndTick();
        fixture.service.onServerEndTick();
        fixture.assertConfig(replacement.getData().getConfig());
        Mockito.verify(fixture.grid).getMachines(Pp2mpInputPart.class);
    }

    @Test
    void taskLookupCalibratesPendingNodeChangesBeforeEndTick() {
        Fixture fixture = new Fixture();
        fixture.inputs(fixture.input(false, OutputPortMode.SINGLE));
        Pp2mpInputPart replacement = fixture.input(true, OutputPortMode.SAME_TYPE);
        Mockito.when(fixture.grid.getMachines(Pp2mpInputPart.class)).thenReturn(Set.of(replacement));
        fixture.transfer.nodeStateChanged();
        Mockito.clearInvocations(fixture.grid);

        fixture.service.getEndpoints((short) 1);
        fixture.assertConfig(replacement.getData().getConfig());
        fixture.service.onServerEndTick();
        Mockito.verify(fixture.grid).getMachines(Pp2mpInputPart.class);
    }

    @Test
    void firstBindingReplacementLateLoadingAndDisconnectRecoveryCalibrateLocalValues() {
        Fixture fixture = new Fixture();
        Pp2mpInputPart first = fixture.input(false, OutputPortMode.SINGLE);
        fixture.inputs(first);
        fixture.assertConfig(first.getData().getConfig());
        Pp2mpInputPart second = fixture.input(true, OutputPortMode.SAME_TYPE);
        fixture.inputs(second);
        fixture.assertConfig(second.getData().getConfig());
        first.getData().update(InputConfig.builder(InputConfig.DEFAULT).productExtraction(true).build());
        fixture.assertConfig(second.getData().getConfig());

        fixture.inputs();
        fixture.assertConfig(second.getData().getConfig());
        second.getData().update(InputConfig.builder(InputConfig.DEFAULT).blocking(false).build());
        Assertions.assertTrue(fixture.output.getData().getConfig().general().blocking());
        fixture.inputs(second);
        fixture.assertConfig(second.getData().getConfig());
    }

    @Test
    void reloadRebindingAndReenablingSyncOverwriteSavedLocalValues() {
        Fixture fixture = new Fixture();
        Pp2mpInputPart input = fixture.input(false, OutputPortMode.SINGLE);
        fixture.inputs(input);
        fixture.output.getData().setConfigSyncSwitch(false);
        fixture.manager.getData().setSyncEnabled(false);
        input.getData().update(InputConfig.DEFAULT);
        Assertions.assertFalse(fixture.output.getData().getConfig().general().blocking());
        fixture.output.getData().setConfigSyncSwitch(true);
        fixture.manager.getData().setSyncEnabled(true);
        fixture.output.refreshConfigRegister();
        fixture.manager.refreshConfigEventRegister();
        fixture.assertConfig(InputConfig.DEFAULT);

        CompoundTag saved = new CompoundTag();
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        fixture.manager.getData().writeToNBT(saved, registries);
        fixture.manager.clearConfigBinding();
        input.getData().update(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SAME_TYPE).build());
        fixture.manager.getData().readFromNBT(saved, registries);
        fixture.service.topologyChanged();
        // 即使其他查询已重建索引，也不能吞掉待处理的配置重绑。
        fixture.service.findInput((short) 1);
        fixture.service.getEndpoints((short) 1);
        fixture.assertConfig(input.getData().getConfig());
    }

    @Test
    void movingPortsDetachesOldManagerAndProviderRulesAreCopiedLocally() {
        Fixture fixture = new Fixture();
        fixture.inputs(fixture.input(false, OutputPortMode.SINGLE));
        Pp2mpUnitProviderPortPart provider = fixture.provider();
        Pp2mpResourceIntakePart intake = Mockito.mock(Pp2mpResourceIntakePart.class,
                Mockito.withSettings().mockMaker(MockMakers.INLINE));
        Pp2mpProviderFilterData source = new Pp2mpProviderFilterData(ItemRegistry.PP2MP_RESOURCE_INTAKE.get(), () -> { });
        source.markers().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 0));
        source.setLimit(0, 100);
        Mockito.when(intake.getFrequency()).thenReturn((short) 1);
        Mockito.when(intake.getProviderFilter()).thenReturn(source);
        Mockito.when(fixture.grid.getMachines(Pp2mpResourceIntakePart.class)).thenReturn(Set.of(intake));
        fixture.service.topologyChanged();
        fixture.service.refreshConfigurations();
        Assertions.assertSame(provider.getProviderFilter(), provider.getEffectiveProviderFilter());
        Assertions.assertEquals(source.snapshot(), provider.getProviderFilter().snapshot());
        Assertions.assertEquals(source.snapshot(), fixture.output.getProviderFilter().snapshot());
        provider.setProviderSync(false);
        source.setLimit(0, 200);
        fixture.service.resourceIntakeConfigChanged();
        fixture.service.refreshConfigurations();
        Assertions.assertEquals(100, provider.getProviderFilter().limit(0));
        provider.setProviderSync(true);
        Assertions.assertEquals(200, provider.getProviderFilter().limit(0));
        provider.applyPlacerAttr((short) 1, (short) 3);
        fixture.transfer.applyPlacerAttr((short) 1, (short) 3);
        fixture.service.refreshConfigurations();
        source.setLimit(0, 300);
        fixture.service.resourceIntakeConfigChanged();
        fixture.service.refreshConfigurations();
        Assertions.assertEquals(200, provider.getProviderFilter().limit(0));
        fixture.manager.getData().apply(InputConfig.DEFAULT);
        Assertions.assertEquals(OutputPortMode.SINGLE, fixture.transfer.getData().getOutputPortMode());
    }

    private static final class Fixture {
        private final IGrid grid = Mockito.mock(IGrid.class);
        private final Pp2mpGridService service = new Pp2mpGridService(this.grid);
        private final ServerLevel level = Mockito.mock(ServerLevel.class);
        private final TestOutput output = new TestOutput(this.grid);
        private final TestManager manager = new TestManager(this.grid);
        private final TestTransfer transfer;

        private Fixture() {
            Mockito.when(this.level.dimension()).thenReturn(Level.OVERWORLD);
            Mockito.when(this.grid.getService(Pp2mpGridService.class)).thenReturn(this.service);
            this.install(this.output, 0);
            this.install(this.manager, 1);
            Mockito.when(this.grid.getMachines(Pp2mpOutputPart.class)).thenReturn(Set.of(this.output));
            Mockito.when(this.grid.getMachines(Pp2mpUnitManagerPart.class)).thenReturn(Set.of(this.manager));
            this.manager.applyPlacerAttr((short) 1, (short) 2);
            this.transfer = new TestTransfer(this.grid);
            this.install(this.transfer, 2);
            Mockito.when(this.grid.getMachineClasses()).thenReturn(List.of(Pp2mpUnitTransferPortPart.class));
            IGridNode transferNode = Fixture.node(this.transfer);
            Mockito.when(this.grid.getMachineNodes(Pp2mpUnitTransferPortPart.class))
                    .thenReturn(List.of(transferNode));
            this.transfer.applyPlacerAttr((short) 1, (short) 2);
        }

        private Pp2mpUnitProviderPortPart provider() {
            Pp2mpUnitProviderPortPart real = new Pp2mpUnitProviderPortPart(ItemRegistry.PP2MP_UNIT_PORT_PROVIDER.get());
            this.install(real, 3);
            Pp2mpUnitProviderPortPart port = Mockito.mock(Pp2mpUnitProviderPortPart.class,
                    Mockito.withSettings().mockMaker(MockMakers.INLINE).spiedInstance(real)
                            .defaultAnswer(Mockito.CALLS_REAL_METHODS));
            Mockito.doReturn(this.grid).when(port).grid();
            Mockito.when(this.grid.getMachineClasses()).thenReturn(List.of(
                    Pp2mpUnitTransferPortPart.class, Pp2mpUnitProviderPortPart.class));
            IGridNode providerNode = Fixture.node(port);
            Mockito.when(this.grid.getMachineNodes(Pp2mpUnitProviderPortPart.class))
                    .thenReturn(List.of(providerNode));
            port.applyPlacerAttr((short) 1, (short) 2);
            return port;
        }

        private static IGridNode node(Pp2mpUnitPortPart port) {
            IGridNode node = Mockito.mock(IGridNode.class);
            Mockito.when(node.getOwner()).thenReturn(port);
            return node;
        }

        private void install(AEBasePart part, int x) {
            ChestBlockEntity entity = new ChestBlockEntity(new BlockPos(x, 0, 0), Blocks.CHEST.defaultBlockState());
            entity.setLevel(this.level);
            part.setPartHostInfo(Direction.NORTH, Mockito.mock(IPartHost.class), entity);
        }

        private Pp2mpInputPart input(boolean blocking, OutputPortMode mode) {
            Pp2mpInputPart part = Mockito.mock(Pp2mpInputPart.class);
            Pp2mpInputData data = new Pp2mpInputData(() -> { }, () -> { });
            data.update(InputConfig.builder(InputConfig.DEFAULT).blocking(blocking).outputPortMode(mode).build());
            Mockito.when(part.getData()).thenReturn(data);
            Mockito.when(part.getFrequency()).thenReturn((short) 1);
            return part;
        }

        private void inputs(Pp2mpInputPart... parts) {
            Mockito.when(this.grid.getMachines(Pp2mpInputPart.class)).thenReturn(Set.of(parts));
            this.service.topologyChanged();
            this.service.refreshConfigurations();
        }

        private void assertConfig(InputConfig config) {
            Assertions.assertEquals(new OutputConfig(config), this.output.getData().getConfig());
            Assertions.assertEquals(new UnitConfig(config), this.manager.getData().getConfig());
            Assertions.assertEquals(config.unit().outputPortMode(), this.transfer.getData().getOutputPortMode());
        }
    }

    private static final class TestOutput extends Pp2mpOutputPart {
        private final IGrid testGrid;

        private TestOutput(IGrid grid) {
            super(ItemRegistry.PP2MP_OUTPUT.get());
            this.testGrid = grid;
        }

        @Override
        public IGrid grid() {
            return this.testGrid;
        }

        @Override
        public short getFrequency() {
            return 1;
        }

        private void nodeStateChanged() {
            this.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        }
    }

    private static final class TestManager extends Pp2mpUnitManagerPart {
        private final IGrid testGrid;

        private TestManager(IGrid grid) {
            super(ItemRegistry.PP2MP_UNIT_MANAGERS.get(AEColor.TRANSPARENT).get());
            this.testGrid = grid;
        }

        @Override
        public IGrid grid() {
            return this.testGrid;
        }

        private void nodeStateChanged() {
            this.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        }
    }

    private static final class TestTransfer extends Pp2mpUnitTransferPortPart {
        private final IGrid testGrid;

        private TestTransfer(IGrid grid) {
            super(ItemRegistry.PP2MP_UNIT_PORT_TRANSFER.get());
            this.testGrid = grid;
        }

        @Override
        public IGrid grid() {
            return this.testGrid;
        }

        private void nodeStateChanged() {
            this.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        }
    }
}
