package org.ae2bc.part;

import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.parts.AEBasePart;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterConfig;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterHost;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterPacket;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitProviderPortPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class ProviderFilterSyncTest {
    private static Pp2mpProviderFilterData filter(Runnable changed) {
        return new Pp2mpProviderFilterData(ItemRegistry.PP2MP_RESOURCE_INTAKE.get(), changed);
    }

    private static RegistryAccess registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), ProviderFilterSyncTest.registries(), ConnectionType.NEOFORGE);
    }

    @Test
    void equalAndInvalidEditsDoNotNotifyAndSnapshotsAreReused() {
        AtomicInteger changes = new AtomicInteger();
        Pp2mpProviderFilterData filter = ProviderFilterSyncTest.filter(changes::incrementAndGet);
        Pp2mpProviderFilterConfig empty = filter.snapshot();
        filter.setLimit(0, 0);
        filter.setLimit(0, -8);
        filter.setLimit(-1, 12);
        filter.setLimit(Pp2mpProviderFilterData.SLOT_COUNT, 12);
        filter.setMarker(-1, new GenericStack(AEItemKey.of(Items.STONE), 0));
        filter.setMarker(Pp2mpProviderFilterData.SLOT_COUNT, null);
        Assertions.assertEquals(0, changes.get());
        Assertions.assertSame(empty, filter.snapshot());
        filter.setLimit(0, 12);
        Assertions.assertEquals(1, changes.get());
        Assertions.assertNotEquals(empty, filter.snapshot());
        Assertions.assertSame(filter.snapshot(), filter.snapshot());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> filter.snapshot().entries().clear());
    }

    @Test
    void ordinaryPartPacketsExcludeFilterRulesAndStillConsumeTheirExactPayload() {
        Map<Pp2mpProviderFilterHost, Pp2mpProviderFilterHost> hosts = Map.of(
                new Pp2mpOutputPart(ItemRegistry.PP2MP_OUTPUT.get()),
                new Pp2mpOutputPart(ItemRegistry.PP2MP_OUTPUT.get()),
                new Pp2mpUnitProviderPortPart(ItemRegistry.PP2MP_UNIT_PORT_PROVIDER.get()),
                new Pp2mpUnitProviderPortPart(ItemRegistry.PP2MP_UNIT_PORT_PROVIDER.get())
        );
        for (Map.Entry<Pp2mpProviderFilterHost, Pp2mpProviderFilterHost> entry : hosts.entrySet()) {
            AEBasePart source = entry.getKey().getProviderFilterPart();
            AEBasePart client = entry.getValue().getProviderFilterPart();
            source.setPartHostInfo(Direction.NORTH, Mockito.mock(IPartHost.class),
                    new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
            if (source instanceof Pp2mpUnitProviderPortPart provider) {
                provider.applyPlacerAttr((short) 7, (short) 9);
                provider.setProviderSync(false);
            }
            RegistryFriendlyByteBuf buffer = ProviderFilterSyncTest.buffer();
            try {
                source.writeToStream(buffer);
                byte[] original = ByteBufUtil.getBytes(buffer);
                buffer.clear();
                Pp2mpProviderFilterData filter = entry.getKey().getProviderFilter();
                for (int slot = 0; slot < Pp2mpProviderFilterData.SLOT_COUNT; slot++) {
                    filter.markers().setStack(slot, new GenericStack(AEItemKey.of(Items.STONE), 0));
                    filter.setLimit(slot, 123456);
                }
                source.writeToStream(buffer);
                Assertions.assertArrayEquals(original, ByteBufUtil.getBytes(buffer));
                buffer.writeInt(123456);
                client.readFromStream(buffer);
                Assertions.assertEquals(123456, buffer.readInt());
                Assertions.assertEquals(0, buffer.readableBytes());
                Assertions.assertTrue(entry.getValue().getProviderFilter().markers().isEmpty());
                if (client instanceof Pp2mpUnitProviderPortPart provider) {
                    Assertions.assertFalse(provider.isProviderSync());
                    Assertions.assertEquals(7, provider.getFrequency());
                    Assertions.assertEquals(9, provider.getUnitId());
                }
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void sourceSwitchAndLocalEditsAreCorrectedButEqualSourcesDoNotNotify() {
        Pp2mpProviderFilterData first = ProviderFilterSyncTest.filter(() -> { });
        Pp2mpProviderFilterData second = ProviderFilterSyncTest.filter(() -> { });
        AtomicInteger changes = new AtomicInteger();
        Pp2mpProviderFilterData target = ProviderFilterSyncTest.filter(changes::incrementAndGet);
        first.markers().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 0));
        first.setLimit(0, 8);
        second.copyFrom(first);
        target.copyFrom(first);
        for (int repeat = 0; repeat < 100; repeat++) {
            target.copyFrom(first);
            target.copyFrom(second);
        }
        Assertions.assertEquals(1, changes.get());
        target.setLimit(0, 99);
        target.copyFrom(first);
        Assertions.assertEquals(8, target.limit(0));
        second.markers().setStack(0, new GenericStack(AEItemKey.of(Items.DIRT), 0));
        second.setLimit(0, 16);
        target.copyFrom(second);
        Assertions.assertEquals(second.snapshot(), target.snapshot());
        Assertions.assertEquals(4, changes.get());
    }

    @Test
    void copiesPreserveRealCardsAndPersistBlacklistUntilALocalCardEdit() {
        Pp2mpProviderFilterData source = ProviderFilterSyncTest.filter(() -> { });
        Pp2mpProviderFilterData target = ProviderFilterSyncTest.filter(() -> { });
        source.upgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        target.copyFrom(source);
        Assertions.assertFalse(target.isWhitelist());
        Assertions.assertTrue(target.upgrades().getStackInSlot(0).isEmpty());
        CompoundTag saved = new CompoundTag();
        target.writeLimits(saved);
        Pp2mpProviderFilterData restored = ProviderFilterSyncTest.filter(() -> { });
        restored.readLimits(saved);
        Assertions.assertFalse(restored.isWhitelist());
        restored.upgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        restored.upgrades().setItemDirect(0, Items.AIR.getDefaultInstance());
        Assertions.assertTrue(restored.isWhitelist());

        target.upgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        source.upgrades().setItemDirect(0, Items.AIR.getDefaultInstance());
        target.copyFrom(source);
        Assertions.assertTrue(target.isWhitelist());
        Assertions.assertTrue(target.upgrades().getStackInSlot(0).is(AEItems.INVERTER_CARD.asItem()));
    }

    @Test
    void realCardChangesAreSavedEvenIfTheEffectiveRuleDoesNotChange() {
        Pp2mpProviderFilterData source = ProviderFilterSyncTest.filter(() -> { });
        AtomicInteger changes = new AtomicInteger();
        Pp2mpProviderFilterData target = ProviderFilterSyncTest.filter(changes::incrementAndGet);
        source.upgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        target.copyFrom(source);
        changes.set(0);
        target.upgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        Assertions.assertFalse(target.isWhitelist());
        Assertions.assertEquals(1, changes.get());
    }

    @Test
    void releasedListAndArrayFormatsLoadWithoutMigrationOrNotifications() {
        // v2.0.0：输出端/管理器使用复合列表，供应端口使用 long[]。
        CompoundTag listFormat = new CompoundTag();
        ListTag limits = new ListTag();
        CompoundTag first = new CompoundTag();
        first.putLong("Value", 4096);
        limits.add(first);
        listFormat.put("ProviderLimits", limits);
        listFormat.putBoolean("ProviderCopiedWhitelist", false);
        Pp2mpProviderFilterData restored = ProviderFilterSyncTest.filter(() -> Assertions.fail("Load must not notify"));
        restored.readFromNBT(listFormat, ProviderFilterSyncTest.registries());
        Assertions.assertEquals(4096, restored.limit(0));
        Assertions.assertFalse(restored.isWhitelist());

        CompoundTag arrayFormat = new CompoundTag();
        arrayFormat.putLongArray("ProviderLimits", new long[]{32, -5});
        restored.readLimits(arrayFormat);
        Assertions.assertEquals(32, restored.limit(0));
        Assertions.assertEquals(0, restored.limit(1));
        Assertions.assertTrue(restored.isWhitelist());
    }

    @Test
    void nbtPreservesMarkersLimitsRealCardsAndCopiedMode() {
        Pp2mpProviderFilterData source = ProviderFilterSyncTest.filter(() -> { });
        source.markers().setStack(2, new GenericStack(AEItemKey.of(Items.DIRT), 0));
        source.setLimit(2, 1234);
        source.upgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        CompoundTag tag = new CompoundTag();
        source.writeToNBT(tag, ProviderFilterSyncTest.registries());
        Pp2mpProviderFilterData restored = ProviderFilterSyncTest.filter(() -> Assertions.fail("Load must not notify"));
        restored.readFromNBT(tag, ProviderFilterSyncTest.registries());
        Assertions.assertEquals(source.snapshot(), restored.snapshot());
        Assertions.assertTrue(restored.upgrades().getStackInSlot(0).is(AEItems.INVERTER_CARD.asItem()));
    }

    @Test
    void menuPacketPreservesResourcesAmountsBindingAndPermissionsTogether() {
        Pp2mpProviderFilterData source = ProviderFilterSyncTest.filter(() -> { });
        source.markers().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 0));
        source.setLimit(0, 10000000);
        RegistryFriendlyByteBuf buffer = ProviderFilterSyncTest.buffer();
        try {
            Pp2mpProviderFilterPacket expected = new Pp2mpProviderFilterPacket(12, -4096, true, source.snapshot());
            Pp2mpProviderFilterPacket.STREAM_CODEC.encode(buffer, expected);
            Assertions.assertEquals(expected, Pp2mpProviderFilterPacket.STREAM_CODEC.decode(buffer));
            Assertions.assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
