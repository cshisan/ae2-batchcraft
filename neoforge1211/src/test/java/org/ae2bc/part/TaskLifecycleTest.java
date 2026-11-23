package org.ae2bc.part;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.unit.ReturnMode;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.task.Pp2mpTaskData;
import org.ae2bc.part.task.Pp2mpTaskLogic;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.task.Pp2mpTaskEndpoint;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.part.input.Pp2mpInputLogic;
import org.ae2bc.part.input.Pp2mpInputPart;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import org.ae2bc.pattern.PatternTestSupport;
import org.ae2bc.pattern.MaterialOutputAttr;
import org.ae2bc.pattern.Pp2mpPatternDetails;
import org.ae2bc.pattern.Pp2mpPatternItem;
import org.ae2bc.pattern.Pp2mpTaskPlan;
import org.ae2bc.part.output.Pp2mpOutputData;
import org.ae2bc.part.output.Pp2mpOutputLogic;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortData;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.ae2bc.registry.BlockRegistry;
import org.ae2bc.registry.component.ComponentRegistry;
import org.ae2bc.storage.ProductExtractor;
import org.ae2bc.storage.PendingMaterial;
import org.ae2bc.storage.ReceiveInventory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class TaskLifecycleTest {
    private static final AEKey PRODUCT = AEItemKey.of(Items.DIAMOND);
    private static final AEKey BYPRODUCT = AEItemKey.of(Items.GOLD_INGOT);

    /* 原样板仅声明票据和显式副产物，机器适配使用独立标记生成内部真实配方。 */
    @Test
    void craftingMachineReceivesRealPlanWithoutAddingMarkerToPublicPatternOutputs() {
        Fixture fixture = new Fixture(false, false);
        ServerLevel level = fixture.prepareOutput();
        ICraftingMachine machine = Mockito.mock(ICraftingMachine.class);
        Mockito.when(level.getCapability(Mockito.eq(AECapabilities.CRAFTING_MACHINE),
                Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenReturn(machine);
        Mockito.when(machine.acceptsPlans()).thenReturn(true);
        AEItemKey material = AEItemKey.of(Items.STONE);
        ItemStack inputTicket = new ItemStack(Items.PAPER);
        inputTicket.set(DataComponents.CUSTOM_NAME, Component.literal("input"));
        ItemStack outputTicket = new ItemStack(Items.PAPER);
        outputTicket.set(DataComponents.CUSTOM_NAME, Component.literal("output"));
        AEItemKey inputKey = AEItemKey.of(inputTicket);
        AEItemKey outputKey = AEItemKey.of(outputTicket);
        FakeCraftingConverterBlockEntity converter = new FakeCraftingConverterBlockEntity(
                BlockPos.ZERO, BlockRegistry.FAKE_CRAFTING_CONVERTER.get().defaultBlockState());
        converter.setMarker(0, inputKey);
        converter.setMarker(9, outputKey);
        converter.setLevel(level);
        Mockito.when(fixture.grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(converter));
        ItemStack encoded = Pp2mpPatternItem.encodePattern(
                PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(material, 5), new GenericStack(inputKey, 1)),
                        List.of(new GenericStack(outputKey, 1), new GenericStack(BYPRODUCT, 2))),
                MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
        encoded.set(ComponentRegistry.REAL_OUTPUT_MARKER, new GenericStack(PRODUCT, 5));
        IPatternDetails pattern = new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
        KeyCounter[] inputs = {new KeyCounter(), new KeyCounter()};
        inputs[0].add(material, 5);
        inputs[1].add(inputKey, 1);
        UUID taskId = UUID.randomUUID();
        Assertions.assertTrue(converter.acceptTaskInputs(taskId, converter.planTask(pattern, inputs)));
        Mockito.when(machine.pushPattern(Mockito.any(IPatternDetails.class),
                Mockito.any(KeyCounter[].class), Mockito.eq(Direction.SOUTH))).thenAnswer(call -> {
                    IPatternDetails realPattern = call.getArgument(0, IPatternDetails.class);
                    Assertions.assertEquals(PRODUCT, realPattern.getPrimaryOutput().what());
                    Assertions.assertEquals(List.of(new GenericStack(PRODUCT, 5), new GenericStack(BYPRODUCT, 2)),
                            realPattern.getOutputs());
                    Assertions.assertEquals(Map.of(material, 5L),
                            Pp2mpTaskPlan.amounts(call.getArgument(1, KeyCounter[].class)));
                    return true;
                });
        Assertions.assertTrue(fixture.logic.tryAcceptTask(pattern, inputs, IActionSource.empty(), taskId));
        Mockito.verify(machine).pushPattern(Mockito.any(IPatternDetails.class),
                Mockito.any(KeyCounter[].class), Mockito.eq(Direction.SOUTH));
        Assertions.assertEquals(List.of(new GenericStack(outputKey, 1), new GenericStack(BYPRODUCT, 2)),
                pattern.getOutputs());
        Assertions.assertEquals(Set.of(PRODUCT, BYPRODUCT), fixture.logic.exactReturnKeys());
        fixture.insert(PRODUCT, 4);
        Assertions.assertFalse(fixture.stored.containsKey(outputKey));
        fixture.insert(PRODUCT, 1);
        Assertions.assertEquals(1, fixture.stored.get(outputKey));
        Assertions.assertFalse(fixture.data.isTaskActive());
    }

    @Test
    void outputNonblockingWaitsForPartialDeliveryAndKeepsSeparateTasks() {
        Fixture fixture = new Fixture(false, false);
        Pp2mpOutputData data = Assertions.assertInstanceOf(Pp2mpOutputData.class, fixture.data);
        Pp2mpOutputLogic logic = Assertions.assertInstanceOf(Pp2mpOutputLogic.class, fixture.logic);
        ServerLevel level = fixture.prepareOutput();
        MEStorage machine = Mockito.mock(MEStorage.class);
        AtomicLong delivered = new AtomicLong();
        Mockito.when(machine.insert(Mockito.any(AEKey.class), Mockito.anyLong(),
                Mockito.any(Actionable.class), Mockito.any(IActionSource.class))).thenAnswer(call -> {
            long requested = call.getArgument(1, Long.class);
            if (call.getArgument(2, Actionable.class) == Actionable.SIMULATE) {
                return requested;
            }
            long inserted = Math.min(requested, 1);
            delivered.addAndGet(inserted);
            return inserted;
        });
        Mockito.when(level.getCapability(Mockito.eq(AECapabilities.ME_STORAGE),
                Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenReturn(machine);
        AEKey material = AEItemKey.of(Items.STONE);
        IPatternDetails pattern = PatternTestSupport.processing(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(material, 2)), List.of(new GenericStack(PRODUCT, 1))));
        KeyCounter[] inputs = {new KeyCounter()};
        inputs[0].add(material, 2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Assertions.assertTrue(logic.tryAcceptTask(pattern, inputs, IActionSource.empty(), first));
        Assertions.assertFalse(logic.canAcceptTask());
        logic.drainPendingInputs();
        Assertions.assertFalse(logic.canAcceptTask());
        Assertions.assertEquals(1L, data.getPendingInputs().get(null).get(material));
        logic.drainPendingInputs();
        Assertions.assertTrue(logic.canAcceptTask());
        Assertions.assertTrue(logic.tryAcceptTask(pattern, inputs, IActionSource.empty(), second));
        Assertions.assertEquals(1, fixture.insert(PRODUCT, 1));
        Assertions.assertEquals(Set.of(second), data.getTasks().keySet());
        Assertions.assertFalse(logic.canAcceptTask());
        logic.drainPendingInputs();
        logic.drainPendingInputs();
        Assertions.assertEquals(4, delivered.get());
        Assertions.assertEquals(64, fixture.insert(PRODUCT, 64));
        Assertions.assertFalse(data.isTaskActive());
        Assertions.assertTrue(logic.canAcceptTask());
    }

    @Test
    void craftingMachineGetsEachCompletePlanWithoutMergingConcurrentTasks() {
        Fixture fixture = new Fixture(false, false);
        Pp2mpOutputData data = Assertions.assertInstanceOf(Pp2mpOutputData.class, fixture.data);
        ServerLevel level = fixture.prepareOutput();
        ICraftingMachine machine = Mockito.mock(ICraftingMachine.class);
        Mockito.when(level.getCapability(Mockito.eq(AECapabilities.CRAFTING_MACHINE),
                Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenReturn(machine);
        Mockito.when(machine.acceptsPlans()).thenReturn(true);
        Mockito.when(machine.pushPattern(Mockito.any(IPatternDetails.class),
                Mockito.any(KeyCounter[].class), Mockito.any(Direction.class))).thenReturn(true);
        AEKey material = AEItemKey.of(Items.STONE);
        IPatternDetails pattern = PatternTestSupport.processing(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(material, 5)), List.of(new GenericStack(PRODUCT, 5))));
        KeyCounter[] inputs = {new KeyCounter()};
        inputs[0].add(material, 5);
        for (int index = 0; index < 2; index++) {
            Assertions.assertTrue(fixture.logic.tryAcceptTask(pattern, inputs, IActionSource.empty(), UUID.randomUUID()));
        }
        Mockito.verify(machine, Mockito.times(2)).pushPattern(
                Mockito.same(pattern), Mockito.same(inputs), Mockito.eq(Direction.SOUTH));
        Assertions.assertEquals(2, data.getTasks().size());
        Assertions.assertEquals(5, fixture.insert(PRODUCT, 5));
        Assertions.assertEquals(1, data.getTasks().size());
        Assertions.assertEquals(Map.of(PRODUCT, 5L), TaskTestSupport.remaining(data));
    }

    @Test
    void unrelatedTasksMayFinishOutOfOrderWithoutLosingTheDelivery() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            fixture.data.begin(Map.of(PRODUCT, 9L), PRODUCT, first, null);
            fixture.data.begin(Map.of(BYPRODUCT, 3L), BYPRODUCT, second, null);
            Assertions.assertEquals(3, fixture.insert(BYPRODUCT, 3));
            Assertions.assertEquals(Set.of(first), fixture.data.getTasks().keySet());
            Assertions.assertTrue(fixture.logic.hasAcceptedTask(first));
            Assertions.assertTrue(fixture.logic.hasAcceptedTask(second));
            Assertions.assertEquals(64, fixture.insert(PRODUCT, 64));
            Assertions.assertEquals(64L, fixture.stored.get(PRODUCT));
            Assertions.assertFalse(fixture.data.isTaskActive());
        }
    }

    @Test
    void resettingMultipleTasksRecoversOnlyMaterialsStillOwnedByEndpoint() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager);
            fixture.data.begin(Map.of(PRODUCT, 9L), PRODUCT, UUID.randomUUID(), null);
            fixture.data.begin(Map.of(BYPRODUCT, 3L), BYPRODUCT, UUID.randomUUID(), null);
            if (fixture.data instanceof Pp2mpUnitManagerData data) {
                data.getPlannedSend().add(new PendingMaterial(
                        new GenericStack(PRODUCT, 2), MaterialOutputMode.NORMAL, 0));
            } else if (fixture.data instanceof Pp2mpOutputData data) {
                data.getPendingInputs().put(null, new LinkedHashMap<>(Map.of(PRODUCT, 2L)));
            }
            fixture.logic.resetTaskState();
            Assertions.assertTrue(fixture.data.isCancelRequested());
            Assertions.assertEquals(Map.of(PRODUCT, 2L), fixture.data.getInputRecovery());
            fixture.logic.drainTaskRecovery();
            Assertions.assertFalse(fixture.data.isTaskActive());
            Assertions.assertEquals(Map.of(PRODUCT, 2L), fixture.stored);
        }
    }

    /* 两类端点都接收完整交付：拆批后等待五个产物，整单交付等待一百个。 */
    @Test
    void bothEndpointsUseDeliveredPatternAmountsForBatchAndFullTasks() {
        for (boolean manager : new boolean[]{false, true}) {
            for (boolean batch : new boolean[]{false, true}) {
                Fixture fixture = new Fixture(manager);
                AEKey material = AEItemKey.of(Items.STONE);
                AEKey second = AEItemKey.of(Items.DIRT);
                ItemStack encoded = Pp2mpPatternItem.encodePattern(
                        PatternDetailsHelper.encodeProcessingPattern(
                                List.of(new GenericStack(material, 100), new GenericStack(second, 100)),
                                List.of(new GenericStack(PRODUCT, 100))), MaterialOutputAttr.EMPTY);
                encoded.set(ComponentRegistry.PATTERN_BATCH_COUNT, 20L);
                IPatternDetails pattern = new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
                KeyCounter[] inputs = {new KeyCounter(), new KeyCounter()};
                inputs[0].add(material, 100);
                inputs[1].add(second, 100);
                if (batch) {
                    Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(pattern, inputs, null);
                    pattern = Pp2mpTaskPlan.restore(plan.pattern(), null);
                    inputs = new Pp2mpTaskPlan.InputTemplate(pattern).offer(plan.inputs());
                }
                long amount = batch ? 5 : 100;
                ICraftingMachine machine = Mockito.mock(ICraftingMachine.class);
                if (manager) {
                    Pp2mpUnitOutputTypePortPart port = Mockito.mock(Pp2mpUnitOutputTypePortPart.class);
                    Pp2mpUnitOutputTypePortData portData = new Pp2mpUnitOutputTypePortData(() -> { }, () -> { });
                    portData.setSyncEnabled(false);
                    Mockito.when(port.getType()).thenReturn(UnitPortType.TRANSFER);
                    Mockito.when(port.getEndpointId()).thenReturn("transfer");
                    Mockito.when(port.getData()).thenReturn(portData);
                    Mockito.when(port.output(Mockito.any(Pp2mpUnitManagerPart.class),
                                    Mockito.any(GenericStack.class), Mockito.any(MaterialOutputMode.class),
                                    Mockito.any(Actionable.class)))
                            .thenAnswer(call -> call.getArgument(1, GenericStack.class).amount());
                    Pp2mpGridService service = fixture.grid.getService(Pp2mpGridService.class);
                    Mockito.when(service.getOutputPorts(Mockito.anyShort(), Mockito.anyShort(),
                            Mockito.eq(UnitPortType.TRANSFER))).thenReturn(List.of(port));
                } else {
                    ServerLevel level = fixture.prepareOutput();
                    Mockito.when(level.getCapability(Mockito.eq(AECapabilities.CRAFTING_MACHINE),
                            Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenReturn(machine);
                    Mockito.when(machine.acceptsPlans()).thenReturn(true);
                    Mockito.when(machine.pushPattern(Mockito.any(IPatternDetails.class),
                            Mockito.any(KeyCounter[].class), Mockito.any(Direction.class))).thenReturn(true);
                }
                Assertions.assertTrue(fixture.logic.tryAcceptTask(
                        pattern, inputs, IActionSource.empty(), UUID.randomUUID()));
                Assertions.assertEquals(Map.of(PRODUCT, amount), TaskTestSupport.remaining(fixture.data));
                if (fixture.data instanceof Pp2mpUnitManagerData data) {
                    Assertions.assertEquals(List.of(
                                    new GenericStack(material, amount), new GenericStack(second, amount)),
                            data.getPlannedSend().stream().map(entry -> entry.stack()).toList());
                    data.getPlannedSend().clear();
                } else {
                    Mockito.verify(machine).pushPattern(
                            Mockito.same(pattern), Mockito.same(inputs), Mockito.eq(Direction.SOUTH));
                    Assertions.assertEquals(Map.of(material, amount, second, amount),
                            Pp2mpTaskPlan.amounts(inputs));
                }
                Assertions.assertEquals(amount - 1, fixture.insert(PRODUCT, amount - 1));
                Assertions.assertTrue(fixture.data.isTaskActive());
                Assertions.assertEquals(1, fixture.insert(PRODUCT, 1));
                Assertions.assertFalse(fixture.data.isTaskActive());
            }
        }
    }

    @Test
    void ordinaryInputDispatchDoesNotConsultConflictingConverters() {
        Pp2mpInputPart part = Mockito.mock(Pp2mpInputPart.class);
        Pp2mpInputData data = new Pp2mpInputData(() -> { }, () -> { });
        IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
        IGrid grid = Mockito.mock(IGrid.class);
        Pp2mpGridService service = Mockito.mock(Pp2mpGridService.class);
        Pp2mpTaskEndpoint endpoint = Mockito.mock(Pp2mpTaskEndpoint.class);
        Mockito.when(part.getData()).thenReturn(data);
        Mockito.when(part.getHost()).thenReturn(Mockito.mock(IPartHost.class));
        Mockito.when(part.getFrequency()).thenReturn((short) 1);
        Mockito.when(node.isActive()).thenReturn(true);
        Mockito.when(node.getGrid()).thenReturn(grid);
        Mockito.when(grid.getService(Pp2mpGridService.class)).thenReturn(service);
        Mockito.when(service.getEndpoints((short) 1)).thenReturn(List.of(endpoint));
        Mockito.when(endpoint.tryAcceptTask(Mockito.any(IPatternDetails.class), Mockito.any(KeyCounter[].class),
                Mockito.any(IActionSource.class), Mockito.any(UUID.class)))
                .thenReturn(true);
        IPatternDetails pattern = PatternTestSupport.processing(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)), List.of(new GenericStack(PRODUCT, 1))));
        KeyCounter[] inputs = {new KeyCounter()};
        inputs[0].add(AEItemKey.of(Items.STONE), 1);
        Assertions.assertTrue(new Pp2mpInputLogic(part, node).pushPattern(pattern, inputs));
        Mockito.verify(service, Mockito.never()).findFakeCraftingConverter(Mockito.anyShort());
        Mockito.verify(grid, Mockito.never()).getMachines(FakeCraftingConverterBlockEntity.class);
    }

    @Test
    void strictReturnsRemainAllowedAfterOneProductReachesItsTarget() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager);
            fixture.data.begin(Map.of(PRODUCT, 6L, BYPRODUCT, 1L), null, UUID.randomUUID(), null);
            Assertions.assertEquals(6, fixture.insert(PRODUCT, 6));
            Assertions.assertEquals(64, fixture.insert(PRODUCT, 64));
            Assertions.assertTrue(fixture.data.isTaskActive());
            Assertions.assertFalse(TaskTestSupport.record(fixture.data).getProgress().remaining().containsKey(PRODUCT));
            Assertions.assertTrue(TaskTestSupport.record(fixture.data).getProgress().declared().containsKey(PRODUCT));
            Assertions.assertEquals(1, fixture.insert(BYPRODUCT, 1));
            Assertions.assertFalse(fixture.data.isTaskActive());
            Assertions.assertEquals(70, fixture.stored.get(PRODUCT));
            Assertions.assertEquals(0, fixture.insert(PRODUCT, 1));
        }
    }

    @Test
    void extractedOverflowKeepsItsTaskUntilTheRecoveryIsSettled() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager);
            UUID taskId = UUID.randomUUID();
            fixture.data.begin(Map.of(PRODUCT, 5L), null, taskId, null);
            fixture.actualLimit = 5;
            AtomicLong sourceAmount = new AtomicLong(8);
            MEStorage source = new MEStorage() {
                @Override
                public void getAvailableStacks(KeyCounter counter) {
                    counter.add(PRODUCT, sourceAmount.get());
                }

                @Override
                public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
                    long taken = Math.min(amount, sourceAmount.get());
                    if (mode == Actionable.MODULATE) {
                        sourceAmount.addAndGet(-taken);
                    }
                    return taken;
                }

                @Override
                public Component getDescription() {
                    return Component.empty();
                }
            };
            ProductExtractor.extract(Map.of(AEKeyType.items(), source), fixture.receiver, 8, 8,
                    IActionSource.empty(), fixture.logic::allowsReturned, fixture.logic::retainExtracted);
            Assertions.assertEquals(Map.of(PRODUCT, 3L), fixture.data.getExtractionRecovery());
            Assertions.assertTrue(fixture.data.isTaskActive());
            Assertions.assertTrue(fixture.logic.hasTaskState());
            Assertions.assertNull(fixture.data.getLastTaskId());
            fixture.actualLimit = Long.MAX_VALUE;
            fixture.logic.drainTaskRecovery();
            Assertions.assertFalse(fixture.data.isTaskActive());
            Assertions.assertEquals(taskId, fixture.data.getLastTaskId());
            Assertions.assertEquals(8, fixture.stored.get(PRODUCT));
            Assertions.assertTrue(fixture.data.getExtractionRecovery().isEmpty());
        }
    }

    @Test
    void missingConverterCannotBeMistakenForAnOrdinaryCompletedTask() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager);
            FakeCraftingConverterBlockEntity converter = new FakeCraftingConverterBlockEntity(
                    BlockPos.ZERO, BlockRegistry.FAKE_CRAFTING_CONVERTER.get().defaultBlockState());
            ItemStack input = new ItemStack(Items.PAPER);
            input.set(DataComponents.CUSTOM_NAME, Component.literal("input"));
            ItemStack output = new ItemStack(Items.PAPER);
            output.set(DataComponents.CUSTOM_NAME, Component.literal("output"));
            converter.setMarker(0, AEItemKey.of(input));
            converter.setMarker(9, AEItemKey.of(output));
            UUID taskId = UUID.randomUUID();
            Assertions.assertTrue(converter.acceptTaskInputs(taskId,
                    new FakeCraftingConverterBlockEntity.TicketPlan(new KeyCounter[0], Map.of(0, 1L), true)));
            fixture.data.begin(Map.of(PRODUCT, 1L), PRODUCT, taskId, converter.getConverterId());
            fixture.insert(PRODUCT, 1);
            if (!manager) {
                Assertions.assertEquals(1, fixture.wakes.get());
            }
            fixture.logic.retryTaskCompletion();
            if (!manager) {
                Assertions.assertEquals(1, fixture.wakes.get());
            }
            Assertions.assertTrue(fixture.data.isTaskActive());
            Assertions.assertNull(fixture.data.getLastTaskId());
            Mockito.when(fixture.grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(converter));
            fixture.logic.retryTaskCompletion();
            fixture.logic.retryTaskCompletion();
            Assertions.assertFalse(fixture.data.isTaskActive());
            Assertions.assertEquals(1, fixture.stored.get(AEItemKey.of(output)));
        }
    }

    @Test
    void exactExtractionDoesNotEnumerateTheMachineAndStillReturnsExcess() {
        Fixture fixture = new Fixture(false);
        fixture.data.begin(Map.of(PRODUCT, 6L), null, UUID.randomUUID(), null);
        AtomicInteger enumerations = new AtomicInteger();
        MEStorage source = new MEStorage() {
            private long remaining = 64;

            @Override
            public void getAvailableStacks(KeyCounter counter) {
                enumerations.incrementAndGet();
            }

            @Override
            public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
                long taken = Math.min(amount, this.remaining);
                if (mode == Actionable.MODULATE) {
                    this.remaining -= taken;
                }
                return taken;
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
        ProductExtractor.extract(Map.of(AEKeyType.items(), source), fixture.receiver, 64, 64,
                IActionSource.empty(), fixture.logic::allowsReturned, fixture.logic::retainExtracted,
                fixture.logic.exactReturnKeys());
        Assertions.assertEquals(0, enumerations.get());
        Assertions.assertEquals(64, fixture.stored.get(PRODUCT));
        Assertions.assertFalse(fixture.data.isTaskActive());
    }

    private static final class Fixture {
        private final IGrid grid = Mockito.mock(IGrid.class);
        private final Pp2mpTaskData data;
        private final Pp2mpTaskLogic logic;
        private final ReceiveInventory receiver;
        private final Map<AEKey, Long> stored = new HashMap<>();
        private final AtomicInteger wakes = new AtomicInteger();
        private long actualLimit = Long.MAX_VALUE;

        private Fixture(boolean manager) {
            this(manager, true);
        }

        private Fixture(boolean manager, boolean blocking) {
            IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
            Mockito.when(node.getGrid()).thenReturn(this.grid);
            Mockito.when(node.isActive()).thenReturn(true);
            Mockito.when(this.grid.getService(Pp2mpGridService.class)).thenReturn(Mockito.mock(Pp2mpGridService.class));
            InputConfig config = InputConfig.builder(InputConfig.DEFAULT)
                    .returnMode(ReturnMode.STRICT_FULL).blocking(blocking).build();
            if (manager) {
                Pp2mpUnitManagerPart part = Mockito.mock(Pp2mpUnitManagerPart.class);
                Pp2mpUnitManagerData data = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
                data.apply(config);
                Mockito.when(part.getData()).thenReturn(data);
                Mockito.when(part.getHost()).thenReturn(Mockito.mock(IPartHost.class));
                Mockito.when(part.validFrequency()).thenReturn(true);
                Mockito.when(part.getLevel()).thenReturn(Mockito.mock(ServerLevel.class));
                this.data = data;
                this.logic = new Pp2mpUnitManagerLogic(node, part);
                this.receiver = this.receiver();
                Mockito.when(part.getReceiveInventory()).thenReturn(this.receiver);
            } else {
                Pp2mpOutputPart part = Mockito.mock(Pp2mpOutputPart.class);
                Pp2mpOutputData data = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
                data.apply(config);
                Mockito.when(part.getData()).thenReturn(data);
                Mockito.when(part.getMainNode()).thenReturn(node);
                Mockito.when(part.getHost()).thenReturn(Mockito.mock(IPartHost.class));
                Mockito.when(part.validFrequency()).thenReturn(true);
                Mockito.when(part.getSide()).thenReturn(Direction.NORTH);
                Mockito.when(part.getLevel()).thenReturn(Mockito.mock(ServerLevel.class));
                this.data = data;
                this.logic = new Pp2mpOutputLogic(part, data);
                Mockito.doAnswer(call -> {
                    this.wakes.incrementAndGet();
                    return null;
                }).when(part).wakeProductExtractor();
                this.receiver = this.receiver();
                Mockito.when(part.getReceiveInventory()).thenReturn(this.receiver);
            }
        }

        private ReceiveInventory receiver() {
            MEStorage target = new MEStorage() {
                @Override
                public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    long inserted = mode == Actionable.SIMULATE ? amount
                            : Math.min(amount, Fixture.this.actualLimit);
                    if (mode == Actionable.MODULATE) {
                        Fixture.this.stored.merge(key, inserted, Math::addExact);
                    }
                    return inserted;
                }

                @Override
                public Component getDescription() {
                    return Component.empty();
                }
            };
            return new ReceiveInventory(() -> null,
                    () -> Map.of(AEKeyType.items(), (extractable, listener) -> target),
                    this.logic::allowsReturned, this.logic::onReturnedInserted,
                    IActionSource.empty(), () -> { }, this.logic::beginReturn, this.logic::endReturn);
        }

        private ServerLevel prepareOutput() {
            Pp2mpOutputLogic output = Assertions.assertInstanceOf(Pp2mpOutputLogic.class, this.logic);
            Pp2mpOutputPart part = output.getPart();
            ServerLevel level = Assertions.assertInstanceOf(ServerLevel.class, part.getLevel());
            ChestBlockEntity blockEntity = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            blockEntity.setLevel(level);
            IPartHost host = part.getHost();
            Mockito.doCallRealMethod().when(part).setPartHostInfo(Direction.NORTH, host, blockEntity);
            part.setPartHostInfo(Direction.NORTH, host, blockEntity);
            Mockito.when(level.isLoaded(Mockito.any(BlockPos.class))).thenReturn(true);
            return level;
        }

        private long insert(AEKey key, long amount) {
            return this.receiver.insert(key, amount, Actionable.MODULATE, IActionSource.empty());
        }
    }
}
