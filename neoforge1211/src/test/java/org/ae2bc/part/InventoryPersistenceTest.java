package org.ae2bc.part;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.ae2bc.core.constant.Pp2mpTaskKeys;
import org.ae2bc.part.output.Pp2mpOutputData;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakeData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.manager.Pp2mpPortAssignment;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.part.task.Pp2mpTaskRecord;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class InventoryPersistenceTest {
    @Test
    void independentTasksReceiptAndPartialDeliverySurviveReload() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpOutputData original = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        AEKey key = AEItemKey.of(Items.DIAMOND);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID converter = UUID.randomUUID();
        original.begin(Map.of(key, 9L), key, first, converter);
        original.getTasks().get(first).getProgress().record(key, 8);
        original.getTasks().get(first).setRedstoneStart(10);
        original.begin(Map.of(key, 9L), key, second, converter);
        original.getTasks().get(second).setRedstoneStart(20);
        original.getBatchReceipt().remember(second);
        original.getPendingInputs().put(null, new LinkedHashMap<>(Map.of(AEItemKey.of(Items.STONE), 2L)));
        CompoundTag saved = new CompoundTag();
        original.writeToNBT(saved, registries);
        Pp2mpOutputData restored = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        restored.readFromNBT(saved, registries);
        Assertions.assertEquals(List.of(first, second), List.copyOf(restored.getTasks().keySet()));
        Assertions.assertEquals(Map.of(key, 1L), restored.getTasks().get(first).getProgress().remaining());
        Assertions.assertEquals(Map.of(key, 9L), restored.getTasks().get(second).getProgress().remaining());
        Assertions.assertEquals(10, restored.getTasks().get(first).getRedstoneStart());
        Assertions.assertEquals(20, restored.getTasks().get(second).getRedstoneStart());
        Assertions.assertEquals(converter, restored.getTasks().get(second).getConverterId());
        Assertions.assertEquals(second, restored.getDeliveryId());
        Assertions.assertTrue(restored.getBatchReceipt().matches(second));
        Assertions.assertEquals(original.getPendingInputs(), restored.getPendingInputs());
    }

    @Test
    void legacySingleTaskRetainsItsIdentityProductsAndClock() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        AEKey key = AEItemKey.of(Items.DIAMOND);
        UUID id = UUID.randomUUID();
        Pp2mpTaskRecord record = new Pp2mpTaskRecord(id, Map.of(key, 9L), key, UUID.randomUUID());
        record.getProgress().record(key, 8);
        record.setRedstoneStart(42);
        CompoundTag task = record.write(registries);
        task.putBoolean(Pp2mpTaskKeys.ACTIVE, true);
        CompoundTag root = new CompoundTag();
        root.put(Pp2mpTaskKeys.TASK, task);
        Pp2mpUnitManagerData restored = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
        restored.readTaskState(root, registries);
        Assertions.assertEquals(id, restored.getDeliveryId());
        Assertions.assertEquals(Map.of(key, 1L), TaskTestSupport.remaining(restored));
        Assertions.assertEquals(record.getConverterId(), TaskTestSupport.record(restored).getConverterId());
        Assertions.assertEquals(42, TaskTestSupport.record(restored).getRedstoneStart());
    }
    @Test
    void completedDeclaredKeysAndOwnedRecoverySurviveReloadWhileCanceling() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpOutputData original = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        AEKey key = AEItemKey.of(Items.DIAMOND);
        UUID id = UUID.randomUUID();
        original.begin(Map.of(key, 6L), null, id, UUID.randomUUID());
        TaskTestSupport.record(original).getProgress().record(key, 6);
        original.getExtractionRecovery().put(key, 58L);
        original.setCancelRequested(true);
        CompoundTag tag = new CompoundTag();
        original.writeToNBT(tag, registries);
        Pp2mpOutputData restored = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        restored.readFromNBT(tag, registries);
        Assertions.assertTrue(restored.isTaskActive());
        Assertions.assertTrue(restored.isCancelRequested());
        Assertions.assertEquals(id, restored.getDeliveryId());
        Assertions.assertEquals(TaskTestSupport.record(original).getConverterId(), TaskTestSupport.record(restored).getConverterId());
        Assertions.assertEquals(Map.of(key, 6L), TaskTestSupport.record(restored).getProgress().declared());
        Assertions.assertTrue(TaskTestSupport.remaining(restored).isEmpty());
        Assertions.assertEquals(Map.of(key, 58L), restored.getExtractionRecovery());
    }

    @Test
    void oldRootTaskFieldsCannotRestoreOnlyHalfOfANewTask() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpOutputData output = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        output.begin(Map.of(AEItemKey.of(Items.DIAMOND), 1L), null, UUID.randomUUID(), null);
        output.getPendingInputs().put(null, new LinkedHashMap<>(Map.of(AEItemKey.of(Items.STONE), 2L)));
        CompoundTag saved = new CompoundTag();
        output.writeToNBT(saved, registries);
        CompoundTag legacyRoot = saved.getCompound(Pp2mpTaskKeys.TASK).copy();
        output.readFromNBT(legacyRoot, registries);
        Assertions.assertFalse(output.isTaskActive());
        Assertions.assertTrue(output.getPendingInputs().isEmpty());
        Assertions.assertTrue(TaskTestSupport.remaining(output).isEmpty());
        Pp2mpInputData input = new Pp2mpInputData(() -> { }, () -> { });
        input.getInputRecovery().put(AEItemKey.of(Items.STONE), 2L);
        input.writeToNBT(saved, registries);
        input.readFromNBT(saved.getCompound(Pp2mpTaskKeys.TASK).copy(), registries);
        Assertions.assertTrue(input.getInputRecovery().isEmpty());
        Assertions.assertFalse(input.hasBatchTask());
    }

    @Test
    void providerDefaultsToPassiveAndPersistsExplicitMode() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpResourceIntakeData data = new Pp2mpResourceIntakeData(
                ItemRegistry.PP2MP_RESOURCE_INTAKE.get(), () -> {
        }, () -> {
        });
        Assertions.assertFalse(data.isActivePull());
        data.readFromNBT(new CompoundTag(), registries);
        Assertions.assertFalse(data.isActivePull());
        data.setActivePull(true);
        CompoundTag tag = new CompoundTag();
        data.writeToNBT(tag, registries);
        data.setActivePull(false);
        data.readFromNBT(tag, registries);
        Assertions.assertTrue(data.isActivePull());
        data.setActivePull(false);
        data.writeToNBT(tag, registries);
        data.setActivePull(true);
        data.readFromNBT(tag, registries);
        Assertions.assertFalse(data.isActivePull());
    }

    @Test
    void outputReloadPreservesCompletedPrimaryWhileWaitingToReturnFakeTicket() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpOutputData original = new Pp2mpOutputData(() -> {
        }, () -> {
        }, () -> {
        });
        original.begin(Map.of(), AEItemKey.of(Items.DIAMOND), UUID.randomUUID(), null);
        CompoundTag tag = new CompoundTag();
        original.writeToNBT(tag, registries);
        Pp2mpOutputData restored = new Pp2mpOutputData(() -> {
        }, () -> {
        }, () -> {
        });
        restored.readFromNBT(tag, registries);
        Assertions.assertTrue(restored.isTaskActive());
        Assertions.assertEquals(original.getDeliveryId(), restored.getDeliveryId());
        Assertions.assertEquals(TaskTestSupport.record(original).getPrimary(), TaskTestSupport.record(restored).getPrimary());
        Assertions.assertTrue(TaskTestSupport.remaining(restored).isEmpty());

        original.getTasks().clear();
        original.afterCompletion();
        original.writeToNBT(tag, registries);
        restored.readFromNBT(tag, registries);
        Assertions.assertFalse(restored.isTaskActive());
        Assertions.assertTrue(restored.getTasks().isEmpty());
    }

    @Test
    void managerReloadDiscardsFinishedDeliveryAssignmentsButPreservesTaskAndByproducts() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpUnitManagerData original = new Pp2mpUnitManagerData(
                ItemRegistry.PP2MP_UNIT_MANAGERS.get(AEColor.WHITE).get(), () -> {
        }, () -> {
        });
        AEKey primary = AEItemKey.of(Items.DIAMOND);
        AEKey byproduct = AEItemKey.of(Items.GOLD_INGOT);
        original.begin(Map.of(primary, 4L, byproduct, 8L), primary, UUID.randomUUID(), UUID.randomUUID());
        Pp2mpPortAssignment assignment = new Pp2mpPortAssignment(OutputPortMode.SLOT_EXCLUSIVE);
        assignment.reserveEmpty(3);
        original.getPortAssignments().put("minecraft:overworld|123|north", assignment);
        original.getInputRecovery().put(AEItemKey.of(Items.STONE), 2L);
        TaskTestSupport.record(original).setRedstoneStart(12345);
        CompoundTag tag = new CompoundTag();
        original.writeToNBT(tag, registries);
        original.writeTaskState(tag, registries);
        Pp2mpUnitManagerData restored = new Pp2mpUnitManagerData(
                ItemRegistry.PP2MP_UNIT_MANAGERS.get(AEColor.WHITE).get(), () -> {
        }, () -> {
        });
        restored.readFromNBT(tag, registries);
        restored.readTaskState(tag, registries);
        Assertions.assertTrue(restored.isTaskActive());
        Assertions.assertEquals(original.getDeliveryId(), restored.getDeliveryId());
        Assertions.assertEquals(primary, TaskTestSupport.record(restored).getPrimary());
        Assertions.assertEquals(TaskTestSupport.remaining(original), TaskTestSupport.remaining(restored));
        Assertions.assertEquals(TaskTestSupport.record(original).getProgress().declared(), TaskTestSupport.record(restored).getProgress().declared());
        Assertions.assertTrue(restored.getPortAssignments().isEmpty());
        Assertions.assertEquals(original.getInputRecovery(), restored.getInputRecovery());
        Assertions.assertEquals(TaskTestSupport.record(original).getConverterId(), TaskTestSupport.record(restored).getConverterId());
        Assertions.assertEquals(12345, TaskTestSupport.record(restored).getRedstoneStart());
    }

    @Test
    void savingProviderDoesNotMutateInventoryAndReloadRestoresAllResources() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        AtomicInteger changes = new AtomicInteger();
        Pp2mpResourceIntakeData original = new Pp2mpResourceIntakeData(ItemRegistry.PP2MP_RESOURCE_INTAKE.get(), changes::incrementAndGet, () -> {
        });
        GenericStack item = new GenericStack(AEItemKey.of(Items.STONE), 8);
        GenericStack fluid = new GenericStack(AEFluidKey.of(Fluids.WATER), 1200);
        original.getBuffer().setStack(0, item);
        original.getBuffer().setStack(1, fluid);
        int beforeSave = changes.get();
        CompoundTag tag = new CompoundTag();
        original.writeToNBT(tag, registries);
        Assertions.assertEquals(item, original.getBuffer().getStack(0));
        Assertions.assertEquals(fluid, original.getBuffer().getStack(1));
        Assertions.assertEquals(beforeSave, changes.get());
        Pp2mpResourceIntakeData restored = new Pp2mpResourceIntakeData(ItemRegistry.PP2MP_RESOURCE_INTAKE.get(), () -> {
        }, () -> {
        });
        restored.readFromNBT(tag, registries);
        Assertions.assertEquals(item, restored.getBuffer().getStack(0));
        Assertions.assertEquals(fluid, restored.getBuffer().getStack(1));
    }

    @Test
    void outputReloadPreservesPendingDirectionsTaskIdentityAndCanceledInputs() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpOutputData original = new Pp2mpOutputData(() -> {
        }, () -> {
        }, () -> {
        });
        AEKey item = AEItemKey.of(Items.STONE);
        AEKey fluid = AEFluidKey.of(Fluids.WATER);
        original.getPendingInputs().put(null, new LinkedHashMap<>(Map.of(item, 3L)));
        original.getPendingInputs().put(Direction.NORTH, new LinkedHashMap<>(Map.of(fluid, 10000000L)));
        original.getInputRecovery().put(item, 11L);
        original.begin(Map.of(), null, UUID.randomUUID(), null);
        CompoundTag tag = new CompoundTag();
        original.writeToNBT(tag, registries);
        Pp2mpOutputData restored = new Pp2mpOutputData(() -> {
        }, () -> {
        }, () -> {
        });
        restored.readFromNBT(tag, registries);
        Assertions.assertEquals(original.getPendingInputs(), restored.getPendingInputs());
        Assertions.assertEquals(original.getInputRecovery(), restored.getInputRecovery());
        Assertions.assertEquals(original.getDeliveryId(), restored.getDeliveryId());
        Assertions.assertTrue(restored.isTaskActive());
        Assertions.assertTrue(TaskTestSupport.remaining(restored).isEmpty());
    }
}
