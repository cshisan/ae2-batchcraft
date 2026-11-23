package org.ae2bc.placer;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardColors;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.datagen.providers.tags.ConventionTags;
import appeng.helpers.IPriorityHost;
import appeng.items.tools.MemoryCardItem;
import appeng.me.service.P2PService;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.parts.AEBasePart;
import appeng.parts.p2p.ItemP2PTunnelPart;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.util.SettingsFrom;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitTransferPortPart;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.registry.component.ComponentPlacerRegistry;
import org.ae2bc.registry.component.Pp2mpUnitManagerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

class ComponentPlacerMemoryCardTest {
    private static final String SETTINGS_TAG = "data/ae2/tags/data_component_type/exported_settings.json";
    private static Map<TagKey<DataComponentType<?>>, List<Holder<DataComponentType<?>>>> originalTags;

    /* 单元测试不启动数据包加载，按实际资源合并 AE2 与本模组的清理标签。 */
    @BeforeAll
    static void loadExportedSettingsTag() throws IOException {
        ComponentPlacerMemoryCardTest.originalTags = new HashMap<>();
        for (Pair<TagKey<DataComponentType<?>>, HolderSet.Named<DataComponentType<?>>> entry
                : BuiltInRegistries.DATA_COMPONENT_TYPE.getTags().toList()) {
            ComponentPlacerMemoryCardTest.originalTags.put(entry.getFirst(), entry.getSecond().stream().toList());
        }
        Set<Holder<DataComponentType<?>>> exported = new LinkedHashSet<>();
        try (InputStream input = Objects.requireNonNull(
                MemoryCardItem.class.getResourceAsStream("/" + SETTINGS_TAG))) {
            ComponentPlacerMemoryCardTest.addSettings(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8), exported);
        }
        ComponentPlacerMemoryCardTest.addSettings(Files.readString(
                ComponentPlacerMemoryCardTest.findModule().resolve("src/main/resources/" + SETTINGS_TAG)), exported);
        Assertions.assertTrue(exported.contains(BuiltInRegistries.DATA_COMPONENT_TYPE.wrapAsHolder(
                AEComponents.EXPORTED_P2P_FREQUENCY)));
        Map<TagKey<DataComponentType<?>>, List<Holder<DataComponentType<?>>>> tags =
                new HashMap<>(ComponentPlacerMemoryCardTest.originalTags);
        tags.put(ConventionTags.EXPORTED_SETTINGS, List.copyOf(exported));
        BuiltInRegistries.DATA_COMPONENT_TYPE.bindTags(tags);
    }

    @AfterAll
    static void restoreTags() {
        if (ComponentPlacerMemoryCardTest.originalTags != null) {
            BuiltInRegistries.DATA_COMPONENT_TYPE.resetTags();
            ComponentPlacerMemoryCardTest.originalTags.putIfAbsent(ConventionTags.EXPORTED_SETTINGS, List.of());
            BuiltInRegistries.DATA_COMPONENT_TYPE.bindTags(ComponentPlacerMemoryCardTest.originalTags);
        }
    }

    @Test
    void frequencyModeLeavesNativeBlockInteractionAndRestrictionsIntact() {
        Fixture fixture = new Fixture();
        ComponentPlacerData.frequencyMode(fixture.stack, true);
        Assertions.assertInstanceOf(IMemoryCard.class, fixture.stack.getItem());
        for (boolean shift : List.of(false, true)) {
            fixture.shift(shift);
            PlayerInteractEvent.RightClickBlock event = fixture.event(InteractionHand.MAIN_HAND);
            ComponentPlacerItem.onRightClickBlock(event);
            Assertions.assertFalse(event.isCanceled());
            Assertions.assertEquals(TriState.DEFAULT, event.getUseBlock());
            Assertions.assertTrue(fixture.item.doesSneakBypassUse(
                    fixture.stack, fixture.level, BlockPos.ZERO, fixture.player));
            Assertions.assertNull(ComponentPlacerData.selection(fixture.stack));
        }
        PlayerInteractEvent.RightClickBlock denied = fixture.event(InteractionHand.MAIN_HAND);
        denied.setUseBlock(TriState.FALSE);
        ComponentPlacerItem.onRightClickBlock(denied);
        Assertions.assertEquals(TriState.FALSE, denied.getUseBlock());
    }

    @Test
    void placementModeStillSelectsWithEitherHandAndConsumesTheInteraction() {
        for (InteractionHand hand : InteractionHand.values()) {
            Fixture fixture = new Fixture();
            ComponentPlacerData.pose(fixture.stack, ComponentPlacerPose.DEFAULT
                    .withDirection(Direction.NORTH).withOffsets(2, 3, 4));
            fixture.hold(hand);
            fixture.shift(true);
            PlayerInteractEvent.RightClickBlock event = fixture.event(InteractionHand.MAIN_HAND);
            ComponentPlacerItem.onRightClickBlock(event);
            Assertions.assertTrue(event.isCanceled());
            Assertions.assertEquals(InteractionResult.CONSUME, event.getCancellationResult());
            ComponentPlacerSelection selection = ComponentPlacerData.selection(fixture.stack);
            Assertions.assertNotNull(selection);
            Assertions.assertEquals(BlockPos.ZERO, selection.first());
            ComponentPlacerPose pose = ComponentPlacerData.pose(fixture.stack);
            Assertions.assertEquals(ComponentPlacerPose.DEFAULT.pos(), pose.pos());
            Assertions.assertEquals(Direction.NORTH, pose.direction());
            fixture.shift(false);
            ComponentPlacerItem.onRightClickBlock(fixture.event(InteractionHand.MAIN_HAND));
            ComponentPlacerSelection completed = ComponentPlacerData.selection(fixture.stack);
            Assertions.assertNotNull(completed);
            Assertions.assertEquals(List.of(BlockPos.ZERO), completed.positions(pose));
            Assertions.assertFalse(fixture.item.doesSneakBypassUse(
                    fixture.stack, fixture.level, BlockPos.ZERO, fixture.player));
        }
    }

    @Test
    void vanillaP2pSavesAndLoadsFrequencyAndOutputRole() {
        Fixture fixture = new Fixture();
        ComponentPlacerData.frequencyMode(fixture.stack, true);
        ItemP2PTunnelPart source = AEParts.ITEM_P2P_TUNNEL.asItem().createPart();
        fixture.install(source);
        source.setFrequency((short) 1234);
        fixture.shift(true);
        Assertions.assertTrue(source.onUseItemOn(fixture.stack, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
        Assertions.assertEquals((short) 1234, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertSame(AEParts.ITEM_P2P_TUNNEL.asItem(), fixture.stack.get(AEComponents.EXPORTED_P2P_TYPE));
        Assertions.assertFalse(source.isOutput());

        ItemP2PTunnelPart target = AEParts.ITEM_P2P_TUNNEL.asItem().createPart();
        fixture.install(target);
        fixture.shift(false);
        Assertions.assertTrue(target.onUseItemOn(fixture.stack, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
        Assertions.assertEquals((short) 1234, target.getFrequency());
        Assertions.assertTrue(target.isOutput());
        source.onUseItemOn(fixture.stack, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        Assertions.assertFalse(source.isOutput(), "加载自身频率不能把输入端变成输出端");
    }

    @Test
    void vanillaP2pCanReplaceTheTargetTunnelType() {
        Fixture fixture = new Fixture();
        fixture.stack.set(AEComponents.EXPORTED_P2P_TYPE, AEParts.ITEM_P2P_TUNNEL.asItem());
        fixture.stack.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 2345);
        MEP2PTunnelPart target = AEParts.ME_P2P_TUNNEL.asItem().createPart();
        IPartHost host = fixture.install(target);
        ItemP2PTunnelPart replacement = AEParts.ITEM_P2P_TUNNEL.asItem().createPart();
        fixture.install(replacement);
        Mockito.when(host.replacePart(
                AEParts.ITEM_P2P_TUNNEL.asItem(), Direction.NORTH, fixture.player, InteractionHand.MAIN_HAND))
                .thenReturn(replacement);
        Assertions.assertTrue(target.onUseItemOn(fixture.stack, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
        Mockito.verify(host).replacePart(
                AEParts.ITEM_P2P_TUNNEL.asItem(), Direction.NORTH, fixture.player, InteractionHand.MAIN_HAND);
        Assertions.assertEquals((short) 2345, replacement.getFrequency());
        Assertions.assertTrue(replacement.isOutput());
    }

    @Test
    void genericMemoryCardSettingsReplacePreviousFrequencyAndUnitId() {
        Fixture fixture = new Fixture();
        fixture.stack.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 1234);
        fixture.stack.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 5678);
        AEBasePart source = AEParts.STORAGE_BUS.asItem().createPart();
        fixture.install(source);
        IPriorityHost sourcePriority = Assertions.assertInstanceOf(IPriorityHost.class, source);
        sourcePriority.setPriority(42);
        fixture.shift(true);
        Assertions.assertTrue(source.onUseItemOn(fixture.stack, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
        Assertions.assertEquals(0, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertEquals(0, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertEquals(42, fixture.stack.get(AEComponents.EXPORTED_PRIORITY));

        AEBasePart target = AEParts.STORAGE_BUS.asItem().createPart();
        fixture.install(target);
        fixture.shift(false);
        Assertions.assertTrue(target.onUseItemOn(fixture.stack, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
        Assertions.assertEquals(42, Assertions.assertInstanceOf(IPriorityHost.class, target).getPriority());
    }

    @Test
    void managerExportsUnitIdThroughTheExistingMemoryCardHandler() {
        Fixture fixture = new Fixture();
        Pp2mpUnitManagerPart manager = fixture.manager(AEColor.WHITE, (short) 1234, (short) 5678);
        DataComponentMap settings = manager.exportSettings(SettingsFrom.MEMORY_CARD);
        Assertions.assertEquals((short) 1234, settings.get(AEComponents.EXPORTED_P2P_FREQUENCY));
        Assertions.assertEquals((short) 5678, settings.get(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID));
        Assertions.assertSame(manager.getPartItem().asItem(), settings.get(AEComponents.EXPORTED_P2P_TYPE));
        DataComponentMap dismantled = manager.exportSettings(SettingsFrom.DISMANTLE_ITEM);
        Assertions.assertNull(dismantled.get(AEComponents.EXPORTED_P2P_TYPE));
        Assertions.assertNull(dismantled.get(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID));
        fixture.shift(true);
        Assertions.assertTrue(manager.onUseItemOn(fixture.stack, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
        Assertions.assertEquals((short) 1234, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertEquals((short) 5678, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertEquals((short) 5678, ComponentPlacerData.createDataModel(fixture.stack, false).unitId());
        Assertions.assertSame(manager.getPartItem().asItem(), fixture.stack.get(AEComponents.EXPORTED_P2P_TYPE));
        Assertions.assertEquals(manager.getPartItem().asItem().getDescription(),
                fixture.stack.get(AEComponents.EXPORTED_SETTINGS_SOURCE));
    }

    @Test
    void managerLoadsAcrossColorsAndPreservesItsOwnUnitIdWithEitherCard() {
        Fixture fixture = new Fixture();
        Pp2mpUnitManagerPart source = fixture.manager(AEColor.WHITE, (short) 1234, (short) 5678);
        for (ItemStack card : List.of(fixture.stack, AEItems.MEMORY_CARD.stack())) {
            Pp2mpUnitManagerPart target = fixture.manager(AEColor.RED, (short) 4321, (short) 8765);
            fixture.shift(true);
            Assertions.assertTrue(source.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            fixture.shift(false);
            Assertions.assertTrue(target.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            Assertions.assertEquals((short) 1234, target.getFrequency());
            Assertions.assertEquals((short) 8765, target.getUnitId());
            Assertions.assertSame(ItemRegistry.PP2MP_UNIT_MANAGERS.get(AEColor.RED).get(), target.getPartItem());
        }
    }

    @Test
    void managerLoadsInputFrequencyButRejectsChangesWhileWorking() {
        Fixture fixture = new Fixture();
        Pp2mpInputPart input = Mockito.spy(ItemRegistry.PP2MP_INPUT.get().createPart());
        fixture.install(input);
        IGrid grid = Mockito.mock(IGrid.class);
        Mockito.when(grid.getService(P2PService.class)).thenReturn(Mockito.mock(P2PService.class));
        Mockito.doReturn(grid).when(input).grid();
        input.setFrequency((short) 1234);
        for (ItemStack card : List.of(fixture.stack, AEItems.MEMORY_CARD.stack())) {
            Pp2mpUnitManagerPart manager = fixture.manager(AEColor.WHITE, (short) 4321, (short) 5678);
            fixture.shift(true);
            Assertions.assertTrue(input.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            Assertions.assertSame(ItemRegistry.PP2MP_INPUT.get(), card.get(AEComponents.EXPORTED_P2P_TYPE));
            Assertions.assertEquals((short) 1234, card.get(AEComponents.EXPORTED_P2P_FREQUENCY));
            fixture.shift(false);
            manager.getData().begin(Map.of(), null, UUID.randomUUID(), null);
            Assertions.assertTrue(manager.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            Assertions.assertEquals((short) 4321, manager.getFrequency());
            Assertions.assertEquals((short) 5678, manager.getUnitId());
            manager.getData().getTasks().clear();
            manager.getData().afterCompletion();
            Assertions.assertTrue(manager.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            Assertions.assertEquals((short) 1234, manager.getFrequency());
            Assertions.assertEquals((short) 5678, manager.getUnitId());
        }
        Mockito.verify(fixture.player, Mockito.times(2)).displayClientMessage(
                Component.translatable("message.ae2_batchcraft.load_frequency.working"), true);
    }

    @Test
    void managerRejectsIncompleteOrUnrelatedCards() {
        Fixture fixture = new Fixture();
        Pp2mpUnitManagerPart manager = fixture.manager(AEColor.WHITE, (short) 4321, (short) 5678);
        for (DataComponentMap settings : List.of(
                DataComponentMap.EMPTY,
                DataComponentMap.builder().set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 1234).build(),
                DataComponentMap.builder().set(AEComponents.EXPORTED_P2P_TYPE, ItemRegistry.PP2MP_INPUT.get()).build(),
                DataComponentMap.builder()
                        .set(AEComponents.EXPORTED_P2P_TYPE, AEParts.ITEM_P2P_TUNNEL.asItem())
                        .set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 1234).build()
        )) {
            ItemStack card = AEItems.MEMORY_CARD.stack();
            card.applyComponents(settings);
            Assertions.assertTrue(manager.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            Assertions.assertEquals((short) 4321, manager.getFrequency());
            Assertions.assertEquals((short) 5678, manager.getUnitId());
        }
    }

    @Test
    void unboundManagerSaveClearsPreviousFrequencyAndOtherSettings() {
        Fixture fixture = ComponentPlacerMemoryCardTest.savedSettings();
        Pp2mpUnitManagerPart manager = fixture.manager(AEColor.WHITE, (short) 0, (short) 6789);
        fixture.stack.set(AEComponents.EXPORTED_PRIORITY, 42);
        fixture.shift(true);
        Assertions.assertTrue(manager.onUseItemOn(fixture.stack, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
        Assertions.assertNull(fixture.stack.get(AEComponents.EXPORTED_P2P_FREQUENCY));
        Assertions.assertNull(fixture.stack.get(AEComponents.EXPORTED_PRIORITY));
        Assertions.assertNull(fixture.stack.get(AEComponents.MEMORY_CARD_COLORS));
        Assertions.assertFalse(fixture.stack.has(ComponentPlacerRegistry.LEGACY_FREQUENCY));
        Assertions.assertFalse(fixture.stack.has(ComponentPlacerRegistry.LEGACY_UNIT_ID));
        Assertions.assertSame(manager.getPartItem().asItem(), fixture.stack.get(AEComponents.EXPORTED_P2P_TYPE));
        Assertions.assertEquals((short) 6789, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertEquals(100, fixture.item.getAECurrentPower(fixture.stack));
    }

    @Test
    void managerCardsBindPortsWithoutAddingPortSaveBehavior() {
        Fixture fixture = new Fixture();
        Pp2mpUnitManagerPart manager = fixture.manager(AEColor.WHITE, (short) 1234, (short) 5678);
        IGrid grid = Mockito.mock(IGrid.class);
        Pp2mpGridService service = Mockito.mock(Pp2mpGridService.class);
        Mockito.when(grid.getService(Pp2mpGridService.class)).thenReturn(service);
        Mockito.when(service.findManager((short) 1234, (short) 5678)).thenReturn(manager);
        for (ItemStack card : List.of(fixture.stack, AEItems.MEMORY_CARD.stack())) {
            Pp2mpUnitTransferPortPart port = Mockito.spy(ItemRegistry.PP2MP_UNIT_PORT_TRANSFER.get().createPart());
            fixture.install(port);
            Mockito.doReturn(grid).when(port).grid();
            fixture.shift(true);
            Assertions.assertTrue(manager.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            fixture.shift(false);
            Assertions.assertTrue(port.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            Assertions.assertEquals((short) 1234, port.getFrequency());
            Assertions.assertEquals((short) 5678, port.getUnitId());
            ItemStack saved = card.copy();
            fixture.shift(true);
            Assertions.assertTrue(port.onUseItemOn(card, fixture.player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            Assertions.assertTrue(ItemStack.isSameItemSameComponents(saved, card));
        }
        Mockito.verify(service, Mockito.times(2)).topologyChanged();
    }

    @Test
    void clearingSettingsPreservesTheToolsEnergyUpgradesLinkAndSelection() {
        Fixture fixture = new Fixture();
        ComponentPlacerData.frequencyMode(fixture.stack, true);
        fixture.item.injectAEPower(fixture.stack, 100, Actionable.MODULATE);
        fixture.item.getUpgrades(fixture.stack).setItemDirect(0, AEItems.CRAFTING_CARD.stack());
        GlobalPos link = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3));
        fixture.stack.set(AEComponents.WIRELESS_LINK_TARGET, link);
        ComponentPlacerSelection selection = ComponentPlacerSelection.start(
                Level.OVERWORLD.location(), new BlockPos(4, 5, 6));
        ComponentPlacerData.selection(fixture.stack, selection);
        ComponentPlacerPose pose = ComponentPlacerPose.DEFAULT.withOffsets(2, 3, 4);
        ComponentPlacerData.pose(fixture.stack, pose);
        fixture.stack.set(ComponentPlacerRegistry.LEGACY_FREQUENCY, (short) 1);
        fixture.stack.set(ComponentPlacerRegistry.LEGACY_UNIT_ID, (short) 2);
        fixture.stack.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 1234);
        fixture.stack.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 5678);
        fixture.shift(true);
        UseOnContext context = new UseOnContext(fixture.player, InteractionHand.MAIN_HAND, Fixture.HIT);
        Assertions.assertEquals(InteractionResult.CONSUME, fixture.item.useOn(context));
        Assertions.assertEquals(0, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertEquals(0, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertFalse(fixture.stack.has(ComponentPlacerRegistry.LEGACY_FREQUENCY));
        Assertions.assertFalse(fixture.stack.has(ComponentPlacerRegistry.LEGACY_UNIT_ID));
        Assertions.assertEquals(100, fixture.item.getAECurrentPower(fixture.stack));
        Assertions.assertEquals(1, fixture.item.getUpgrades(fixture.stack).getInstalledUpgrades(AEItems.CRAFTING_CARD));
        Assertions.assertEquals(link, fixture.stack.get(AEComponents.WIRELESS_LINK_TARGET));
        Assertions.assertEquals(selection, ComponentPlacerData.selection(fixture.stack));
        Assertions.assertEquals(pose, ComponentPlacerData.pose(fixture.stack));
        Assertions.assertTrue(ComponentPlacerData.frequencyMode(fixture.stack));
    }

    @Test
    void legacyNumbersDoNotMasqueradeAsACompleteMemoryCard() {
        Fixture fixture = new Fixture();
        fixture.stack.set(ComponentPlacerRegistry.LEGACY_FREQUENCY, (short) 1234);
        fixture.stack.set(ComponentPlacerRegistry.LEGACY_UNIT_ID, (short) 5678);
        Assertions.assertEquals(0, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertEquals(0, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertNull(fixture.stack.get(AEComponents.EXPORTED_P2P_TYPE));
    }

    @Test
    void menuClearsOnlyFrequencyAndRefreshesTheSnapshot() {
        Fixture fixture = ComponentPlacerMemoryCardTest.savedSettings();
        ComponentPlacerMenu menu = fixture.menu();
        menu.receiveClientAction("clearFrequency", null);
        menu.broadcastChanges();
        Assertions.assertEquals(0, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertFalse(fixture.stack.has(AEComponents.MEMORY_CARD_COLORS));
        Assertions.assertFalse(fixture.stack.has(ComponentPlacerRegistry.LEGACY_FREQUENCY));
        Assertions.assertEquals((short) 5678, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertSame(AEParts.ITEM_P2P_TUNNEL.asItem(), fixture.stack.get(AEComponents.EXPORTED_P2P_TYPE));
        Assertions.assertEquals(0, menu.getModel().frequency());
        Assertions.assertEquals((short) 5678, menu.getModel().unitId());
        Assertions.assertEquals(100, fixture.item.getAECurrentPower(fixture.stack));
        menu.receiveClientAction("clearFrequency", null);
        Assertions.assertEquals((short) 5678, ComponentPlacerData.unitId(fixture.stack));
    }

    @Test
    void menuClearsOnlyUnitIdAndRefreshesTheSnapshot() {
        Fixture fixture = ComponentPlacerMemoryCardTest.savedSettings();
        ComponentPlacerMenu menu = fixture.menu();
        menu.receiveClientAction("clearUnitId", null);
        menu.broadcastChanges();
        Assertions.assertEquals(0, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertFalse(fixture.stack.has(ComponentPlacerRegistry.LEGACY_UNIT_ID));
        Assertions.assertEquals((short) 1234, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertTrue(fixture.stack.has(AEComponents.MEMORY_CARD_COLORS));
        Assertions.assertSame(AEParts.ITEM_P2P_TUNNEL.asItem(), fixture.stack.get(AEComponents.EXPORTED_P2P_TYPE));
        Assertions.assertEquals((short) 1234, menu.getModel().frequency());
        Assertions.assertEquals(0, menu.getModel().unitId());
        Assertions.assertEquals(100, fixture.item.getAECurrentPower(fixture.stack));
        menu.receiveClientAction("clearUnitId", null);
        Assertions.assertEquals((short) 1234, ComponentPlacerData.frequency(fixture.stack));
    }

    @Test
    void menuLoadsOnlySelectedValueAndPreservesTheCardAndOtherToolSettings() {
        for (String action : List.of("loadFrequency", "loadUnitId")) {
            Fixture fixture = ComponentPlacerMemoryCardTest.savedSettings();
            Mockito.when(fixture.level.getServer()).thenReturn(Mockito.mock(MinecraftServer.class));
            fixture.stack.set(AEComponents.EXPORTED_PRIORITY, 42);
            fixture.stack.set(AEComponents.WIRELESS_LINK_TARGET,
                    GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3)));
            fixture.item.getUpgrades(fixture.stack).setItemDirect(0, AEItems.CRAFTING_CARD.stack());
            ComponentPlacerData.selection(fixture.stack,
                    ComponentPlacerSelection.start(Level.OVERWORLD.location(), new BlockPos(4, 5, 6)));
            ComponentPlacerData.pose(fixture.stack, ComponentPlacerPose.DEFAULT.withOffsets(2, 3, 4));
            ItemStack card = AEItems.MEMORY_CARD.stack();
            card.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 0xABCD);
            card.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 0xEF01);
            card.set(AEComponents.EXPORTED_P2P_TYPE, ItemRegistry.PP2MP_INPUT.get());
            card.set(AEComponents.EXPORTED_PRIORITY, 99);
            MemoryCardColors colors = new MemoryCardColors(
                    AEColor.BLACK, AEColor.BLACK, AEColor.RED, AEColor.GREEN,
                    AEColor.BLUE, AEColor.WHITE, AEColor.YELLOW, AEColor.ORANGE);
            card.set(AEComponents.MEMORY_CARD_COLORS, colors);
            ItemStack originalCard = card.copy();
            ItemStack expected = fixture.stack.copy();
            if (action.equals("loadFrequency")) {
                expected.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 0xABCD);
                expected.set(AEComponents.EXPORTED_P2P_TYPE, ItemRegistry.PP2MP_INPUT.get());
                expected.set(AEComponents.MEMORY_CARD_COLORS, colors);
                expected.remove(ComponentPlacerRegistry.LEGACY_FREQUENCY);
            } else {
                expected.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 0xEF01);
                expected.remove(ComponentPlacerRegistry.LEGACY_UNIT_ID);
            }
            ComponentPlacerMenu menu = fixture.menu();
            menu.setCarried(card);

            menu.receiveClientAction(action, null);
            menu.broadcastChanges();

            Assertions.assertTrue(ItemStack.isSameItemSameComponents(expected, fixture.stack), action);
            Assertions.assertSame(card, menu.getCarried());
            Assertions.assertEquals(originalCard.getCount(), card.getCount());
            Assertions.assertTrue(ItemStack.isSameItemSameComponents(originalCard, card));
            Assertions.assertEquals(ComponentPlacerData.frequency(expected), menu.getModel().frequency());
            Assertions.assertEquals(ComponentPlacerData.unitId(expected), menu.getModel().unitId());
        }
    }

    @Test
    void menuIgnoresNonCardsMissingValuesAndZeroValuesWithoutClearingSettings() {
        Fixture fixture = ComponentPlacerMemoryCardTest.savedSettings();
        ComponentPlacerMenu menu = fixture.menu();
        ItemStack original = fixture.stack.copy();
        ItemStack nonCard = new ItemStack(Items.STONE);
        nonCard.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 4321);
        nonCard.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 8765);
        ItemStack zeroCard = AEItems.MEMORY_CARD.stack();
        zeroCard.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 0);
        zeroCard.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 0);
        for (String action : List.of("loadFrequency", "loadUnitId")) {
            ItemStack otherValueCard = AEItems.MEMORY_CARD.stack();
            otherValueCard.set(action.equals("loadFrequency")
                    ? Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID
                    : AEComponents.EXPORTED_P2P_FREQUENCY, (short) 9999);
            for (ItemStack card : List.of(ItemStack.EMPTY, nonCard, AEItems.MEMORY_CARD.stack(),
                    otherValueCard, zeroCard)) {
                ItemStack originalCard = card.copy();
                menu.setCarried(card);
                menu.receiveClientAction(action, null);
                menu.broadcastChanges();
                Assertions.assertTrue(ItemStack.isSameItemSameComponents(original, fixture.stack), action);
                Assertions.assertEquals((short) 1234, menu.getModel().frequency());
                Assertions.assertEquals((short) 5678, menu.getModel().unitId());
                Assertions.assertEquals(originalCard.getCount(), menu.getCarried().getCount());
                Assertions.assertTrue(ItemStack.isSameItemSameComponents(originalCard, menu.getCarried()));
            }
        }
    }

    @Test
    void loadingFrequencyWithoutSourceMetadataRemovesStaleTypeAndColors() {
        Fixture fixture = ComponentPlacerMemoryCardTest.savedSettings();
        ComponentPlacerMenu menu = fixture.menu();
        ItemStack card = AEItems.MEMORY_CARD.stack();
        card.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 4321);
        menu.setCarried(card);

        menu.receiveClientAction("loadFrequency", null);

        Assertions.assertEquals((short) 4321, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertEquals((short) 5678, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertNull(fixture.stack.get(AEComponents.EXPORTED_P2P_TYPE));
        Assertions.assertNull(fixture.stack.get(AEComponents.MEMORY_CARD_COLORS));
    }

    @Test
    void menuLoadsUnitIdFromUnboundManagerCardWithoutChangingFrequency() {
        Fixture fixture = ComponentPlacerMemoryCardTest.savedSettings();
        ComponentPlacerMenu menu = fixture.menu();
        ItemStack card = AEItems.MEMORY_CARD.stack();
        card.set(AEComponents.EXPORTED_P2P_TYPE, ItemRegistry.PP2MP_UNIT_MANAGERS.get(AEColor.WHITE).get());
        card.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 6789);
        menu.setCarried(card);

        menu.receiveClientAction("loadUnitId", null);

        Assertions.assertEquals((short) 6789, ComponentPlacerData.unitId(fixture.stack));
        Assertions.assertEquals((short) 1234, ComponentPlacerData.frequency(fixture.stack));
        Assertions.assertSame(AEParts.ITEM_P2P_TUNNEL.asItem(), fixture.stack.get(AEComponents.EXPORTED_P2P_TYPE));
    }

    @Test
    void menuResetClearsOffsetsAndPreservesDirection() {
        Fixture fixture = new Fixture();
        ComponentPlacerData.pose(fixture.stack, ComponentPlacerPose.DEFAULT
                .withDirection(Direction.NORTH).withOffsets(2, 3, 4));
        ComponentPlacerMenu menu = fixture.menu();
        menu.receiveClientAction("resetOffsets", null);
        menu.broadcastChanges();
        Assertions.assertEquals(ComponentPlacerPose.DEFAULT.pos(), ComponentPlacerData.pose(fixture.stack).pos());
        Assertions.assertEquals(ComponentPlacerPose.DEFAULT.pos(), menu.getModel().pos1());
        Assertions.assertEquals(Direction.NORTH, menu.getDirection());
    }

    private static Fixture savedSettings() {
        Fixture fixture = new Fixture();
        fixture.stack.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 1234);
        fixture.stack.set(AEComponents.EXPORTED_P2P_TYPE, AEParts.ITEM_P2P_TUNNEL.asItem());
        fixture.stack.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 5678);
        fixture.stack.set(ComponentPlacerRegistry.LEGACY_FREQUENCY, (short) 1);
        fixture.stack.set(ComponentPlacerRegistry.LEGACY_UNIT_ID, (short) 2);
        fixture.stack.set(AEComponents.MEMORY_CARD_COLORS, new MemoryCardColors(
                AEColor.WHITE, AEColor.WHITE, AEColor.RED, AEColor.RED,
                AEColor.BLUE, AEColor.BLUE, AEColor.GREEN, AEColor.GREEN));
        fixture.item.injectAEPower(fixture.stack, 100, Actionable.MODULATE);
        return fixture;
    }

    private static void addSettings(String json, Set<Holder<DataComponentType<?>>> exported) {
        for (JsonElement value : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("values")) {
            exported.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getHolder(
                    ResourceLocation.parse(value.getAsString())).orElseThrow());
        }
    }

    private static Path findModule() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            for (Path candidate : List.of(current, current.resolve("neoforge1211"))) {
                if (Files.isRegularFile(candidate.resolve("src/main/resources/" + SETTINGS_TAG))) {
                    return candidate;
                }
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到内存卡清理标签");
    }

    private static final class Fixture {
        private static final BlockHitResult HIT = new BlockHitResult(
                Vec3.ZERO, Direction.NORTH, BlockPos.ZERO, false);
        private final ComponentPlacerItem item = ItemRegistry.COMPONENT_PLACER.get();
        private final ItemStack stack = new ItemStack(this.item);
        private final ServerLevel level = Mockito.mock(ServerLevel.class);
        private final Player player = Mockito.mock(Player.class);

        private Fixture() {
            Mockito.when(this.level.dimension()).thenReturn(Level.OVERWORLD);
            Mockito.when(this.player.level()).thenReturn(this.level);
            Mockito.when(this.player.getCommandSenderWorld()).thenReturn(this.level);
            Mockito.when(this.player.getAbilities()).thenReturn(new Abilities());
            Mockito.when(this.player.getInventory()).thenReturn(new Inventory(this.player));
            this.hold(InteractionHand.MAIN_HAND);
        }

        private void hold(InteractionHand hand) {
            ItemStack main = hand == InteractionHand.MAIN_HAND ? this.stack : ItemStack.EMPTY;
            ItemStack off = hand == InteractionHand.OFF_HAND ? this.stack : ItemStack.EMPTY;
            Mockito.when(this.player.getMainHandItem()).thenReturn(main);
            Mockito.when(this.player.getOffhandItem()).thenReturn(off);
            Mockito.when(this.player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(main);
            Mockito.when(this.player.getItemInHand(InteractionHand.OFF_HAND)).thenReturn(off);
        }

        private void shift(boolean shift) {
            Mockito.when(this.player.isShiftKeyDown()).thenReturn(shift);
            Mockito.when(this.player.isSecondaryUseActive()).thenReturn(shift);
        }

        private PlayerInteractEvent.RightClickBlock event(InteractionHand hand) {
            return new PlayerInteractEvent.RightClickBlock(this.player, hand, BlockPos.ZERO, Fixture.HIT);
        }

        private ComponentPlacerMenu menu() {
            ItemMenuHostLocator locator = Mockito.mock(ItemMenuHostLocator.class);
            Mockito.when(locator.locateItem(this.player)).thenReturn(this.stack);
            ComponentPlacerMenuHost host = new ComponentPlacerMenuHost(
                    this.item, this.player, locator, (player, subMenu) -> { });
            return new ComponentPlacerMenu(1, this.player.getInventory(), host);
        }

        private Pp2mpUnitManagerPart manager(AEColor color, short frequency, short unitId) {
            Pp2mpUnitManagerPart manager = new Pp2mpUnitManagerPart(
                    ItemRegistry.PP2MP_UNIT_MANAGERS.get(color).get());
            this.install(manager);
            manager.applyPlacerAttr(frequency, unitId);
            return manager;
        }

        private IPartHost install(AEBasePart part) {
            IPartHost host = Mockito.mock(IPartHost.class);
            ChestBlockEntity blockEntity = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            blockEntity.setLevel(this.level);
            part.setPartHostInfo(Direction.NORTH, host, blockEntity);
            return host;
        }
    }
}
