package org.ae2bc.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.me.storage.ExternalStorageFacade;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.ae2bc.integration.BulkPatternProviderReturnInventory;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.unit.ReturnMode;
import org.ae2bc.part.output.Pp2mpOutputData;
import org.ae2bc.part.output.Pp2mpOutputLogic;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

class BulkReturnTest {
    private static final AEKey PRODUCT = AEItemKey.of(Items.GRAVEL);

    @Test
    void oneExtractionReturns4096ThroughTheRealProviderWithoutFillingItsSlots() {
        Fixture fixture = new Fixture();
        ProductExtractor.Result result = fixture.extract();
        Assertions.assertEquals(4096, result.insertedAmount());
        Assertions.assertEquals(1, fixture.extractions.get());
        Assertions.assertEquals(1, fixture.networkInsertions.get());
        Assertions.assertEquals(4096, fixture.networkAmount.get());
        Assertions.assertEquals(4096, fixture.counted.get());
        Assertions.assertEquals(0, fixture.stock.get());
        Assertions.assertTrue(fixture.provider.getReturnInv().isEmpty());
        Assertions.assertNull(fixture.provider.getUnlockStack());
        Assertions.assertEquals(0, fixture.scopes.get());
        Assertions.assertTrue(fixture.recovery.isEmpty());
        Assertions.assertFalse(fixture.task.isTaskActive());
        Mockito.verify(fixture.host).saveChanges();
    }

    @Test
    void simulationDoesNotExtractCountOrUnlockAndOrdinaryInsertionStillUsesSlots() {
        Fixture fixture = new Fixture();
        fixture.receiver.beginBatch();
        try {
            Assertions.assertEquals(4096, fixture.receiver.insertExtracted(PRODUCT, 4096, Actionable.SIMULATE));
            Assertions.assertEquals(0, fixture.receiver.insertExtracted(
                    AEItemKey.of(Items.DIAMOND), 4096, Actionable.SIMULATE));
            Assertions.assertEquals(576, fixture.receiver.insert(PRODUCT, 4096, Actionable.SIMULATE, IActionSource.empty()));
            Assertions.assertEquals(4096, fixture.stock.get());
            Assertions.assertEquals(0, fixture.networkAmount.get());
            Assertions.assertEquals(0, fixture.counted.get());
            Assertions.assertEquals(4096, fixture.unlockAmount());
            Assertions.assertTrue(fixture.provider.getReturnInv().isEmpty());
            Mockito.verify(fixture.host, Mockito.never()).saveChanges();
        } finally {
            fixture.receiver.endBatch();
        }
        Assertions.assertThrows(IllegalStateException.class,
                () -> fixture.receiver.insertExtracted(PRODUCT, 1, Actionable.MODULATE));
    }

    @Test
    void reducedActualAcceptanceReturnsTheUnacceptedRemainderToTheMachine() {
        Fixture fixture = new Fixture();
        fixture.networkCapacity = 1000;
        fixture.restore = true;
        ProductExtractor.Result result = fixture.extract();
        Assertions.assertEquals(1576, result.insertedAmount());
        Assertions.assertEquals(1000, fixture.networkAmount.get());
        Assertions.assertEquals(576, fixture.bufferedAmount());
        Assertions.assertEquals(2520, fixture.stock.get());
        Assertions.assertEquals(3096, fixture.unlockAmount());
        Assertions.assertTrue(fixture.recovery.isEmpty());
        Assertions.assertEquals(4096, fixture.networkAmount.get() + fixture.bufferedAmount() + fixture.stock.get());
    }

