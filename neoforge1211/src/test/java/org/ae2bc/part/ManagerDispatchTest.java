package org.ae2bc.part;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.ae2bc.core.constant.Pp2mpTaskKeys;
import org.ae2bc.core.constant.Pp2mpUnitManagerKeys;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.core.unit.RedstoneMode;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.Pp2mpInsertPlan;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import appeng.api.stacks.AEFluidKey;
import net.minecraft.world.level.material.Fluids;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortData;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitRedstonePortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitTransferPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitDropPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitPlacePortPart;
import org.ae2bc.pattern.PatternTestSupport;
import org.ae2bc.pattern.MaterialOutputAttr;
import org.ae2bc.pattern.Pp2mpPatternDetails;
import org.ae2bc.pattern.Pp2mpPatternItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;

class ManagerDispatchTest {
    private static final AEKey MATERIAL = AEItemKey.of(Items.STONE);
    private static final AEKey PRODUCT = AEItemKey.of(Items.DIAMOND);

    @Test
    void managerDoesNotAllocateTwoMaterialsIntoOneSharedEmptySlot() {
        Fixture fixture = new Fixture();
        Pp2mpUnitTransferPortPart port = fixture.port(64);
        ItemStackHandler handler = new ItemStackHandler(1);
        Mockito.when(port.reserve(Mockito.same(fixture.part), Mockito.any(GenericStack.class),
                        Mockito.any(MaterialOutputMode.class), Mockito.any(Pp2mpInsertPlan.class)))
                .thenAnswer(call -> call.getArgument(3, Pp2mpInsertPlan.class)
                        .reserve(handler, (AEItemKey) call.getArgument(1, GenericStack.class).what(),
                                call.getArgument(1, GenericStack.class).amount()));
        AEKey second = AEItemKey.of(Items.DIRT);
        IPatternDetails pattern = PatternTestSupport.processing(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(MATERIAL, 2), new GenericStack(second, 2)),
                List.of(new GenericStack(PRODUCT, 1))));
        KeyCounter[] inputs = {new KeyCounter(), new KeyCounter()};
        inputs[0].add(MATERIAL, 2);
        inputs[1].add(second, 2);
        Assertions.assertFalse(fixture.logic.tryAcceptTask(
                pattern, inputs, IActionSource.empty(), UUID.randomUUID()));
        Assertions.assertFalse(fixture.data.isTaskActive());
        Assertions.assertTrue(handler.getStackInSlot(0).isEmpty());
    }

    @Test
    void overlappingJobsKeepIndependentRedstoneClocksAndDoNotClearEachOthersPower() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).blocking(false)
                .redstonePortMode(RedstoneMode.ONCE).redstonePortPulseWidth((short) 4).build());
        Pp2mpUnitRedstonePortPart redstone = Mockito.mock(Pp2mpUnitRedstonePortPart.class);
        Mockito.when(fixture.service.getPorts((short) 1, (short) 2, UnitPortType.REDSTONE))
                .thenReturn(List.of(redstone));
        fixture.port(8);
        Mockito.when(fixture.level.getGameTime()).thenReturn(10L);
        Assertions.assertTrue(fixture.accept(8));
        fixture.logic.tick(1);
        UUID first = fixture.data.getDeliveryId();
        Mockito.when(fixture.level.getGameTime()).thenReturn(13L);
        Assertions.assertTrue(fixture.accept(8));
        fixture.logic.tick(1);
        UUID second = fixture.data.getDeliveryId();
        Assertions.assertEquals(10, fixture.data.getTasks().get(first).getRedstoneStart());
        Assertions.assertEquals(13, fixture.data.getTasks().get(second).getRedstoneStart());
        fixture.logic.onReturnedInserted(PRODUCT, 1);
        Mockito.verify(redstone, Mockito.never()).resetPower();
        Mockito.clearInvocations(redstone);
        Mockito.when(fixture.level.getGameTime()).thenReturn(15L);
        fixture.logic.tick(1);
        Mockito.verify(redstone).updatePower(15);
        fixture.logic.onReturnedInserted(PRODUCT, 1);
        Mockito.verify(redstone).resetPower();
    }

    @Test
    void ordinaryTransferRequiresTheWholeSingleBatchAcrossItsPorts() {
        Fixture fixture = new Fixture();
        fixture.port(3);
        Assertions.assertFalse(fixture.accept(8));
        fixture.port(5);
        Assertions.assertTrue(fixture.accept(8));
        Assertions.assertEquals(8, fixture.data.getPlannedSend().getFirst().stack().amount());
    }

    @Test
    void singleModeRequiresEnoughPortsForTheWholeJob() {
        for (MaterialOutputMode output : List.of(MaterialOutputMode.NORMAL, MaterialOutputMode.DROP, MaterialOutputMode.PLACE)) {
            Fixture fixture = new Fixture();
            fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SINGLE).build());
            Pp2mpUnitOutputTypePortPart first = fixture.port(output.matchPort(), 64);
            List<GenericStack> materials = List.of(new GenericStack(MATERIAL, 2));
            MaterialOutputAttr attr = MaterialOutputAttr.EMPTY.withMode(0, output);
            Assertions.assertFalse(fixture.accept(materials, attr));
            Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
            Pp2mpUnitOutputTypePortPart second = fixture.port(output.matchPort(), 64);
            Assertions.assertTrue(fixture.accept(materials, attr));
            fixture.logic.tick(1);
            fixture.logic.tick(1);
            Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
            for (Pp2mpUnitOutputTypePortPart port : List.of(first, second)) {
                Mockito.verify(port).output(fixture.part, new GenericStack(MATERIAL, 1), output, Actionable.MODULATE);
            }
        }
    }

    @Test
    void nonblockingRequiresFullCapacityEvenInSingleMode() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).blocking(false)
                .outputPortMode(OutputPortMode.SINGLE).build());
        fixture.port(1);
        Assertions.assertFalse(fixture.accept(8));
        Assertions.assertFalse(fixture.data.isTaskActive());
    }

    @Test
    void nonblockingAdmitsAfterDeliveryAndTurningOnWaitsForAllExistingTasks() {
        Fixture fixture = new Fixture();
        InputConfig config = InputConfig.builder(InputConfig.DEFAULT).blocking(false).build();
        fixture.data.apply(config);
        fixture.port(8);
        Assertions.assertTrue(fixture.accept(8));
        Assertions.assertFalse(fixture.accept(8));
        fixture.logic.tick(1);
        UUID first = fixture.data.getDeliveryId();
        fixture.logic.holdBatchReceipt(first);
        Assertions.assertFalse(fixture.accept(8));
        fixture.logic.acknowledgeBatch(first);
        Assertions.assertTrue(fixture.accept(8));
        fixture.logic.tick(1);
        Assertions.assertEquals(2, fixture.data.getTasks().size());
        fixture.data.apply(InputConfig.builder(config).blocking(true).build());
        Assertions.assertFalse(fixture.accept(8));
        fixture.logic.onReturnedInserted(PRODUCT, 1);
        Assertions.assertEquals(1, fixture.data.getTasks().size());
        Assertions.assertFalse(fixture.accept(8));
        fixture.logic.onReturnedInserted(PRODUCT, 1);
        Assertions.assertTrue(fixture.accept(8));
        fixture.logic.tick(1);
        Assertions.assertFalse(fixture.accept(8));
    }

    @Test
    void defaultBlockingRejectsEvenAfterAllMaterialsAreDelivered() {
        Fixture fixture = new Fixture();
        fixture.port(8);
        Assertions.assertTrue(fixture.accept(8));
        fixture.logic.tick(1);
        Assertions.assertFalse(fixture.accept(8));
        Assertions.assertEquals(1, fixture.data.getTasks().size());
    }

    @Test
    void aLaterPortFailureDoesNotResendTheEarlierSuccessfulTransfer() {
        Fixture fixture = new Fixture();
        fixture.port(3);
        Pp2mpUnitOutputTypePortPart second = fixture.port(5);
        Assertions.assertTrue(fixture.accept(8));
        Mockito.clearInvocations(fixture.host);
        Mockito.when(second.output(Mockito.same(fixture.part), Mockito.any(GenericStack.class),
                        Mockito.any(MaterialOutputMode.class), Mockito.eq(Actionable.MODULATE)))
                .thenThrow(new IllegalStateException("Test port failure"));
        Assertions.assertThrows(IllegalStateException.class, () -> fixture.logic.tick(1));
        Assertions.assertEquals(5, fixture.data.getPlannedSend().getFirst().stack().amount());
        Mockito.verify(fixture.host, Mockito.atLeastOnce()).markForSave();
    }

    @Test
    void availabilityChecksDoNotFinalizeTasksAndIdleManagersSleep() {
        Fixture fixture = new Fixture();
        Assertions.assertEquals(TickRateModulation.SLEEP, fixture.logic.tick(1));
        fixture.data.begin(Map.of(), null, UUID.randomUUID(), null);
        Assertions.assertFalse(fixture.logic.canAcceptTask());
        Assertions.assertTrue(fixture.data.isTaskActive());
        fixture.logic.retryTaskCompletion();
        Assertions.assertFalse(fixture.data.isTaskActive());
        Assertions.assertEquals(TickRateModulation.SLEEP, fixture.logic.tick(1));
    }

    @Test
    void internalEmptySlotsReserveEachOutputTypeWithoutOutputOrCapacityChecks() {
        for (MaterialOutputMode mode : List.of(MaterialOutputMode.NORMAL, MaterialOutputMode.DROP, MaterialOutputMode.PLACE)) {
            Fixture fixture = new Fixture();
            fixture.exclusive();
            Pp2mpUnitOutputTypePortPart first = fixture.port(mode.matchPort(), 8);
            Pp2mpUnitOutputTypePortPart empty = fixture.port(mode.matchPort(), 0);
            Pp2mpUnitOutputTypePortPart last = fixture.port(mode.matchPort(), 8);
            MaterialOutputAttr config = MaterialOutputAttr.EMPTY.withMode(0, mode).withMode(1, mode).withMode(2, mode);
            Assertions.assertTrue(fixture.accept(Arrays.asList(
                    new GenericStack(MATERIAL, 2), null, new GenericStack(AEItemKey.of(Items.DIRT), 3)), config));
            Assertions.assertEquals(Map.of("port-0", 0, "port-1", 1, "port-2", 2), fixture.slotAssignments());
            Assertions.assertEquals(2, fixture.data.getPlannedSend().size());
            fixture.logic.tick(1);
            Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
            Mockito.verify(empty, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.verify(first).output(fixture.part, new GenericStack(MATERIAL, 2), mode, Actionable.MODULATE);
            Mockito.verify(last).output(fixture.part, new GenericStack(AEItemKey.of(Items.DIRT), 3), mode, Actionable.MODULATE);
            if (empty instanceof Pp2mpUnitTransferPortPart transfer) {
                Mockito.verify(transfer, Mockito.never()).reserve(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
            }
            fixture.logic.onReturnedInserted(PRODUCT, 1);
            Assertions.assertTrue(fixture.slotAssignments().isEmpty());
        }
    }

    @Test
    void leadingConsecutiveAndTrailingEmptySlotsUseOnlyTheActiveRange() {
        Fixture fixture = new Fixture();
        fixture.exclusive();
        fixture.port(0);
        fixture.port(0);
        fixture.port(8);
        Pp2mpUnitTransferPortPart trailing = fixture.port(8);
        Assertions.assertTrue(fixture.accept(Arrays.asList(null, null, new GenericStack(MATERIAL, 2), null, null),
                MaterialOutputAttr.EMPTY.withMode(3, MaterialOutputMode.PLACE)));
        Assertions.assertEquals(Map.of("port-0", 0, "port-1", 1, "port-2", 2), fixture.slotAssignments());
        fixture.logic.tick(1);
        Mockito.verify(trailing, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void emptySlotsAndMaterialsAllocateWithinTheirOwnPortType() {
        Fixture fixture = new Fixture();
        fixture.exclusive();
        fixture.port(8);
        Pp2mpUnitOutputTypePortPart empty = fixture.port(UnitPortType.PLACE, 0);
        Pp2mpUnitOutputTypePortPart placed = fixture.port(UnitPortType.PLACE, 8);
        Assertions.assertTrue(fixture.accept(Arrays.asList(new GenericStack(MATERIAL, 2), null,
                        new GenericStack(MATERIAL, 3)),
                MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.PLACE).withMode(2, MaterialOutputMode.PLACE)));
        Assertions.assertEquals(Map.of("port-0", 0, "port-1", 1, "port-2", 2), fixture.slotAssignments());
        fixture.logic.tick(1);
        Mockito.verify(empty, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(placed).output(fixture.part, new GenericStack(MATERIAL, 3),
                MaterialOutputMode.PLACE, Actionable.MODULATE);
    }

    @Test
    void insufficientExclusivePortsRejectWithoutLeavingAssignmentsOrSendingMaterials() {
        Fixture fixture = new Fixture();
        fixture.exclusive();
        Pp2mpUnitTransferPortPart first = fixture.port(8);
        Pp2mpUnitTransferPortPart second = fixture.port(8);
        List<GenericStack> inputs = Arrays.asList(new GenericStack(MATERIAL, 2), null,
                new GenericStack(AEItemKey.of(Items.DIRT), 3));
        Assertions.assertFalse(fixture.accept(inputs, MaterialOutputAttr.EMPTY));
        Assertions.assertFalse(fixture.accept(inputs, MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.DROP)));
        Assertions.assertTrue(fixture.slotAssignments().isEmpty());
        Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
        Assertions.assertFalse(fixture.data.isTaskActive());
        for (Pp2mpUnitTransferPortPart port : List.of(first, second)) {
            Mockito.verify(port, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(),
                    Mockito.eq(Actionable.MODULATE));
        }
    }

    @Test
    void disabledAndMixedExclusivityOnlyReserveEnabledPorts() {
        List<GenericStack> inputs = Arrays.asList(new GenericStack(MATERIAL, 2), null,
                new GenericStack(AEItemKey.of(Items.DIRT), 3));
        Fixture disabled = new Fixture();
        disabled.port(8);
        Assertions.assertTrue(disabled.accept(inputs, MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.DROP)));
        disabled.logic.tick(1);
        Assertions.assertTrue(disabled.data.getPlannedSend().isEmpty());
        Assertions.assertTrue(disabled.slotAssignments().isEmpty());

        Fixture mixed = new Fixture();
        mixed.data.apply(InputConfig.builder(InputConfig.DEFAULT)
                .outputPortMode(OutputPortMode.SLOT_EXCLUSIVE).build());
        Pp2mpUnitTransferPortPart shared = mixed.port(8);
        shared.getData().setSyncEnabled(false);
        shared.getData().setOutputPortMode(OutputPortMode.NORMAL);
        Pp2mpUnitTransferPortPart empty = mixed.port(0);
        Assertions.assertTrue(mixed.accept(inputs, MaterialOutputAttr.EMPTY));
        Assertions.assertEquals(Map.of("port-1", 1), mixed.slotAssignments());
        mixed.logic.tick(1);
        Mockito.verify(empty, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void splitMaterialsKeepTheirPortsBeforeAllocatingAnEmptySlot() {
        Fixture fixture = new Fixture();
        fixture.exclusive();
        fixture.port(3);
        fixture.port(5);
        fixture.port(0);
        fixture.port(8);
        Assertions.assertTrue(fixture.accept(Arrays.asList(new GenericStack(MATERIAL, 8), null,
                new GenericStack(AEItemKey.of(Items.DIRT), 2)), MaterialOutputAttr.EMPTY));
        Assertions.assertEquals(Map.of("port-0", 0, "port-1", 0, "port-2", 1, "port-3", 2),
                fixture.slotAssignments());
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
    }

    @Test
    void reloadKeepsEmptySlotBindingsEvenWhenPortOrderChanges() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Fixture original = new Fixture();
        original.data.apply(InputConfig.builder(InputConfig.DEFAULT)
                .outputPortMode(OutputPortMode.SLOT_EXCLUSIVE).build());
        for (int port = 0; port < 3; port++) {
            original.port(8);
        }
        Assertions.assertTrue(original.accept(Arrays.asList(new GenericStack(MATERIAL, 2), null,
                new GenericStack(AEItemKey.of(Items.DIRT), 3)), MaterialOutputAttr.EMPTY));
        Assertions.assertNull(original.data.getPortAssignments().get("port-1").getKey());
        CompoundTag saved = new CompoundTag();
        original.data.writeToNBT(saved, registries);
        original.data.writeTaskState(saved, registries);
        Fixture restored = new Fixture();
        restored.port(8);
        Pp2mpUnitTransferPortPart empty = restored.port(8);
        Pp2mpUnitTransferPortPart last = restored.port(8);
        restored.data.readFromNBT(saved, registries);
        restored.data.readTaskState(saved, registries);
        Collections.reverse(restored.ports.get(UnitPortType.TRANSFER));
        Assertions.assertEquals(original.slotAssignments(), restored.slotAssignments());
        restored.logic.tick(1);
        Assertions.assertTrue(restored.data.getPlannedSend().isEmpty());
        Mockito.verify(empty, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(last).output(restored.part, new GenericStack(AEItemKey.of(Items.DIRT), 3),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
    }

    @Test
    void slotExclusiveRetainsEmptyReservationsAcrossPartialDeliveryAndCancellation() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT)
                .outputPortMode(OutputPortMode.SLOT_EXCLUSIVE).build());
        Pp2mpUnitTransferPortPart first = fixture.port(2);
        Pp2mpUnitTransferPortPart empty = fixture.port(0);
        Pp2mpUnitTransferPortPart last = fixture.port(2);
        for (Pp2mpUnitTransferPortPart port : List.of(first, last)) {
            Mockito.when(port.output(Mockito.same(fixture.part), Mockito.any(GenericStack.class),
                    Mockito.any(MaterialOutputMode.class), Mockito.eq(Actionable.MODULATE))).thenReturn(1L);
        }
        Assertions.assertTrue(fixture.accept(Arrays.asList(new GenericStack(MATERIAL, 2), null,
                new GenericStack(AEItemKey.of(Items.DIRT), 2)), MaterialOutputAttr.EMPTY));
        fixture.logic.tick(1);
        Assertions.assertEquals(2, fixture.data.getPlannedSend().size());
        Assertions.assertEquals(1, fixture.slotAssignments().get("port-1"));
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
        fixture.logic.resetTaskState();
        Assertions.assertTrue(fixture.slotAssignments().isEmpty());
        Assertions.assertTrue(fixture.data.getInputRecovery().isEmpty());
        Mockito.verify(empty, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void nonblockingNextDeliveryCanReuseThePreviousEmptyPort() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).blocking(false)
                .outputPortMode(OutputPortMode.SLOT_EXCLUSIVE).build());
        fixture.port(8);
        Pp2mpUnitTransferPortPart empty = fixture.port(8);
        fixture.port(8);
        GenericStack first = new GenericStack(MATERIAL, 2);
        GenericStack last = new GenericStack(AEItemKey.of(Items.DIRT), 3);
        Assertions.assertTrue(fixture.accept(Arrays.asList(first, null, last), MaterialOutputAttr.EMPTY));
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.accept(List.of(first, last), MaterialOutputAttr.EMPTY));
        fixture.logic.tick(1);
        Mockito.verify(empty).output(fixture.part, last, MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Assertions.assertEquals(2, fixture.data.getTasks().size());
    }

    @Test
    void legacyFakeModeOnAnEmptySlotFallsBackToTransferReservation() {
        Fixture fixture = new Fixture();
        fixture.exclusive();
        fixture.port(8);
        Pp2mpUnitTransferPortPart empty = fixture.port(0);
        fixture.port(8);
        Assertions.assertTrue(fixture.accept(Arrays.asList(new GenericStack(MATERIAL, 2), null,
                        new GenericStack(AEItemKey.of(Items.DIRT), 3)),
                MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER)));
        Assertions.assertEquals(1, fixture.slotAssignments().get("port-1"));
        fixture.logic.tick(1);
        Mockito.verify(empty, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void cancellationRecoversOnlyRealMaterialsAndFailedDeliveryCannotUseTheEmptyPort() {
        Fixture fixture = new Fixture();
        fixture.exclusive();
        Pp2mpUnitTransferPortPart first = fixture.port(8);
        Pp2mpUnitTransferPortPart empty = fixture.port(8);
        fixture.port(8);
        AEKey dirt = AEItemKey.of(Items.DIRT);
        Assertions.assertTrue(fixture.accept(Arrays.asList(new GenericStack(MATERIAL, 2), null,
                new GenericStack(dirt, 3)), MaterialOutputAttr.EMPTY));
        Mockito.when(first.output(Mockito.same(fixture.part), Mockito.any(GenericStack.class),
                Mockito.any(MaterialOutputMode.class), Mockito.eq(Actionable.MODULATE))).thenReturn(0L);
        fixture.logic.tick(1);
        Assertions.assertEquals(1, fixture.data.getPlannedSend().size());
        fixture.logic.resetTaskState();
        Assertions.assertEquals(Map.of(MATERIAL, 2L), fixture.data.getInputRecovery());
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
        Mockito.verify(empty, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void vanillaProcessingPatternsAlsoKeepSparseSlotNumbers() {
        Fixture fixture = new Fixture();
        fixture.exclusive();
        fixture.port(8);
        Pp2mpUnitTransferPortPart empty = fixture.port(0);
        fixture.port(8);
        IPatternDetails pattern = PatternTestSupport.processing(PatternDetailsHelper.encodeProcessingPattern(
                Arrays.asList(new GenericStack(MATERIAL, 2), null, new GenericStack(MATERIAL, 3)),
                List.of(new GenericStack(PRODUCT, 1))));
        Assertions.assertTrue(fixture.acceptPattern(pattern));
        Assertions.assertEquals(Map.of("port-0", 0, "port-1", 1, "port-2", 2), fixture.slotAssignments());
        fixture.logic.tick(1);
        Mockito.verify(empty, Mockito.never()).output(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void singleLimitAccumulatesAcrossIngredientSlotsAndIgnoresEmptySlots() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SINGLE).build());
        Pp2mpUnitTransferPortPart first = fixture.port(64);
        List<GenericStack> inputs = Arrays.asList(new GenericStack(MATERIAL, 1), null,
                new GenericStack(AEItemKey.of(Items.DIRT), 1));
        Assertions.assertFalse(fixture.accept(inputs, MaterialOutputAttr.EMPTY));
        Pp2mpUnitTransferPortPart second = fixture.port(64);
        Assertions.assertTrue(fixture.accept(inputs, MaterialOutputAttr.EMPTY));
        Assertions.assertTrue(fixture.slotAssignments().isEmpty());
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
        Mockito.verify(first).output(fixture.part, new GenericStack(MATERIAL, 1),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Mockito.verify(second).output(fixture.part, new GenericStack(AEItemKey.of(Items.DIRT), 1),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
    }

    @Test
    void mixedSingleAndNormalPortsShareAJobWithoutApplyingSingleLimitToNormalPort() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SINGLE).build());
        Pp2mpUnitTransferPortPart single = fixture.port(64);
        Pp2mpUnitTransferPortPart normal = fixture.port(64);
        normal.getData().setSyncEnabled(false);
        normal.getData().setOutputPortMode(OutputPortMode.NORMAL);
        Assertions.assertTrue(fixture.accept(64));
        fixture.logic.tick(1);
        Mockito.verify(single).output(fixture.part, new GenericStack(MATERIAL, 1),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Mockito.verify(normal).output(fixture.part, new GenericStack(MATERIAL, 63),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
    }

    @Test
    void sameTypeSeparatesItemsAndComponentsOnEveryOutputPortType() {
        ItemStack named = new ItemStack(Items.COBBLESTONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("different resource"));
        List<GenericStack> materials = List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 2),
                new GenericStack(AEItemKey.of(Items.OAK_LOG), 3),
                new GenericStack(Objects.requireNonNull(AEItemKey.of(named)), 4));
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (MaterialOutputMode mode : List.of(MaterialOutputMode.NORMAL, MaterialOutputMode.DROP, MaterialOutputMode.PLACE)) {
            Fixture fixture = new Fixture();
            fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SAME_TYPE).build());
            List<Pp2mpUnitOutputTypePortPart> ports = new ArrayList<>();
            ports.add(fixture.port(mode.matchPort(), 64));
            MaterialOutputAttr attr = MaterialOutputAttr.EMPTY.withMode(0, mode).withMode(1, mode).withMode(2, mode);
            Assertions.assertFalse(fixture.accept(materials, attr));
            ports.add(fixture.port(mode.matchPort(), 64));
            Assertions.assertFalse(fixture.accept(materials, attr));
            Assertions.assertTrue(fixture.data.getTasks().isEmpty());
            Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
            Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
            ports.add(fixture.port(mode.matchPort(), 64));
            Assertions.assertTrue(fixture.accept(materials, attr));
            CompoundTag saved = new CompoundTag();
            fixture.data.writeTaskState(saved, registries);
            fixture.data.readTaskState(saved, registries);
            for (int index = 0; index < ports.size(); index++) {
                Assertions.assertEquals(materials.get(index).what(),
                        fixture.data.getPortAssignments().get(ports.get(index).getEndpointId()).getKey());
            }
            Collections.reverse(fixture.ports.get(mode.matchPort()));
            fixture.logic.tick(1);
            Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
            Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
            for (int index = 0; index < ports.size(); index++) {
                Mockito.verify(ports.get(index)).output(fixture.part, materials.get(index), mode, Actionable.MODULATE);
            }
        }
    }

    @Test
    void sameTypeSharesOnePortAcrossIngredientSlotsAndIgnoresEmptySlots() {
        for (MaterialOutputMode mode : List.of(MaterialOutputMode.NORMAL, MaterialOutputMode.DROP, MaterialOutputMode.PLACE)) {
            Fixture fixture = new Fixture();
            fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SAME_TYPE).build());
            Pp2mpUnitOutputTypePortPart port = fixture.port(mode.matchPort(), 64);
            GenericStack first = new GenericStack(MATERIAL, 2);
            GenericStack last = new GenericStack(MATERIAL, 3);
            Assertions.assertTrue(fixture.accept(Arrays.asList(first, null, last),
                    MaterialOutputAttr.EMPTY.withMode(0, mode).withMode(1, mode).withMode(2, mode)));
            fixture.logic.tick(1);
            Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
            Mockito.verify(port).output(fixture.part, first, mode, Actionable.MODULATE);
            Mockito.verify(port).output(fixture.part, last, mode, Actionable.MODULATE);
        }
    }

    @Test
    void sameTypeSeparatesFluidsAndItemsWithoutSingleModeLimit() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SAME_TYPE).build());
        List<GenericStack> materials = List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 2500),
                new GenericStack(AEFluidKey.of(Fluids.LAVA), 5000), new GenericStack(MATERIAL, 32));
        List<Pp2mpUnitTransferPortPart> ports = List.of(fixture.port(10000), fixture.port(10000));
        Assertions.assertFalse(fixture.accept(materials, MaterialOutputAttr.EMPTY));
        Pp2mpUnitTransferPortPart items = fixture.port(64);
        Assertions.assertTrue(fixture.accept(materials, MaterialOutputAttr.EMPTY));
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
        for (int index = 0; index < ports.size(); index++) {
            Mockito.verify(ports.get(index)).output(fixture.part, materials.get(index),
                    MaterialOutputMode.NORMAL, Actionable.MODULATE);
        }
        Mockito.verify(items).output(fixture.part, materials.get(2), MaterialOutputMode.NORMAL, Actionable.MODULATE);
    }

    @Test
    void sameTypeSpreadsOneResourceAcrossPortsWhenCapacityRequiresIt() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SAME_TYPE).build());
        Pp2mpUnitTransferPortPart first = fixture.port(3);
        Pp2mpUnitTransferPortPart second = fixture.port(3);
        Assertions.assertFalse(fixture.accept(8));
        Pp2mpUnitTransferPortPart last = fixture.port(3);
        Assertions.assertTrue(fixture.accept(8));
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
        Mockito.verify(first).output(fixture.part, new GenericStack(MATERIAL, 8),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Mockito.verify(second).output(fixture.part, new GenericStack(MATERIAL, 5),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Mockito.verify(last).output(fixture.part, new GenericStack(MATERIAL, 2),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
    }

    @Test
    void sameTypeRetainsBindingsAcrossPartialDeliveryRetriesReloadAndModeChanges() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Fixture original = new Fixture();
        InputConfig config = InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SAME_TYPE).build();
        original.data.apply(config);
        Pp2mpUnitTransferPortPart first = original.port(64);
        Pp2mpUnitTransferPortPart second = original.port(64);
        GenericStack dirt = new GenericStack(AEItemKey.of(Items.DIRT), 3);
        Assertions.assertTrue(original.accept(List.of(new GenericStack(MATERIAL, 2), dirt), MaterialOutputAttr.EMPTY));
        Mockito.when(first.output(Mockito.same(original.part), Mockito.any(GenericStack.class),
                Mockito.any(MaterialOutputMode.class), Mockito.eq(Actionable.MODULATE))).thenReturn(1L, 0L);
        Mockito.when(second.output(Mockito.same(original.part), Mockito.any(GenericStack.class),
                Mockito.any(MaterialOutputMode.class), Mockito.eq(Actionable.MODULATE))).thenReturn(0L);
        original.logic.tick(1);
        Assertions.assertEquals(2, original.data.getPlannedSend().size());
        Mockito.clearInvocations(original.host);
        for (int retry = 0; retry < 20; retry++) {
            original.logic.tick(1);
        }
        Mockito.verify(original.host, Mockito.never()).markForSave();
        CompoundTag saved = new CompoundTag();
        original.data.writeToNBT(saved, registries);
        original.data.writeTaskState(saved, registries);
        Fixture restored = new Fixture();
        Pp2mpUnitTransferPortPart restoredFirst = restored.port(64);
        Pp2mpUnitTransferPortPart restoredSecond = restored.port(64);
        restored.data.readFromNBT(saved, registries);
        restored.data.readTaskState(saved, registries);
        restored.data.apply(InputConfig.builder(config).outputPortMode(OutputPortMode.NORMAL).build());
        Assertions.assertEquals(original.data.getPortAssignments(), restored.data.getPortAssignments());
        Collections.reverse(restored.ports.get(UnitPortType.TRANSFER));
        restored.logic.tick(1);
        Mockito.verify(restoredFirst).output(restored.part, new GenericStack(MATERIAL, 1),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Mockito.verify(restoredSecond).output(restored.part, dirt, MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Assertions.assertTrue(restored.data.getPlannedSend().isEmpty());
        Assertions.assertTrue(restored.data.getPortAssignments().isEmpty());
        Assertions.assertEquals(1, restored.data.getTasks().size());
    }

    @Test
    void sameTypeCanReuseAPortForAnotherResourceWhileAwaitingPreviousProducts() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).blocking(false)
                .outputPortMode(OutputPortMode.SAME_TYPE).build());
        Pp2mpUnitTransferPortPart port = fixture.port(64);
        for (GenericStack material : List.of(new GenericStack(MATERIAL, 2),
                new GenericStack(AEItemKey.of(Items.DIRT), 3))) {
            Assertions.assertTrue(fixture.accept(List.of(material), MaterialOutputAttr.EMPTY));
            fixture.logic.tick(1);
            Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
            Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
            Mockito.verify(port).output(fixture.part, material, MaterialOutputMode.NORMAL, Actionable.MODULATE);
        }
        Assertions.assertEquals(2, fixture.data.getTasks().size());
    }

    @Test
    void reloadAndModeChangesDoNotRefreshPartialFluidAllowance() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        AEKey water = AEFluidKey.of(Fluids.WATER);
        Fixture original = new Fixture();
        InputConfig config = InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SINGLE).build();
        original.data.apply(config);
        for (int index = 0; index < 2; index++) {
            Pp2mpUnitTransferPortPart port = original.port(1000);
            Mockito.when(port.output(Mockito.same(original.part), Mockito.any(GenericStack.class),
                    Mockito.any(MaterialOutputMode.class), Mockito.eq(Actionable.MODULATE))).thenReturn(400L);
        }
        Assertions.assertTrue(original.accept(List.of(new GenericStack(water, 2000)), MaterialOutputAttr.EMPTY));
        original.logic.tick(1);
        CompoundTag saved = new CompoundTag();
        original.data.writeToNBT(saved, registries);
        original.data.writeTaskState(saved, registries);
        Fixture restored = new Fixture();
        Pp2mpUnitTransferPortPart first = restored.port(1000);
        Pp2mpUnitTransferPortPart second = restored.port(1000);
        restored.data.readFromNBT(saved, registries);
        restored.data.readTaskState(saved, registries);
        restored.data.apply(InputConfig.builder(config).outputPortMode(OutputPortMode.NORMAL).build());
        Assertions.assertEquals(OutputPortMode.NORMAL, restored.data.getConfig().unit().outputPortMode());
        Assertions.assertEquals(original.data.getPortAssignments(), restored.data.getPortAssignments());
        Collections.reverse(restored.ports.get(UnitPortType.TRANSFER));
        restored.logic.tick(1);
        Assertions.assertTrue(restored.data.getPlannedSend().isEmpty());
        Assertions.assertTrue(restored.data.getPortAssignments().isEmpty());
        for (Pp2mpUnitTransferPortPart port : List.of(first, second)) {
            Mockito.verify(port).output(restored.part, new GenericStack(water, 600),
                    MaterialOutputMode.NORMAL, Actionable.MODULATE);
        }
    }

    @Test
    void singlePortCannotSpendRemainingFluidAllowanceOnAnotherFluid() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SINGLE).build());
        fixture.port(1000);
        Assertions.assertFalse(fixture.accept(List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 500),
                new GenericStack(AEFluidKey.of(Fluids.LAVA), 500)), MaterialOutputAttr.EMPTY));
        Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
    }

    @Test
    void exhaustedSinglePortIsNotProbedAgainAndFailedRetriesDoNotSave() {
        Fixture fixture = new Fixture();
        fixture.data.apply(InputConfig.builder(InputConfig.DEFAULT).outputPortMode(OutputPortMode.SINGLE).build());
        Pp2mpUnitTransferPortPart first = fixture.port(64);
        Pp2mpUnitTransferPortPart blocked = fixture.port(64);
        Assertions.assertTrue(fixture.accept(2));
        Mockito.when(blocked.output(Mockito.same(fixture.part), Mockito.any(GenericStack.class),
                Mockito.any(MaterialOutputMode.class), Mockito.eq(Actionable.MODULATE))).thenReturn(0L);
        fixture.logic.tick(1);
        Mockito.clearInvocations(fixture.host);
        for (int retry = 0; retry < 20; retry++) {
            fixture.logic.tick(1);
        }
        Mockito.verify(first).output(fixture.part, new GenericStack(MATERIAL, 1),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Assertions.assertEquals(2, fixture.data.getPortAssignments().size());
        Assertions.assertEquals(1, fixture.data.getPortAssignments().get("port-0").getDelivered());
        Assertions.assertEquals(0, fixture.data.getPortAssignments().get("port-1").getDelivered());
        Mockito.verify(fixture.host, Mockito.never()).markForSave();
        fixture.logic.resetTaskState();
        Assertions.assertEquals(Map.of(MATERIAL, 1L), fixture.data.getInputRecovery());
        Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
    }

    @Test
    void modeChangesAndOverlappingJobsDoNotReuseAnEarlierJobsAllowance() {
        Fixture fixture = new Fixture();
        InputConfig config = InputConfig.builder(InputConfig.DEFAULT).blocking(false).build();
        fixture.data.apply(config);
        Pp2mpUnitTransferPortPart port = fixture.port(64);
        Assertions.assertTrue(fixture.accept(8));
        fixture.data.apply(InputConfig.builder(config)
                .outputPortMode(OutputPortMode.SINGLE).build());
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
        Assertions.assertFalse(fixture.accept(2));
        Assertions.assertTrue(fixture.accept(1));
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
        Assertions.assertTrue(fixture.accept(1));
        fixture.logic.tick(1);
        Assertions.assertEquals(3, fixture.data.getTasks().size());
        Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
        Mockito.verify(port).output(fixture.part, new GenericStack(MATERIAL, 8),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
        Mockito.verify(port, Mockito.times(2)).output(fixture.part, new GenericStack(MATERIAL, 1),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
    }

    @Test
    void completedDeliveryReleasesAllCandidateAssignmentsWhileKeepingTheTask() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Fixture fixture = new Fixture();
        for (int index = 0; index < 64; index++) {
            fixture.port(64);
        }
        Assertions.assertTrue(fixture.accept(8));
        UUID delivery = fixture.data.getDeliveryId();
        Assertions.assertEquals(64, fixture.data.getPortAssignments().size());
        Mockito.clearInvocations(fixture.host);
        fixture.logic.tick(1);
        Assertions.assertTrue(fixture.data.getPlannedSend().isEmpty());
        Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
        Assertions.assertEquals(delivery, fixture.data.getDeliveryId());
        Assertions.assertEquals(Map.of(PRODUCT, 1L), TaskTestSupport.remaining(fixture.data));
        Assertions.assertEquals(1, fixture.data.getTasks().size());
        Mockito.verify(fixture.host).markForSave();
        CompoundTag saved = new CompoundTag();
        fixture.data.writeTaskState(saved, registries);
        Assertions.assertTrue(saved.getCompound(Pp2mpTaskKeys.TASK)
                .getCompound(Pp2mpUnitManagerKeys.PORT_ASSIGNMENTS).isEmpty());
        fixture.data.readTaskState(saved, registries);
        Assertions.assertTrue(fixture.data.getPortAssignments().isEmpty());
        Assertions.assertEquals(Map.of(PRODUCT, 1L), TaskTestSupport.remaining(fixture.data));
        fixture.logic.onReturnedInserted(PRODUCT, 1);
        Assertions.assertFalse(fixture.data.isTaskActive());
    }

    @Test
    void largeAmountsArePlannedInBulkRatherThanOneItemAtATime() {
        Fixture fixture = new Fixture();
        Pp2mpUnitTransferPortPart port = fixture.port(1_000_000);
        Assertions.assertTrue(fixture.accept(1_000_000));
        fixture.logic.tick(1);
        Mockito.verify(port).reserve(Mockito.same(fixture.part), Mockito.any(GenericStack.class),
                Mockito.eq(MaterialOutputMode.NORMAL), Mockito.any(Pp2mpInsertPlan.class));
        Mockito.verify(port).output(fixture.part, new GenericStack(MATERIAL, 1_000_000),
                MaterialOutputMode.NORMAL, Actionable.MODULATE);
    }

    private static final class Fixture {
        private final Pp2mpUnitManagerPart part = Mockito.mock(Pp2mpUnitManagerPart.class);
        private final IPartHost host = Mockito.mock(IPartHost.class);
        private final Pp2mpUnitManagerData data = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
        private final ServerLevel level = Mockito.mock(ServerLevel.class);
        private final Pp2mpGridService service = Mockito.mock(Pp2mpGridService.class);
        private final Map<UnitPortType, List<Pp2mpUnitOutputTypePortPart>> ports = new EnumMap<>(UnitPortType.class);
        private int nextPortId;
        private final Pp2mpUnitManagerLogic logic;

        private Fixture() {
            IGrid grid = Mockito.mock(IGrid.class);
            IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
            Mockito.when(this.part.getData()).thenReturn(this.data);
            Mockito.when(this.part.getHost()).thenReturn(this.host);
            Mockito.when(this.part.getLevel()).thenReturn(this.level);
            Mockito.when(this.part.getFrequency()).thenReturn((short) 1);
            Mockito.when(this.part.getUnitId()).thenReturn((short) 2);
            Mockito.when(this.part.validFrequency()).thenReturn(true);
            Mockito.when(node.isActive()).thenReturn(true);
            Mockito.when(node.getGrid()).thenReturn(grid);
            Mockito.when(grid.getService(Pp2mpGridService.class)).thenReturn(this.service);
            for (UnitPortType type : List.of(UnitPortType.TRANSFER, UnitPortType.DROP, UnitPortType.PLACE)) {
                List<Pp2mpUnitOutputTypePortPart> outputPorts = new ArrayList<>();
                this.ports.put(type, outputPorts);
                Mockito.when(this.service.getOutputPorts((short) 1, (short) 2, type)).thenReturn(outputPorts);
            }
            this.logic = new Pp2mpUnitManagerLogic(node, this.part);
        }

        private Pp2mpUnitTransferPortPart port(long capacity) {
            Pp2mpUnitTransferPortPart port = Mockito.mock(Pp2mpUnitTransferPortPart.class);
            this.configurePort(port, UnitPortType.TRANSFER, capacity);
            Mockito.when(port.reserve(Mockito.same(this.part), Mockito.any(GenericStack.class),
                            Mockito.any(MaterialOutputMode.class), Mockito.any(Pp2mpInsertPlan.class)))
                    .thenAnswer(call -> Math.min(capacity, call.getArgument(1, GenericStack.class).amount()));
            return port;
        }

        private Pp2mpUnitOutputTypePortPart port(UnitPortType type, long capacity) {
            if (type == UnitPortType.TRANSFER) {
                return this.port(capacity);
            }
            Pp2mpUnitOutputTypePortPart port = switch (type) {
                case DROP -> Mockito.mock(Pp2mpUnitDropPortPart.class);
                case PLACE -> Mockito.mock(Pp2mpUnitPlacePortPart.class);
                default -> throw new IllegalArgumentException("Not an output port");
            };
            this.configurePort(port, type, capacity);
            return port;
        }

        private void configurePort(Pp2mpUnitOutputTypePortPart port, UnitPortType type, long capacity) {
            Mockito.when(port.getType()).thenReturn(type);
            Mockito.when(port.getEndpointId()).thenReturn("port-" + this.nextPortId++);
            Mockito.when(port.isBoundTo(this.part)).thenReturn(true);
            Mockito.when(port.getData()).thenReturn(new Pp2mpUnitOutputTypePortData(() -> { }, () -> { }));
            Mockito.when(port.output(Mockito.same(this.part), Mockito.any(GenericStack.class),
                            Mockito.any(MaterialOutputMode.class), Mockito.any(Actionable.class)))
                    .thenAnswer(call -> Math.min(capacity, call.getArgument(1, GenericStack.class).amount()));
            this.ports.get(type).add(port);
        }

        private Map<String, Integer> slotAssignments() {
            Map<String, Integer> slots = new HashMap<>();
            this.data.getPortAssignments().forEach((id, assignment) -> {
                if (assignment.getSlot() >= 0) {
                    slots.put(id, assignment.getSlot());
                }
            });
            return slots;
        }

        private void exclusive() {
            this.data.apply(InputConfig.builder(InputConfig.DEFAULT)
                    .outputPortMode(OutputPortMode.SLOT_EXCLUSIVE).build());
        }

        private boolean accept(List<GenericStack> inputs, MaterialOutputAttr config) {
            ItemStack vanilla = PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(new GenericStack(PRODUCT, 1)));
            AEItemKey definition = AEItemKey.of(Pp2mpPatternItem.encodePattern(vanilla, config));
            Assertions.assertNotNull(definition);
            return this.acceptPattern(new Pp2mpPatternDetails(definition, null));
        }

        private boolean accept(long amount) {
            IPatternDetails pattern = PatternTestSupport.processing(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(MATERIAL, amount)), List.of(new GenericStack(PRODUCT, 1))));
            return this.acceptPattern(pattern);
        }

        private boolean acceptPattern(IPatternDetails pattern) {
            IPatternDetails.IInput[] required = pattern.getInputs();
            KeyCounter[] inputs = new KeyCounter[required.length];
            for (int slot = 0; slot < required.length; slot++) {
                inputs[slot] = new KeyCounter();
                inputs[slot].add(required[slot].getPossibleInputs()[0].what(), required[slot].getMultiplier());
            }
            return this.logic.tryAcceptTask(pattern, inputs, IActionSource.empty(), UUID.randomUUID());
        }
    }
}
