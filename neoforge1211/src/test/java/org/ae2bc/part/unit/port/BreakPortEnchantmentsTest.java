package org.ae2bc.part.unit.port;

import appeng.api.behaviors.PickupSink;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.SettingsFrom;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.port.part.Pp2mpUnitBreakPortPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
class BreakPortEnchantmentsTest {
    private static final HolderLookup.Provider REGISTRIES = VanillaRegistries.createLookup();
    private static Map<TagKey<Item>, List<Holder<Item>>> originalTags;

    /* 单元测试不启动数据包加载，合并实际标签资源并在结束时恢复全局注册表。 */
    @BeforeAll
    static void loadTags() throws IOException {
        BreakPortEnchantmentsTest.originalTags = new HashMap<>();
        for (Pair<TagKey<Item>, HolderSet.Named<Item>> entry : BuiltInRegistries.ITEM.getTags().toList()) {
            BreakPortEnchantmentsTest.originalTags.put(entry.getFirst(), entry.getSecond().stream().toList());
        }
        Map<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>(BreakPortEnchantmentsTest.originalTags);
        for (String name : List.of("mining", "mining_loot", "durability")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("enchantable/" + name));
            BreakPortEnchantmentsTest.originalTags.putIfAbsent(tag, List.of());
            Set<Holder<Item>> values = new LinkedHashSet<>(tags.getOrDefault(tag, List.of()));
            Enumeration<URL> resources = BreakPortEnchantmentsTest.class.getClassLoader().getResources(
                    "data/minecraft/tags/item/enchantable/" + name + ".json");
            boolean found = false;
            while (resources.hasMoreElements()) {
                try (InputStream input = resources.nextElement().openStream()) {
                    JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    for (JsonElement value : json.getAsJsonArray("values")) {
                        if (value.isJsonPrimitive()
                                && value.getAsString().equals("ae2_batchcraft:pp2mp_unit_port_break")) {
                            Assertions.assertFalse(json.get("replace").getAsBoolean());
                            values.add(BuiltInRegistries.ITEM.wrapAsHolder(ItemRegistry.PP2MP_UNIT_PORT_BREAK.get()));
                            found = true;
                        }
                    }
                }
            }
            Assertions.assertTrue(found, "缺少破坏端口附魔标签：" + name);
            tags.put(tag, List.copyOf(values));
        }
        BuiltInRegistries.ITEM.bindTags(tags);
    }

    @AfterAll
    static void restoreTags() {
        if (BreakPortEnchantmentsTest.originalTags != null) {
            BuiltInRegistries.ITEM.resetTags();
            BuiltInRegistries.ITEM.bindTags(BreakPortEnchantmentsTest.originalTags);
        }
    }

    @Test
    void nativeEnchantingAndBookRulesRecognizeTheSameMiningEnchantments() {
        ItemStack stack = new ItemStack(ItemRegistry.PP2MP_UNIT_PORT_BREAK.get());
        Assertions.assertTrue(stack.isEnchantable());
        Assertions.assertEquals(10, stack.getEnchantmentValue());
        for (ResourceKey<Enchantment> key : List.of(
                Enchantments.SILK_TOUCH, Enchantments.FORTUNE, Enchantments.EFFICIENCY, Enchantments.UNBREAKING)) {
            Holder<Enchantment> enchantment = BreakPortEnchantmentsTest.REGISTRIES
                    .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
            Assertions.assertTrue(stack.supportsEnchantment(enchantment), key.location().toString());
            Assertions.assertFalse(EnchantmentHelper.getAvailableEnchantmentResults(30, stack, Stream.of(enchantment))
                    .isEmpty(), key.location().toString());
        }
        Assertions.assertFalse(stack.supportsEnchantment(BreakPortEnchantmentsTest.REGISTRIES
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS)));
    }

    @Test
    void placedEnchantmentsSurviveSavingBothDropPathsAndReinstallation() {
        Fixture fixture = new Fixture();
        ItemStack original = new ItemStack(ItemRegistry.PP2MP_UNIT_PORT_BREAK.get());
        ItemEnchantments enchantments = BreakPortEnchantmentsTest.enchantments(Enchantments.SILK_TOUCH, 1);
        original.set(DataComponents.ENCHANTMENTS, enchantments);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("测试破坏端口"));
        fixture.port.importSettings(SettingsFrom.DISMANTLE_ITEM, original.getComponents(), null);
        CompoundTag saved = new CompoundTag();
        fixture.port.writeToNBT(saved, BreakPortEnchantmentsTest.REGISTRIES);
        Fixture restored = new Fixture();
        restored.port.readFromNBT(saved, BreakPortEnchantmentsTest.REGISTRIES);

        for (boolean wrenched : List.of(false, true)) {
            List<ItemStack> drops = new ArrayList<>();
            restored.port.addPartDrop(drops, wrenched);
            Assertions.assertEquals(1, drops.size());
            ItemStack dropped = drops.getFirst();
            Assertions.assertEquals(enchantments, dropped.get(DataComponents.ENCHANTMENTS));
            Assertions.assertEquals(original.get(DataComponents.CUSTOM_NAME), dropped.get(DataComponents.CUSTOM_NAME));
            Fixture placedAgain = new Fixture();
            placedAgain.port.importSettings(SettingsFrom.DISMANTLE_ITEM, dropped.getComponents(), null);
            Assertions.assertEquals(enchantments, placedAgain.port.exportSettings(SettingsFrom.DISMANTLE_ITEM)
                    .get(DataComponents.ENCHANTMENTS));
        }
    }

    @Test
    void memoryCardsDoNotExportOrReplaceEnchantments() {
        Fixture fixture = new Fixture();
        ItemEnchantments silk = BreakPortEnchantmentsTest.enchantments(Enchantments.SILK_TOUCH, 1);
        fixture.port.importSettings(SettingsFrom.DISMANTLE_ITEM,
                DataComponentMap.builder().set(DataComponents.ENCHANTMENTS, silk).build(), null);
        Assertions.assertNull(fixture.port.exportSettings(SettingsFrom.MEMORY_CARD).get(DataComponents.ENCHANTMENTS));
        fixture.port.importSettings(SettingsFrom.MEMORY_CARD, DataComponentMap.builder()
                .set(DataComponents.ENCHANTMENTS, BreakPortEnchantmentsTest.enchantments(Enchantments.FORTUNE, 3))
                .build(), null);
        Assertions.assertEquals(silk, fixture.port.exportSettings(SettingsFrom.DISMANTLE_ITEM)
                .get(DataComponents.ENCHANTMENTS));
    }

    @Test
    void changingEnchantmentsRebuildsOnlyTheCachedStrategies() {
        Fixture fixture = new Fixture();
        List<PickupStrategy> before = fixture.port.strategies();
        Assertions.assertSame(before, fixture.port.strategies());
        Assertions.assertTrue(before.stream().anyMatch(Pp2mpBlockPickupStrategy.class::isInstance));
        fixture.port.importSettings(SettingsFrom.DISMANTLE_ITEM, DataComponentMap.builder()
                .set(DataComponents.ENCHANTMENTS, BreakPortEnchantmentsTest.enchantments(Enchantments.FORTUNE, 3))
                .build(), null);
        Assertions.assertNotSame(before, fixture.port.strategies());
    }

    @Test
    void pickupToggleRoutesTheSameStrategyWithoutReturningDropsWhenDisabled() {
        Fixture fixture = new Fixture();
        PickupStrategy strategy = Mockito.mock(PickupStrategy.class);
        Mockito.when(strategy.tryPickup(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            PickupSink sink = invocation.getArgument(1);
            long simulated = sink.insert(AEItemKey.of(Items.STONE), 2, Actionable.SIMULATE);
            long actual = sink.insert(AEItemKey.of(Items.STONE), 2, Actionable.MODULATE);
            Assertions.assertEquals(2, simulated);
            Assertions.assertEquals(fixture.port.expectedPickup ? 2 : 0, actual);
            return PickupStrategy.Result.PICKED_UP;
        });
        fixture.port.testStrategies = List.of(strategy);
        Assertions.assertTrue(fixture.port.breakAdjacentBlock(false));
        Assertions.assertEquals(0, fixture.port.returned);
        fixture.port.expectedPickup = true;
        Assertions.assertTrue(fixture.port.breakAdjacentBlock(true));
        Assertions.assertEquals(2, fixture.port.returned);
        Mockito.verify(fixture.level, Mockito.never()).destroyBlock(Mockito.any(), Mockito.anyBoolean());
        Assertions.assertFalse(fixture.port.collectProducts(64));
    }

    private static ItemEnchantments enchantments(ResourceKey<Enchantment> key, int level) {
        ItemEnchantments.Mutable result = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        result.set(BreakPortEnchantmentsTest.REGISTRIES.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
        return result.toImmutable();
    }

    private static final class Fixture {
        private final ServerLevel level = Mockito.mock(ServerLevel.class);
        private final TestPort port = new TestPort();

        private Fixture() {
            ChestBlockEntity host = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            host.setLevel(this.level);
            this.port.setPartHostInfo(Direction.NORTH, Mockito.mock(IPartHost.class), host);
            Mockito.when(this.level.getBlockState(BlockPos.ZERO.north())).thenReturn(Blocks.STONE.defaultBlockState());
        }
    }

    private static final class TestPort extends Pp2mpUnitBreakPortPart {
        private final Pp2mpUnitManagerPart manager = Mockito.mock(Pp2mpUnitManagerPart.class);
        private final IGrid testGrid = Mockito.mock(IGrid.class);
        private List<PickupStrategy> testStrategies;
        private boolean expectedPickup;
        private long returned;

        private TestPort() {
            super(ItemRegistry.PP2MP_UNIT_PORT_BREAK.get());
            Pp2mpUnitManagerLogic logic = Mockito.mock(
                    Pp2mpUnitManagerLogic.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
            Mockito.when(this.manager.getLogic()).thenReturn(logic);
            Mockito.when(logic.isTaskOperational()).thenReturn(true);
        }

        private List<PickupStrategy> strategies() {
            return this.getPickupStrategies(Objects.requireNonNull(this.adjacentTarget.resolve()));
        }

        @Override
        public IGrid grid() {
            return this.testGrid;
        }

        @Override
        protected Pp2mpUnitManagerPart findManager() {
            return this.manager;
        }

        @Override
        public boolean isBoundTo(Pp2mpUnitManagerPart candidate) {
            return candidate == this.manager;
        }

        @Override
        protected List<PickupStrategy> getPickupStrategies(Pp2mpAdjacentTarget.Target target) {
            return this.testStrategies == null ? super.getPickupStrategies(target) : this.testStrategies;
        }

        @Override
        protected long insertCollected(AEKey key, long amount, Actionable mode) {
            if (mode == Actionable.MODULATE) {
                this.returned += amount;
            }
            return amount;
        }
    }
}
