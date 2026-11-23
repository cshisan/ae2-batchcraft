package org.ae2bc.part.resource_intake;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.me.GridNode;
import appeng.me.ManagedGridNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.ae2bc.core.dispatch.ResourceTransferPlan;
import org.ae2bc.core.dispatch.WorkBudget;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.output.Pp2mpOutputLogic;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.registry.ItemRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public class Pp2mpResourceIntakeEnergyTest {
    @Test
    void activeIntakeKeepsOneTickCadenceAndSlowsDownOnlyWithoutProgress() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.part.setFrequency((short) 1);
        Mockito.when(fixture.node.isActive()).thenReturn(true);
        AEKey key = AEItemKey.of(Items.GRAVEL);
        GenericStackInv source = new GenericStackInv(Set.of(AEKeyType.items()), null, GenericStackInv.Mode.STORAGE, 1);
        source.setStack(0, new GenericStack(key, 64));
        BlockPos sourcePos = BlockPos.ZERO.relative(Direction.NORTH);
        Mockito.when(fixture.level.isLoaded(sourcePos)).thenReturn(true);
        Mockito.when(fixture.level.getCapability(AECapabilities.ME_STORAGE, sourcePos, Direction.SOUTH))
                .thenReturn(source);
        Pp2mpOutputPart output = Mockito.mock(Pp2mpOutputPart.class);
        Pp2mpOutputLogic logic = Mockito.mock(Pp2mpOutputLogic.class);
        Mockito.when(output.getLogic()).thenReturn(logic);
        Mockito.when(fixture.gridService.getOutputs((short) 1)).thenReturn(List.of(output));
        AtomicLong received = new AtomicLong();
        Mockito.when(logic.planProvider(Mockito.eq(key), Mockito.anyLong(), Mockito.any(WorkBudget.class)))
                .thenAnswer(call -> ResourceTransferPlan.leaf(call.getArgument(1), amount -> {
                    received.addAndGet(amount);
                    return amount;
                }));
        fixture.part.getData().setActivePull(true);
        fixture.part.getData().setExtractionInterval(1);
        fixture.part.getData().setExtractionQuantity(1);
        Pp2mpResourceIntakeTicker ticker = new Pp2mpResourceIntakeTicker(fixture.part);
        Assertions.assertEquals(1, ticker.getTickingRequest(fixture.node).minTickRate());
        Assertions.assertEquals(1, ticker.getTickingRequest(fixture.node).initialTickRate());
        for (int tick = 0; tick < 20; tick++) {
            Assertions.assertEquals(TickRateModulation.URGENT, ticker.tickingRequest(fixture.node, 1));
        }
        Assertions.assertEquals(20, received.get());
        Assertions.assertEquals(44, source.getAmount(0));
        Mockito.when(logic.planProvider(Mockito.eq(key), Mockito.anyLong(), Mockito.any(WorkBudget.class)))
                .thenReturn(ResourceTransferPlan.empty());
        Assertions.assertEquals(TickRateModulation.SLOWER, ticker.tickingRequest(fixture.node, 1));
        Assertions.assertEquals(44, source.getAmount(0));
    }

    @Test
    void nativeExtractionKeepsKeysUntilTheScanCompletesOrTheSourceChanges() throws Exception {
        Fixture fixture = new Fixture(0);
        ItemStackHandler source = new ItemStackHandler(128);
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack item = new ItemStack(Items.PAPER, 64);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("source " + slot));
            source.setStackInSlot(slot, item);
        }
        AtomicLong received = fixture.enablePullFrom(source);
        Pp2mpResourceIntakeLogic logic = fixture.part.getLogic();
        Assertions.assertTrue(logic.tick(1));
        Object candidates = Pp2mpResourceIntakeEnergyTest.candidates(logic);
        for (int tick = 0; tick < 10; tick++) {
            Assertions.assertTrue(logic.tick(1));
            Assertions.assertSame(candidates, Pp2mpResourceIntakeEnergyTest.candidates(logic));
        }
        Assertions.assertEquals(11, received.get());
        logic.onSourceChanged();
        Assertions.assertTrue(logic.tick(1));
        Assertions.assertNotSame(candidates, Pp2mpResourceIntakeEnergyTest.candidates(logic));
        Assertions.assertEquals(12, received.get());
    }

    @Test
    void whitelistKeysSurviveOwnTransfersAndExpiryButFollowFilterChanges() throws Exception {
        Fixture fixture = new Fixture(0);
        ItemStackHandler source = new ItemStackHandler(2);
        source.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        source.setStackInSlot(1, new ItemStack(Items.DIRT, 64));
        AtomicLong received = fixture.enablePullFrom(source);
        fixture.part.getProviderFilter().markers().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 0));
        Pp2mpResourceIntakeLogic logic = fixture.part.getLogic();
        Assertions.assertTrue(logic.tick(1));
        Object candidates = Pp2mpResourceIntakeEnergyTest.candidates(logic);
        for (int tick = 0; tick < 10; tick++) {
            Assertions.assertTrue(logic.tick(1));
        }
        Assertions.assertTrue(logic.tick(200));
        Assertions.assertSame(candidates, Pp2mpResourceIntakeEnergyTest.candidates(logic));
        fixture.part.getProviderFilter().markers().setStack(0, new GenericStack(AEItemKey.of(Items.DIRT), 0));
        Assertions.assertTrue(logic.tick(1));
        Assertions.assertNotSame(candidates, Pp2mpResourceIntakeEnergyTest.candidates(logic));
        Assertions.assertEquals(52, source.getStackInSlot(0).getCount());
        Assertions.assertEquals(63, source.getStackInSlot(1).getCount());
        Assertions.assertEquals(13, received.get());
    }

    @Test
    void capabilityInvalidationDuringTransferStillReplacesTheOldSource() throws Exception {
        Fixture fixture = new Fixture(0);
        List<ICapabilityInvalidationListener> listeners = new ArrayList<>();
        Mockito.doAnswer(call -> {
            listeners.add(call.getArgument(1, ICapabilityInvalidationListener.class));
            return null;
        }).when(fixture.level).registerCapabilityListener(
                Mockito.any(BlockPos.class), Mockito.any(ICapabilityInvalidationListener.class));
        ItemStackHandler replacement = new ItemStackHandler(1);
        replacement.setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        ItemStackHandler source = new ItemStackHandler(1) {
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack extracted = super.extractItem(slot, amount, simulate);
                if (!simulate && !extracted.isEmpty()) {
                    Mockito.when(fixture.level.getCapability(Capabilities.ItemHandler.BLOCK,
                            BlockPos.ZERO.relative(Direction.NORTH), Direction.SOUTH)).thenReturn(replacement);
                    for (ICapabilityInvalidationListener listener : List.copyOf(listeners)) {
                        listener.onInvalidate();
                    }
                }
                return extracted;
            }
        };
        source.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        AtomicLong received = fixture.enablePullFrom(source);
        Pp2mpResourceIntakeLogic logic = fixture.part.getLogic();
        Assertions.assertTrue(logic.tick(1));
        Assertions.assertTrue(logic.tick(1));
        Assertions.assertEquals(2, received.get());
        Assertions.assertEquals(63, source.getStackInSlot(0).getCount());
        Assertions.assertEquals(63, replacement.getStackInSlot(0).getCount());
    }

    private static Object candidates(Pp2mpResourceIntakeLogic logic) throws ReflectiveOperationException {
        Field field = Pp2mpResourceIntakeLogic.class.getDeclaredField("pullCandidates");
        field.setAccessible(true);
        return field.get(logic);
    }

    private static final AEKeyType FLUX_TYPE = Mockito.mock(AEKeyType.class,
            Mockito.withSettings().useConstructor(
                            ResourceLocation.fromNamespaceAndPath("appflux", "flux"),
                            TestKey.class, Component.literal("FE"))
                    .defaultAnswer(Mockito.CALLS_REAL_METHODS));
    private static final AEKey FE = Pp2mpResourceIntakeEnergyTest.key("fe");
    private static final AEKey EU = Pp2mpResourceIntakeEnergyTest.key("gteu");

    /* 测试只替换节点的外部网络，实际部件、配置、库存与调度对象均保留。 */
    private static void setNode(Pp2mpResourceIntakePart part, @Nullable GridNode node) throws Exception {
        Field nodeField = ManagedGridNode.class.getDeclaredField("node");
        nodeField.setAccessible(true);
        nodeField.set(part.getMainNode(), node);
    }

    private static AEKey key(String name) {
        TestKey key = Mockito.mock(TestKey.class, Mockito.CALLS_REAL_METHODS);
        Mockito.when(key.getType()).thenReturn(Pp2mpResourceIntakeEnergyTest.FLUX_TYPE);
        Mockito.when(key.getId()).thenReturn(ResourceLocation.fromNamespaceAndPath("appflux", name));
        return key;
    }

    /* 经过实际资源接收库存验证：断电、没有频率、没有输出端时仍能给网络充电。 */
    @Test
    void resourceInsertionPowersAnInactiveNetworkWithoutFrequencyOrDestinations() throws Exception {
        Fixture fixture = new Fixture(80);
        Assertions.assertFalse(fixture.part.getMainNode().isActive());
        Assertions.assertEquals(0, fixture.part.getFrequency());
        Assertions.assertTrue(fixture.storage.isSupportedType(Pp2mpResourceIntakeEnergyTest.FLUX_TYPE));
        Assertions.assertTrue(fixture.storage.isAllowedIn(0, Pp2mpResourceIntakeEnergyTest.FE));

        Assertions.assertEquals(80, fixture.insert(100, Actionable.SIMULATE));
        Assertions.assertEquals(0, fixture.storedFe, 0.000001);
        Assertions.assertEquals(80, fixture.insert(100, Actionable.MODULATE));
        Assertions.assertEquals(80, fixture.storedFe, 0.000001);
        Assertions.assertEquals(0, fixture.insert(100, Actionable.MODULATE));
        Assertions.assertTrue(fixture.part.getData().getBuffer().isEmpty());
    }

    @Test
    void genericInventoryInsertionAndMeStorageUseTheSameEnergyAccounting() throws Exception {
        Fixture fixture = new Fixture(100);
        Assertions.assertEquals(40, fixture.storage.insert(0, Pp2mpResourceIntakeEnergyTest.FE, 40, Actionable.SIMULATE));
        Assertions.assertEquals(0, fixture.storedFe, 0.000001);
        Assertions.assertEquals(40, fixture.storage.insert(0, Pp2mpResourceIntakeEnergyTest.FE, 40, Actionable.MODULATE));
        Assertions.assertEquals(60, fixture.insert(100, Actionable.MODULATE));
        Assertions.assertEquals(100, fixture.storedFe, 0.000001);
        Assertions.assertEquals(0, fixture.storage.extract(0, Pp2mpResourceIntakeEnergyTest.FE, 100, Actionable.MODULATE));
    }

    /* 三种分配模式均先供电；输出端和管理器只能共享剩余能量。 */
    @Test
    void networkPowerPrecedesEveryDistributionModeWithoutDuplicatingForwardedEnergy() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            Fixture fixture = new Fixture(30);
            fixture.part.setFrequency((short) 1);
            fixture.part.getData().setDistributionMode(mode);
            Mockito.when(fixture.node.isActive()).thenReturn(true);
            Pp2mpOutputPart output = Mockito.mock(Pp2mpOutputPart.class);
            Pp2mpOutputLogic outputLogic = Mockito.mock(Pp2mpOutputLogic.class);
            Pp2mpUnitManagerPart manager = Mockito.mock(Pp2mpUnitManagerPart.class);
            Pp2mpUnitManagerLogic managerLogic = Mockito.mock(Pp2mpUnitManagerLogic.class);
            AtomicLong outputAmount = new AtomicLong();
            AtomicLong managerAmount = new AtomicLong();
            Mockito.when(output.getLogic()).thenReturn(outputLogic);
            Mockito.when(manager.getLogic()).thenReturn(managerLogic);
            Mockito.when(fixture.gridService.getOutputs((short) 1)).thenReturn(List.of(output));
            Mockito.when(fixture.gridService.getManagers((short) 1)).thenReturn(List.of(manager));
            Mockito.when(outputLogic.planProvider(Mockito.same(Pp2mpResourceIntakeEnergyTest.FE),
                            Mockito.anyLong(), Mockito.any(WorkBudget.class)))
                    .thenAnswer(invocation -> Pp2mpResourceIntakeEnergyTest.plan(
                            invocation.getArgument(1), invocation.getArgument(2), 40, outputAmount));
            Mockito.when(managerLogic.planProvider(Mockito.same(Pp2mpResourceIntakeEnergyTest.FE),
                            Mockito.anyLong(), Mockito.any(WorkBudget.class), Mockito.anyBoolean()))
                    .thenAnswer(invocation -> Pp2mpResourceIntakeEnergyTest.plan(
                            invocation.getArgument(1), invocation.getArgument(2), 60, managerAmount));

            long simulated = fixture.insert(100, Actionable.SIMULATE);
            Assertions.assertTrue(simulated > 0 && simulated <= 100);
            Assertions.assertEquals(0, fixture.storedFe, 0.000001);
            Assertions.assertEquals(0, outputAmount.get() + managerAmount.get());
            Assertions.assertEquals(100, fixture.insert(100, Actionable.MODULATE));
            Assertions.assertEquals(30, fixture.storedFe, 0.000001);
            Assertions.assertEquals(70, outputAmount.get() + managerAmount.get());
            Assertions.assertTrue(fixture.part.getData().getBuffer().isEmpty());
        }
    }

    /* 通过实际策略发现和 tick 证明断电时主动提取可启动，且只缓存插入失败余量。 */
    @SuppressWarnings("UnstableApiUsage")
    @Test
    void activeExtractionCanPowerAnInactiveGridAndRecoverOnlyTheActualRemainder() throws Exception {
        Fixture fixture = new Fixture(30);
        fixture.actualCapacityFe = 10;
        GenericStackInv source = new GenericStackInv(
                Set.of(Pp2mpResourceIntakeEnergyTest.FLUX_TYPE), null, GenericStackInv.Mode.STORAGE, 1);
        source.setStack(0, new GenericStack(Pp2mpResourceIntakeEnergyTest.FE, 100));
        ExternalStorageStrategy.register(Pp2mpResourceIntakeEnergyTest.FLUX_TYPE,
                (level, pos, side) -> (extractableOnly, callback) -> level == fixture.level ? source : null);
        GenericStackInv pending = new GenericStackInv(
                Set.of(Pp2mpResourceIntakeEnergyTest.FLUX_TYPE), null, GenericStackInv.Mode.STORAGE, 1);
        Pp2mpResourceIntakeLogic logic = new Pp2mpResourceIntakeLogic(fixture.part, pending);
        fixture.part.getData().setActivePull(true);
        fixture.part.getData().setExtractionQuantity(100);

        logic.tick(1);
        Assertions.assertEquals(70, source.getAmount(0));
        Assertions.assertEquals(10, fixture.storedFe, 0.000001);
        Assertions.assertEquals(20, pending.getAmount(0));
        fixture.actualCapacityFe = 30;
        logic.tick(20);
        Assertions.assertEquals(70, source.getAmount(0));
        Assertions.assertEquals(30, fixture.storedFe, 0.000001);
        Assertions.assertTrue(pending.isEmpty());
    }

    @Test
    void filtersStillRejectEnergyAndNonEnergyResourcesDoNotPowerTheNetwork() throws Exception {
        Fixture fixture = new Fixture(100);
        AEItemKey stone = AEItemKey.of(Items.STONE);
        fixture.part.getProviderFilter().markers().setStack(0, new GenericStack(stone, 0));
        Assertions.assertEquals(0, fixture.insert(100, Actionable.SIMULATE));
        Assertions.assertEquals(0, fixture.insert(100, Actionable.MODULATE));
        Assertions.assertEquals(0, fixture.storage.insert(stone, 100, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(0, fixture.storage.insert(
                Pp2mpResourceIntakeEnergyTest.EU, 100, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(0, fixture.storedFe, 0.000001);
    }

    @Test
    void disconnectedNodeRejectsEnergyAndReconnectUsesTheCurrentGrid() throws Exception {
        Fixture fixture = new Fixture(100);
        Pp2mpResourceIntakeEnergyTest.setNode(fixture.part, null);
        Assertions.assertFalse(fixture.storage.isSupportedType(Pp2mpResourceIntakeEnergyTest.FLUX_TYPE));
        Assertions.assertEquals(0, fixture.insert(100, Actionable.MODULATE));
        Pp2mpResourceIntakeEnergyTest.setNode(fixture.part, fixture.node);
        Assertions.assertEquals(100, fixture.insert(100, Actionable.MODULATE));
    }

    @Test
    void actualCapacityChangesDoNotChargeOrForwardTheSameEnergyTwice() throws Exception {
        Fixture fixture = new Fixture(100);
        fixture.actualCapacityFe = 35;
        Assertions.assertEquals(100, fixture.insert(100, Actionable.SIMULATE));
        Assertions.assertEquals(35, fixture.insert(100, Actionable.MODULATE));
        Assertions.assertEquals(35, fixture.storedFe, 0.000001);
        Assertions.assertEquals(0, fixture.insert(65, Actionable.MODULATE));
    }

    @Test
    void fractionalCapacityCannotProduceFreeEnergyOnRepeatedCalls() throws Exception {
        Fixture fixture = new Fixture(0.5);
        for (int attempt = 0; attempt < 10; attempt++) {
            Assertions.assertEquals(0, fixture.insert(1, Actionable.MODULATE));
        }
        Assertions.assertEquals(0, fixture.storedFe, 0.000001);
        fixture.capacityFe = 2;
        fixture.actualCapacityFe = 0.5;
        Assertions.assertEquals(1, fixture.insert(2, Actionable.MODULATE));
        Assertions.assertEquals(0.5, fixture.storedFe, 0.000001);
    }

    @Test
    void conversionUsesConfiguredAe2RatioAndHandlesLargeRequests() throws Exception {
        double originalFeRatio = PowerUnit.FE.conversionRatio;
        double originalAeRatio = PowerUnit.AE.conversionRatio;
        try {
            PowerUnit.FE.conversionRatio = 0.25;
            PowerUnit.AE.conversionRatio = 1;
            Fixture fixture = new Fixture(4096);
            Assertions.assertEquals(4096, fixture.insert(Long.MAX_VALUE, Actionable.SIMULATE));
            Assertions.assertEquals(4096, fixture.insert(Long.MAX_VALUE, Actionable.MODULATE));
            Assertions.assertEquals(4096, fixture.storedFe, 0.000001);
            Assertions.assertEquals(0, fixture.insert(0, Actionable.MODULATE));
            Assertions.assertEquals(0, fixture.insert(-1, Actionable.MODULATE));
        } finally {
            PowerUnit.FE.conversionRatio = originalFeRatio;
            PowerUnit.AE.conversionRatio = originalAeRatio;
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    @Test
    void rejectedCandidatesDoNotStarveResourcesAfterTheFirstSixtyFourEntries() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.part.setFrequency((short) 1);
        Mockito.when(fixture.node.isActive()).thenReturn(true);
        GenericStackInv source = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, 130);
        for (int index = 0; index < 130; index++) {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(Integer.toString(index)));
            source.setStack(index, new GenericStack(AEItemKey.of(stack), 1));
        }
        List<AEKey> keys = new ArrayList<>();
        for (Map.Entry<AEKey, Long> entry : source.getAvailableStacks()) {
            keys.add(entry.getKey());
        }
        AEKey acceptedKey = keys.getLast();
        BlockPos sourcePos = BlockPos.ZERO.relative(Direction.NORTH);
        Mockito.when(fixture.level.isLoaded(sourcePos)).thenReturn(true);
        Mockito.when(fixture.level.getCapability(AECapabilities.ME_STORAGE, sourcePos, Direction.SOUTH))
                .thenReturn(source);
        Pp2mpOutputPart output = Mockito.mock(Pp2mpOutputPart.class);
        Pp2mpOutputLogic outputLogic = Mockito.mock(Pp2mpOutputLogic.class);
        Mockito.when(output.getLogic()).thenReturn(outputLogic);
        Mockito.when(fixture.gridService.getOutputs((short) 1)).thenReturn(List.of(output));
        AtomicLong received = new AtomicLong();
        AtomicInteger visited = new AtomicInteger();
        Mockito.when(outputLogic.planProvider(Mockito.any(AEKey.class), Mockito.anyLong(),
                Mockito.any(WorkBudget.class))).thenAnswer(invocation -> {
            WorkBudget budget = invocation.getArgument(2);
            Assertions.assertTrue(budget.tryUse());
            visited.incrementAndGet();
            return acceptedKey.equals(invocation.getArgument(0))
                    ? ResourceTransferPlan.leaf(invocation.getArgument(1), received::addAndGet)
                    : ResourceTransferPlan.empty();
        });
        fixture.part.getData().setActivePull(true);
        fixture.part.getData().setExtractionInterval(1);
        fixture.part.getData().setExtractionQuantity(100);
        fixture.part.getLogic().tick(1);
        Assertions.assertEquals(64, visited.get());
        fixture.part.getLogic().tick(1);
        Assertions.assertEquals(128, visited.get());
        Assertions.assertEquals(0, received.get());
        Assertions.assertEquals(130, source.getAvailableStacks().size());
        fixture.part.getLogic().tick(1);
        Assertions.assertEquals(1, received.get());
        Assertions.assertEquals(0, source.getAvailableStacks().get(acceptedKey));
        Assertions.assertTrue(fixture.part.getData().getBuffer().isEmpty());
    }

    @Test
    void equalDistributionVisitsAreBoundedAndContinueAfterTheBudgetBoundary() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.part.setFrequency((short) 1);
        fixture.part.getData().setDistributionMode(2);
        Mockito.when(fixture.node.isActive()).thenReturn(true);
        List<Pp2mpOutputPart> outputs = new ArrayList<>();
        List<Integer> visits = new ArrayList<>();
        AtomicLong received = new AtomicLong();
        for (int index = 0; index < 300; index++) {
            int targetIndex = index;
            Pp2mpOutputPart output = Mockito.mock(Pp2mpOutputPart.class);
            Pp2mpOutputLogic logic = Mockito.mock(Pp2mpOutputLogic.class);
            Mockito.when(output.getLogic()).thenReturn(logic);
            Mockito.when(logic.planProvider(Mockito.any(AEKey.class), Mockito.anyLong(), Mockito.any(WorkBudget.class)))
                    .thenAnswer(invocation -> {
                        WorkBudget budget = invocation.getArgument(2);
                        Assertions.assertTrue(budget.tryUse());
                        visits.add(targetIndex);
                        return ResourceTransferPlan.leaf(1, supplied -> {
                            received.addAndGet(supplied);
                            return supplied;
                        });
                    });
            outputs.add(output);
        }
        Mockito.when(fixture.gridService.getOutputs((short) 1)).thenReturn(outputs);
        Assertions.assertEquals(1, fixture.insert(1, Actionable.SIMULATE));
        Assertions.assertEquals(255, visits.size());
        Assertions.assertEquals(0, received.get());
        visits.clear();
        Assertions.assertEquals(1, fixture.insert(1, Actionable.MODULATE));
        Assertions.assertEquals(0, visits.getFirst());
        Assertions.assertEquals(255, visits.size());
        visits.clear();
        Assertions.assertEquals(1, fixture.insert(1, Actionable.MODULATE));
        Assertions.assertEquals(255, visits.getFirst());
        Assertions.assertEquals(255, visits.size());
        Assertions.assertEquals(2, received.get());
        Assertions.assertTrue(fixture.part.getData().getBuffer().isEmpty());
    }

    public abstract static class TestKey extends AEKey {
    }

    private static ResourceTransferPlan plan(long requested, WorkBudget budget, long capacity, AtomicLong stored) {
        if (!budget.tryUse()) {
            return ResourceTransferPlan.empty();
        }
        return ResourceTransferPlan.leaf(Math.min(requested, capacity - stored.get()), supplied -> {
            long accepted = Math.min(supplied, capacity - stored.get());
            stored.addAndGet(accepted);
            return accepted;
        });
    }

    private static final class Fixture {
        private final IGrid grid = Mockito.mock(IGrid.class);
        private final IEnergyService energy = Mockito.mock(IEnergyService.class);
        private final GridNode node = Mockito.mock(GridNode.class);
        private final ServerLevel level = Mockito.mock(ServerLevel.class);
        private final Pp2mpGridService gridService = Mockito.mock(Pp2mpGridService.class);
        private final Pp2mpResourceIntakePart part = new Pp2mpResourceIntakePart(ItemRegistry.PP2MP_RESOURCE_INTAKE.get());
        private final Pp2mpResourceIntakeStorage storage = this.part.getLogic().getResourceIntakeStorage();
        private double capacityFe;
        private double actualCapacityFe;
        private double storedFe;

        private Fixture(double capacityFe) throws Exception {
            this.capacityFe = capacityFe;
            this.actualCapacityFe = capacityFe;
            ChestBlockEntity host = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            host.setLevel(this.level);
            this.part.setPartHostInfo(Direction.NORTH, Mockito.mock(IPartHost.class), host);
            Mockito.when(this.grid.getEnergyService()).thenReturn(this.energy);
            Mockito.when(this.grid.getTickManager()).thenReturn(Mockito.mock(ITickManager.class));
            Mockito.when(this.grid.getService(Pp2mpGridService.class)).thenReturn(this.gridService);
            Mockito.when(this.node.getGrid()).thenReturn(this.grid);
            Pp2mpResourceIntakeEnergyTest.setNode(this.part, this.node);
            Mockito.when(this.energy.injectPower(Mockito.anyDouble(), Mockito.any(Actionable.class)))
                    .thenAnswer(invocation -> {
                        double offered = invocation.getArgument(0);
                        Actionable mode = invocation.getArgument(1);
                        double offeredFe = PowerUnit.AE.convertTo(PowerUnit.FE, offered);
                        double capacity = mode == Actionable.SIMULATE ? this.capacityFe : this.actualCapacityFe;
                        double acceptedFe = Math.min(offeredFe, Math.max(0, capacity - this.storedFe));
                        if (mode == Actionable.MODULATE) {
                            this.storedFe += acceptedFe;
                        }
                        return PowerUnit.FE.convertTo(PowerUnit.AE, offeredFe - acceptedFe);
                    });
        }

        private long insert(long amount, Actionable mode) {
            return this.storage.insert(Pp2mpResourceIntakeEnergyTest.FE, amount, mode, IActionSource.empty());
        }

        private AtomicLong enablePullFrom(ItemStackHandler handler) {
            this.part.setFrequency((short) 1);
            Mockito.when(this.node.isActive()).thenReturn(true);
            BlockPos pos = BlockPos.ZERO.relative(Direction.NORTH);
            Mockito.when(this.level.isLoaded(pos)).thenReturn(true);
            Mockito.when(this.level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.SOUTH))
                    .thenReturn(handler);
            Pp2mpOutputPart output = Mockito.mock(Pp2mpOutputPart.class);
            Pp2mpOutputLogic outputLogic = Mockito.mock(Pp2mpOutputLogic.class);
            Mockito.when(output.getLogic()).thenReturn(outputLogic);
            Mockito.when(this.gridService.getOutputs((short) 1)).thenReturn(List.of(output));
            AtomicLong received = new AtomicLong();
            Mockito.when(outputLogic.planProvider(Mockito.any(AEKey.class), Mockito.anyLong(),
                    Mockito.any(WorkBudget.class))).thenAnswer(call ->
                    ResourceTransferPlan.leaf(call.getArgument(1), amount -> {
                        received.addAndGet(amount);
                        return amount;
                    }));
            this.part.getData().setActivePull(true);
            this.part.getData().setExtractionInterval(1);
            this.part.getData().setExtractionQuantity(1);
            return received;
        }
    }
}
