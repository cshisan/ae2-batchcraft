package org.ae2bc.placer;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.features.P2PTunnelAttunement;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ILinkStatus;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.AEItems;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.me.helpers.PlayerSource;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.ae2bc.core.placer.MaterialsMode;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

class ComponentPlacerPowerTest {
    @Test
    void chargingCapabilityDoesNotTurnThePlacerIntoAnFeTunnelAttunementTool() {
        ItemStack stack = new ItemStack(ItemRegistry.COMPONENT_PLACER.get());
        for (boolean frequency : List.of(false, true)) {
            ComponentPlacerData.frequencyMode(stack, frequency);
            Assertions.assertNotNull(stack.getCapability(Capabilities.EnergyStorage.ITEM));
            Assertions.assertTrue(P2PTunnelAttunement.getTunnelPartByTriggerItem(stack).isEmpty());
        }
        Assertions.assertTrue(P2PTunnelAttunement.getTunnelPartByTriggerItem(
                AEItems.WIRELESS_TERMINAL.stack()).is(AEParts.FE_P2P_TUNNEL.asItem()));
    }

    @Test
    void registeredFeCapabilityChargesTheExistingBatteryAndRespectsSimulationAndCapacity() {
        ComponentPlacerItem item = ItemRegistry.COMPONENT_PLACER.get();
        ItemStack stack = new ItemStack(item);
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        Assertions.assertNotNull(energy);
        Assertions.assertTrue(energy.canReceive());
        Assertions.assertFalse(energy.canExtract());
        Assertions.assertEquals(0, energy.getEnergyStored());

        int offered = Math.min(1000, energy.getMaxEnergyStored());
        Assertions.assertTrue(offered > 0);
        Assertions.assertEquals(offered, energy.receiveEnergy(offered, true));
        Assertions.assertEquals(0, item.getAECurrentPower(stack));
        Assertions.assertEquals(offered, energy.receiveEnergy(offered, false));
        Assertions.assertEquals(PowerUnit.FE.convertTo(PowerUnit.AE, offered), item.getAECurrentPower(stack));
        Assertions.assertEquals(offered, energy.getEnergyStored());
        Assertions.assertEquals(0, energy.extractEnergy(offered, true));
        Assertions.assertEquals(0, energy.extractEnergy(offered, false));
        Assertions.assertEquals(offered, energy.getEnergyStored());

        int remaining = energy.getMaxEnergyStored() - energy.getEnergyStored();
        Assertions.assertEquals(remaining, energy.receiveEnergy(energy.getMaxEnergyStored(), false));
        Assertions.assertEquals(item.getAEMaxPower(stack), item.getAECurrentPower(stack));
        Assertions.assertEquals(0, energy.receiveEnergy(offered, false));
    }

    @Test
    void feCapacityTracksEnergyCardUpgrades() {
        ComponentPlacerItem item = ItemRegistry.COMPONENT_PLACER.get();
        ItemStack stack = new ItemStack(item);
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        Assertions.assertNotNull(energy);
        int originalCapacity = energy.getMaxEnergyStored();
        item.getUpgrades(stack).setItemDirect(0, AEItems.ENERGY_CARD.stack());
        Assertions.assertTrue(energy.getMaxEnergyStored() > originalCapacity);
        Assertions.assertEquals((int) PowerUnit.AE.convertTo(PowerUnit.FE, item.getAEMaxPower(stack)),
                energy.getMaxEnergyStored());
        Assertions.assertEquals(energy.getMaxEnergyStored(), energy.receiveEnergy(energy.getMaxEnergyStored(), false));
        Assertions.assertEquals(item.getAEMaxPower(stack), item.getAECurrentPower(stack));
    }

