package org.ae2bc.part;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.ae2bc.part.task.Pp2mpTaskReceipt;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.pattern.PatternTestSupport;
import org.ae2bc.pattern.Pp2mpTaskPlan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class BatchOwnershipTest {
    private static final String ENDPOINT_ID = "v2|output|minecraft:overworld|0|north";
    private static Pp2mpInputData input(long copies) {
        AEKey material = AEItemKey.of(Items.STONE);
        IPatternDetails pattern = PatternTestSupport.processing(
                PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(material, 2)),
                        List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))));
        KeyCounter[] resources = {new KeyCounter()};
        resources[0].add(material, copies * 2);
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(pattern, resources, null);
        Pp2mpInputData input = new Pp2mpInputData(() -> {
        }, () -> {
        });
        input.setBatchTask(plan.pattern(), plan.inputs(), plan.totalUnits(), UUID.randomUUID());
        return input;
    }

    private static Pp2mpInputData reload(Pp2mpInputData input) {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag tag = new CompoundTag();
        input.writeToNBT(tag, registries);
        Pp2mpInputData restored = new Pp2mpInputData(() -> {
        }, () -> {
        });
        restored.readFromNBT(tag, registries);
        return restored;
    }

    @Test
    void commitDecrementsOneRecipeAndReloadKeepsOnlyUnsentResources() {
        Pp2mpInputData input = BatchOwnershipTest.input(64);
        IPatternDetails pattern = Pp2mpTaskPlan.restore(input.getBatchPattern(), null);
        input.setBatchInFlight(BatchOwnershipTest.ENDPOINT_ID, 1, 0);
        KeyCounter[] batch = Pp2mpTaskPlan.nextInputs(pattern, input.getBatchInputs());
        input.commitBatchInputs(batch);
        input.commitBatchInputs(batch);
        Assertions.assertEquals(63, input.getBatchRemainingUnits());
        Assertions.assertEquals(Map.of(AEItemKey.of(Items.STONE), 126L),
                Pp2mpTaskPlan.amounts(input.getBatchInputs()));
        Pp2mpInputData restored = BatchOwnershipTest.reload(input);
        Assertions.assertTrue(restored.isBatchInFlightCommitted());
        Assertions.assertEquals(BatchOwnershipTest.ENDPOINT_ID, restored.getBatchInFlightEndpointId());
        Assertions.assertEquals(63, restored.getBatchRemainingUnits());
        Assertions.assertEquals(Pp2mpTaskPlan.amounts(input.getBatchInputs()),
                Pp2mpTaskPlan.amounts(restored.getBatchInputs()));
    }

    @Test
    void lastCommittedBatchSurvivesReloadUntilReceiptIsAcknowledged() {
        Pp2mpInputData input = BatchOwnershipTest.input(1);
        input.setBatchInFlight(BatchOwnershipTest.ENDPOINT_ID, 1, 0);
        input.commitBatchInputs(Pp2mpTaskPlan.nextInputs(
                Pp2mpTaskPlan.restore(input.getBatchPattern(), null), input.getBatchInputs()));
        Pp2mpInputData restored = BatchOwnershipTest.reload(input);
        Assertions.assertTrue(restored.hasBatchTask());
        Assertions.assertEquals(0, restored.getBatchRemainingUnits());
        Assertions.assertTrue(Pp2mpTaskPlan.amounts(restored.getBatchInputs()).isEmpty());
        Assertions.assertTrue(restored.isBatchInFlightCommitted());
    }

    @Test
    void resetRecoversOnlyRemainingResourcesAndNeverAnUnresolvedDelivery() {
        Pp2mpInputData input = BatchOwnershipTest.input(4);
        input.setBatchInFlight(BatchOwnershipTest.ENDPOINT_ID, 1, 0);
        Assertions.assertThrows(IllegalStateException.class, input::recoverBatchInputs);
        input.commitBatchInputs(Pp2mpTaskPlan.nextInputs(
                Pp2mpTaskPlan.restore(input.getBatchPattern(), null), input.getBatchInputs()));
        input.clearBatchInFlight();
        input.recoverBatchInputs();
        Assertions.assertFalse(input.hasBatchTask());
        Assertions.assertEquals(Map.of(AEItemKey.of(Items.STONE), 6L), input.getInputRecovery());
        Assertions.assertEquals(input.getInputRecovery(), BatchOwnershipTest.reload(input).getInputRecovery());
    }

    @Test
    void committedTemplateDoesNotDependOnMutableMachineInputCounters() {
        Pp2mpInputData input = BatchOwnershipTest.input(4);
        Pp2mpTaskPlan.InputTemplate template = new Pp2mpTaskPlan.InputTemplate(
                Pp2mpTaskPlan.restore(input.getBatchPattern(), null));
        KeyCounter[] offered = template.offer(input.getBatchInputs());
        offered[0].remove(AEItemKey.of(Items.STONE));
        input.setBatchInFlight(BatchOwnershipTest.ENDPOINT_ID, 1, 0);
        input.commitBatchInputs(template);
        input.commitBatchInputs(template);
        Assertions.assertEquals(3, input.getBatchRemainingUnits());
        Assertions.assertEquals(6, input.getBatchInputs()[0].get(AEItemKey.of(Items.STONE)));
    }

    @Test
    void receiptCannotBeOverwrittenBeforeAcknowledgement() {
        Pp2mpTaskReceipt receipt = new Pp2mpTaskReceipt();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        receipt.remember(first);
        Assertions.assertThrows(IllegalStateException.class, () -> receipt.remember(second));
        CompoundTag tag = new CompoundTag();
        receipt.writeToNBT(tag);
        Pp2mpTaskReceipt restored = new Pp2mpTaskReceipt();
        restored.readFromNBT(tag);
        Assertions.assertTrue(restored.matches(first));
        restored.acknowledge(second);
        Assertions.assertTrue(restored.isPending());
        restored.acknowledge(first);
        restored.remember(second);
        Assertions.assertTrue(restored.matches(second));
    }

    @Test
    void removalExcludesInFlightOnlyOnceAndKeepsRecoveryResources() {
        Pp2mpInputData input = BatchOwnershipTest.input(4);
        AEKey material = AEItemKey.of(Items.STONE);
        input.getInputRecovery().put(material, 3L);
        input.setBatchInFlight(BatchOwnershipTest.ENDPOINT_ID, 1, 0);
        Assertions.assertEquals(Map.of(material, 11L), input.storedInputs(false));
        Assertions.assertEquals(Map.of(material, 9L), input.storedInputs(true));
        input.commitBatchInputs(Pp2mpTaskPlan.nextInputs(
                Pp2mpTaskPlan.restore(input.getBatchPattern(), null), input.getBatchInputs()));
        Assertions.assertEquals(Map.of(material, 9L), input.storedInputs(true));
        Assertions.assertEquals(Map.of(material, 9L), BatchOwnershipTest.reload(input).storedInputs(true));
        Assertions.assertEquals(3, input.getBatchRemainingUnits());
    }
}
