package org.ae2bc.part;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterPacket;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

class ProviderFilterVersionTest {
    private static Pp2mpProviderFilterData filter(Runnable changed) {
        return new Pp2mpProviderFilterData(ItemRegistry.PP2MP_RESOURCE_INTAKE.get(), changed);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buffer, int version, long amount) {
        buffer.writeVarInt(version);
        for (int slot = 0; slot < Pp2mpProviderFilterData.SLOT_COUNT; slot++) {
            buffer.writeVarLong(amount);
        }
    }

    @Test
    void validEditsAdvanceVersionEvenWhenValueIsUnchanged() {
        AtomicInteger changes = new AtomicInteger();
        Pp2mpProviderFilterData filter = ProviderFilterVersionTest.filter(changes::incrementAndGet);
        int initial = filter.version();
        filter.setLimit(0, 0);
        Assertions.assertEquals(initial + 1, filter.version());
        filter.setLimit(0, -8);
        Assertions.assertEquals(initial + 2, filter.version());
        Assertions.assertEquals(0, filter.limit(0));
        filter.setLimit(-1, 12);
        filter.setLimit(Pp2mpProviderFilterData.SLOT_COUNT, 12);
        Assertions.assertEquals(initial + 2, filter.version());
        Assertions.assertEquals(2, changes.get());
    }

    @Test
    void streamUsesVersionInsteadOfComparingValuesAndConsumesDuplicatePayload() {
        Pp2mpProviderFilterData client = ProviderFilterVersionTest.filter(() -> Assertions.fail("Read must not notify"));
        RegistryFriendlyByteBuf buffer = ProviderFilterVersionTest.buffer();
        try {
            ProviderFilterVersionTest.writeSnapshot(buffer, 0, 8);
            ProviderFilterVersionTest.writeSnapshot(buffer, 0, 99);
            ProviderFilterVersionTest.writeSnapshot(buffer, 1, 8);
            buffer.writeInt(123456);
            Assertions.assertTrue(client.readFromStream(buffer));
            Assertions.assertEquals(8, client.limit(0));
            Assertions.assertFalse(client.readFromStream(buffer));
            Assertions.assertEquals(8, client.limit(0));
            // 字段完全相同，但版本推进，仍报告更新。
            Assertions.assertTrue(client.readFromStream(buffer));
            Assertions.assertEquals(1, client.version());
            Assertions.assertEquals(123456, buffer.readInt());
            Assertions.assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void clientInventoryCallbacksCannotHideNewServerVersion() {
        Pp2mpProviderFilterData client = ProviderFilterVersionTest.filter(() -> {
        });
        RegistryFriendlyByteBuf buffer = ProviderFilterVersionTest.buffer();
        try {
            ProviderFilterVersionTest.writeSnapshot(buffer, 1, 8);
            Assertions.assertTrue(client.readFromStream(buffer));
            client.onChanged();
            Assertions.assertEquals(2, client.version());
            ProviderFilterVersionTest.writeSnapshot(buffer, 2, 16);
            Assertions.assertTrue(client.readFromStream(buffer));
            Assertions.assertEquals(16, client.limit(0));
            ProviderFilterVersionTest.writeSnapshot(buffer, 2, 16);
            Assertions.assertFalse(client.readFromStream(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void copiesRefreshOnSourceVersionLocalVersionOrSourceIdentityChanges() {
        Pp2mpProviderFilterData first = ProviderFilterVersionTest.filter(() -> {
        });
        Pp2mpProviderFilterData second = ProviderFilterVersionTest.filter(() -> {
        });
        AtomicInteger changes = new AtomicInteger();
        Pp2mpProviderFilterData target = ProviderFilterVersionTest.filter(changes::incrementAndGet);
        first.markers().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 0));
        second.markers().setStack(0, new GenericStack(AEItemKey.of(Items.DIRT), 0));
        first.setLimit(0, 8);
        second.setLimit(0, 16);
        Assertions.assertEquals(first.version(), second.version());
        target.copyFrom(first);
        int copiedVersion = target.version();
        target.copyFrom(first);
        Assertions.assertEquals(copiedVersion, target.version());
        Assertions.assertEquals(1, changes.get());
        first.setLimit(0, 8);
        target.copyFrom(first);
        Assertions.assertEquals(copiedVersion + 1, target.version());
        target.setLimit(0, 99);
        target.copyFrom(first);
        Assertions.assertEquals(8, target.limit(0));
        target.copyFrom(second);
        Assertions.assertEquals(AEItemKey.of(Items.DIRT), target.markers().getKey(0));
        Assertions.assertEquals(16, target.limit(0));
    }

    @Test
    void upgradesAdvanceVersionAndCopyOnlyBlacklistSemantics() {
        Pp2mpProviderFilterData source = ProviderFilterVersionTest.filter(() -> {
        });
        Pp2mpProviderFilterData target = ProviderFilterVersionTest.filter(() -> {
        });
        int before = source.version();
        source.upgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        Assertions.assertNotEquals(before, source.version());
        Assertions.assertFalse(source.isWhitelist());
        target.copyFrom(source);
        Assertions.assertFalse(target.isWhitelist());
        Assertions.assertTrue(target.upgrades().getStackInSlot(0).isEmpty());
    }

    @Test
    void nbtLoadAdvancesOnceWithoutSendingChangeNotifications() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpProviderFilterData source = ProviderFilterVersionTest.filter(() -> {
        });
        source.setLimit(0, 32);
        CompoundTag tag = new CompoundTag();
        source.writeToNBT(tag, registries);
        Pp2mpProviderFilterData restored = ProviderFilterVersionTest.filter(() -> Assertions.fail("Load must not notify"));
        int before = restored.version();
        restored.readFromNBT(tag, registries);
        Assertions.assertEquals(before + 1, restored.version());
        Assertions.assertEquals(32, restored.limit(0));
        CompoundTag limits = new CompoundTag();
        source.writeLimits(limits);
        restored.readLimits(limits);
        Assertions.assertEquals(before + 2, restored.version());
        Assertions.assertEquals(32, restored.limit(0));
    }

    @Test
    void menuPacketPreservesVersionAndAllState() {
        RegistryFriendlyByteBuf buffer = ProviderFilterVersionTest.buffer();
        try {
            Pp2mpProviderFilterPacket expected = new Pp2mpProviderFilterPacket(
                    12, 4096, true, false, Collections.nCopies(Pp2mpProviderFilterData.SLOT_COUNT, 10000000L));
            Pp2mpProviderFilterPacket.STREAM_CODEC.encode(buffer, expected);
            Assertions.assertEquals(expected, Pp2mpProviderFilterPacket.STREAM_CODEC.decode(buffer));
            Assertions.assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
