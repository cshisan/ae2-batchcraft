package org.ae2bc;

import appeng.api.AECapabilities;
import appeng.api.features.GridLinkables;
import appeng.api.networking.GridServices;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.AEBaseInvBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.externalstorage.GenericStackFluidStorage;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.ae2bc.core.ModConstants;
import org.ae2bc.ioport.IoPortTransferPacket;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterPacket;
import org.ae2bc.part.input.Pp2mpInputModel;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.output.Pp2mpOutputModel;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakeModel;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakePart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerModel;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;
import org.ae2bc.part.unit.port.part.Pp2mpUnitReturnPortPart;
import org.ae2bc.placer.ComponentPlacerItem;
import org.ae2bc.placer.packet.ComponentPlacerClientPacket;
import org.ae2bc.placer.packet.ComponentPlacerServerPacket;
import org.ae2bc.registry.BlockRegistry;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.registry.MenuRegistry;
import org.ae2bc.registry.component.ComponentRegistry;
import org.ae2bc.registry.RecipeRegistry;
import org.slf4j.Logger;

@Mod(Ae2bcMod.MOD_ID)
public final class Ae2bcMod {
    public static final String MOD_ID = ModConstants.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ae2bcMod(IEventBus bus) {
        GridServices.register(Pp2mpGridService.class, Pp2mpGridService.class);

        ItemRegistry.register(bus);
        BlockRegistry.register(bus);
        MenuRegistry.register(bus);
        RecipeRegistry.register(bus);
        ComponentRegistry.register(bus);

        Pp2mpInputModel.register();
        Pp2mpOutputModel.register();
        Pp2mpResourceIntakeModel.register();
        Pp2mpUnitManagerModel.register();
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_TRANSFER);
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_DROP);
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_PLACE);
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_PROVIDER);
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_RETURN);
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_EXTRACT);
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_BREAK);
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_COLLECT);
        Pp2mpUnitPortPartModel.register(Pp2mpUnitPortPartModel.MODEL_REDSTONE);

        bus.addListener(Ae2bcMod::registerCapabilities);
        bus.addListener(Ae2bcMod::registerBlockCapabilities);
        bus.addListener(Ae2bcMod::registerUpgrades);
        bus.addListener(ComponentPlacerServerPacket::register);
        bus.addListener(ComponentPlacerClientPacket::register);
        bus.addListener(Pp2mpProviderFilterPacket::register);
        bus.addListener(IoPortTransferPacket::register);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ComponentPlacerItem::onRightClickBlock);
    }

    private static void registerUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            AEBaseBlockEntity.registerBlockEntityItem(BlockRegistry.IO_PORT_ENTITY.get(), ItemRegistry.IO_PORT.get());
            Upgrades.add(AEItems.SPEED_CARD, ItemRegistry.IO_PORT.get(), 5);
            Upgrades.add(AEItems.REDSTONE_CARD, ItemRegistry.IO_PORT.get(), 1);
            Upgrades.add(AEItems.CRAFTING_CARD, ItemRegistry.COMPONENT_PLACER.get(), 1);
            Upgrades.add(AEItems.ENERGY_CARD, ItemRegistry.COMPONENT_PLACER.get(), 2);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_UNIT_PORT_TRANSFER.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_UNIT_PORT_DROP.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_UNIT_PORT_PLACE.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_UNIT_PORT_RETURN.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_UNIT_PORT_EXTRACT.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_UNIT_PORT_BREAK.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_UNIT_PORT_COLLECT.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_UNIT_PORT_PROVIDER.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_OUTPUT.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ItemRegistry.PP2MP_RESOURCE_INTAKE.get(), 1);
            ItemRegistry.PP2MP_UNIT_MANAGERS.values().forEach(item -> Upgrades.add(
                    AEItems.INVERTER_CARD, item.get(), 1
            ));
            GridLinkables.register(ItemRegistry.COMPONENT_PLACER.get(), WirelessTerminalItem.LINKABLE_HANDLER);
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void registerCapabilities(RegisterPartCapabilitiesEvent event) {
        event.register(
                AECapabilities.CRAFTING_MACHINE,
                (part, side) -> part,
                Pp2mpInputPart.class
        );

        event.register(
                AECapabilities.ME_STORAGE,
                (part, side) -> part.getReceiveInventory(),
                Pp2mpOutputPart.class
        );
        event.register(
                AECapabilities.GENERIC_INTERNAL_INV,
                (part, side) -> part.getReceiveInventory(),
                Pp2mpOutputPart.class
        );
        event.register(
                Capabilities.ItemHandler.BLOCK,
                (part, side) -> new GenericStackItemStorage(
                        part.getReceiveInventory()
                ),
                Pp2mpOutputPart.class
        );
        event.register(
                Capabilities.FluidHandler.BLOCK,
                (part, side) -> new GenericStackFluidStorage(
                        part.getReceiveInventory()
                ),
                Pp2mpOutputPart.class
        );

        event.register(
                AECapabilities.ME_STORAGE,
                (part, side) -> part.getLogic().getResourceIntakeStorage(),
                Pp2mpResourceIntakePart.class
        );
        event.register(
                AECapabilities.GENERIC_INTERNAL_INV,
                (part, side) -> part.getLogic().getResourceIntakeStorage(),
                Pp2mpResourceIntakePart.class
        );
        event.register(
                Capabilities.ItemHandler.BLOCK,
                (part, side) -> new GenericStackItemStorage(part.getLogic().getResourceIntakeStorage()),
                Pp2mpResourceIntakePart.class
        );
        event.register(
                Capabilities.FluidHandler.BLOCK,
                (part, side) -> new GenericStackFluidStorage(part.getLogic().getResourceIntakeStorage()),
                Pp2mpResourceIntakePart.class
        );


        event.register(
                AECapabilities.ME_STORAGE,
                (part, side) -> part.getReceiveInventory(),
                Pp2mpUnitReturnPortPart.class
        );
        event.register(
                AECapabilities.GENERIC_INTERNAL_INV,
                (part, side) -> part.getReceiveInventory(),
                Pp2mpUnitReturnPortPart.class
        );
        event.register(
                Capabilities.ItemHandler.BLOCK,
                (part, side) -> new GenericStackItemStorage(
                        part.getReceiveInventory()
                ),
                Pp2mpUnitReturnPortPart.class
        );

        event.register(
                Capabilities.FluidHandler.BLOCK,
                (part, side) -> new GenericStackFluidStorage(
                        part.getReceiveInventory()
                ),
                Pp2mpUnitReturnPortPart.class
        );
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void registerBlockCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                BlockRegistry.IO_PORT_ENTITY.get(),
                (blockEntity, context) -> blockEntity
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                BlockRegistry.IO_PORT_ENTITY.get(),
                AEBaseInvBlockEntity::getExposedItemHandler
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                BlockRegistry.FAKE_CRAFTING_CONVERTER_ENTITY.get(),
                (blockEntity, context) -> blockEntity
        );
        event.registerBlockEntity(
                AECapabilities.ME_STORAGE,
                BlockRegistry.FAKE_CRAFTING_CONVERTER_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getStorage()
        );
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                BlockRegistry.FAKE_CRAFTING_CONVERTER_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getGenericInventory()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                BlockRegistry.FAKE_CRAFTING_CONVERTER_ENTITY.get(),
                (blockEntity, side) -> new GenericStackItemStorage(blockEntity.getGenericInventory())
        );
    }
}
