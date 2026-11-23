package org.ae2bc.part.task;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.parts.AEBasePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.pattern.MaterialOutputAttr;
import org.ae2bc.pattern.PatternTestSupport;
import org.ae2bc.pattern.Pp2mpPatternDetails;
import org.ae2bc.pattern.Pp2mpPatternItem;
import org.ae2bc.storage.ReceiveInventory;
import org.ae2bc.registry.BlockRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

class TaskConverterLookupTest {
    @Test
    void ordinaryVanillaAndPp2mpPatternsDoNotQueryConverters() {
        Fixture fixture = new Fixture();
        IPatternDetails vanilla = PatternTestSupport.processing(TaskConverterLookupTest.encoded());
        IPatternDetails pp2mp = TaskConverterLookupTest.pattern(MaterialOutputAttr.EMPTY);
        Assertions.assertNull(fixture.logic.findFakeTaskConverter(vanilla, fixture.taskId));
        Assertions.assertNull(fixture.logic.findFakeTaskConverter(pp2mp, fixture.taskId));
        Mockito.verifyNoInteractions(fixture.grid);
    }

    @Test
    void missingConverterIsNotRememberedAcrossTaskAttempts() {
        Fixture fixture = new Fixture();
        IPatternDetails pattern = TaskConverterLookupTest.pattern(
                MaterialOutputAttr.EMPTY.withMode(0, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
        Assertions.assertNull(fixture.logic.findFakeTaskConverter(pattern, fixture.taskId));
        FakeCraftingConverterBlockEntity converter = fixture.converter();
        Mockito.when(fixture.grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(converter));
        Assertions.assertSame(converter, fixture.logic.findFakeTaskConverter(pattern, fixture.taskId));
        Mockito.verify(fixture.grid, Mockito.times(2)).getMachines(FakeCraftingConverterBlockEntity.class);
    }

    @Test
    void resolvedConverterIsSharedByOutputAndInputPlanning() {
        Fixture fixture = new Fixture();
        IPatternDetails pattern = TaskConverterLookupTest.pattern(
                MaterialOutputAttr.EMPTY.withMode(0, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
        FakeCraftingConverterBlockEntity converter = fixture.converter();
        Mockito.when(fixture.grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(converter));
        GenericStack output = new GenericStack(TaskConverterLookupTest.ticket("output"), 1);
        KeyCounter[] inputs = {new KeyCounter()};
        inputs[0].add(TaskConverterLookupTest.ticket("input"), 1);
        inputs[0].add(AEItemKey.of(Items.STONE), 2);
        FakeCraftingConverterBlockEntity resolved = fixture.logic.findFakeTaskConverter(pattern, fixture.taskId);
        Assertions.assertEquals(Set.of(output.what()), Pp2mpTaskLogic.getFakeTaskOutputKeys(resolved, fixture.taskId));
        KeyCounter[] filtered = Pp2mpTaskLogic.filterFakeTaskInputs(resolved, inputs, fixture.taskId);
        Assertions.assertEquals(0, filtered[0].get(TaskConverterLookupTest.ticket("input")));
        Assertions.assertEquals(2, filtered[0].get(AEItemKey.of(Items.STONE)));
        Assertions.assertEquals(1, inputs[0].get(TaskConverterLookupTest.ticket("input")));
        Mockito.verify(fixture.grid).getMachines(FakeCraftingConverterBlockEntity.class);
    }

    private static ItemStack encoded() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(TaskConverterLookupTest.ticket("input"), 1),
                        new GenericStack(AEItemKey.of(Items.STONE), 2)),
                List.of(new GenericStack(TaskConverterLookupTest.ticket("output"), 1),
                        new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
    }

    private static AEItemKey ticket(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return Objects.requireNonNull(AEItemKey.of(stack));
    }

    private static IPatternDetails pattern(MaterialOutputAttr attr) {
        ItemStack stack = Pp2mpPatternItem.encodePattern(TaskConverterLookupTest.encoded(), attr);
        return new Pp2mpPatternDetails(Objects.requireNonNull(AEItemKey.of(stack)), null);
    }

    private static final class Fixture {
        private final UUID taskId = UUID.randomUUID();
        private final IGrid grid = Mockito.mock(IGrid.class);
        private final Pp2mpTaskLogic logic;

        private Fixture() {
            Supplier<IGrid> gridSupplier = () -> this.grid;
            ReceiveInventory inventory = new ReceiveInventory(() -> null, Map::of, key -> true,
                    (key, amount) -> { }, IActionSource.empty(), () -> { });
            Supplier<ReceiveInventory> inventorySupplier = () -> inventory;
            this.logic = Mockito.mock(Pp2mpTaskLogic.class, Mockito.withSettings()
                    .useConstructor(Mockito.mock(AEBasePart.class), gridSupplier, new Pp2mpTaskData() { },
                            inventorySupplier)
                    .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        }

        private FakeCraftingConverterBlockEntity converter() {
            FakeCraftingConverterBlockEntity converter = new FakeCraftingConverterBlockEntity(
                    BlockPos.ZERO, BlockRegistry.FAKE_CRAFTING_CONVERTER.get().defaultBlockState());
            converter.setMarker(0, TaskConverterLookupTest.ticket("input"));
            converter.setMarker(9, TaskConverterLookupTest.ticket("output"));
            Assertions.assertTrue(converter.acceptTaskInputs(this.taskId,
                    new FakeCraftingConverterBlockEntity.TicketPlan(new KeyCounter[0], Map.of(0, 1L), true)));
            return converter;
        }
    }
}