    @Test
    void unreturnableRemainderIsRecoveredWithoutDuplicatingAlreadyReturnedItems() {
        Fixture fixture = new Fixture();
        fixture.networkCapacity = 1000;
        fixture.extract();
        Assertions.assertEquals(Map.of(PRODUCT, 2520L), fixture.recovery);
        Assertions.assertEquals(1576, fixture.counted.get());
        Assertions.assertTrue(fixture.task.isTaskActive());
        CompoundTag persisted = new CompoundTag();
        fixture.task.writeToNBT(persisted, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Pp2mpOutputData restored = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        restored.readFromNBT(persisted, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Assertions.assertEquals(fixture.recovery, restored.getExtractionRecovery());
        Assertions.assertTrue(restored.isTaskActive());
        fixture.networkCapacity = 4096;
        for (int attempt = 0; attempt < 6 && !fixture.recovery.isEmpty(); attempt++) {
            fixture.receiver.drainRecovery(fixture.recovery);
        }
        Assertions.assertTrue(fixture.recovery.isEmpty());
        Assertions.assertEquals(4096, fixture.networkAmount.get());
        Assertions.assertEquals(4096, fixture.counted.get());
        Assertions.assertNull(fixture.provider.getUnlockStack());
        Assertions.assertTrue(fixture.provider.getReturnInv().isEmpty());
        Assertions.assertFalse(fixture.task.isTaskActive());
    }

    @Test
    void fullNetworkAndBufferLeaveTheMachineUntouched() {
        Fixture fixture = new Fixture();
        fixture.simulatedCapacity = 0;
        fixture.networkCapacity = 0;
        for (int slot = 0; slot < fixture.provider.getReturnInv().size(); slot++) {
            fixture.provider.getReturnInv().setStack(slot, new GenericStack(PRODUCT, 64));
        }
        ProductExtractor.Result result = fixture.extract();
        Assertions.assertEquals(0, result.insertedAmount());
        Assertions.assertTrue(result.destinationBlocked());
        Assertions.assertEquals(0, fixture.extractions.get());
        Assertions.assertEquals(4096, fixture.stock.get());
    }

    @Test
    void providerReturnRejectsReentryAndUsesTheProvidersActionSource() {
        Fixture fixture = new Fixture();
        fixture.checkReentry = true;
        fixture.extract();
        Assertions.assertEquals(4096, fixture.networkAmount.get());
        Assertions.assertNotSame(fixture.receiverSource, fixture.networkSource);
        Assertions.assertTrue(fixture.provider.getReturnInv().canInsert());
    }

    @Test
    void inactiveProviderFallsBackToItsOriginalBuffer() {
        Fixture fixture = new Fixture();
        Mockito.when(fixture.node.isActive()).thenReturn(false);
        Assertions.assertEquals(576, fixture.extract().insertedAmount());
        Assertions.assertEquals(3520, fixture.stock.get());
        Assertions.assertEquals(576, fixture.bufferedAmount());
        Assertions.assertEquals(0, fixture.networkAmount.get());
    }

    @Test
    void simulationFailureReleasesBothReentryGuardsAndTheTaskScope() {
        Fixture fixture = new Fixture();
        fixture.failSimulation = true;
        Assertions.assertThrows(IllegalStateException.class, fixture::extract);
        Assertions.assertEquals(4096, fixture.stock.get());
        Assertions.assertEquals(0, fixture.scopes.get());
        Assertions.assertTrue(fixture.provider.getReturnInv().canInsert());
        fixture.failSimulation = false;
        Assertions.assertEquals(4096, fixture.extract().insertedAmount());
    }

    @Test
    void unlockedProviderDoesNotBecomeDirtyOnBulkReturn() {
        Fixture fixture = new Fixture();
        fixture.provider.resetCraftingLock();
        Mockito.clearInvocations(fixture.host);
        Assertions.assertEquals(4096, fixture.extract().insertedAmount());
        Mockito.verify(fixture.host, Mockito.never()).saveChanges();
    }

    @Test
    void unrelatedReturnPreservesTheLockWithoutMarkingItDirty() {
        Fixture fixture = new Fixture();
        fixture.awaitProduct(AEItemKey.of(Items.DIAMOND), 4096);
        Assertions.assertEquals(4096, fixture.extract().insertedAmount());
        Assertions.assertEquals(4096, fixture.unlockAmount());
        Mockito.verify(fixture.host, Mockito.never()).saveChanges();
    }

    @Test
    void partialUnlockProgressIsMarkedAndPersisted() {
        Fixture fixture = new Fixture();
        fixture.stock.set(1024);
        Assertions.assertEquals(1024, fixture.extract().insertedAmount());
        Assertions.assertEquals(3072, fixture.unlockAmount());
        Mockito.verify(fixture.host).saveChanges();
        CompoundTag tag = new CompoundTag();
        fixture.provider.writeToNBT(tag, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        fixture.provider.readFromNBT(tag, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Assertions.assertEquals(3072, fixture.unlockAmount());
    }

    @Test
    void strictEmptySourceDoesNotProbeTheNetwork() {
        Fixture fixture = new Fixture();
        fixture.stock.set(0);
        ProductExtractor.Result result = fixture.extractStrict();
        Assertions.assertEquals(0, result.insertedAmount());
        Assertions.assertFalse(result.destinationBlocked());
        Assertions.assertEquals(0, fixture.networkSimulations.get());
        Assertions.assertEquals(0, fixture.networkInsertions.get());
        Assertions.assertEquals(0, fixture.extractions.get());
        Assertions.assertEquals(0, fixture.scopes.get());
    }

    @Test
    void strictReturnSimulatesOnlyAvailableResourcesAndStillReturns4096() {
        Fixture partial = new Fixture();
        partial.stock.set(3);
        Assertions.assertEquals(3, partial.extractStrict().insertedAmount());
        Assertions.assertEquals(3, partial.lastSimulatedAmount.get());
        Assertions.assertEquals(1, partial.networkInsertions.get());
        Fixture full = new Fixture();
        Assertions.assertEquals(4096, full.extractStrict().insertedAmount());
        Assertions.assertEquals(4096, full.lastSimulatedAmount.get());
        Assertions.assertEquals(1, full.extractions.get());
        Assertions.assertEquals(1, full.networkInsertions.get());
        Assertions.assertTrue(full.provider.getReturnInv().isEmpty());
    }

    @Test
    void strictSimulationDoesNotReplaceActualAcceptanceRecovery() {
        Fixture fixture = new Fixture();
        fixture.networkCapacity = 1000;
        fixture.restore = true;
        Assertions.assertEquals(1576, fixture.extractStrict().insertedAmount());
        Assertions.assertEquals(2520, fixture.stock.get());
        Assertions.assertEquals(4096, fixture.stock.get() + fixture.networkAmount.get() + fixture.bufferedAmount());
    }

    @Test
    void strictNativeOverstackedMachineStillReturns4096InOneNetworkInsertion() {
        Fixture fixture = new Fixture();
        ItemStackHandler machine = new ItemStackHandler(1);
        machine.setStackInSlot(0, new ItemStack(Items.GRAVEL, 4096));
        ExternalStorageFacade source = ExternalStorageFacade.of(machine);
        fixture.sourceDebited = () -> machine.getStackInSlot(0).isEmpty();
        ProductExtractor.Result result = ProductExtractor.extract(
                Map.of(AEKeyType.items(), source), fixture.receiver, 4096, Integer.MAX_VALUE,
                IActionSource.empty(), fixture.logic::allowsReturned, fixture.logic::retainExtracted,
                fixture.logic.exactReturnKeys(), new ProductExtractor.Cursor());
        Assertions.assertEquals(4096, result.insertedAmount());
        Assertions.assertTrue(machine.getStackInSlot(0).isEmpty());
        Assertions.assertEquals(4096, fixture.networkAmount.get());
        Assertions.assertEquals(1, fixture.networkInsertions.get());
        Assertions.assertEquals(0, fixture.bufferedAmount());
        Assertions.assertTrue(fixture.recovery.isEmpty());
    }

    private static final class Fixture {
        private final IManagedGridNode node = Mockito.mock(IManagedGridNode.class, Mockito.RETURNS_SELF);
        private final PatternProviderLogicHost host = Mockito.mock(PatternProviderLogicHost.class);
        private final PatternProviderLogic provider;
        private final ReceiveInventory receiver;
        private final Pp2mpOutputData task = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        private final Pp2mpOutputLogic logic;
        private final IActionSource receiverSource = IActionSource.empty();
        private final AtomicLong stock = new AtomicLong(4096);
        private final AtomicLong networkAmount = new AtomicLong();
        private final AtomicLong counted = new AtomicLong();
        private final AtomicInteger scopes = new AtomicInteger();
        private final AtomicInteger extractions = new AtomicInteger();
        private BooleanSupplier sourceDebited = () -> this.extractions.get() > 0;
        private final AtomicInteger networkInsertions = new AtomicInteger();
        private final AtomicInteger networkSimulations = new AtomicInteger();
        private final AtomicLong lastSimulatedAmount = new AtomicLong();
        private final Map<AEKey, Long> recovery = this.task.getExtractionRecovery();
        private final MEStorage source = new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter counter) {
                counter.add(PRODUCT, Fixture.this.stock.get());
            }

            @Override
            public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
                long extracted = Math.min(amount, Fixture.this.stock.get());
                if (mode == Actionable.MODULATE) {
                    Fixture.this.stock.addAndGet(-extracted);
                    Fixture.this.extractions.incrementAndGet();
                }
                return extracted;
            }

            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
                if (!Fixture.this.restore) {
                    return 0;
                }
                if (mode == Actionable.MODULATE) {
                    Fixture.this.stock.addAndGet(amount);
                }
                return amount;
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
        private long simulatedCapacity = 4096;
        private long networkCapacity = 4096;
        private boolean restore;
        private boolean checkReentry;
        private boolean failSimulation;
        private IActionSource networkSource;

        private Fixture() {
            IGrid grid = Mockito.mock(IGrid.class);
            IStorageService storage = Mockito.mock(IStorageService.class);
            Mockito.when(this.node.getGrid()).thenReturn(grid);
            Mockito.when(this.node.isActive()).thenReturn(true);
            Mockito.when(grid.getStorageService()).thenReturn(storage);
            this.provider = new PatternProviderLogic(this.node, this.host);
            Assertions.assertInstanceOf(BulkPatternProviderReturnInventory.class, this.provider.getReturnInv());
            Pp2mpOutputPart part = Mockito.mock(Pp2mpOutputPart.class);
            Mockito.when(part.getData()).thenReturn(this.task);
            Mockito.when(part.getMainNode()).thenReturn(this.node);
            Mockito.when(part.getHost()).thenReturn(Mockito.mock(IPartHost.class));
            Mockito.when(part.getSide()).thenReturn(Direction.NORTH);
            Mockito.when(part.getLevel()).thenReturn(Mockito.mock(ServerLevel.class));
            Mockito.when(part.validFrequency()).thenReturn(true);
            this.task.apply(InputConfig.builder(InputConfig.DEFAULT).returnMode(ReturnMode.STRICT_FULL).build());
            this.task.begin(Map.of(PRODUCT, 4096L), null, UUID.randomUUID(), null);
            this.logic = new Pp2mpOutputLogic(part, this.task);
            this.receiver = new ReceiveInventory(this.provider::getReturnInv, Map::of, this.logic::allowsReturned,
                    (key, amount) -> {
                        this.counted.addAndGet(amount);
                        this.logic.onReturnedInserted(key, amount);
                    }, this.receiverSource, () -> { }, () -> {
                        this.scopes.incrementAndGet();
                        this.logic.beginReturn();
                    }, () -> {
                        this.logic.endReturn();
                        this.scopes.decrementAndGet();
                    });
            Mockito.when(part.getReceiveInventory()).thenReturn(this.receiver);
            MEStorage network = Mockito.mock(MEStorage.class);
            Mockito.when(storage.getInventory()).thenReturn(network);
            Mockito.when(network.insert(Mockito.any(AEKey.class), Mockito.anyLong(),
                    Mockito.any(Actionable.class), Mockito.any(IActionSource.class))).thenAnswer(call -> {
                        AEKey key = call.getArgument(0);
                        long amount = call.getArgument(1);
                        Actionable mode = call.getArgument(2);
                        if (mode == Actionable.SIMULATE) {
                            this.networkSimulations.incrementAndGet();
                            this.lastSimulatedAmount.set(amount);
                        }
                        if (mode == Actionable.SIMULATE && this.failSimulation) {
                            throw new IllegalStateException("Test network unavailable");
                        }
                        this.networkSource = call.getArgument(3);
                        if (this.checkReentry) {
                            Assertions.assertFalse(this.provider.getReturnInv().canInsert());
                            BulkPatternProviderReturnInventory bulk = Assertions.assertInstanceOf(
                                    BulkPatternProviderReturnInventory.class, this.provider.getReturnInv());
                            Assertions.assertEquals(0, bulk.ae2bc$insertBulk(key, amount, mode));
                            Assertions.assertEquals(0, this.provider.getReturnInv().insert(0, key, amount, mode));
                        }
                        long capacity = mode == Actionable.SIMULATE ? this.simulatedCapacity : this.networkCapacity;
                        long inserted = Math.min(amount, Math.max(0, capacity - this.networkAmount.get()));
                        if (mode == Actionable.MODULATE) {
                            if (inserted > 0) {
                                Assertions.assertTrue(this.sourceDebited.getAsBoolean(), "来源实物必须先扣账");
                            }
                            this.networkAmount.addAndGet(inserted);
                            this.networkInsertions.incrementAndGet();
                        }
                        return inserted;
                    });
            this.awaitProduct(PRODUCT, 4096);
        }

        private void awaitProduct(AEKey key, long amount) {
            CompoundTag tag = new CompoundTag();
            tag.putByte(PatternProviderLogic.NBT_UNLOCK_EVENT, (byte) 2);
            tag.put(PatternProviderLogic.NBT_UNLOCK_STACK, GenericStack.writeTag(
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), new GenericStack(key, amount)));
            this.provider.readFromNBT(tag, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            Mockito.clearInvocations(this.host);
        }

        private ProductExtractor.Result extract() {
            return ProductExtractor.extract(Map.of(AEKeyType.items(), this.source), this.receiver, 4096,
                    Integer.MAX_VALUE, IActionSource.empty(), key -> true,
                    this.logic::retainExtracted);
        }

        private ProductExtractor.Result extractStrict() {
            return ProductExtractor.extract(Map.of(AEKeyType.items(), this.source), this.receiver, 4096,
                    Integer.MAX_VALUE, IActionSource.empty(), this.logic::allowsReturned,
                    this.logic::retainExtracted, this.logic.exactReturnKeys(), new ProductExtractor.Cursor());
        }

        private long bufferedAmount() {
            PatternProviderReturnInventory inventory = this.provider.getReturnInv();
            long amount = 0;
            for (int slot = 0; slot < inventory.size(); slot++) {
                amount += inventory.getAmount(slot);
            }
            return amount;
        }

        private long unlockAmount() {
            GenericStack stack = this.provider.getUnlockStack();
            return stack == null ? 0 : stack.amount();
        }
    }
}
