package org.ae2bc.placer;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.util.DimensionalBlockPos;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.ae2bc.placer.packet.ComponentPlacerClientPacket;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.util.Set;

class ComponentPlacerConnectionTest {
    @Test
    void pairedEmptySurvivalBatteryReportsPowerAndRecoversInTheSameBroadcast() {
        Fixture fixture = new Fixture(false, 0, true);
        fixture.menu.broadcastChanges();
        Assertions.assertTrue(fixture.menu.isPaired());
        Assertions.assertFalse(fixture.menu.getModel().aeConnected());
        Assertions.assertEquals(GuiText.OutOfPower.text(), fixture.menu.getLinkError());

        fixture.item.injectAEPower(fixture.stack, 1000, Actionable.MODULATE);
        fixture.menu.broadcastChanges();
        Assertions.assertTrue(fixture.menu.getModel().aeConnected());
        Assertions.assertNull(fixture.menu.getLinkError());
        fixture.menu.broadcastChanges();
        Mockito.verify(fixture.player.connection, Mockito.times(2)).send(
                Mockito.isA(ComponentPlacerClientPacket.class));
    }

    @Test
    void creativeEmptyBatteryStillRequiresAnActiveInRangeAccessPoint() {
        Fixture fixture = new Fixture(true, 0, true);
        fixture.menu.broadcastChanges();
        Assertions.assertTrue(fixture.menu.getModel().aeConnected());
        Assertions.assertEquals(0, fixture.item.getAECurrentPower(fixture.stack));

        Mockito.when(fixture.accessPoint.isActive()).thenReturn(false);
        fixture.menu.broadcastChanges();
        Assertions.assertFalse(fixture.menu.getModel().aeConnected());
        Assertions.assertEquals(PlayerMessages.OutOfRange.text(), fixture.menu.getLinkError());

        Mockito.when(fixture.accessPoint.isActive()).thenReturn(true);
        Mockito.when(fixture.player.getX()).thenReturn(100.0);
        fixture.menu.broadcastChanges();
        Assertions.assertFalse(fixture.menu.getModel().aeConnected());
        Mockito.verify(fixture.player.connection, Mockito.times(2)).send(
                Mockito.isA(ComponentPlacerClientPacket.class));
        Assertions.assertEquals(0, fixture.item.getAECurrentPower(fixture.stack));
    }

    @Test
    void missingBindingAndUnloadedTargetHaveDifferentReasons() {
        Fixture fixture = new Fixture(true, 0, false);
        fixture.menu.broadcastChanges();
        Assertions.assertFalse(fixture.menu.isPaired());
        Assertions.assertEquals(PlayerMessages.DeviceNotLinked.text(), fixture.menu.getLinkError());

        fixture.stack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(Level.OVERWORLD, Fixture.TARGET));
        Mockito.when(fixture.chunks.isPositionTicking(Mockito.anyLong())).thenReturn(false);
        fixture.menu.broadcastChanges();
        Assertions.assertTrue(fixture.menu.isPaired());
        Assertions.assertFalse(fixture.menu.getModel().aeConnected());
        Assertions.assertEquals(PlayerMessages.LinkedNetworkNotFound.text(), fixture.menu.getLinkError());

