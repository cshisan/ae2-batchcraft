package org.ae2bc.part;

import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.parts.AEBasePart;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterHost;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

class ProviderFilterMenuTest {
    @Test
    void replacingAndClearingMarkersNotifyOnceWhileIdenticalResourcesKeepTheirLimit() {
        Fixture fixture = new Fixture();
        fixture.changes.set(0);
        fixture.menu.setFilter(0, new ItemStack(Items.DIRT));
        Assertions.assertEquals(AEItemKey.of(Items.DIRT), fixture.filter.markers().getKey(0));
        Assertions.assertEquals(0, fixture.filter.limit(0));
        Assertions.assertEquals(1, fixture.changes.get());

        fixture.filter.setLimit(0, 50);
        fixture.changes.set(0);
        fixture.menu.setFilter(0, new ItemStack(Items.DIRT, 64));
        Assertions.assertEquals(50, fixture.filter.limit(0));
        Assertions.assertEquals(0, fixture.changes.get());

        fixture.menu.setFilter(0, ItemStack.EMPTY);
        Assertions.assertNull(fixture.filter.markers().getKey(0));
        Assertions.assertEquals(0, fixture.filter.limit(0));
        Assertions.assertEquals(1, fixture.changes.get());
        fixture.menu.setFilter(0, ItemStack.EMPTY);
        Assertions.assertEquals(1, fixture.changes.get());
    }

    @Test
    void currentValuesPermitEditsIncludingUnrelatedChangesAndAba() {
        Fixture fixture = new Fixture();
        fixture.filter.markers().setStack(1, new GenericStack(AEItemKey.of(Items.DIRT), 0));
        fixture.filter.setLimit(1, 50);
        fixture.submit(fixture.key, 100, 7, 101);
        Assertions.assertEquals(101, fixture.filter.limit(0));
        fixture.filter.setLimit(0, 200);
        fixture.filter.setLimit(0, 100);
        fixture.submit(fixture.key, 100, 7, 300);
        Assertions.assertEquals(300, fixture.filter.limit(0));
    }

    @Test
    void differentOldAmountBindingResourceOrComponentsAreRejected() {
        Fixture fixture = new Fixture();
        fixture.submit(fixture.key, 99, 7, 400);
        fixture.submit(fixture.key, 100, 8, 400);
        fixture.submit(AEItemKey.of(Items.DIRT), 100, 7, 400);
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("different"));
        AEItemKey namedKey = AEItemKey.of(named);
        Assertions.assertNotNull(namedKey);
        fixture.submit(namedKey, 100, 7, 400);
        Assertions.assertEquals(100, fixture.filter.limit(0));
    }

    @Test
    void syncBlacklistClosedWindowAndInvalidQuantitiesRejectWrites() {
        Fixture fixture = new Fixture();
        Mockito.when(fixture.host.isProviderFilterSynced()).thenReturn(true);
        fixture.submit(fixture.key, 100, 7, 400);
        Mockito.when(fixture.host.isProviderFilterSynced()).thenReturn(false);
        fixture.filter.upgrades().setItemDirect(0, AEItems.INVERTER_CARD.stack());
        fixture.submit(fixture.key, 100, 7, 400);
        fixture.filter.upgrades().setItemDirect(0, ItemStack.EMPTY);
        fixture.submit(fixture.key, 100, 7, -1);
        fixture.submit(fixture.key, 100, 7, Long.MAX_VALUE);
        fixture.player.containerMenu = null;
        fixture.submit(fixture.key, 100, 7, 400);
        Assertions.assertEquals(100, fixture.filter.limit(0));
    }

    private static final class Fixture {
        private final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        private final Player player = Mockito.mock(Player.class);
        private final Pp2mpProviderFilterHost host = Mockito.mock(Pp2mpProviderFilterHost.class);
        private final AtomicInteger changes = new AtomicInteger();
        private final Pp2mpProviderFilterData filter =
                new Pp2mpProviderFilterData(ItemRegistry.PP2MP_RESOURCE_INTAKE.get(), this.changes::incrementAndGet);
        private final AEItemKey key = AEItemKey.of(Items.STONE);
        private final Pp2mpProviderFilterMenu menu;

        private Fixture() {
            ServerLevel level = Mockito.mock(ServerLevel.class);
            Mockito.when(this.player.getCommandSenderWorld()).thenReturn(level);
            Mockito.when(this.player.level()).thenReturn(level);
            Mockito.when(this.player.registryAccess()).thenReturn(this.registries);
            Mockito.when(level.registryAccess()).thenReturn(this.registries);
            AEBasePart part = Mockito.mock(AEBasePart.class);
            IPartHost partHost = Mockito.mock(IPartHost.class);
            Mockito.when(part.getHost()).thenReturn(partHost);
            Mockito.when(part.getSide()).thenReturn(Direction.NORTH);
            Mockito.when(partHost.isInWorld()).thenReturn(true);
            Mockito.when(partHost.getPart(Direction.NORTH)).thenReturn(part);
            Mockito.when(this.host.getProviderFilterPart()).thenReturn(part);
            Mockito.when(this.host.getProviderFilter()).thenReturn(this.filter);
            Mockito.when(this.host.getEffectiveProviderFilter()).thenReturn(this.filter);
            Mockito.when(this.host.getProviderFilterBinding()).thenReturn(7);
            this.filter.markers().setStack(0, new GenericStack(this.key, 0));
            this.filter.setLimit(0, 100);
            this.menu = new Pp2mpProviderFilterMenu(1, new Inventory(this.player), this.host);
            this.player.containerMenu = this.menu;
            this.menu.sendAllDataToRemote();
        }

        private void submit(AEKey key, long expectedAmount, int binding, long amount) {
            JsonObject change = new JsonObject();
            change.addProperty("slot", 0);
            change.addProperty("expectedAmount", expectedAmount);
            change.addProperty("binding", binding);
            change.addProperty("amount", amount);
            change.add("key", AEKey.CODEC.encodeStart(
                    this.registries.createSerializationContext(JsonOps.INSTANCE), key).getOrThrow());
            this.menu.receiveClientAction("SetProviderLimit", change.toString());
        }
    }
}
