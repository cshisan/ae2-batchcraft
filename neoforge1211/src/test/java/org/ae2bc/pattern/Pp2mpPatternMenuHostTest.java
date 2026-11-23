package org.ae2bc.pattern;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.me.InWorldGridNode;
import appeng.me.helpers.ActionHostEnergySource;
import appeng.menu.locator.MenuLocators;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.DataSynchronization;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.network.connection.ConnectionType;
import io.netty.buffer.Unpooled;
import com.google.common.collect.ImmutableSet;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.registry.component.ComponentRegistry;
import org.ae2bc.core.constant.Pp2mpEncodingTermKeys;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;

class Pp2mpPatternMenuHostTest {
    private static final AEItemKey MATERIAL = AEItemKey.of(Items.STONE);
    private static final BlockPos TARGET = new BlockPos(2, 3, 4);

    /* 使用实际 PP2MP 菜单选择的能源来源执行原版存取，验证资源守恒和编码标记。 */
    @Test
    void connectedMenuUsesNetworkEnergyForTakingAndReturningPatternIngredients() throws Exception {
        Fixture fixture = new Fixture(true);
        Assertions.assertInstanceOf(ActionHostEnergySource.class, fixture.energy);
        Assertions.assertSame(fixture.node, fixture.menu.getGridNode());
        Assertions.assertSame(fixture.grid, fixture.host.getGrid());
        Assertions.assertTrue(fixture.host.getLinkStatus().connected());

        Assertions.assertEquals(8, fixture.extract(8));
        Assertions.assertEquals(56, fixture.stock.getAvailableStacks().get(Pp2mpPatternMenuHostTest.MATERIAL));
        ItemStack carried = Pp2mpPatternMenuHostTest.MATERIAL.toStack(8);
        fixture.menu.getProcessingInputSlots()[0].set(carried.copyWithCount(1));
        Assertions.assertEquals(Pp2mpPatternMenuHostTest.MATERIAL,
                AEItemKey.of(fixture.menu.getProcessingInputSlots()[0].getItem()));
        Assertions.assertEquals(8, carried.getCount());

        Assertions.assertEquals(8, StorageHelper.poweredInsert(fixture.energy, fixture.inventory,
                Pp2mpPatternMenuHostTest.MATERIAL, 8, fixture.menu.getActionSource()));
        Assertions.assertEquals(64, fixture.stock.getAvailableStacks().get(Pp2mpPatternMenuHostTest.MATERIAL));
        Assertions.assertTrue(fixture.availableEnergy < 100);
    }

