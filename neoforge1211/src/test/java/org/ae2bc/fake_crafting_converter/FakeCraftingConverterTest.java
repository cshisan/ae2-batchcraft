package org.ae2bc.fake_crafting_converter;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.*;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.me.GridNode;
import appeng.me.ManagedGridNode;
import appeng.util.ConfigInventory;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.ae2bc.integration.ImmediatePatternProviderReturnInventory;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.part.task.Pp2mpTaskEndpoint;
import org.ae2bc.part.input.Pp2mpInputLogic;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.registry.BlockRegistry;
import org.ae2bc.registry.component.ComponentRegistry;
import org.ae2bc.pattern.MaterialOutputAttr;
import org.ae2bc.pattern.Pp2mpPatternDetails;
import org.ae2bc.pattern.Pp2mpPatternItem;
import org.ae2bc.pattern.Pp2mpTaskPlan;
import org.ae2bc.storage.ReceiveInventory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class FakeCraftingConverterTest {
    private static void setNode(FakeCraftingConverterBlockEntity converter, GridNode node) throws Exception {
        Field field = ManagedGridNode.class.getDeclaredField("node");
        field.setAccessible(true);
        field.set(converter.getMainNode(), node);
    }

    private static FakeCraftingConverterBlockEntity converter() {
        return new FakeCraftingConverterBlockEntity(BlockPos.ZERO,
                BlockRegistry.FAKE_CRAFTING_CONVERTER.get().defaultBlockState());
    }

    private static AEItemKey paper(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }

    private static void configure(FakeCraftingConverterBlockEntity converter, int row, AEItemKey input, AEItemKey output) {
        converter.setMarker(row, input);
        converter.setMarker(FakeCraftingConverterBlockEntity.COLUMN_COUNT + row, output);
    }

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static FakeCraftingConverterBlockEntity.TicketPlan plan(Map<Integer, Long> rows) {
        return new FakeCraftingConverterBlockEntity.TicketPlan(new KeyCounter[0], rows, true);
    }

    private static ReceiveInventory receiver(GenericStackInv destination) {
        return new ReceiveInventory(() -> destination, Map::of, key -> false,
                (key, amount) -> Assertions.fail("Ticket return must not count as another real product"),
                IActionSource.empty(), () -> {
        });
    }

    /* 两行都保存满槽票据，验证实际 NBT 和菜单物品编码不会截断数量。 */
    @Test
    void fullSlotsSurviveSaveAndMenuPacketRoundTrip() {
        FakeCraftingConverterBlockEntity original = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(original, 0, input, output);
        Assertions.assertEquals(4096, original.insertExternal(0, input, 5000, Actionable.MODULATE));
        UUID task = UUID.randomUUID();
        Assertions.assertTrue(original.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 4096L))));
        Assertions.assertFalse(original.acceptTaskInputs(UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(0, 1L))));
        Assertions.assertEquals(0, original.insertExternal(0, input, 1, Actionable.MODULATE));
        Assertions.assertEquals(4096, original.getGenericInventory().getCapacity(AEKeyType.items()));
        Assertions.assertEquals(4096, original.getGenericInventory().getMaxAmount(input));
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved = new CompoundTag();
        original.saveAdditional(saved, registries);
        FakeCraftingConverterBlockEntity restored = FakeCraftingConverterTest.converter();
        restored.loadTag(saved, registries);
        Assertions.assertEquals(4096, restored.getGenericInventory().getAmount(0));
        Assertions.assertEquals(4096, restored.getPendingOutputAmount(0));
        Assertions.assertEquals(original.getConverterId(), restored.getConverterId());
        Assertions.assertEquals(List.of(new GenericStack(output, 4096)), restored.getTaskOutputs(task));
        Assertions.assertTrue(restored.getStock().extractItem(9, 64, false).isEmpty());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, restored.getStock().getStackInSlot(9));
            ItemStack synchronizedStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            Assertions.assertEquals(0, buffer.readableBytes());
            Assertions.assertEquals(new GenericStack(output, 4096), GenericStack.unwrapItemStack(synchronizedStack));
        } finally {
            buffer.release();
        }
    }

    @Test
    void overstackedReturnsKeepRemainderAndDoNotAffectAnotherTask() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, FakeCraftingConverterTest.paper("input"), output);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Assertions.assertTrue(converter.acceptTaskInputs(first, FakeCraftingConverterTest.plan(Map.of(0, 2048L))));
        Assertions.assertTrue(converter.acceptTaskInputs(second, FakeCraftingConverterTest.plan(Map.of(0, 2048L))));
        ConfigInventory destination = ConfigInventory.storage(1).supportedType(AEKeyType.items())
                .allowOverstacking(true).build();
        destination.setCapacity(AEKeyType.items(), 100);
        ReceiveInventory receiver = FakeCraftingConverterTest.receiver(destination);
        Assertions.assertFalse(converter.returnTaskOutputs(first, receiver));
        Assertions.assertEquals(100, destination.getAmount(0));
        Assertions.assertEquals(3996, converter.getPendingOutputAmount(0));
        Assertions.assertEquals(List.of(new GenericStack(output, 1948)), converter.getTaskOutputs(first));
        Assertions.assertEquals(List.of(new GenericStack(output, 2048)), converter.getTaskOutputs(second));
        destination.setCapacity(AEKeyType.items(), 4096);
        Assertions.assertTrue(converter.returnTaskOutputs(first, receiver));
        Assertions.assertTrue(converter.returnTaskOutputs(first, receiver));
        Assertions.assertEquals(2048, destination.getAmount(0));
        Assertions.assertEquals(2048, converter.getPendingOutputAmount(0));
        Assertions.assertTrue(converter.returnTaskOutputs(second, receiver));
        Assertions.assertEquals(4096, destination.getAmount(0));
        Assertions.assertTrue(converter.getStock().isEmpty());
    }

    @Test
    void nonStackableTicketsUseFullCapacityButExtractAsNormalItems() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        ItemStack ticket = new ItemStack(Items.DIAMOND_PICKAXE);
        ticket.set(DataComponents.CUSTOM_NAME, Component.literal("input"));
        AEItemKey input = AEItemKey.of(ticket);
        ticket.set(DataComponents.CUSTOM_NAME, Component.literal("output"));
        AEItemKey output = AEItemKey.of(ticket);
        FakeCraftingConverterTest.configure(converter, 0, input, output);
        Assertions.assertTrue(converter.acceptTaskInputs(UUID.randomUUID(),
                FakeCraftingConverterTest.plan(Map.of(0, 4096L))));
        Assertions.assertEquals(4096, converter.insertExternal(0, output, 4097, Actionable.MODULATE));
        Assertions.assertEquals(1, converter.getStock().extractItem(0, 4096, false).getCount());
        Assertions.assertEquals(4095, converter.getGenericInventory().getAmount(0));
        Assertions.assertEquals(4095, converter.getGenericInventory().extract(0, input, 4096, Actionable.MODULATE));
        Assertions.assertEquals(4096, converter.getPendingOutputAmount(0));
    }

    @Test
    void breakingFullConverterDropsAllStockInNormalStacks() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey paper = FakeCraftingConverterTest.paper("ticket");
        for (int slot = 0; slot < FakeCraftingConverterBlockEntity.STOCK_SLOT_COUNT; slot++) {
            converter.getStock().setItemDirect(slot, GenericStack.wrapInItemStack(paper, 4096));
        }
        List<ItemStack> drops = new ArrayList<>();
        converter.addAdditionalDrops(Mockito.mock(Level.class), BlockPos.ZERO, drops);
        Assertions.assertEquals(18 * 4096, drops.stream().mapToInt(ItemStack::getCount).sum());
        Assertions.assertTrue(drops.stream().allMatch(stack -> paper.matches(stack)
                && stack.getCount() <= stack.getMaxStackSize() && !GenericStack.isWrapped(stack)));
        converter.clearContent();
        Assertions.assertTrue(converter.getStock().isEmpty());
    }

    @Test
    void menuMarksFirstThenInsertsAndExtractsNormalStacksFromLargeStock() {
        MenuFixture fixture = new MenuFixture();
        fixture.menu.setCarried(fixture.input.toStack(64));
        fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
        Assertions.assertEquals(64, fixture.menu.getCarried().getCount());
        Assertions.assertEquals(0, fixture.converter.getGenericInventory().getAmount(0));
        fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
        Assertions.assertTrue(fixture.menu.getCarried().isEmpty());
        Assertions.assertEquals(64, fixture.converter.getGenericInventory().getAmount(0));
        fixture.converter.insertExternal(0, fixture.input, 4032, Actionable.MODULATE);
        fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
        Assertions.assertEquals(fixture.input, AEItemKey.of(fixture.menu.getCarried()));
        Assertions.assertEquals(64, fixture.menu.getCarried().getCount());
        Assertions.assertEquals(4032, fixture.converter.getGenericInventory().getAmount(0));
        fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
        Assertions.assertTrue(fixture.menu.getCarried().isEmpty());
        Assertions.assertEquals(4096, fixture.converter.getGenericInventory().getAmount(0));
        fixture.menu.clicked(0, 1, ClickType.PICKUP, fixture.player);
        Assertions.assertNull(fixture.converter.getMarkers().getKey(0));
        Assertions.assertEquals(4096, fixture.converter.getGenericInventory().getAmount(0));
        fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
        Assertions.assertEquals(64, fixture.menu.getCarried().getCount());
    }

    @Test
    void hotbarAndQuickMoveNeverTakeWrapperItemsOrReservedStock() {
        MenuFixture fixture = new MenuFixture();
        FakeCraftingConverterTest.configure(fixture.converter, 0, fixture.input, fixture.output);
        fixture.converter.insertExternal(0, fixture.input, 4096, Actionable.MODULATE);
        fixture.menu.clicked(0, 0, ClickType.SWAP, fixture.player);
        Assertions.assertEquals(fixture.input, AEItemKey.of(fixture.player.getInventory().getItem(0)));
        Assertions.assertEquals(64, fixture.player.getInventory().getItem(0).getCount());
        fixture.menu.clicked(0, 0, ClickType.SWAP, fixture.player);
        Assertions.assertTrue(fixture.player.getInventory().getItem(0).isEmpty());
        Assertions.assertEquals(4096, fixture.converter.getGenericInventory().getAmount(0));
        fixture.menu.quickMoveStack(fixture.player, 0);
        Assertions.assertEquals(4032, fixture.converter.getGenericInventory().getAmount(0));
        Assertions.assertEquals(64, fixture.player.getInventory().countItem(Items.PAPER));
        fixture.converter.acceptTaskInputs(UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(0, 4096L)));
        fixture.menu.clicked(9, 0, ClickType.PICKUP, fixture.player);
        fixture.menu.clicked(9, 0, ClickType.SWAP, fixture.player);
        fixture.menu.quickMoveStack(fixture.player, 9);
        Assertions.assertTrue(fixture.menu.getCarried().isEmpty());
        Assertions.assertEquals(4096, fixture.converter.getPendingOutputAmount(0));
        Assertions.assertEquals(64, fixture.player.getInventory().countItem(Items.PAPER));
    }

    @Test
    void menuRejectsDuplicateClickAndJeiMarkersWithoutConsumingCarriedItems() {
        MenuFixture fixture = new MenuFixture();
        FakeCraftingConverterTest.configure(fixture.converter, 0, fixture.input, fixture.output);
        fixture.menu.setCarried(fixture.input.toStack(64));
        fixture.menu.clicked(1, 0, ClickType.PICKUP, fixture.player);
        fixture.menu.setFilter(1, fixture.input.toStack());
        Assertions.assertNull(fixture.converter.getMarkers().getKey(1));
        Assertions.assertEquals(64, fixture.menu.getCarried().getCount());
        fixture.menu.setFilter(1, FakeCraftingConverterTest.paper("other-input").toStack());
        fixture.menu.setCarried(fixture.output.toStack(64));
        fixture.menu.clicked(10, 0, ClickType.PICKUP, fixture.player);
        fixture.menu.setFilter(10, fixture.output.toStack());
        Assertions.assertNull(fixture.converter.getMarkers().getKey(10));
        Assertions.assertEquals(64, fixture.menu.getCarried().getCount());
        Assertions.assertEquals(fixture.output, AEItemKey.of(fixture.menu.getCarried()));
        Assertions.assertTrue(fixture.converter.getStock().isEmpty());
    }

    @Test
    void mergedConvertersDisableOnlyTheirFrequencyAndRecoverAfterRemoval() {
        FakeCraftingConverterBlockEntity first = FakeCraftingConverterTest.converter();
        FakeCraftingConverterBlockEntity second = new FakeCraftingConverterBlockEntity(new BlockPos(1, 0, 0),
                BlockRegistry.FAKE_CRAFTING_CONVERTER.get().defaultBlockState());
        FakeCraftingConverterBlockEntity unbound = FakeCraftingConverterTest.converter();
        FakeCraftingConverterBlockEntity other = FakeCraftingConverterTest.converter();
        first.setFrequency((short) 42);
        second.setFrequency((short) 42);
        other.setFrequency((short) 99);
        IGrid grid = Mockito.mock(IGrid.class);
        Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class))
                .thenReturn(Set.of(second, first, unbound, other));
        Pp2mpGridService service = new Pp2mpGridService(grid);
        Assertions.assertNull(service.findFakeCraftingConverter((short) 42));
        Assertions.assertTrue(service.isFakeCraftingFrequencyUsed((short) 42, first));
        Assertions.assertTrue(service.isFakeCraftingFrequencyUsed((short) 42, second));
        Assertions.assertSame(other, service.findFakeCraftingConverter((short) 99));
        Assertions.assertNull(service.findFakeCraftingConverter((short) 0));
        Assertions.assertFalse(service.isFakeCraftingFrequencyUsed((short) 0, unbound));

        Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(first, other, unbound));
        service.removeNode(Mockito.mock(IGridNode.class));
        Assertions.assertSame(first, service.findFakeCraftingConverter((short) 42));
        Assertions.assertFalse(service.isFakeCraftingFrequencyUsed((short) 42, first));
        Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class))
                .thenReturn(Set.of(first, second, other, unbound));
        service.addNode(Mockito.mock(IGridNode.class), null);
        Assertions.assertNull(service.findFakeCraftingConverter((short) 42));
    }

    @Test
    void dispatchRejectsFullMismatchedAndInactiveConverterWithoutUsingAnotherFrequency() throws Exception {
        for (int reason = 0; reason < 3; reason++) {
            RoutingFixture fixture = new RoutingFixture();
            if (reason == 0) {
                fixture.first.acceptTaskInputs(UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(0, 4096L)));
            } else if (reason == 1) {
                FakeCraftingConverterTest.configure(fixture.first, 0,
                        FakeCraftingConverterTest.paper("other-input"), FakeCraftingConverterTest.paper("other-output"));
            } else {
                GridNode inactive = Mockito.mock(GridNode.class);
                FakeCraftingConverterTest.setNode(fixture.first, inactive);
            }
            Assertions.assertFalse(fixture.dispatch(1));
            Assertions.assertFalse(fixture.first.hasTaskTickets(fixture.taskId));
            Assertions.assertTrue(fixture.second.getStock().isEmpty());
            Mockito.verify(fixture.endpoint, Mockito.never()).tryAcceptTask(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    @Test
    void wholeBatchAboveSlotCapacityIsRejectedWithoutPartialTickets() throws Exception {
        RoutingFixture fixture = new RoutingFixture();
        Assertions.assertFalse(fixture.dispatch(4097));
        Assertions.assertTrue(fixture.first.getStock().isEmpty());
        Assertions.assertTrue(fixture.second.getStock().isEmpty());
        Mockito.verify(fixture.endpoint, Mockito.never()).tryAcceptTask(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void endpointRejectionRollsBackOnlyNewTickets() throws Exception {
        RoutingFixture fixture = new RoutingFixture();
        fixture.accept = false;
        fixture.first.insertExternal(0, fixture.input, 3, Actionable.MODULATE);
        Assertions.assertFalse(fixture.dispatch(1000));
        Assertions.assertEquals(3, fixture.first.getGenericInventory().getAmount(0));
        Assertions.assertEquals(0, fixture.first.getPendingOutputAmount(0));
        Assertions.assertFalse(fixture.first.hasTaskTickets(fixture.taskId));
        Assertions.assertTrue(fixture.second.getStock().isEmpty());
    }

    @Test
    void unavailableEndpointDoesNotLookUpOrCreateNewTickets() throws Exception {
        for (boolean operational : new boolean[]{false, true}) {
            RoutingFixture fixture = new RoutingFixture();
            Mockito.when(fixture.endpoint.isOperational()).thenReturn(operational);
            Mockito.when(fixture.endpoint.canAcceptTask()).thenReturn(!operational);
            Mockito.clearInvocations(fixture.service);

            Assertions.assertFalse(fixture.dispatch(1000));
            Mockito.verify(fixture.service, Mockito.never()).findFakeCraftingConverter(Mockito.anyShort());
            Mockito.verify(fixture.endpoint, Mockito.never()).tryAcceptTask(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
            Assertions.assertEquals(0, fixture.first.getPendingOutputAmount(0));
            Assertions.assertTrue(fixture.first.getStock().isEmpty());
            Assertions.assertTrue(fixture.second.getStock().isEmpty());
        }
    }

    @Test
    void wholeTaskSkipsUnavailableEndpointsBeforeCreatingTickets() throws Exception {
        RoutingFixture fixture = new RoutingFixture();
        List<Pp2mpTaskEndpoint> endpoints = new ArrayList<>();
        for (boolean operational : new boolean[]{true, false}) {
            Pp2mpTaskEndpoint endpoint = Mockito.mock(Pp2mpTaskEndpoint.class);
            Mockito.when(endpoint.isOperational()).thenReturn(operational);
            Mockito.when(endpoint.canAcceptTask()).thenReturn(!operational);
            endpoints.add(endpoint);
        }
        endpoints.add(fixture.endpoint);
        Mockito.doReturn(endpoints).when(fixture.service).getEndpoints((short) 42);
        Mockito.clearInvocations(fixture.service);

        Assertions.assertTrue(fixture.dispatch(1000));
        Mockito.verify(fixture.service).findFakeCraftingConverter((short) 42);
        for (Pp2mpTaskEndpoint endpoint : endpoints.subList(0, 2)) {
            Mockito.verify(endpoint).isOperational();
            Mockito.verify(endpoint, Mockito.never()).tryAcceptTask(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
        Assertions.assertEquals(1000, fixture.first.getPendingOutputAmount(0));
        Assertions.assertTrue(fixture.first.hasTaskTickets(fixture.taskId));
        Assertions.assertTrue(fixture.second.getStock().isEmpty());
    }

    @Test
    void dispatchRetryKeepsOriginalConverterAfterFrequencyChanges() throws Exception {
        RoutingFixture fixture = new RoutingFixture();
        Assertions.assertTrue(fixture.dispatch(1000));
        UUID taskId = fixture.taskId;
        Assertions.assertFalse(fixture.first.setFrequency((short) 99));
        Assertions.assertEquals((short) 42, fixture.first.getFrequency());
        Assertions.assertTrue(fixture.first.setFrequency((short) 100));
        Assertions.assertTrue(fixture.second.setFrequency((short) 42));
        Mockito.when(fixture.endpoint.isOperational()).thenReturn(false);
        Mockito.when(fixture.endpoint.canAcceptTask()).thenReturn(false);
        Mockito.clearInvocations(fixture.endpoint);
        Method dispatch = Pp2mpInputLogic.class.getDeclaredMethod("tryAcceptTaskWithTickets",
                Pp2mpTaskEndpoint.class, IPatternDetails.class, KeyCounter[].class, UUID.class);
        dispatch.setAccessible(true);
        Assertions.assertEquals(true, dispatch.invoke(fixture.logic, fixture.endpoint, fixture.pattern, fixture.inputs, taskId));
        Mockito.verify(fixture.endpoint, Mockito.never()).isOperational();
        Mockito.verify(fixture.endpoint, Mockito.never()).canAcceptTask();
        Assertions.assertEquals(1000, fixture.first.getPendingOutputAmount(0));
        Assertions.assertFalse(fixture.second.hasTaskTickets(taskId));
        Assertions.assertTrue(fixture.second.getStock().isEmpty());
    }

    @Test
    void frequencyBindingRejectsOccupiedFrequencyEvenWhenItsOwnerIsInactive() throws Exception {
        RoutingFixture fixture = new RoutingFixture();
        Assertions.assertFalse(fixture.second.setFrequency((short) 42));
        Assertions.assertEquals((short) 99, fixture.second.getFrequency());
        GridNode inactive = Mockito.mock(GridNode.class);
        Mockito.when(inactive.getGrid()).thenReturn(fixture.grid);
        FakeCraftingConverterTest.setNode(fixture.first, inactive);
        Assertions.assertFalse(fixture.second.setFrequency((short) 42));
        Assertions.assertTrue(fixture.first.setFrequency((short) 42));
        Assertions.assertTrue(fixture.first.setFrequency((short) 0));
        Assertions.assertTrue(fixture.second.setFrequency((short) 42));
        Assertions.assertSame(fixture.second, fixture.service.findFakeCraftingConverter((short) 42));
    }

    /* 合网只阻止新任务，原归属票据可继续结算，改频后自动恢复派发。 */
    @Test
    void mergedNetworksPreserveOutstandingTicketsAndResumeAfterFrequencyChange() throws Exception {
        RoutingFixture fixture = new RoutingFixture();
        Assertions.assertTrue(fixture.dispatch(2));
        UUID firstTask = fixture.taskId;
        IGrid isolated = Mockito.mock(IGrid.class);
        Pp2mpGridService isolatedService = new Pp2mpGridService(isolated);
        Mockito.when(isolated.getService(Pp2mpGridService.class)).thenReturn(isolatedService);
        Mockito.when(isolated.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(fixture.second));
        GridNode secondNode = Mockito.mock(GridNode.class);
        Mockito.when(secondNode.isActive()).thenReturn(true);
        Mockito.when(secondNode.getGrid()).thenReturn(isolated);
        FakeCraftingConverterTest.setNode(fixture.second, secondNode);
        Mockito.when(fixture.grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(fixture.first));
        fixture.service.removeNode(secondNode);
        Assertions.assertTrue(fixture.second.setFrequency((short) 42));
        Assertions.assertSame(fixture.first, fixture.service.findFakeCraftingConverter((short) 42));
        Assertions.assertSame(fixture.second, isolatedService.findFakeCraftingConverter((short) 42));
        UUID secondTask = UUID.randomUUID();
        Assertions.assertTrue(fixture.second.acceptTaskInputs(secondTask, FakeCraftingConverterTest.plan(Map.of(0, 3L))));

        Mockito.when(secondNode.getGrid()).thenReturn(fixture.grid);
        Mockito.when(fixture.grid.getMachines(FakeCraftingConverterBlockEntity.class))
                .thenReturn(Set.of(fixture.first, fixture.second));
        fixture.service.addNode(secondNode, null);
        Assertions.assertTrue(fixture.first.hasFrequencyConflict());
        Assertions.assertTrue(fixture.second.hasFrequencyConflict());
        Assertions.assertFalse(fixture.dispatch(1));
        Assertions.assertFalse(fixture.second.setFrequency((short) 42));
        Assertions.assertFalse(fixture.first.acceptTaskInputs(
                UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(0, 1L))));
        Assertions.assertSame(fixture.first, FakeCraftingConverterBlockEntity.findTaskOwner(fixture.grid, firstTask));
        Assertions.assertSame(fixture.second, FakeCraftingConverterBlockEntity.findTaskOwner(fixture.grid, secondTask));

        LimitedInventory destination = new LimitedInventory();
        destination.limits.put(fixture.output, 4L);
        ReceiveInventory receiver = FakeCraftingConverterTest.receiver(destination);
        Assertions.assertTrue(fixture.first.returnTaskOutputs(firstTask, receiver));
        Assertions.assertFalse(fixture.second.returnTaskOutputs(secondTask, receiver));
        Assertions.assertEquals(4, destination.count(fixture.output));
        Assertions.assertEquals(1, fixture.second.getPendingOutputAmount(0));
        destination.limits.put(fixture.output, 5L);
        Assertions.assertTrue(fixture.second.returnTaskOutputs(secondTask, receiver));
        Assertions.assertTrue(fixture.first.returnTaskOutputs(firstTask, receiver));
        Assertions.assertEquals(5, destination.count(fixture.output));
        Assertions.assertTrue(fixture.first.getStock().isEmpty());
        Assertions.assertTrue(fixture.second.getStock().isEmpty());

        Assertions.assertTrue(fixture.second.setFrequency((short) 99));
        Assertions.assertFalse(fixture.first.hasFrequencyConflict());
        Assertions.assertFalse(fixture.second.hasFrequencyConflict());
        Assertions.assertTrue(fixture.dispatch(1));
        Assertions.assertEquals(1, fixture.first.getPendingOutputAmount(0));
        Assertions.assertTrue(fixture.second.getStock().isEmpty());
    }

    /* 整单与拆批均按本次样板的票据数量规划，不能再除以原批次数。 */
    @Test
    void ticketPlanUsesTheFullDeliveredPatternForBatchAndUnsplitTasks() {
        for (boolean batch : new boolean[]{false, true}) {
            FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
            AEItemKey input = FakeCraftingConverterTest.paper("input");
            AEItemKey output = FakeCraftingConverterTest.paper("output");
            int usedRow = FakeCraftingConverterBlockEntity.COLUMN_COUNT - 1;
            for (int row = 0; row < usedRow; row++) {
                FakeCraftingConverterTest.configure(converter, row,
                        FakeCraftingConverterTest.paper("unused-input-" + row),
                        FakeCraftingConverterTest.paper("unused-output-" + row));
            }
            FakeCraftingConverterTest.configure(converter, usedRow, input, output);
            converter.setLevel(Mockito.mock(Level.class));
            AEKey material = AEItemKey.of(Items.STONE);
            ItemStack encoded = Pp2mpPatternItem.encodePattern(
                    PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(material, 100), new GenericStack(input, 20)),
                            List.of(new GenericStack(output, 20))),
                    MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
            encoded.set(ComponentRegistry.REAL_OUTPUT_MARKER, new GenericStack(AEItemKey.of(Items.DIAMOND), 100));
            encoded.set(ComponentRegistry.PATTERN_BATCH_COUNT, 20L);
            IPatternDetails pattern = new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
            KeyCounter[] inputs = {
                    FakeCraftingConverterTest.counter(material, 100),
                    FakeCraftingConverterTest.counter(input, 20)
            };
            if (batch) {
                Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(pattern, inputs, null);
                pattern = Pp2mpTaskPlan.restore(plan.pattern(), null);
                inputs = new Pp2mpTaskPlan.InputTemplate(pattern).offer(plan.inputs());
            }
            FakeCraftingConverterBlockEntity.TicketPlan plan = converter.planTask(pattern, inputs);
            Assertions.assertTrue(plan.valid());
            Assertions.assertEquals(Map.of(usedRow, batch ? 1L : 20L), plan.rows());
            Assertions.assertEquals(Pp2mpTaskPlan.amounts(inputs),
                    Pp2mpTaskPlan.amounts(plan.forwardedInputs()));
            Assertions.assertTrue(converter.getStock().isEmpty());
            // 规划只读材料，重新规划也不能复制或消耗票据。
            Assertions.assertEquals(plan.rows(), converter.planTask(pattern, inputs).rows());
        }
    }

    /* 同物品不同名称的副产物不能替代第一槽假主产物。 */
    @Test
    void converterRejectsWrongNamedOutputEvenIfItIsDeclaredAsAByproduct() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey expected = FakeCraftingConverterTest.paper("fake-A");
        AEItemKey other = FakeCraftingConverterTest.paper("fake-A1");
        AEItemKey material = AEItemKey.of(Items.STONE);
        GenericStack realProduct = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        FakeCraftingConverterTest.configure(converter, 0, input, other);
        converter.setLevel(Mockito.mock(Level.class));
        for (boolean byproduct : new boolean[]{false, true}) {
            List<GenericStack> outputs = byproduct
                    ? List.of(new GenericStack(expected, 1), new GenericStack(other, 1))
                    : List.of(new GenericStack(expected, 1));
            ItemStack encoded = Pp2mpPatternItem.encodePattern(
                    PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(material, 1), new GenericStack(input, 1)), outputs),
                    MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
            encoded.set(ComponentRegistry.REAL_OUTPUT_MARKER, realProduct);
            IPatternDetails pattern = new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
            KeyCounter[] inputs = {
                    FakeCraftingConverterTest.counter(material, 1), FakeCraftingConverterTest.counter(input, 1)
            };
            FakeCraftingConverterBlockEntity.TicketPlan plan = converter.planTask(pattern, inputs);
            Assertions.assertFalse(plan.valid());
            Assertions.assertFalse(converter.acceptTaskInputs(UUID.randomUUID(), plan));
            Assertions.assertEquals(1, inputs[1].get(input));
            Assertions.assertTrue(converter.getStock().isEmpty());
        }
    }

    /* 高频率位、活动状态、标记与票据数量必须按同一顺序完整往返。 */
    @Test
    void frequencyAndActivityRoundTripWithoutChangingTicketSynchronization() throws Exception {
        FakeCraftingConverterBlockEntity server = FakeCraftingConverterTest.converter();
        FakeCraftingConverterBlockEntity client = FakeCraftingConverterTest.converter();
        GridNode node = Mockito.mock(GridNode.class);
        FakeCraftingConverterTest.setNode(server, node);
        Mockito.when(node.isActive()).thenReturn(true);
        server.setFrequency((short) 0xabcd);
        FakeCraftingConverterTest.configure(server, 0,
                FakeCraftingConverterTest.paper("input"), FakeCraftingConverterTest.paper("output"));
        server.acceptTaskInputs(UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(0, 3L)));
        RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), ConnectionType.NEOFORGE);
        try {
            server.writeToStream(data);
            Assertions.assertTrue(client.readFromStream(data));
            Assertions.assertEquals(0, data.readableBytes());
            Assertions.assertEquals((short) 0xabcd, client.getModelData().get(FakeCraftingConverterBlockEntity.FREQUENCY_PROPERTY));
            Assertions.assertEquals(Boolean.TRUE, client.getModelData().get(FakeCraftingConverterBlockEntity.ACTIVE_PROPERTY));
            Assertions.assertFalse(client.getMainNode().isActive(), "The client must not consult its local grid");
            Assertions.assertEquals(server.getMarkers().getKey(0), client.getMarkers().getKey(0));
            Assertions.assertEquals(server.getMarkers().getKey(9), client.getMarkers().getKey(9));
            Assertions.assertEquals(3, client.getPendingOutputAmount(0));
            data.readerIndex(0);
            Assertions.assertFalse(client.readFromStream(data), "An identical snapshot must not rebuild the model");
            data.clear();
            Mockito.when(node.isActive()).thenReturn(false);
            server.writeToStream(data);
            Assertions.assertTrue(client.readFromStream(data));
            Assertions.assertEquals(Boolean.FALSE, client.getModelData().get(FakeCraftingConverterBlockEntity.ACTIVE_PROPERTY));
            Assertions.assertEquals((short) 0xabcd, client.getFrequency());
            Assertions.assertEquals(0, data.readableBytes());
            data.clear();
            server.setFrequency((short) 0);
            server.writeToStream(data);
            Assertions.assertTrue(client.readFromStream(data));
            Assertions.assertEquals((short) 0, client.getFrequency());
            Assertions.assertEquals(3, client.getPendingOutputAmount(0));
            Assertions.assertEquals(0, data.readableBytes());
            // 未绑定频率时，通电和断电同样必须独立传递给客户端。
            for (boolean active : new boolean[]{true, false}) {
                data.clear();
                Mockito.when(node.isActive()).thenReturn(active);
                server.writeToStream(data);
                Assertions.assertTrue(client.readFromStream(data));
                Assertions.assertEquals((short) 0, client.getFrequency());
                Assertions.assertEquals(Boolean.valueOf(active),
                        client.getModelData().get(FakeCraftingConverterBlockEntity.ACTIVE_PROPERTY));
                Assertions.assertEquals(3, client.getPendingOutputAmount(0));
                Assertions.assertEquals(0, data.readableBytes());
            }
        } finally {
            data.release();
        }
    }

    /* 重进存档后，初始区块更新必须携带频率，无需打开 GUI。 */
    @Test
    void savedFrequencyReachesTheClientThroughInitialChunkUpdate() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        FakeCraftingConverterBlockEntity original = FakeCraftingConverterTest.converter();
        original.setFrequency((short) 0xfedc);
        CompoundTag saved = new CompoundTag();
        original.saveAdditional(saved, registries);
        FakeCraftingConverterBlockEntity restored = FakeCraftingConverterTest.converter();
        restored.loadTag(saved, registries);
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.registryAccess()).thenReturn(registries);
        restored.setLevel(level);
        FakeCraftingConverterBlockEntity client = FakeCraftingConverterTest.converter();
        client.loadAdditional(restored.getUpdateTag(registries), registries);
        Assertions.assertEquals((short) 0xfedc, client.getModelData().get(FakeCraftingConverterBlockEntity.FREQUENCY_PROPERTY));
        Assertions.assertEquals(Boolean.FALSE, client.getModelData().get(FakeCraftingConverterBlockEntity.ACTIVE_PROPERTY));
    }

    @Test
    void frequencyAndNetworkChangesNotifyWatchingClients() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        // 保留世界读取接口的默认方法，由区块坐标查询决定当前位置是否已加载。
        Level level = Mockito.mock(Level.class, invocation -> invocation.getMethod().isDefault()
                ? invocation.callRealMethod() : Mockito.RETURNS_DEFAULTS.answer(invocation));
        Mockito.doReturn(true).when(level).hasChunk(0, 0);
        converter.setLevel(level);
        converter.setFrequency((short) 42);
        converter.setFrequency((short) 42);
        Mockito.verify(level).sendBlockUpdated(BlockPos.ZERO, converter.getBlockState(),
                converter.getBlockState(), Block.UPDATE_CLIENTS);
        Mockito.clearInvocations(level);
        converter.onMainNodeStateChanged(IGridNodeListener.State.POWER);
        Mockito.verify(level).sendBlockUpdated(BlockPos.ZERO, converter.getBlockState(),
                converter.getBlockState(), Block.UPDATE_CLIENTS);
    }

    /* 通过已注册能力查找节点宿主，验证后放置的线缆可以发现转换器。 */
    @Test
    void converterExposesItsGridHostAndAllSixCableSides() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        Level level = Mockito.mock(Level.class);
        // 已提供状态与实体，注册的能力应直接返回实体，不需要进一步查询世界。
        Assertions.assertSame(converter, AECapabilities.IN_WORLD_GRID_NODE_HOST.getCapability(
                level, converter.getBlockPos(), converter.getBlockState(), converter, null));
        Mockito.verifyNoInteractions(level);
        Assertions.assertEquals(EnumSet.allOf(Direction.class),
                converter.getGridConnectableSides(converter.getOrientation()));
    }

    @Test
    void completeRecycleCycleConservesOneTicketAndDoesNotConsumeSpareStock() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, input, output);
        converter.insertExternal(0, input, 2, Actionable.MODULATE);
        Assertions.assertEquals(1, converter.getStorage().extract(input, 1, Actionable.MODULATE, IActionSource.empty()));
        UUID task = UUID.randomUUID();
        Assertions.assertTrue(converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L))));
        Assertions.assertEquals(1, converter.getStock().getStackInSlot(0).getCount());

        RecyclingInventory destination = new RecyclingInventory();
        ReceiveInventory receiver = FakeCraftingConverterTest.receiver(destination);
        AtomicInteger flushes = new AtomicInteger();
        destination.ae2bc$setImmediateFlushHandler(() -> {
            flushes.incrementAndGet();
            Assertions.assertEquals(0, converter.getStock().getStackInSlot(9).getCount());
            Assertions.assertEquals(0, converter.getPendingOutputAmount(0));
            Assertions.assertFalse(converter.hasTaskTickets(task));
            Assertions.assertTrue(converter.returnTaskOutputs(task, receiver));
            for (int slot = 0; slot < destination.size(); slot++) {
                GenericStack stored = destination.getStack(slot);
                if (stored != null) {
                    long recycled = converter.getStorage().insert(stored.what(), stored.amount(),
                            Actionable.MODULATE, IActionSource.empty());
                    destination.extract(slot, stored.what(), recycled, Actionable.MODULATE);
                }
            }
        });
        Assertions.assertTrue(converter.returnTaskOutputs(task, receiver));
        Assertions.assertEquals(1, flushes.get());
        Assertions.assertEquals(2, converter.getStock().getStackInSlot(0).getCount());
        Assertions.assertTrue(converter.returnTaskOutputs(task, receiver));
        Assertions.assertEquals(2, converter.getStock().getStackInSlot(0).getCount());
    }

    @Test
    void missingOutputMarkerRejectsAllColumnsWithoutCreatingTickets() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        FakeCraftingConverterTest.configure(converter, 0,
                FakeCraftingConverterTest.paper("input"), FakeCraftingConverterTest.paper("output"));
        converter.setMarker(1, FakeCraftingConverterTest.paper("unpaired-input"));
        UUID task = UUID.randomUUID();

        Assertions.assertFalse(converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L, 1, 1L))));
        Assertions.assertFalse(converter.hasTaskTickets(task));
        Assertions.assertEquals(0, converter.getPendingOutputAmount(0));
        Assertions.assertEquals(0, converter.getPendingOutputAmount(1));
        Assertions.assertTrue(converter.getStock().getStackInSlot(9).isEmpty());
        Assertions.assertTrue(converter.getStock().getStackInSlot(10).isEmpty());
    }

    @Test
    void unavailableOutputMarkerRetainsStockAndLedgerUntilItCanReturn() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, FakeCraftingConverterTest.paper("input"), output);
        UUID task = UUID.randomUUID();
        Assertions.assertTrue(converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L))));
        // 模拟存档中标记缺失；正常 GUI 在任务期间不允许清除标记。
        converter.getMarkers().setStack(9, null);
        RecyclingInventory destination = new RecyclingInventory();
        ReceiveInventory receiver = FakeCraftingConverterTest.receiver(destination);

        Assertions.assertFalse(converter.returnTaskOutputs(task, receiver));
        Assertions.assertTrue(converter.hasTaskTickets(task));
        Assertions.assertEquals(1, converter.getPendingOutputAmount(0));
        Assertions.assertEquals(1, converter.getStock().getStackInSlot(9).getCount());
        Assertions.assertNull(destination.getStack(0));

        converter.getMarkers().setStack(9, new GenericStack(output, 0));
        Assertions.assertTrue(converter.returnTaskOutputs(task, receiver));
        Assertions.assertEquals(new GenericStack(output, 1), destination.getStack(0));
        Assertions.assertTrue(converter.getStock().getStackInSlot(9).isEmpty());
        Assertions.assertEquals(0, converter.getPendingOutputAmount(0));
    }

    @Test
    void externalInsertionNeverConvertsAnExistingTaskTicket() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, input, output);
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L)));
        Assertions.assertEquals(3, converter.getGenericInventory().insert(0, output, 3, Actionable.MODULATE));
        Assertions.assertEquals(input, AEItemKey.of(converter.getStock().getStackInSlot(0)));
        Assertions.assertEquals(3, converter.getStock().getStackInSlot(0).getCount());
        Assertions.assertEquals(output, AEItemKey.of(converter.getStock().getStackInSlot(9)));
        Assertions.assertEquals(1, converter.getStock().getStackInSlot(9).getCount());
        Assertions.assertEquals(1, converter.getPendingOutputAmount(0));
    }

    @Test
    void simulationAndPartialInsertionLeaveCallerStackUnchanged() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, input, output);
        converter.insertExternal(0, input, 4095, Actionable.MODULATE);
        ItemStack offered = output.toStack(8);
        Assertions.assertEquals(1, converter.insertExternal(0, AEItemKey.of(offered), 8, Actionable.SIMULATE));
        Assertions.assertEquals(4095, converter.getGenericInventory().getAmount(0));
        Assertions.assertEquals(1, converter.insertExternal(0, AEItemKey.of(offered), 8, Actionable.MODULATE));
        Assertions.assertEquals(8, offered.getCount());
        Assertions.assertEquals(output, AEItemKey.of(offered));
        Assertions.assertEquals(4096, converter.getGenericInventory().getAmount(0));
        Assertions.assertTrue(converter.getStock().getStackInSlot(9).isEmpty());
    }

    @Test
    void renamedTicketPreservesTypeCountAndOtherComponents() {
        ItemStack source = new ItemStack(Items.DIAMOND, 7);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("output"));
        source.set(DataComponents.REPAIR_COST, 123);
        ItemStack renamed = FakeCraftingConverterBlockEntity.renameTicket(
                source, FakeCraftingConverterTest.paper("input"));
        Assertions.assertEquals(Items.DIAMOND, renamed.getItem());
        Assertions.assertEquals(7, renamed.getCount());
        Assertions.assertEquals(123, renamed.get(DataComponents.REPAIR_COST));
        Assertions.assertEquals(Component.literal("input"), renamed.get(DataComponents.CUSTOM_NAME));
        Assertions.assertEquals(Component.literal("output"), source.get(DataComponents.CUSTOM_NAME));
        ItemStack unnamed = FakeCraftingConverterBlockEntity.renameTicket(source, AEItemKey.of(Items.PAPER));
        Assertions.assertNull(unnamed.get(DataComponents.CUSTOM_NAME));
        Assertions.assertEquals(Items.DIAMOND, unnamed.getItem());
        Assertions.assertEquals(7, unnamed.getCount());
        Assertions.assertEquals(123, unnamed.get(DataComponents.REPAIR_COST));
        Assertions.assertEquals(Component.literal("output"), source.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void incompatibleMarkerItemsCannotTransmuteMaterials() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey paper = AEItemKey.of(Items.PAPER);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        FakeCraftingConverterTest.configure(converter, 0, paper, diamond);
        Assertions.assertEquals(paper, converter.getMarkers().getKey(0));
        Assertions.assertNull(converter.getMarkers().getKey(9));
        Assertions.assertEquals(0, converter.insertExternal(0, diamond, 1, Actionable.MODULATE));
        Assertions.assertFalse(converter.acceptTaskInputs(UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(0, 1L))));
        Assertions.assertTrue(converter.getStock().isEmpty());
    }

    @Test
    void pairedMarkersAcceptOnlyNameDifferencesAfterUpperMarkerIsConfigured() {
        ItemStack inputStack = new ItemStack(Items.DIAMOND, 7);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("ticket", "same-data");
        inputStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        inputStack.set(DataComponents.REPAIR_COST, 2);
        inputStack.set(DataComponents.CUSTOM_NAME, Component.literal("input"));
        ItemStack outputStack = inputStack.copyWithCount(1);
        outputStack.set(DataComponents.CUSTOM_NAME, Component.literal("output"));
        AEItemKey input = AEItemKey.of(inputStack);
        AEItemKey output = AEItemKey.of(outputStack);

        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        converter.setMarker(0, input);
        Assertions.assertTrue(converter.canSetMarker(9, output));
        converter.setMarker(9, output);
        Assertions.assertEquals(input, converter.getMarkers().getKey(0));
        Assertions.assertEquals(output, converter.getMarkers().getKey(9));
        Assertions.assertEquals(7, inputStack.getCount());
        Assertions.assertEquals(Component.literal("input"), inputStack.get(DataComponents.CUSTOM_NAME));
    }

    /* 普通点击和 JEI 标记都必须以同列上槽为前置条件，不能借用其他列。 */
    @Test
    void lowerMarkersRequireTheirOwnUpperMarkerForEveryColumn() {
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        for (int column = 0; column < FakeCraftingConverterBlockEntity.COLUMN_COUNT; column++) {
            FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
            int lower = FakeCraftingConverterBlockEntity.COLUMN_COUNT + column;
            int otherColumn = (column + 1) % FakeCraftingConverterBlockEntity.COLUMN_COUNT;
            converter.setMarker(otherColumn, FakeCraftingConverterTest.paper("other-input"));
            FakeCraftingConverterMarkerSlot lowerSlot = new FakeCraftingConverterMarkerSlot(
                    converter.getStock(), converter.getMarkers().createMenuWrapper(), lower, lower, converter);
            Assertions.assertFalse(converter.canSetMarker(lower, output));
            Assertions.assertFalse(lowerSlot.canSetFilterTo(output.toStack()));
            converter.setMarker(lower, output);
            Assertions.assertNull(converter.getMarkers().getKey(lower));

            converter.setMarker(column, input);
            Assertions.assertTrue(converter.canSetMarker(lower, output));
            Assertions.assertTrue(lowerSlot.canSetFilterTo(output.toStack()));
            converter.setMarker(lower, output);
            Assertions.assertEquals(output, converter.getMarkers().getKey(lower));
            Assertions.assertTrue(converter.getStock().isEmpty());
        }
    }

    @Test
    void clearingUpperMarkerClearsItsPairedMarkerButPreservesStockAndOtherColumns() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        AEItemKey otherInput = FakeCraftingConverterTest.paper("other-input");
        AEItemKey otherOutput = FakeCraftingConverterTest.paper("other-output");
        FakeCraftingConverterTest.configure(converter, 0, input, output);
        FakeCraftingConverterTest.configure(converter, 1, otherInput, otherOutput);
        converter.insertExternal(0, input, 2, Actionable.MODULATE);
        converter.getStock().setItemDirect(9, output.toStack(3));
        converter.setMarker(0, null);

        Assertions.assertNull(converter.getMarkers().getKey(0));
        Assertions.assertNull(converter.getMarkers().getKey(9));
        Assertions.assertEquals(otherInput, converter.getMarkers().getKey(1));
        Assertions.assertEquals(otherOutput, converter.getMarkers().getKey(10));
        Assertions.assertEquals(input, AEItemKey.of(converter.getStock().getStackInSlot(0)));
        Assertions.assertEquals(2, converter.getStock().getStackInSlot(0).getCount());
        Assertions.assertEquals(output, AEItemKey.of(converter.getStock().getStackInSlot(9)));
        Assertions.assertEquals(3, converter.getStock().getStackInSlot(9).getCount());
        Assertions.assertFalse(converter.canSetMarker(9, output));
        converter.setMarker(0, input);
        converter.setMarker(9, output);
        Assertions.assertEquals(output, converter.getMarkers().getKey(9));
    }

    @Test
    void clearingLowerMarkerKeepsItsUpperMarker() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, input, output);
        converter.setMarker(9, null);
        Assertions.assertEquals(input, converter.getMarkers().getKey(0));
        Assertions.assertNull(converter.getMarkers().getKey(9));
        Assertions.assertTrue(converter.canSetMarker(9, output));
    }

    @Test
    void pairedMarkersRejectChangedNbtComponentsAndItemName() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        ItemStack inputStack = FakeCraftingConverterTest.paper("input").toStack();
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("ticket", 1);
        inputStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        ItemStack outputStack = inputStack.copy();
        outputStack.set(DataComponents.CUSTOM_NAME, Component.literal("output"));
        AEItemKey input = AEItemKey.of(inputStack);
        AEItemKey output = AEItemKey.of(outputStack);
        FakeCraftingConverterTest.configure(converter, 0, input, output);

        ItemStack changedNbt = outputStack.copy();
        CompoundTag differentNbt = new CompoundTag();
        differentNbt.putInt("ticket", 2);
        changedNbt.set(DataComponents.CUSTOM_DATA, CustomData.of(differentNbt));
        ItemStack removedNbt = outputStack.copy();
        removedNbt.remove(DataComponents.CUSTOM_DATA);
        ItemStack changedComponent = outputStack.copy();
        changedComponent.set(DataComponents.REPAIR_COST, 3);
        ItemStack changedItemName = outputStack.copy();
        changedItemName.set(DataComponents.ITEM_NAME, Component.literal("different component"));
        FakeCraftingConverterMarkerSlot lowerSlot = new FakeCraftingConverterMarkerSlot(
                converter.getStock(), converter.getMarkers().createMenuWrapper(), 9, 9, converter);

        for (ItemStack candidate : new ItemStack[]{changedNbt, removedNbt, changedComponent, changedItemName}) {
            AEItemKey candidateKey = AEItemKey.of(candidate);
            Assertions.assertFalse(converter.canSetMarker(0, candidateKey));
            Assertions.assertFalse(converter.canSetMarker(9, candidateKey));
            Assertions.assertFalse(lowerSlot.canSetFilterTo(candidate));
            converter.setMarker(9, candidateKey);
            Assertions.assertEquals(output, converter.getMarkers().getKey(9));
        }
        Assertions.assertEquals(input, converter.getMarkers().getKey(0));
    }

    @Test
    void clearingMarkerDoesNotAllowPairingDifferentItemsWithRemainingStock() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        converter.setMarker(0, input);
        converter.insertExternal(0, input, 2, Actionable.MODULATE);
        converter.setMarker(0, null);

        Assertions.assertFalse(converter.canSetMarker(9, AEItemKey.of(Items.DIAMOND)));
        Assertions.assertFalse(converter.canSetMarker(9, FakeCraftingConverterTest.paper("output")));
        Assertions.assertFalse(converter.canSetMarker(0, AEItemKey.of(Items.DIAMOND)));
        converter.setMarker(0, input);
        Assertions.assertTrue(converter.canSetMarker(9, FakeCraftingConverterTest.paper("output")));
        Assertions.assertEquals(2, converter.getStock().getStackInSlot(0).getCount());
    }

    @Test
    void duplicateUpperOrLowerMarkersAreRejectedByClickAndJeiValidation() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, input, output);
        Assertions.assertFalse(converter.canSetMarker(1, input));
        converter.setMarker(1, input);
        Assertions.assertNull(converter.getMarkers().getKey(1));
        converter.setMarker(1, FakeCraftingConverterTest.paper("other-input"));
        Assertions.assertFalse(converter.canSetMarker(10, output));
        converter.setMarker(10, output);
        Assertions.assertNull(converter.getMarkers().getKey(10));
        for (int slotIndex : new int[]{1, 10}) {
            AEItemKey duplicate = slotIndex == 1 ? input : output;
            FakeCraftingConverterMarkerSlot slot = new FakeCraftingConverterMarkerSlot(
                    converter.getStock(), converter.getMarkers().createMenuWrapper(), slotIndex, slotIndex, converter);
            Assertions.assertFalse(slot.canSetFilterTo(duplicate.toStack()));
        }
        AEItemKey otherOutput = FakeCraftingConverterTest.paper("other-output");
        converter.setMarker(10, otherOutput);
        Assertions.assertEquals(otherOutput, converter.getMarkers().getKey(10));
        Assertions.assertFalse(converter.canSetMarker(1, input));
        Assertions.assertTrue(converter.getStock().isEmpty());
    }

    @Test
    void ordinaryMaterialSharingTheTicketKeyIsNotRemoved() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        FakeCraftingConverterTest.configure(converter, 0, input, FakeCraftingConverterTest.paper("output"));
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 2L)));
        KeyCounter[] delivered = {FakeCraftingConverterTest.counter(input, 7)};
        KeyCounter[] forwarded = converter.filterTaskInputs(task, delivered);
        Assertions.assertEquals(5, forwarded[0].get(input));
        Assertions.assertEquals(7, delivered[0].get(input));
    }

    @Test
    void partialReturnAccountsForEveryColumnBeforeRetry() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey first = FakeCraftingConverterTest.paper("output-a");
        AEItemKey second = FakeCraftingConverterTest.paper("output-b");
        FakeCraftingConverterTest.configure(converter, 0, FakeCraftingConverterTest.paper("input-a"), first);
        FakeCraftingConverterTest.configure(converter, 1, FakeCraftingConverterTest.paper("input-b"), second);
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 4L, 1, 5L)));
        LimitedInventory destination = new LimitedInventory();
        destination.limits.put(first, 2L);
        destination.limits.put(second, 5L);
        ReceiveInventory receiver = FakeCraftingConverterTest.receiver(destination);
        Assertions.assertFalse(converter.returnTaskOutputs(task, receiver));
        Assertions.assertEquals(2, converter.getStock().getStackInSlot(9).getCount());
        Assertions.assertTrue(converter.getStock().getStackInSlot(10).isEmpty());
        Assertions.assertEquals(2, converter.getPendingOutputAmount(0));
        Assertions.assertEquals(0, converter.getPendingOutputAmount(1));
        Assertions.assertEquals(5, destination.count(second));
        destination.limits.put(first, 4L);
        Assertions.assertTrue(converter.returnTaskOutputs(task, receiver));
        Assertions.assertEquals(4, destination.count(first));
        Assertions.assertEquals(5, destination.count(second));
    }

    @Test
    void synchronousInsertionReentryCannotReturnTheSameTicketTwice() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, FakeCraftingConverterTest.paper("input"), output);
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L)));
        ReceiveInventory[] receiver = new ReceiveInventory[1];
        AtomicInteger reentries = new AtomicInteger();
        GenericStackInv destination = new GenericStackInv(AEKeyTypes.getAll(), () -> {
        }, GenericStackInv.Mode.STORAGE, 1) {
            @Override
            public long insert(int slot, AEKey key, long amount, Actionable mode) {
                if (mode == Actionable.MODULATE) {
                    reentries.incrementAndGet();
                    Assertions.assertTrue(converter.getStock().getStackInSlot(9).isEmpty());
                    Assertions.assertFalse(converter.returnTaskOutputs(task, receiver[0]));
                    Assertions.assertFalse(converter.acceptTaskInputs(UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(0, 4096L))));
                }
                return super.insert(slot, key, amount, mode);
            }
        };
        receiver[0] = FakeCraftingConverterTest.receiver(destination);
        Assertions.assertTrue(converter.returnTaskOutputs(task, receiver[0]));
        Assertions.assertEquals(1, reentries.get());
        Assertions.assertEquals(1, destination.getAmount(0));
    }

    @Test
    void rejectedTaskRollbackDoesNotCreateOrConsumeUpperStock() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        FakeCraftingConverterTest.configure(converter, 0, input, FakeCraftingConverterTest.paper("output"));
        converter.insertExternal(0, input, 5, Actionable.MODULATE);
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L)));
        converter.cancelTaskTickets(task, false);
        converter.cancelTaskTickets(task, false);
        Assertions.assertEquals(5, converter.getStock().getStackInSlot(0).getCount());
        Assertions.assertTrue(converter.getStock().getStackInSlot(9).isEmpty());
        Assertions.assertEquals(0, converter.getPendingOutputAmount(0));
    }

    @Test
    void cancellationKeepsRealRemainderWhenUpperStockIsFull() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        FakeCraftingConverterTest.configure(converter, 0, input, FakeCraftingConverterTest.paper("output"));
        converter.insertExternal(0, input, 4096, Actionable.MODULATE);
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 2L)));
        Assertions.assertTrue(converter.getStock().extractItem(9, 2, false).isEmpty());
        converter.cancelTaskTickets(task);
        converter.cancelTaskTickets(task);
        Assertions.assertEquals(4096, converter.getGenericInventory().getAmount(0));
        Assertions.assertEquals(2, converter.getStock().extractItem(9, 2, false).getCount());
        Assertions.assertFalse(converter.hasTaskTickets(task));
    }

    @Test
    void inventoryReloadPreservesTicketsWithoutConvertingThem() {
        FakeCraftingConverterBlockEntity original = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(original, 0, input, output);
        UUID task = UUID.randomUUID();
        original.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 3L)));
        original.insertExternal(0, output, 2, Actionable.MODULATE);
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved = new CompoundTag();
        original.saveAdditional(saved, registries);
        FakeCraftingConverterBlockEntity restored = FakeCraftingConverterTest.converter();
        restored.loadTag(saved, registries);
        Assertions.assertEquals(2, restored.getStock().getStackInSlot(0).getCount());
        Assertions.assertEquals(3, restored.getStock().getStackInSlot(9).getCount());
        Assertions.assertEquals(output, AEItemKey.of(restored.getStock().getStackInSlot(9)));
        Assertions.assertEquals(3, restored.getPendingOutputAmount(0));
        Assertions.assertTrue(restored.hasTaskTickets(task));
    }

    @Test
    void missingSavedStockIsNeverRecreatedFromLedgerOrCancellation() {
        FakeCraftingConverterBlockEntity original = FakeCraftingConverterTest.converter();
        FakeCraftingConverterTest.configure(original, 0, FakeCraftingConverterTest.paper("input"), FakeCraftingConverterTest.paper("output"));
        UUID task = UUID.randomUUID();
        original.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L)));
        original.getStock().setItemDirect(9, ItemStack.EMPTY);
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved = new CompoundTag();
        original.saveAdditional(saved, registries);
        FakeCraftingConverterBlockEntity restored = FakeCraftingConverterTest.converter();
        restored.loadTag(saved, registries);
        Assertions.assertTrue(restored.getStock().isEmpty());
        Assertions.assertFalse(restored.returnTaskOutputs(task, FakeCraftingConverterTest.receiver(new LimitedInventory())));
        restored.cancelTaskTickets(task);
        Assertions.assertTrue(restored.getStock().isEmpty());
    }

    @Test
    void slotExtractionDoesNotRemoveAnotherColumnsStock() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        for (int row = 0; row < 2; row++) {
            AEItemKey input = FakeCraftingConverterTest.paper("input-" + row);
            FakeCraftingConverterTest.configure(converter, row, input, FakeCraftingConverterTest.paper("output-" + row));
            converter.insertExternal(row, input, row + 1, Actionable.MODULATE);
        }
        Assertions.assertEquals(2, converter.getGenericInventory().extract(
                1, FakeCraftingConverterTest.paper("input-1"), 8, Actionable.MODULATE));
        Assertions.assertEquals(1, converter.getStock().getStackInSlot(0).getCount());
        Assertions.assertTrue(converter.getStock().getStackInSlot(1).isEmpty());
    }

    @Test
    void duplicateMarkersLoadedFromStorageCannotAcceptNewTasksButKeepExistingStock() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        FakeCraftingConverterTest.configure(converter, 0, input, output);
        UUID task = UUID.randomUUID();
        Assertions.assertTrue(converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 2L))));
        converter.getMarkers().setStack(1, new GenericStack(input, 0));
        converter.getMarkers().setStack(10, new GenericStack(output, 0));
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved = new CompoundTag();
        converter.saveAdditional(saved, registries);
        FakeCraftingConverterBlockEntity restored = FakeCraftingConverterTest.converter();
        restored.loadTag(saved, registries);
        Assertions.assertFalse(restored.allocateTickets(new KeyCounter[0],
                FakeCraftingConverterTest.counter(input, 1), FakeCraftingConverterTest.counter(output, 1)).valid());
        Assertions.assertFalse(restored.acceptTaskInputs(UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(1, 1L))));
        Assertions.assertEquals(2, restored.getPendingOutputAmount(0));
        Assertions.assertEquals(0, restored.getPendingOutputAmount(1));
        LimitedInventory destination = new LimitedInventory();
        destination.limits.put(output, 2L);
        Assertions.assertTrue(restored.returnTaskOutputs(task, FakeCraftingConverterTest.receiver(destination)));
        Assertions.assertEquals(2, destination.count(output));
        restored.setMarker(1, null);
        Assertions.assertTrue(restored.acceptTaskInputs(UUID.randomUUID(), FakeCraftingConverterTest.plan(Map.of(0, 1L))));
    }

    @Test
    void markersDoNotConsumeItemsAndGuiCannotExtractReservedTickets() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey input = FakeCraftingConverterTest.paper("input");
        AEItemKey output = FakeCraftingConverterTest.paper("output");
        ItemStack carried = input.toStack(5);
        FakeCraftingConverterTest.configure(converter, 0, AEItemKey.of(carried), output);
        Assertions.assertTrue(converter.getStock().isEmpty());
        Assertions.assertEquals(5, carried.getCount());
        FakeCraftingConverterMarkerSlot slot = new FakeCraftingConverterMarkerSlot(
                converter.getStock(), converter.getMarkers().createMenuWrapper(), 9, 9, converter);
        Assertions.assertFalse(slot.mayPlace(output.toStack()));
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L)));
        Player player = Mockito.mock(Player.class);
        Assertions.assertFalse(slot.mayPickup(player));
        Assertions.assertTrue(slot.remove(1).isEmpty());
        Assertions.assertFalse(converter.canSetMarker(9, null));
        Assertions.assertFalse(converter.canSetMarker(0, null));
        converter.setMarker(0, null);
        Assertions.assertEquals(input, converter.getMarkers().getKey(0));
        Assertions.assertEquals(output, converter.getMarkers().getKey(9));
        Assertions.assertEquals(1, converter.getStock().getStackInSlot(9).getCount());
    }

    @Test
    void acknowledgedDispatchRetryDoesNotReachConverterOrDeliverAgain() throws Exception {
        UUID task = UUID.randomUUID();
        IManagedGridNode node = (IManagedGridNode) Proxy.newProxyInstance(
                IManagedGridNode.class.getClassLoader(), new Class<?>[]{IManagedGridNode.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("Acknowledged retry must not look up the converter");
                });
        Pp2mpTaskEndpoint endpoint = (Pp2mpTaskEndpoint) Proxy.newProxyInstance(
                Pp2mpTaskEndpoint.class.getClassLoader(), new Class<?>[]{Pp2mpTaskEndpoint.class},
                (proxy, method, arguments) -> {
                    Assertions.assertEquals("hasAcceptedTask", method.getName());
                    return task.equals(arguments[0]);
                });
        Pp2mpInputLogic input = new Pp2mpInputLogic(Mockito.mock(Pp2mpInputPart.class), node);
        Method dispatch = Pp2mpInputLogic.class.getDeclaredMethod("tryAcceptTaskWithTickets",
                Pp2mpTaskEndpoint.class, IPatternDetails.class, KeyCounter[].class, UUID.class);
        dispatch.setAccessible(true);
        Assertions.assertEquals(true, dispatch.invoke(input, endpoint, null, new KeyCounter[0], task));
    }

    @Test
    void flushFailureAfterSuccessfulInsertionDoesNotRestoreTheConsumedTicket() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        FakeCraftingConverterTest.configure(converter, 0, FakeCraftingConverterTest.paper("input"), FakeCraftingConverterTest.paper("output"));
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L)));
        RecyclingInventory destination = new RecyclingInventory();
        destination.ae2bc$setImmediateFlushHandler(() -> {
            throw new IllegalStateException("Test flush failed after insertion");
        });
        ReceiveInventory receiver = FakeCraftingConverterTest.receiver(destination);
        Assertions.assertThrows(IllegalStateException.class, () -> converter.returnTaskOutputs(task, receiver));
        Assertions.assertEquals(1, destination.getAmount(0));
        Assertions.assertTrue(converter.getStock().getStackInSlot(9).isEmpty());
        Assertions.assertEquals(0, converter.getPendingOutputAmount(0));
        Assertions.assertTrue(converter.returnTaskOutputs(task, receiver));
        Assertions.assertEquals(1, destination.getAmount(0));
    }

    @Test
    void laterColumnFailureDoesNotRestoreAnEarlierCommittedTicket() {
        FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        AEItemKey first = FakeCraftingConverterTest.paper("output-a");
        AEItemKey second = FakeCraftingConverterTest.paper("output-b");
        FakeCraftingConverterTest.configure(converter, 0, FakeCraftingConverterTest.paper("input-a"), first);
        FakeCraftingConverterTest.configure(converter, 1, FakeCraftingConverterTest.paper("input-b"), second);
        UUID task = UUID.randomUUID();
        converter.acceptTaskInputs(task, FakeCraftingConverterTest.plan(Map.of(0, 1L, 1, 1L)));
        GenericStackInv destination = new GenericStackInv(AEKeyTypes.getAll(), () -> {
        }, GenericStackInv.Mode.STORAGE, 9) {
            @Override
            public long insert(int slot, AEKey key, long amount, Actionable mode) {
                if (second.equals(key)) {
                    throw new IllegalStateException("Test second column rejected");
                }
                return super.insert(slot, key, amount, mode);
            }
        };
        ReceiveInventory receiver = FakeCraftingConverterTest.receiver(destination);
        Assertions.assertThrows(IllegalStateException.class, () -> converter.returnTaskOutputs(task, receiver));
        Assertions.assertEquals(1, destination.getAmount(0));
        Assertions.assertTrue(converter.getStock().getStackInSlot(9).isEmpty());
        Assertions.assertEquals(1, converter.getStock().getStackInSlot(10).getCount());
        Assertions.assertEquals(0, converter.getPendingOutputAmount(0));
        Assertions.assertEquals(1, converter.getPendingOutputAmount(1));
    }

    private static final class MenuFixture {
        private final Player player = Mockito.mock(Player.class);
        private final FakeCraftingConverterBlockEntity converter = FakeCraftingConverterTest.converter();
        private final AEItemKey input = FakeCraftingConverterTest.paper("input");
        private final AEItemKey output = FakeCraftingConverterTest.paper("output");
        private final FakeCraftingConverterMenu menu;

        private MenuFixture() {
            Level level = Mockito.mock(Level.class);
            RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            Inventory inventory = new Inventory(this.player);
            Mockito.when(this.player.level()).thenReturn(level);
            Mockito.when(this.player.getCommandSenderWorld()).thenReturn(level);
            Mockito.when(this.player.getInventory()).thenReturn(inventory);
            Mockito.when(this.player.getAbilities()).thenReturn(new Abilities());
            Mockito.when(level.registryAccess()).thenReturn(registries);
            Mockito.when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(this.converter);
            this.converter.setLevel(level);
            this.menu = new FakeCraftingConverterMenu(FakeCraftingConverterMenu.TYPE, 1, inventory, this.converter);
        }
    }

    private static final class RoutingFixture {
        private final FakeCraftingConverterBlockEntity first = FakeCraftingConverterTest.converter();
        private final FakeCraftingConverterBlockEntity second = FakeCraftingConverterTest.converter();
        private final AEItemKey input = FakeCraftingConverterTest.paper("input");
        private final AEItemKey output = FakeCraftingConverterTest.paper("output");
        private final IGrid grid = Mockito.mock(IGrid.class);
        private final Pp2mpGridService service = Mockito.spy(new Pp2mpGridService(this.grid));
        private final Pp2mpTaskEndpoint endpoint = Mockito.mock(Pp2mpTaskEndpoint.class);
        private final Pp2mpInputLogic logic;
        private boolean accept = true;
        private UUID taskId;
        private IPatternDetails pattern;
        private KeyCounter[] inputs;

        private RoutingFixture() throws Exception {
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.dimension()).thenReturn(Level.OVERWORLD);
            for (FakeCraftingConverterBlockEntity converter : List.of(this.first, this.second)) {
                converter.setFrequency((short) (converter == this.first ? 42 : 99));
                FakeCraftingConverterTest.configure(converter, 0, this.input, this.output);
                converter.setLevel(level);
                GridNode active = Mockito.mock(GridNode.class);
                Mockito.when(active.isActive()).thenReturn(true);
                Mockito.when(active.getGrid()).thenReturn(this.grid);
                FakeCraftingConverterTest.setNode(converter, active);
            }
            Mockito.when(this.grid.getService(Pp2mpGridService.class)).thenReturn(this.service);
            Mockito.when(this.grid.getMachines(FakeCraftingConverterBlockEntity.class))
                    .thenReturn(Set.of(this.first, this.second));
            Mockito.doReturn(List.of(this.endpoint)).when(this.service).getEndpoints((short) 42);
            IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
            Mockito.when(node.getGrid()).thenReturn(this.grid);
            Mockito.when(node.isActive()).thenReturn(true);
            Pp2mpInputPart part = Mockito.mock(Pp2mpInputPart.class);
            Mockito.when(part.getFrequency()).thenReturn((short) 42);
            Mockito.when(part.getHost()).thenReturn(Mockito.mock(IPartHost.class));
            Mockito.when(part.getData()).thenReturn(new Pp2mpInputData(() -> { }, () -> { }));
            this.logic = new Pp2mpInputLogic(part, node);
            Mockito.when(this.endpoint.isOperational()).thenReturn(true);
            Mockito.when(this.endpoint.canAcceptTask()).thenReturn(true);
            Mockito.when(this.endpoint.tryAcceptTask(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenAnswer(invocation -> {
                        this.taskId = invocation.getArgument(3, UUID.class);
                        return this.accept;
                    });
        }

        private boolean dispatch(int amount) {
            AEItemKey material = AEItemKey.of(Items.STONE);
            ItemStack encoded = Pp2mpPatternItem.encodePattern(
                    PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(material, amount), new GenericStack(this.input, amount)),
                            List.of(new GenericStack(this.output, amount))),
                    MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
            encoded.set(ComponentRegistry.REAL_OUTPUT_MARKER, new GenericStack(AEItemKey.of(Items.DIAMOND), amount));
            this.pattern = new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
            this.inputs = new KeyCounter[]{
                    FakeCraftingConverterTest.counter(material, amount),
                    FakeCraftingConverterTest.counter(this.input, amount)
            };
            return this.logic.pushPattern(this.pattern, this.inputs);
        }
    }

    private static final class RecyclingInventory extends GenericStackInv implements ImmediatePatternProviderReturnInventory {
        private Runnable flush = () -> {
        };

        private RecyclingInventory() {
            super(AEKeyTypes.getAll(), () -> {
            }, Mode.STORAGE, 9);
        }

        @Override
        public void ae2bc$setImmediateFlushHandler(Runnable handler) {
            this.flush = handler;
        }

        @Override
        public void ae2bc$requestImmediateFlush() {
            this.flush.run();
        }
    }

    private static final class LimitedInventory extends GenericStackInv {
        private final Map<AEKey, Long> limits = new HashMap<>();

        private LimitedInventory() {
            super(AEKeyTypes.getAll(), () -> {
            }, Mode.STORAGE, 9);
        }

        private long count(AEKey key) {
            long count = 0;
            for (int slot = 0; slot < this.size(); slot++) {
                if (key.equals(this.getKey(slot))) {
                    count += this.getAmount(slot);
                }
            }
            return count;
        }

        @Override
        public long insert(int slot, AEKey key, long amount, Actionable mode) {
            long allowed = Math.max(0, this.limits.getOrDefault(key, 0L) - this.count(key));
            return super.insert(slot, key, Math.min(amount, allowed), mode);
        }
    }
}
