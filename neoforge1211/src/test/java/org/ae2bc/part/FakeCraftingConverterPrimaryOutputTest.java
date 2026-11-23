package org.ae2bc.part;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.*;
import appeng.api.util.AEColor;
import appeng.helpers.externalstorage.GenericStackInv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.unit.ReturnMode;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.output.Pp2mpOutputLogic;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.task.Pp2mpTaskData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.registry.BlockRegistry;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.registry.component.ComponentRegistry;
import org.ae2bc.pattern.MaterialOutputAttr;
import org.ae2bc.pattern.Pp2mpPatternDetails;
import org.ae2bc.pattern.Pp2mpPatternItem;
import org.ae2bc.pattern.Pp2mpTaskBatch;
import org.ae2bc.storage.PendingMaterial;
import org.ae2bc.storage.ReceiveInventory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class FakeCraftingConverterPrimaryOutputTest {
    /* 独立标记不在 AE2 输出列表中，严格提取仍允许返回，重载后仅结算剩余数量。 */
    @Test
    void independentMarkerSurvivesPartialReturnReloadAndStrictFilteringOnBothEndpoints() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager);
            InputConfig config = InputConfig.builder(InputConfig.DEFAULT).returnMode(ReturnMode.STRICT_FULL).build();
            if (manager) {
                fixture.manager.getData().apply(config);
                Assertions.assertEquals(Set.of(fixture.primary, fixture.byproduct),
                        fixture.managerLogic.exactReturnKeys());
            } else {
                fixture.output.getData().apply(config);
                Assertions.assertEquals(Set.of(fixture.primary, fixture.byproduct),
                        fixture.outputLogic.exactReturnKeys());
            }
            Assertions.assertEquals(0, fixture.receiver.insert(
                    AEItemKey.of(Items.STONE), 1, Actionable.MODULATE, IActionSource.empty()));
            Assertions.assertEquals(2, fixture.receiver.insert(
                    fixture.primary, 2, Actionable.MODULATE, IActionSource.empty()));
            HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            CompoundTag saved = new CompoundTag();
            if (manager) {
                fixture.manager.getData().writeTaskState(saved, registries);
                fixture.manager.getData().readTaskState(saved, registries);
            } else {
                fixture.output.getData().writeToNBT(saved, registries);
                fixture.output.getData().readFromNBT(saved, registries);
            }
            Assertions.assertEquals(4L, fixture.pending().get(fixture.primary));
            fixture.receiver.insert(fixture.primary, 3, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(0, fixture.count(fixture.outputTicket));
            fixture.receiver.insert(fixture.primary, 1, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(1, fixture.count(fixture.outputTicket));
            Assertions.assertEquals(6, fixture.count(fixture.primary));
            Assertions.assertFalse(fixture.active());
        }
    }

    @Test
    void concurrentFakeTasksSettleAtNineAndEighteenWithoutDoubleCounting() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager, 9, 2);
            UUID second = UUID.randomUUID();
            Assertions.assertTrue(fixture.converter.acceptTaskInputs(second,
                    new FakeCraftingConverterBlockEntity.TicketPlan(new KeyCounter[0], Map.of(0, 2L), true)));
            Pp2mpTaskData data = manager ? fixture.manager.getData() : fixture.output.getData();
            data.begin(Map.of(fixture.primary, 9L), fixture.primary, second, fixture.converter.getConverterId());
            fixture.receiver.insert(fixture.primary, 8, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(0, fixture.count(fixture.outputTicket));
            fixture.receiver.insert(fixture.primary, 1, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(2, fixture.count(fixture.outputTicket));
            Assertions.assertFalse(data.getTasks().containsKey(fixture.taskId));
            fixture.receiver.insert(fixture.primary, 8, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(2, fixture.count(fixture.outputTicket));
            fixture.receiver.insert(fixture.primary, 64, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(4, fixture.count(fixture.outputTicket));
            Assertions.assertEquals(81, fixture.count(fixture.primary));
            Assertions.assertFalse(fixture.active());
            Assertions.assertFalse(fixture.converter.hasTaskTickets(second));
        }
    }
    @Test
    void managerResetPreservesOnlyUnsentMaterialsAndUnacknowledgedReceiptAcrossReload() {
        Fixture fixture = new Fixture(true);
        UUID taskId = fixture.taskId;
        AEKey material = AEItemKey.of(Items.STONE);
        fixture.manager.getData().getPlannedSend().add(
                new PendingMaterial(new GenericStack(material, 3), MaterialOutputMode.NORMAL, 0));
        fixture.managerLogic.holdBatchReceipt(taskId);
        fixture.managerLogic.resetTaskState();
        Assertions.assertTrue(fixture.active());
        Assertions.assertTrue(fixture.manager.getData().isCancelRequested());
        Assertions.assertTrue(fixture.manager.getData().getPlannedSend().isEmpty());
        Assertions.assertEquals(Map.of(material, 3L), fixture.manager.getData().getInputRecovery());
        Assertions.assertTrue(fixture.managerLogic.hasAcceptedTask(taskId));
        Assertions.assertFalse(fixture.converter.hasTaskTickets(taskId));

        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag tag = new CompoundTag();
        fixture.manager.getData().writeTaskState(tag, registries);
        Pp2mpUnitManagerData restored = new Pp2mpUnitManagerData(
                ItemRegistry.PP2MP_UNIT_MANAGERS.get(AEColor.WHITE).get(), () -> {
        }, () -> {
        });
        restored.readTaskState(tag, registries);
        Assertions.assertEquals(Map.of(material, 3L), restored.getInputRecovery());
        Assertions.assertTrue(restored.getBatchReceipt().matches(taskId));
        Assertions.assertEquals(taskId, restored.getDeliveryId());
        Assertions.assertTrue(restored.isCancelRequested());
    }

    @Test
    void successiveAtomicTasksReturnOnlyTheirOwnTicketsAtTheRequiredAmount() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager);
            fixture.receiver.insert(fixture.primary, 5, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(0, fixture.count(fixture.outputTicket));
            fixture.receiver.insert(fixture.primary, 1, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(1, fixture.count(fixture.outputTicket));
            Assertions.assertFalse(fixture.active());

            UUID nextTask = UUID.randomUUID();
            Assertions.assertTrue(fixture.converter.acceptTaskInputs(nextTask,
                    new FakeCraftingConverterBlockEntity.TicketPlan(new KeyCounter[0], Map.of(0, 1L), true)));
            if (manager) {
                fixture.manager.getData().begin(Map.of(fixture.primary, 6L), fixture.primary,
                        nextTask, fixture.converter.getConverterId());
            } else {
                fixture.output.getData().begin(Map.of(fixture.primary, 6L), fixture.primary,
                        nextTask, fixture.converter.getConverterId());
            }
            fixture.receiver.insert(fixture.primary, 5, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(1, fixture.count(fixture.outputTicket));
            Assertions.assertTrue(fixture.active());
            fixture.receiver.insert(fixture.primary, 64, Actionable.MODULATE, IActionSource.empty());
            Assertions.assertEquals(2, fixture.count(fixture.outputTicket));
            Assertions.assertEquals(75, fixture.count(fixture.primary));
            Assertions.assertFalse(fixture.active());
        }
    }

    /* 两种端点均只在真实主产物齐备后回传票据，副产物和多余产物不影响完成数量。 */
    @Test
    void bothEndpointsReturnFakeTicketAfterPrimaryWithoutWaitingForByproducts() {
        for (boolean manager : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(manager);
            Assertions.assertEquals(1, fixture.receiver.insert(
                    fixture.byproduct, 1, Actionable.MODULATE, IActionSource.empty()));
            Assertions.assertTrue(fixture.active());
            Assertions.assertEquals(0, fixture.count(fixture.outputTicket));
            Assertions.assertEquals(7, fixture.receiver.insert(
                    AEItemKey.of(Items.STONE), 7, Actionable.MODULATE, IActionSource.empty()));
            Assertions.assertTrue(fixture.active());
            Assertions.assertEquals(2, fixture.receiver.insert(
                    fixture.primary, 2, Actionable.MODULATE, IActionSource.empty()));
            Assertions.assertEquals(4L, fixture.pending().get(fixture.primary));
            Assertions.assertEquals(64, fixture.receiver.insert(
                    fixture.primary, 64, Actionable.SIMULATE, IActionSource.empty()));
            Assertions.assertEquals(4L, fixture.pending().get(fixture.primary));
            Assertions.assertEquals(64, fixture.receiver.insert(
                    fixture.primary, 64, Actionable.MODULATE, IActionSource.empty()));

            Assertions.assertFalse(fixture.active());
            Assertions.assertTrue(fixture.pending().isEmpty());
            Assertions.assertEquals(66, fixture.count(fixture.primary));
            Assertions.assertEquals(1, fixture.count(fixture.byproduct));
            Assertions.assertEquals(1, fixture.count(fixture.outputTicket));
            Assertions.assertFalse(fixture.converter.hasTaskTickets(fixture.taskId));
            Assertions.assertTrue(fixture.converter.getStock().getStackInSlot(9).isEmpty());
            if (manager) {
                fixture.managerLogic.tick(1);
            } else {
                fixture.outputLogic.retryTaskCompletion();
            }
            Assertions.assertEquals(1, fixture.count(fixture.outputTicket));
        }
    }

    private static final class Fixture {
        private final UUID taskId = UUID.randomUUID();
        private final AEKey primary = AEItemKey.of(Items.DIAMOND);
        private final AEKey byproduct = AEItemKey.of(Items.GOLD_INGOT);
        private final AEItemKey outputTicket;
        private final FakeCraftingConverterBlockEntity converter = new FakeCraftingConverterBlockEntity(
                BlockPos.ZERO, BlockRegistry.FAKE_CRAFTING_CONVERTER.get().defaultBlockState());
        private final GenericStackInv destination = new GenericStackInv(
                AEKeyTypes.getAll(), () -> {
        }, GenericStackInv.Mode.STORAGE, 9);
        private final IGrid grid;
        private final IManagedGridNode node;
        private final IPartHost host;
        private final TestOutput output;
        private final TestManager manager;
        private final Pp2mpOutputLogic outputLogic;
        private final Pp2mpUnitManagerLogic managerLogic;
        private final ReceiveInventory receiver;
        private Pp2mpGridService service;

        private Fixture(boolean useManager) {
            this(useManager, 6, 1);
        }

        private Fixture(boolean useManager, long primaryAmount, long tickets) {
            this.grid = (IGrid) Proxy.newProxyInstance(
                    IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "getMachines" -> arguments[0] == FakeCraftingConverterBlockEntity.class
                                ? Set.of(this.converter) : Set.of();
                        case "getMachineClasses" -> Set.of();
                        case "getService" -> this.service;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            this.service = new Pp2mpGridService(this.grid);
            this.node = (IManagedGridNode) Proxy.newProxyInstance(
                    IManagedGridNode.class.getClassLoader(), new Class<?>[]{IManagedGridNode.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getGrid" -> this.grid;
                        case "getNode" -> null;
                        case "isActive" -> true;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            this.host = (IPartHost) Proxy.newProxyInstance(
                    IPartHost.class.getClassLoader(), new Class<?>[]{IPartHost.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "markForSave", "markForUpdate" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            ItemStack input = new ItemStack(Items.PAPER);
            input.set(DataComponents.CUSTOM_NAME, Component.literal("input"));
            ItemStack output = new ItemStack(Items.PAPER);
            output.set(DataComponents.CUSTOM_NAME, Component.literal("output"));
            this.outputTicket = AEItemKey.of(output);
            this.converter.setMarker(0, AEItemKey.of(input));
            this.converter.setMarker(9, this.outputTicket);
            Assertions.assertTrue(this.converter.acceptTaskInputs(this.taskId,
                    new FakeCraftingConverterBlockEntity.TicketPlan(new KeyCounter[0], Map.of(0, tickets), true)));
            ItemStack encoded = Pp2mpPatternItem.encodePattern(
                    PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(AEItemKey.of(input), tickets)),
                            List.of(new GenericStack(this.outputTicket, tickets), new GenericStack(this.byproduct, 8))),
                    MaterialOutputAttr.EMPTY.withMode(0, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
            encoded.set(ComponentRegistry.REAL_OUTPUT_MARKER, new GenericStack(this.primary, primaryAmount));
            Pp2mpPatternDetails pattern = new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
            Assertions.assertFalse(pattern.getOutputs().stream().anyMatch(stack -> stack.what().equals(this.primary)));
            Pp2mpTaskBatch.OutputPlan plan = Pp2mpTaskBatch.planOutputs(pattern, Set.of(this.outputTicket));
            Assertions.assertNotNull(plan);
            if (useManager) {
                this.output = null;
                this.outputLogic = null;
                this.manager = new TestManager(this);
                this.managerLogic = new Pp2mpUnitManagerLogic(this.node, this.manager);
                this.manager.getData().begin(plan.pending(), plan.primary(), this.taskId, this.converter.getConverterId());
                this.receiver = new ReceiveInventory(() -> this.destination, Map::of, this.managerLogic::allowsReturned,
                        this.managerLogic::onReturnedInserted, IActionSource.empty(), () -> {
                });
            } else {
                this.manager = null;
                this.managerLogic = null;
                this.output = new TestOutput(this);
                this.outputLogic = this.output.getLogic();
                this.output.getData().begin(plan.pending(), plan.primary(), this.taskId, this.converter.getConverterId());
                this.receiver = new ReceiveInventory(() -> this.destination, Map::of,
                        this.outputLogic::accessReceive, this.outputLogic::onReturnedInserted,
                        IActionSource.empty(), () -> {
                });
            }
        }

        private Map<AEKey, Long> pending() {
            return this.manager == null
                    ? TaskTestSupport.remaining(this.output.getData()) : TaskTestSupport.remaining(this.manager.getData());
        }

        private boolean active() {
            return this.manager == null
                    ? this.output.getData().isTaskActive() : this.manager.getData().isTaskActive();
        }

        private long count(AEKey key) {
            long amount = 0;
            for (int slot = 0; slot < this.destination.size(); slot++) {
                if (key.equals(this.destination.getKey(slot))) {
                    amount += this.destination.getAmount(slot);
                }
            }
            return amount;
        }
    }

    private static final class TestOutput extends Pp2mpOutputPart {
        private final Fixture fixture;

        private TestOutput(Fixture fixture) {
            super(ItemRegistry.PP2MP_OUTPUT.get());
            this.fixture = fixture;
        }

        @Override
        public IManagedGridNode getMainNode() {
            return this.fixture == null ? super.getMainNode() : this.fixture.node;
        }

        @Override
        public IPartHost getHost() {
            return this.fixture.host;
        }

        @Override
        public ReceiveInventory getReceiveInventory() {
            return this.fixture.receiver;
        }

        @Override
        public void wakeProductExtractor() {
        }
    }

    private static final class TestManager extends Pp2mpUnitManagerPart {
        private final Fixture fixture;

        private TestManager(Fixture fixture) {
            super(ItemRegistry.PP2MP_UNIT_MANAGERS.get(AEColor.WHITE).get());
            this.fixture = fixture;
        }

        @Override
        public IPartHost getHost() {
            return this.fixture.host;
        }

        @Override
        public ReceiveInventory getReceiveInventory() {
            return this.fixture.receiver;
        }
    }
}