        Mockito.when(fixture.chunks.isPositionTicking(Mockito.anyLong())).thenReturn(true);
        fixture.menu.broadcastChanges();
        Assertions.assertTrue(fixture.menu.getModel().aeConnected());
        Mockito.verify(fixture.player.connection, Mockito.times(3)).send(
                Mockito.isA(ComponentPlacerClientPacket.class));
    }

    @Test
    void anotherDimensionDoesNotCountAsAUsableAccessPoint() {
        Fixture fixture = new Fixture(true, 0, true);
        Mockito.when(fixture.accessPoint.getLocation()).thenReturn(
                new DimensionalBlockPos(Mockito.mock(ServerLevel.class), Fixture.TARGET));
        fixture.menu.broadcastChanges();
        Assertions.assertFalse(fixture.menu.getModel().aeConnected());
        Assertions.assertEquals(PlayerMessages.OutOfRange.text(), fixture.menu.getLinkError());
    }

    @Test
    void disconnectedMaterialFailuresExplainTheReasonButSuccessfulLocalPlacementDoesNot() {
        Fixture fixture = new Fixture(false, 0, true);
        try (MockedStatic<PlacementService> placement = Mockito.mockStatic(
                PlacementService.class, Mockito.withSettings().mockMaker(MockMakers.INLINE))) {
            placement.when(() -> PlacementService.place(fixture.player, fixture.host)).thenReturn(
                    new PlacementService.Result(0, 0, 1, 0, ItemStack.EMPTY, 0));
            fixture.menu.receiveClientAction("execute", null);
            Mockito.verify(fixture.player).displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.network_unavailable", GuiText.OutOfPower.text()), false);

            Mockito.clearInvocations(fixture.player);
            placement.when(() -> PlacementService.place(fixture.player, fixture.host)).thenReturn(
                    new PlacementService.Result(1, 0, 0, 0, ItemStack.EMPTY, 0));
            fixture.menu.receiveClientAction("execute", null);
            Mockito.verify(fixture.player).displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.result", 1, 0, 0, 0), false);
            Mockito.verify(fixture.player, Mockito.times(1)).displayClientMessage(Mockito.any(), Mockito.eq(false));
        }
    }

    private static final class Fixture {
        private static final BlockPos TARGET = new BlockPos(1, 0, 0);
        private final ComponentPlacerItem item = ItemRegistry.COMPONENT_PLACER.get();
        private final ItemStack stack = new ItemStack(this.item);
        private final ServerPlayer player = Mockito.mock(
                ServerPlayer.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        private final ServerChunkCache chunks = Mockito.mock(ServerChunkCache.class);
        private final WirelessAccessPointBlockEntity accessPoint = Mockito.mock(WirelessAccessPointBlockEntity.class);
        private final ComponentPlacerMenuHost host;
        private final ComponentPlacerMenu menu;

        private Fixture(boolean creative, double power, boolean paired) {
            ServerLevel level = Mockito.mock(ServerLevel.class);
            MinecraftServer server = Mockito.mock(MinecraftServer.class);
            IGrid grid = Mockito.mock(IGrid.class);
            Mockito.when(level.dimension()).thenReturn(Level.OVERWORLD);
            Mockito.when(level.getServer()).thenReturn(server);
            Mockito.when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
            Mockito.when(level.getChunkSource()).thenReturn(this.chunks);
            Mockito.when(this.chunks.isPositionTicking(Mockito.anyLong())).thenReturn(true);
            Mockito.when(level.getBlockEntity(Fixture.TARGET)).thenReturn(this.accessPoint);
            Mockito.when(this.accessPoint.getGrid()).thenReturn(grid);
            Mockito.when(grid.getMachines(WirelessAccessPointBlockEntity.class)).thenReturn(Set.of(this.accessPoint));
            Mockito.when(this.accessPoint.getLocation()).thenReturn(new DimensionalBlockPos(level, Fixture.TARGET));
            Mockito.when(this.accessPoint.getRange()).thenReturn(16.0);
            Mockito.when(this.accessPoint.isActive()).thenReturn(true);
            Mockito.when(this.player.level()).thenReturn(level);
            Mockito.when(this.player.getCommandSenderWorld()).thenReturn(level);
            Mockito.when(this.player.isCreative()).thenReturn(creative);
            Mockito.when(this.player.getAbilities()).thenReturn(new Abilities());
            Mockito.when(this.player.getInventory()).thenReturn(new Inventory(this.player));
            this.player.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
            if (paired) {
                this.stack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(Level.OVERWORLD, Fixture.TARGET));
            }
            this.item.injectAEPower(this.stack, power, Actionable.MODULATE);
            ItemMenuHostLocator locator = Mockito.mock(ItemMenuHostLocator.class);
            Mockito.when(locator.locateItem(this.player)).thenReturn(this.stack);
            this.host = new ComponentPlacerMenuHost(this.item, this.player, locator, (player, subMenu) -> { });
            this.menu = new ComponentPlacerMenu(1, this.player.getInventory(), this.host);
        }
    }
}