    @Test
    void emptyNetworkEnergyCannotExtractOrInsertResources() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.availableEnergy = 0;
        Assertions.assertEquals(0, fixture.extract(8));
        Assertions.assertEquals(0, StorageHelper.poweredInsert(fixture.energy, fixture.inventory,
                Pp2mpPatternMenuHostTest.MATERIAL, 8, fixture.menu.getActionSource()));
        Assertions.assertEquals(64, fixture.stock.getAvailableStacks().get(Pp2mpPatternMenuHostTest.MATERIAL));
    }

    /* 同一个菜单和库存代理在超距、断电、节点移除后失效，恢复连接后重新可用。 */
    @Test
    void connectionChangesInvalidateStorageAndEnergyTogether() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.setDistanceSquared(64);
        Assertions.assertSame(fixture.node, fixture.host.getActionableNode());
        fixture.setDistanceSquared(64.01);
        fixture.assertDisconnected();

        fixture.setDistanceSquared(0);
        Mockito.when(fixture.node.isActive()).thenReturn(false);
        fixture.assertDisconnected();
        Mockito.when(fixture.node.isActive()).thenReturn(true);

        Mockito.when(fixture.level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                Pp2mpPatternMenuHostTest.TARGET, null)).thenReturn(null);
        fixture.assertDisconnected();
        Mockito.when(fixture.level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                Pp2mpPatternMenuHostTest.TARGET, null)).thenReturn(fixture.nodeHost);

        Assertions.assertTrue(fixture.host.getLinkStatus().connected());
        Assertions.assertEquals(8, fixture.extract(8));
        Assertions.assertEquals(56, fixture.stock.getAvailableStacks().get(Pp2mpPatternMenuHostTest.MATERIAL));
    }

    @Test
    void openingInAirKeepsEncodingAvailableWithoutAccessingAnyNetwork() throws Exception {
        Fixture fixture = new Fixture(false);
        Assertions.assertFalse(fixture.host.wasOpenedOnBlock());
        fixture.assertDisconnected();
        fixture.menu.getProcessingInputSlots()[0].set(Pp2mpPatternMenuHostTest.MATERIAL.toStack());
        Assertions.assertEquals(Pp2mpPatternMenuHostTest.MATERIAL,
                AEItemKey.of(fixture.menu.getProcessingInputSlots()[0].getItem()));
        Mockito.verify(fixture.level, Mockito.never()).getCapability(
                AECapabilities.IN_WORLD_GRID_NODE_HOST, Pp2mpPatternMenuHostTest.TARGET, null);
    }

    @Test
    void emptySlotsAcceptOrdinaryDropAndPlaceButRejectFakeCraftingOnBothEntryPoints() throws Exception {
        Fixture fixture = new Fixture(false);
        for (MaterialOutputMode mode : List.of(MaterialOutputMode.NORMAL, MaterialOutputMode.DROP, MaterialOutputMode.PLACE)) {
            Assertions.assertTrue(fixture.menu.supportsOutputMode(1, mode));
            fixture.menu.receiveClientAction(Pp2mpEncodingTermKeys.PATTERN_OUTPUT_ATTR, "[1,0," + mode.id() + "]");
            Assertions.assertEquals(mode, fixture.menu.mode(1));
        }
        Assertions.assertFalse(fixture.menu.supportsOutputMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
        fixture.menu.receiveClientAction(Pp2mpEncodingTermKeys.PATTERN_OUTPUT_ATTR, "[1,0,3]");
        fixture.menu.setOutputMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER);
        Assertions.assertEquals(MaterialOutputMode.PLACE, fixture.menu.mode(1));
    }

    @Test
    void invalidOutputConfigurationRequestsDoNotChangeTheSlot() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.menu.setOutputMode(1, MaterialOutputMode.DROP);
        for (String request : List.of("null", "[]", "[1]", "[1,0]", "[1,0,1,0]",
                "[-1,0,1]", "[81,0,1]", "[1,-1,1]", "[1,7,1]", "[1,0,99]", "[1,0,3]")) {
            fixture.menu.receiveClientAction(Pp2mpEncodingTermKeys.PATTERN_OUTPUT_ATTR, request);
            Assertions.assertEquals(MaterialOutputMode.DROP, fixture.menu.mode(1), request);
        }
    }

    @Test
    void ingredientChangesRetainCompatibleModesAndResetIncompatibleOrEmptyFakeModes() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.menu.setOutputMode(0, MaterialOutputMode.PLACE);
        fixture.menu.getProcessingInputSlots()[0].set(new ItemStack(Items.STONE));
        Assertions.assertEquals(MaterialOutputMode.PLACE, fixture.menu.mode(0));
        fixture.menu.getProcessingInputSlots()[0].set(ItemStack.EMPTY);
        Assertions.assertEquals(MaterialOutputMode.PLACE, fixture.menu.mode(0));
        fixture.menu.getProcessingInputSlots()[0].set(new ItemStack(Items.PAPER));
        Assertions.assertEquals(MaterialOutputMode.NORMAL, fixture.menu.mode(0));
        fixture.menu.setOutputMode(0, MaterialOutputMode.FAKE_CRAFTING_CONVERTER);
        Assertions.assertEquals(MaterialOutputMode.FAKE_CRAFTING_CONVERTER, fixture.menu.mode(0));
        fixture.menu.getProcessingInputSlots()[0].set(ItemStack.EMPTY);
        Assertions.assertEquals(MaterialOutputMode.NORMAL, fixture.menu.mode(0));
        fixture.menu.getProcessingInputSlots()[0].set(new ItemStack(Items.PAPER));
        Assertions.assertEquals(MaterialOutputMode.NORMAL, fixture.menu.mode(0));
    }

    @Test
    void serverSyncCarriesEmptySlotModesAndCorrectionsThroughAe2Synchronization() throws Exception {
        Fixture server = new Fixture(false);
        Fixture client = new Fixture(false);
        server.menu.setOutputMode(1, MaterialOutputMode.DROP);
        server.menu.setOutputMode(2, MaterialOutputMode.PLACE);
        server.menu.getProcessingInputSlots()[2].set(new ItemStack(Items.PAPER));
        client.menu.setOutputMode(2, MaterialOutputMode.PLACE);
        server.menu.broadcastChanges();
        Field syncField = AEBaseMenu.class.getDeclaredField("dataSync");
        syncField.setAccessible(true);
        DataSynchronization synchronization = DataSynchronization.class.cast(syncField.get(server.menu));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), ConnectionType.NEOFORGE);
        try {
            synchronization.writeFull(buffer);
            client.menu.receiveServerSyncData(buffer);
            Assertions.assertEquals(MaterialOutputMode.DROP, client.menu.mode(1));
            Assertions.assertEquals(MaterialOutputMode.NORMAL, client.menu.mode(2));
        } finally {
            buffer.release();
        }
    }

    @Test
    void encodingAndReopeningRetainInternalAndTrailingEmptySlotConfiguration() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.menu.getProcessingInputSlots()[0].set(new ItemStack(Items.STONE));
        fixture.menu.getProcessingInputSlots()[2].set(new ItemStack(Items.DIRT));
        fixture.menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).getFirst().set(new ItemStack(Items.DIAMOND));
        fixture.menu.setOutputMode(1, MaterialOutputMode.DROP);
        fixture.menu.setOutputMode(5, MaterialOutputMode.PLACE);
        fixture.menu.encode();
        ItemStack encoded = fixture.host.getItemStack();
        Assertions.assertTrue(Pp2mpPatternItem.hasPatternData(encoded));
        MaterialOutputAttr config = encoded.get(ComponentRegistry.MATERIAL_OUTPUT_CONFIG);
        Assertions.assertNotNull(config);
        Assertions.assertEquals(MaterialOutputMode.DROP, config.mode(1));
        Assertions.assertEquals(MaterialOutputMode.PLACE, config.mode(5));
        Pp2mpPatternMenuHost reopenedHost = new Pp2mpPatternMenuHost(
                ItemRegistry.PP2MP_PATTERN.get(), fixture.player, MenuLocators.forInventorySlot(0), null);
        Pp2mpEncodingTermMenu reopened = new Pp2mpEncodingTermMenu(2, fixture.player.getInventory(), reopenedHost);
        Assertions.assertFalse(reopened.getProcessingInputSlots()[1].hasItem());
        Assertions.assertFalse(reopened.getProcessingInputSlots()[5].hasItem());
        Assertions.assertEquals(MaterialOutputMode.DROP, reopened.mode(1));
        Assertions.assertEquals(MaterialOutputMode.PLACE, reopened.mode(5));
        reopened.resetRecipeConfiguration();
        Assertions.assertEquals(MaterialOutputMode.NORMAL, reopened.mode(1));
        Assertions.assertEquals(MaterialOutputMode.NORMAL, reopened.mode(5));
    }

    @Test
    void emptySlotSettingsAloneCannotEncodeARecipe() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.menu.setOutputMode(0, MaterialOutputMode.PLACE);
        fixture.menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).getFirst().set(new ItemStack(Items.DIAMOND));
        fixture.menu.encode();
        Assertions.assertFalse(Pp2mpPatternItem.hasPatternData(fixture.host.getItemStack()));
    }

    @Test
    void reopeningPreservesValidFakeInputsAndBroadcastCorrectsDirectInventoryChanges() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.menu.getProcessingInputSlots()[0].set(new ItemStack(Items.GOLD_INGOT));
        fixture.menu.setOutputMode(0, MaterialOutputMode.FAKE_CRAFTING_CONVERTER);
        fixture.menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).get(0).set(new ItemStack(Items.PAPER));
        fixture.menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).get(1).set(new ItemStack(Items.DIAMOND));
        fixture.menu.getRealOutputMarkerSlot().set(new ItemStack(Items.DIAMOND));
        fixture.menu.encode();
        Pp2mpPatternMenuHost reopenedHost = new Pp2mpPatternMenuHost(
                ItemRegistry.PP2MP_PATTERN.get(), fixture.player, MenuLocators.forInventorySlot(0), null);
        Pp2mpEncodingTermMenu reopened = new Pp2mpEncodingTermMenu(2, fixture.player.getInventory(), reopenedHost);
        reopened.broadcastChanges();
        Assertions.assertEquals(MaterialOutputMode.FAKE_CRAFTING_CONVERTER, reopened.mode(0));
        reopenedHost.getLogic().getEncodedInputInv().setStack(0, null);
        reopened.broadcastChanges();
        Assertions.assertEquals(MaterialOutputMode.NORMAL, reopened.mode(0));
    }

    /* 专用槽不占用产物槽，未启用假合成时拒绝客户端对隐藏标记的修改。 */
    @Test
    void markerIsIndependentAndUsesNativeGhostSlotAmountEditing() throws Exception {
        Fixture fixture = new Fixture(false);
        Assertions.assertFalse(fixture.menu.hasFakeInputs());
        fixture.menu.setFilter(fixture.menu.getRealOutputMarkerSlot().index, new ItemStack(Items.DIAMOND, 4));
        Assertions.assertNull(fixture.menu.getRealOutputMarker());
        Assertions.assertFalse(fixture.menu.isProcessingPatternSlot(fixture.menu.getRealOutputMarkerSlot()));
        fixture.fakeRecipe();
        ItemStack carried = new ItemStack(Items.DIAMOND, 4);
        fixture.menu.setCarried(carried);
        fixture.menu.setFilter(fixture.menu.getRealOutputMarkerSlot().index, carried);
        Assertions.assertTrue(fixture.menu.hasFakeInputs());
        Assertions.assertEquals(new GenericStack(AEItemKey.of(Items.DIAMOND), 4), fixture.menu.getRealOutputMarker());
        Assertions.assertEquals(4, carried.getCount());
        Assertions.assertFalse(fixture.menu.getRealOutputMarkerSlot().mayPickup(fixture.player));
        Assertions.assertTrue(fixture.menu.canModifyAmountForSlot(fixture.menu.getRealOutputMarkerSlot()));
        Assertions.assertEquals(Pp2mpEncodingTermMenu.REAL_OUTPUT_MARKER,
                fixture.menu.getSlotSemantic(fixture.menu.getRealOutputMarkerSlot()));
        Assertions.assertFalse(fixture.menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS)
                .contains(fixture.menu.getRealOutputMarkerSlot()));
    }

    @Test
    void encodingReopeningAndSerializationPreserveMarkerWithoutDeclaringItAsOutput() throws Exception {
        for (boolean connected : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(connected);
            fixture.fakeRecipe();
            fixture.menu.getProcessingOutputSlots()[1].set(new ItemStack(Items.EMERALD, 2));
            GenericStack marker = new GenericStack(AEFluidKey.of(Fluids.WATER), 4000);
            fixture.menu.getRealOutputMarkerSlot().set(GenericStack.wrapInItemStack(marker));
            fixture.menu.encode();
            RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            ItemStack encoded = fixture.host.getItemStack();
            ItemStack saved = ItemStack.parse(registries, encoded.save(registries)).orElseThrow();
            Assertions.assertTrue(ItemStack.matches(encoded, saved));
            Assertions.assertEquals(marker, saved.get(ComponentRegistry.REAL_OUTPUT_MARKER));
            Pp2mpPatternDetails details = new Pp2mpPatternDetails(AEItemKey.of(saved), fixture.level);
            Assertions.assertEquals(List.of(new GenericStack(AEItemKey.of(Items.PAPER), 1),
                    new GenericStack(AEItemKey.of(Items.EMERALD), 2)), details.getOutputs());
            Pp2mpPatternMenuHost reopenedHost = new Pp2mpPatternMenuHost(
                    ItemRegistry.PP2MP_PATTERN.get(), fixture.player, MenuLocators.forInventorySlot(0), null);
            Pp2mpEncodingTermMenu reopened = new Pp2mpEncodingTermMenu(2, fixture.player.getInventory(), reopenedHost);
            Assertions.assertEquals(marker, reopened.getRealOutputMarker());
            Assertions.assertTrue(reopened.hasFakeInputs());
            Assertions.assertEquals(AEItemKey.of(Items.EMERALD),
                    AEItemKey.of(reopened.getProcessingOutputSlots()[1].getItem()));
        }
    }

    @Test
    void missingMarkerNeverUsesSecondOutputAndLeavesBlankOrEncodedItemUntouched() throws Exception {
        for (boolean alreadyEncoded : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(false);
            fixture.fakeRecipe();
            fixture.menu.getProcessingOutputSlots()[1].set(new ItemStack(Items.DIAMOND, 4));
            if (alreadyEncoded) {
                fixture.menu.getRealOutputMarkerSlot().set(new ItemStack(Items.DIAMOND, 4));
                fixture.menu.encode();
                fixture.menu.getRealOutputMarkerSlot().set(ItemStack.EMPTY);
            } else {
                fixture.host.getItemStack().setCount(4);
            }
            ItemStack before = fixture.host.getItemStack().copy();
            ItemStack previousResult = fixture.host.getLogic().getEncodedPatternInv().getStackInSlot(0).copy();
            fixture.menu.encode();
            Assertions.assertTrue(ItemStack.matches(before, fixture.host.getItemStack()));
            Assertions.assertTrue(ItemStack.matches(previousResult,
                    fixture.host.getLogic().getEncodedPatternInv().getStackInSlot(0)));
            Assertions.assertEquals(4, fixture.menu.getProcessingOutputSlots()[1].getItem().getCount());
            fixture.assertEncodingError("missing_marker");
        }
    }

    @Test
    void encodingValidatesFirstOutputMarkerIdentityAndBatchDivisibility() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.fakeRecipe();
        ItemStack before = fixture.host.getItemStack().copy();
        fixture.menu.getRealOutputMarkerSlot().set(new ItemStack(Items.DIAMOND, 2));
        fixture.menu.getProcessingOutputSlots()[0].set(ItemStack.EMPTY);
        fixture.menu.encode();
        fixture.assertEncodingError("missing_fake_output");
        fixture.menu.getProcessingOutputSlots()[0].set(new ItemStack(Items.DIAMOND, 2));
        fixture.menu.encode();
        fixture.assertEncodingError("marker_is_fake_output");
        fixture.menu.getProcessingInputSlots()[0].set(new ItemStack(Items.GOLD_INGOT, 2));
        fixture.menu.getProcessingOutputSlots()[0].set(new ItemStack(Items.PAPER, 2));
        fixture.menu.setBatchCount(2);
        Assertions.assertEquals(2, fixture.menu.getBatchCount());
        fixture.menu.getRealOutputMarkerSlot().set(new ItemStack(Items.DIAMOND, 3));
        fixture.menu.encode();
        fixture.assertEncodingError("invalid_batch");
        Assertions.assertTrue(ItemStack.matches(before, fixture.host.getItemStack()));
    }

    @Test
    void sameItemWithDifferentComponentsIsAllowedAsMarkerAndFakeOutput() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.fakeRecipe();
        ItemStack namedPaper = new ItemStack(Items.PAPER);
        namedPaper.set(DataComponents.CUSTOM_NAME, Component.literal("real"));
        fixture.menu.getRealOutputMarkerSlot().set(namedPaper);
        fixture.menu.encode();
        Assertions.assertTrue(Pp2mpPatternItem.hasPatternData(fixture.host.getItemStack()));
        GenericStack marker = fixture.host.getItemStack().get(ComponentRegistry.REAL_OUTPUT_MARKER);
        Assertions.assertNotNull(marker);
        Assertions.assertEquals(AEItemKey.of(namedPaper), marker.what());
    }

    @Test
    void hidingMarkerNeverMovesItToAnOutputAndOrdinaryEncodingDropsTheComponent() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.fakeRecipe();
        fixture.menu.getRealOutputMarkerSlot().set(new ItemStack(Items.DIAMOND, 4));
        fixture.menu.encode();
        fixture.menu.setOutputMode(0, MaterialOutputMode.NORMAL);
        Assertions.assertFalse(fixture.menu.hasFakeInputs());
        Assertions.assertNotNull(fixture.menu.getRealOutputMarker());
        Assertions.assertTrue(fixture.menu.getProcessingOutputSlots()[1].getItem().isEmpty());
        fixture.menu.encode();
        Assertions.assertFalse(fixture.host.getItemStack().has(ComponentRegistry.REAL_OUTPUT_MARKER));
        fixture.menu.setOutputMode(0, MaterialOutputMode.FAKE_CRAFTING_CONVERTER);
        Assertions.assertTrue(fixture.menu.hasFakeInputs());
        Assertions.assertNotNull(fixture.menu.getRealOutputMarker());
        fixture.menu.resetRecipeConfiguration();
        Assertions.assertNull(fixture.menu.getRealOutputMarker());
        fixture.fakeRecipe();
        fixture.menu.getRealOutputMarkerSlot().set(new ItemStack(Items.DIAMOND));
        fixture.menu.clear();
        Assertions.assertNull(fixture.menu.getRealOutputMarker());
        Assertions.assertFalse(fixture.menu.hasFakeInputs());
    }

    @Test
    void outputRotationDoesNotRotateMarkerAndBatchLimitIncludesMarkerAmount() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.fakeRecipe();
        fixture.menu.getProcessingInputSlots()[0].set(new ItemStack(Items.GOLD_INGOT, 12));
        fixture.menu.getProcessingOutputSlots()[0].set(new ItemStack(Items.PAPER, 12));
        fixture.menu.getProcessingOutputSlots()[1].set(new ItemStack(Items.EMERALD, 12));
        fixture.menu.getRealOutputMarkerSlot().set(new ItemStack(Items.DIAMOND, 8));
        fixture.menu.broadcastChanges();
        Assertions.assertEquals(4, fixture.menu.getMaximumBatchCount());
        fixture.menu.cycleProcessingOutput();
        Assertions.assertEquals(new GenericStack(AEItemKey.of(Items.DIAMOND), 8), fixture.menu.getRealOutputMarker());
        Assertions.assertEquals(AEItemKey.of(Items.EMERALD),
                AEItemKey.of(fixture.menu.getProcessingOutputSlots()[0].getItem()));
    }

    @Test
    void multipleFakeInputsKeepMarkerAvailableUntilTheLastOneIsRemoved() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.fakeRecipe();
        fixture.menu.getRealOutputMarkerSlot().set(new ItemStack(Items.DIAMOND));
        fixture.menu.getProcessingInputSlots()[2].set(new ItemStack(Items.IRON_INGOT));
        fixture.menu.setOutputMode(2, MaterialOutputMode.FAKE_CRAFTING_CONVERTER);
        fixture.menu.getProcessingInputSlots()[0].set(ItemStack.EMPTY);
        Assertions.assertTrue(fixture.menu.hasFakeInputs());
        fixture.menu.getProcessingInputSlots()[2].set(ItemStack.EMPTY);
        Assertions.assertFalse(fixture.menu.hasFakeInputs());
        Assertions.assertNotNull(fixture.menu.getRealOutputMarker());
        Assertions.assertEquals(1, fixture.menu.getSlots(Pp2mpEncodingTermMenu.REAL_OUTPUT_MARKER).size());
    }

    @Test
    void successfulEncodingConsumesExactlyOneBlankAndNoMarkerItems() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.fakeRecipe();
        fixture.host.getItemStack().setCount(3);
        ItemStack carried = new ItemStack(Items.DIAMOND, 4);
        fixture.menu.setCarried(carried);
        fixture.menu.setFilter(fixture.menu.getRealOutputMarkerSlot().index, carried);
        fixture.menu.encode();
        Assertions.assertEquals(2, fixture.host.getItemStack().getCount());
        Assertions.assertFalse(Pp2mpPatternItem.hasPatternData(fixture.host.getItemStack()));
        Assertions.assertEquals(4, carried.getCount());
        List<ItemStack> results = fixture.player.getInventory().items.stream()
                .filter(Pp2mpPatternItem::hasPatternData).toList();
        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals(1, results.getFirst().getCount());
        Assertions.assertEquals(new GenericStack(AEItemKey.of(Items.DIAMOND), 4),
                results.getFirst().get(ComponentRegistry.REAL_OUTPUT_MARKER));
    }

    @Test
    void nativeSlotSyncRetainsLargeMarkerBeforeOutputModeSyncArrives() throws Exception {
        Fixture server = new Fixture(false);
        Fixture client = new Fixture(false);
        server.fakeRecipe();
        GenericStack marker = new GenericStack(AEItemKey.of(Items.DIAMOND), 4096);
        server.menu.getRealOutputMarkerSlot().set(GenericStack.wrapInItemStack(marker));
        client.menu.initializeContents(1, server.menu.slots.stream().map(slot -> slot.getItem().copy()).toList(),
                ItemStack.EMPTY);
        Assertions.assertEquals(marker, client.menu.getRealOutputMarker());
        Assertions.assertFalse(client.menu.hasFakeInputs());
        server.menu.broadcastChanges();
        Field syncField = AEBaseMenu.class.getDeclaredField("dataSync");
        syncField.setAccessible(true);
        DataSynchronization synchronization = DataSynchronization.class.cast(syncField.get(server.menu));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), ConnectionType.NEOFORGE);
        try {
            synchronization.writeFull(buffer);
            client.menu.receiveServerSyncData(buffer);
            Assertions.assertTrue(client.menu.hasFakeInputs());
            Assertions.assertEquals(marker, client.menu.getRealOutputMarker());
        } finally {
            buffer.release();
        }
    }

    private static final class Fixture {
        private final Player player = Mockito.mock(Player.class);
        private final Level level = Mockito.mock(Level.class);
        private final InWorldGridNode node = Mockito.mock(InWorldGridNode.class);
        private final IInWorldGridNodeHost nodeHost = Mockito.mock(IInWorldGridNodeHost.class);
        private final IGrid grid = Mockito.mock(IGrid.class);
        private final GenericStackInv stock = new GenericStackInv(
                AEKeyTypes.getAll(), () -> {
        }, GenericStackInv.Mode.STORAGE, 1);
        private final Pp2mpPatternMenuHost host;
        private final Pp2mpEncodingTermMenu menu;
        private final MEStorage inventory;
        private final IEnergySource energy;
        private double availableEnergy = 100;

        private Fixture(boolean connected) throws Exception {
            Inventory playerInventory = new Inventory(this.player);
            RecipeManager recipes = Mockito.mock(RecipeManager.class);
            Mockito.when(this.player.level()).thenReturn(this.level);
            Mockito.when(this.player.getCommandSenderWorld()).thenReturn(this.level);
            Mockito.when(this.player.getInventory()).thenReturn(playerInventory);
            Mockito.when(this.level.getRecipeManager()).thenReturn(recipes);
            Pp2mpPatternItem item = ItemRegistry.PP2MP_PATTERN.get();
            playerInventory.setItem(0, new ItemStack(item));
            this.stock.setStack(0, new GenericStack(Pp2mpPatternMenuHostTest.MATERIAL, 64));
            IStorageService storageService = Mockito.mock(IStorageService.class);
            IEnergyService energyService = Mockito.mock(IEnergyService.class);
            ICraftingService craftingService = Mockito.mock(ICraftingService.class);
            Mockito.when(craftingService.getCpus()).thenReturn(ImmutableSet.of());
            Mockito.when(this.grid.getCraftingService()).thenReturn(craftingService);
            Mockito.when(storageService.getInventory()).thenReturn(this.stock);
            Mockito.when(this.grid.getStorageService()).thenReturn(storageService);
            Mockito.when(this.grid.getEnergyService()).thenReturn(energyService);
            Mockito.when(this.node.getGrid()).thenReturn(this.grid);
            Mockito.when(this.node.isActive()).thenReturn(true);
            Mockito.when(this.node.isExposedOnSide(Direction.UP)).thenReturn(true);
            Mockito.when(this.nodeHost.getGridNode(Direction.UP)).thenReturn(this.node);
            Mockito.when(this.level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    Pp2mpPatternMenuHostTest.TARGET, null)).thenReturn(this.nodeHost);
            Mockito.when(energyService.extractAEPower(Mockito.anyDouble(), Mockito.any(), Mockito.any()))
                    .thenAnswer(invocation -> {
                        double requested = invocation.getArgument(0, Double.class);
                        double supplied = Math.min(requested, this.availableEnergy);
                        if (invocation.getArgument(1, Actionable.class) == Actionable.MODULATE) {
                            this.availableEnergy -= supplied;
                        }
                        return supplied;
                    });
            BlockHitResult hit = connected ? new BlockHitResult(Vec3.atCenterOf(Pp2mpPatternMenuHostTest.TARGET),
                    Direction.UP, Pp2mpPatternMenuHostTest.TARGET, false) : null;
            this.host = new Pp2mpPatternMenuHost(item, this.player, MenuLocators.forInventorySlot(0), hit);
            this.menu = new Pp2mpEncodingTermMenu(1, playerInventory, this.host);
            this.inventory = this.host.getInventory();
            Field energyField = MEStorageMenu.class.getDeclaredField("energySource");
            energyField.setAccessible(true);
            this.energy = IEnergySource.class.cast(energyField.get(this.menu));
        }

        private void setDistanceSquared(double distance) {
            Mockito.when(this.player.distanceToSqr(2.5, 3.5, 4.5)).thenReturn(distance);
        }

        private void fakeRecipe() {
            this.menu.getProcessingInputSlots()[0].set(new ItemStack(Items.GOLD_INGOT));
            this.menu.setOutputMode(0, MaterialOutputMode.FAKE_CRAFTING_CONVERTER);
            this.menu.getProcessingOutputSlots()[0].set(new ItemStack(Items.PAPER));
        }

        private void assertEncodingError(String error) {
            Mockito.verify(this.player).displayClientMessage(Mockito.argThat(message ->
                    message.getContents() instanceof TranslatableContents contents
                            && contents.getKey().equals("message.ae2_batchcraft.pattern.encode." + error)),
                    Mockito.eq(false));
        }

        private long extract(long amount) {
            return StorageHelper.poweredExtraction(this.energy, this.inventory,
                    Pp2mpPatternMenuHostTest.MATERIAL, amount, this.menu.getActionSource());
        }

        private void assertDisconnected() {
            Assertions.assertNull(this.host.getActionableNode());
            Assertions.assertNull(this.host.getGrid());
            Assertions.assertFalse(this.host.getLinkStatus().connected());
            Assertions.assertTrue(this.inventory.getAvailableStacks().isEmpty());
            Assertions.assertEquals(0, this.energy.extractAEPower(8, Actionable.MODULATE, PowerMultiplier.ONE));
            Assertions.assertEquals(0, this.extract(8));
            Assertions.assertEquals(64, this.stock.getAvailableStacks().get(Pp2mpPatternMenuHostTest.MATERIAL));
            Assertions.assertEquals(100, this.availableEnergy);
        }
    }
}