    @Test
    void creativePowerRequestsNeverRequireOrChangeCharge() {
        for (double charge : List.of(0.0, 12.0)) {
            Fixture fixture = new Fixture(true, charge);
            for (Actionable mode : Actionable.values()) {
                Assertions.assertEquals(100, fixture.host.extractAEPower(100, mode, PowerMultiplier.CONFIG));
                Assertions.assertEquals(charge, fixture.item.getAECurrentPower(fixture.stack));
            }
            Assertions.assertTrue(fixture.host.consumeIdlePower(Actionable.MODULATE));
            Assertions.assertEquals(charge, fixture.item.getAECurrentPower(fixture.stack));
        }
    }

    @Test
    void survivalSimulationAndConsumptionStillRespectCharge() {
        Fixture fixture = new Fixture(false, 5);
        Assertions.assertEquals(3, fixture.host.extractAEPower(3, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        Assertions.assertEquals(5, fixture.item.getAECurrentPower(fixture.stack));
        Assertions.assertEquals(3, fixture.host.extractAEPower(3, Actionable.MODULATE, PowerMultiplier.CONFIG));
        Assertions.assertEquals(2, fixture.item.getAECurrentPower(fixture.stack));
        Assertions.assertEquals(2, fixture.host.extractAEPower(3, Actionable.MODULATE, PowerMultiplier.CONFIG));
        Assertions.assertEquals(0, fixture.item.getAECurrentPower(fixture.stack));
        Assertions.assertEquals(0, fixture.host.extractAEPower(1, Actionable.MODULATE, PowerMultiplier.CONFIG));
    }

    @Test
    void creativePrecheckExtractionRefundAndInsertionWorkWithoutPower() {
        Fixture fixture = new Fixture(true, 0);
        for (ItemStack material : List.of(
                AEParts.GLASS_CABLE.stack(AEColor.TRANSPARENT), ItemRegistry.PP2MP_OUTPUT.get().getDefaultInstance())) {
            AEItemKey key = Assertions.assertInstanceOf(AEItemKey.class, AEItemKey.of(material));
            fixture.network.setStack(0, new GenericStack(key, 4));
            Assertions.assertNull(PlacementMaterials.findMissing(fixture.host, fixture.source, material, 4));
            Assertions.assertEquals(4, fixture.network.getAmount(0));

            PlacementMaterials.Reservation reservation = PlacementMaterials.extract(fixture.host, fixture.source, material);
            Assertions.assertNotNull(reservation);
            Assertions.assertEquals(MaterialsMode.NETWORK, reservation.source());
            Assertions.assertEquals(3, fixture.network.getAmount(0));
            PlacementMaterials.refund(fixture.host, fixture.player, fixture.source, reservation);
            Assertions.assertEquals(4, fixture.network.getAmount(0));

            Assertions.assertEquals(1, fixture.host.insert(fixture.player, key, 1, Actionable.SIMULATE));
            Assertions.assertEquals(4, fixture.network.getAmount(0));
            Assertions.assertEquals(1, fixture.host.insert(fixture.player, key, 1, Actionable.MODULATE));
            Assertions.assertEquals(5, fixture.network.getAmount(0));
            Assertions.assertEquals(0, fixture.item.getAECurrentPower(fixture.stack));
        }
    }

    @Test
    void creativeStillNeedsMatchingMaterialsAndAConnection() {
        Fixture fixture = new Fixture(true, 0);
        ItemStack material = ItemRegistry.PP2MP_OUTPUT.get().getDefaultInstance();
        PlacementMaterials.MissingMaterial missing = PlacementMaterials.findMissing(
                fixture.host, fixture.source, material, 1);
        Assertions.assertNotNull(missing);
        Assertions.assertEquals(1, missing.amount());
        Assertions.assertNull(PlacementMaterials.extract(fixture.host, fixture.source, material));

        AEItemKey key = Assertions.assertInstanceOf(AEItemKey.class, AEItemKey.of(material));
        fixture.network.setStack(0, new GenericStack(key, 4));
        Mockito.doReturn(ILinkStatus.ofDisconnected()).when(fixture.host).getLinkStatus();
        Assertions.assertNull(PlacementMaterials.extract(fixture.host, fixture.source, material));
        Assertions.assertEquals(0, fixture.host.insert(fixture.player, key, 1, Actionable.MODULATE));
        Assertions.assertEquals(4, fixture.network.getAmount(0));
    }

    @Test
    void survivalNetworkExtractionStillNeedsPower() {
        Fixture fixture = new Fixture(false, 0);
        ItemStack material = ItemRegistry.PP2MP_OUTPUT.get().getDefaultInstance();
        AEItemKey key = Assertions.assertInstanceOf(AEItemKey.class, AEItemKey.of(material));
        fixture.network.setStack(0, new GenericStack(key, 4));
        Assertions.assertNull(PlacementMaterials.extract(fixture.host, fixture.source, material));
        Assertions.assertEquals(4, fixture.network.getAmount(0));
        fixture.item.injectAEPower(fixture.stack, 5, Actionable.MODULATE);
        Assertions.assertNotNull(PlacementMaterials.extract(fixture.host, fixture.source, material));
        Assertions.assertEquals(3, fixture.network.getAmount(0));
        Assertions.assertEquals(4, fixture.item.getAECurrentPower(fixture.stack));
    }

    @Test
    void disconnectedPlacerStillUsesAndRefundsLocalMaterialsWithoutPower() {
        Fixture fixture = new Fixture(false, 0);
        Mockito.doReturn(ILinkStatus.ofDisconnected()).when(fixture.host).getLinkStatus();
        ItemStack material = ItemRegistry.PP2MP_OUTPUT.get().getDefaultInstance();
        fixture.host.getMaterials().setItemDirect(0, material.copyWithCount(2));
        PlacementMaterials.Reservation reservation = PlacementMaterials.extract(fixture.host, fixture.source, material);
        Assertions.assertNotNull(reservation);
        Assertions.assertEquals(MaterialsMode.LOCAL, reservation.source());
        Assertions.assertEquals(1, fixture.host.getMaterials().getStackInSlot(0).getCount());
        PlacementMaterials.refund(fixture.host, fixture.player, fixture.source, reservation);
        Assertions.assertEquals(2, fixture.host.getMaterials().getStackInSlot(0).getCount());
        Assertions.assertEquals(0, fixture.item.getAECurrentPower(fixture.stack));
        Mockito.verify(fixture.host, Mockito.never()).getInventory();
    }

    private static final class Fixture {
        private final ComponentPlacerItem item = ItemRegistry.COMPONENT_PLACER.get();
        private final ItemStack stack = new ItemStack(this.item);
        private final ServerPlayer player = Mockito.mock(ServerPlayer.class);
        private final PlayerSource source = new PlayerSource(this.player);
        private final GenericStackInv network = new GenericStackInv(
                Set.of(AEKeyType.items()), null, GenericStackInv.Mode.STORAGE, 1);
        private final ComponentPlacerMenuHost host;

        private Fixture(boolean creative, double charge) {
            ServerLevel level = Mockito.mock(ServerLevel.class);
            Mockito.when(this.player.level()).thenReturn(level);
            Mockito.when(this.player.isCreative()).thenReturn(creative);
            this.item.injectAEPower(this.stack, charge, Actionable.MODULATE);
            ItemMenuHostLocator locator = Mockito.mock(ItemMenuHostLocator.class);
            Mockito.when(locator.locateItem(this.player)).thenReturn(this.stack);
            ComponentPlacerMenuHost realHost = new ComponentPlacerMenuHost(
                    this.item, this.player, locator, (player, subMenu) -> { });
            this.host = Mockito.mock(ComponentPlacerMenuHost.class, Mockito.withSettings()
                    .mockMaker(MockMakers.INLINE).spiedInstance(realHost).defaultAnswer(Mockito.CALLS_REAL_METHODS));
            Mockito.doReturn(this.network).when(this.host).getInventory();
            Mockito.doReturn(ILinkStatus.ofConnected()).when(this.host).getLinkStatus();
        }
    }
}
