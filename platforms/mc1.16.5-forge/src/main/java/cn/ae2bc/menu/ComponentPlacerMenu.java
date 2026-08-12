package cn.ae2bc.menu;

import appeng.core.Api;
import appeng.api.implementations.items.IMemoryCard;
import appeng.container.ContainerLocator;
import appeng.container.me.crafting.CraftAmountContainer;
import appeng.util.Platform;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.placer.ComponentPlacementService;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.placer.ComponentPlacerNetworkAccess;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import cn.ae2bc.registry.ModContent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.IIntArray;
import net.minecraft.util.IntArray;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public final class ComponentPlacerMenu extends Container {
    public static final int DATA_COUNT = 11;
    private final PlayerEntity player;
    private final Hand hand;
    private final ItemStack placer;
    private final IItemHandler cableMarker;
    private final IItemHandler partMarker;
    private final IItemHandler materials;
    private final IItemHandler upgrades;
    private final ComponentPlacerNetworkAccess networkAccess;
    private final IIntArray data;

    public ComponentPlacerMenu(int id, PlayerInventory inventory, PacketBuffer buffer) {
        this(id, inventory, Hand.values()[buffer.readUnsignedByte() % Hand.values().length], readData(buffer));
    }

    private ComponentPlacerMenu(int id, PlayerInventory inventory, Hand hand, IIntArray initialData) {
        super(ModContent.COMPONENT_PLACER_MENU.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        this.placer = player.getItemInHand(hand);
        this.data = initialData;
        this.cableMarker = ComponentPlacerItem.getCableMarker(placer);
        this.partMarker = ComponentPlacerItem.getPartMarker(placer);
        this.materials = ComponentPlacerItem.getMaterials(placer);
        this.upgrades = ComponentPlacerItem.getUpgrades(placer);
        this.networkAccess = player.level.isClientSide ? null
                : new ComponentPlacerNetworkAccess(player, hand, placer);
        addDataSlots(data);
        addPlacerSlots(inventory);
    }

    public ComponentPlacerMenu(int id, PlayerInventory inventory, Hand hand) {
        this(id, inventory, hand, createData(inventory.player, hand));
    }

    private void addPlacerSlots(PlayerInventory inventory) {
        addSlot(new MarkerSlot(cableMarker, 0, 40, 22));
        addSlot(new MarkerSlot(partMarker, 0, 88, 22));
        for (int i = 0; i < ComponentPlacerItem.MATERIAL_SLOT_COUNT; i++) {
            addSlot(new SlotItemHandler(materials, i, 8 + i * 18, 115));
        }
        for (int i = 0; i < 3; i++) addSlot(new SlotItemHandler(upgrades, i, 184, 8 + i * 18));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 146 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 204));
        }
    }

    @Override
    public ItemStack clicked(int slotId, int dragType, ClickType clickType, PlayerEntity player) {
        if (slotId == 0 || slotId == 1) {
            IItemHandler marker = slotId == 0 ? cableMarker : partMarker;
            ItemStack carried = player.inventory.getCarried();
            if (carried.isEmpty()) {
                if (marker instanceof net.minecraftforge.items.IItemHandlerModifiable) {
                    ((net.minecraftforge.items.IItemHandlerModifiable) marker).setStackInSlot(0, ItemStack.EMPTY);
                }
            } else if (marker.isItemValid(0, carried)
                    && marker instanceof net.minecraftforge.items.IItemHandlerModifiable) {
                ItemStack marked = carried.copy();
                marked.setCount(1);
                ((net.minecraftforge.items.IItemHandlerModifiable) marker).setStackInSlot(0, marked);
            }
            return carried;
        }
        return super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(PlayerEntity player, int index) {
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack result = source.copy();
        if (index < 14) {
            if (!moveItemStackTo(source, 14, slots.size(), true)) return ItemStack.EMPTY;
        } else if (ComponentPlacerItem.isAllowedMaterial(source)) {
            if (!moveItemStackTo(source, 2, 11, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 11, 14, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }

    @Override
    public void broadcastChanges() {
        if (!player.level.isClientSide) refreshData(data, player, hand, networkAccess);
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(PlayerEntity player) {
        return placer.getItem() instanceof ComponentPlacerItem && player.getItemInHand(hand) == placer;
    }

    public void handleAction(int action, int value) {
        if (!(player instanceof ServerPlayerEntity) || !stillValid(player)) return;
        ComponentPlacerSettings settings = ComponentPlacerItem.getSettings(placer);
        switch (action) {
            case ModNetwork.PLACER_SET_DIRECTION:
                ComponentPlacerItem.setSettings(placer, settings.withDirection(
                        Direction.values()[Math.floorMod(value, Direction.values().length)]));
                break;
            case ModNetwork.PLACER_ADJUST_X:
                ComponentPlacerItem.setSettings(placer, settings.withOffsets(
                        ComponentPlacerSettings.clampOffset(settings.getOffsetX() + value),
                        settings.getOffsetY(), settings.getOffsetZ()));
                break;
            case ModNetwork.PLACER_ADJUST_Y:
                ComponentPlacerItem.setSettings(placer, settings.withOffsets(settings.getOffsetX(),
                        ComponentPlacerSettings.clampOffset(settings.getOffsetY() + value), settings.getOffsetZ()));
                break;
            case ModNetwork.PLACER_ADJUST_Z:
                ComponentPlacerItem.setSettings(placer, settings.withOffsets(settings.getOffsetX(),
                        settings.getOffsetY(), ComponentPlacerSettings.clampOffset(settings.getOffsetZ() + value)));
                break;
            case ModNetwork.PLACER_RESET_OFFSETS:
                ComponentPlacerItem.setSettings(placer, settings.resetOffsets());
                break;
            case ModNetwork.PLACER_CLEAR_SELECTION:
                ComponentPlacerItem.setSelection(placer, null);
                break;
            case ModNetwork.PLACER_EXECUTE:
                execute((ServerPlayerEntity) player);
                break;
            case ModNetwork.PLACER_LOAD_FREQUENCY:
                loadFrequency();
                break;
            case ModNetwork.PLACER_RESET_FREQUENCY:
                ComponentPlacerItem.setFrequency(placer, (short) 0);
                player.displayClientMessage(new TranslationTextComponent(
                        "message.ae2_batchcraft.component_placer.frequency.reset"), false);
                break;
            default:
                break;
        }
        refreshData(data, player, hand, networkAccess);
    }

    private void execute(ServerPlayerEntity serverPlayer) {
        ComponentPlacementService.Result result = ComponentPlacementService.place(serverPlayer, this);
        if (result.getFailureReason() == ComponentPlacementService.FailureReason.POWER) {
            serverPlayer.displayClientMessage(new TranslationTextComponent(
                    "message.ae2_batchcraft.component_placer.power_unavailable"), false);
            return;
        }
        if (result.getFailureReason() == ComponentPlacementService.FailureReason.NETWORK) {
            serverPlayer.displayClientMessage(new TranslationTextComponent(
                    "message.ae2_batchcraft.component_placer.network_unavailable"), false);
            return;
        }
        if (!result.getMissingMaterial().isEmpty()) {
            appeng.api.storage.data.IAEItemStack missing = Api.instance().storage()
                    .getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class)
                    .createStack(result.getMissingMaterial());
            if (missing != null) {
                missing.setStackSize(result.getMissingAmount());
                CraftAmountContainer.open(serverPlayer, ContainerLocator.forHand(player, hand),
                        missing, result.getMissingAmount());
                return;
            }
        }
        serverPlayer.displayClientMessage(new TranslationTextComponent(
                "message.ae2_batchcraft.component_placer.result", result.getPlaced(), result.getOccupied(),
                result.getMaterialFailed(), result.getPlacementFailed()), false);
    }

    private void loadFrequency() {
        ItemStack card = player.inventory.getCarried();
        if (!(card.getItem() instanceof IMemoryCard)) card = player.getMainHandItem();
        if (!(card.getItem() instanceof IMemoryCard)) card = player.getOffhandItem();
        if (!(card.getItem() instanceof IMemoryCard)) {
            player.displayClientMessage(new TranslationTextComponent(
                    "message.ae2_batchcraft.component_placer.frequency.no_card"), false);
            return;
        }
        net.minecraft.nbt.CompoundNBT cardData = ((IMemoryCard) card.getItem()).getData(card);
        if (!cardData.contains("freq")) {
            player.displayClientMessage(new TranslationTextComponent(
                    "message.ae2_batchcraft.component_placer.frequency.invalid_card"), false);
            return;
        }
        short frequency = cardData.getShort("freq");
        ComponentPlacerItem.setFrequency(placer, frequency);
        player.displayClientMessage(new TranslationTextComponent(
                "message.ae2_batchcraft.component_placer.frequency.loaded",
                Platform.p2p().toHexString(frequency)), false);
    }

    public Direction getDirection() { return Direction.values()[data.get(0)]; }
    public int getOffsetX() { return data.get(1); }
    public int getOffsetY() { return data.get(2); }
    public int getOffsetZ() { return data.get(3); }
    public ComponentPlacerSelection.SelectionValidation getSelectionState() {
        return ComponentPlacerSelection.SelectionValidation.values()[data.get(4)];
    }
    public int getSizeX() { return data.get(5); }
    public int getSizeY() { return data.get(6); }
    public int getSizeZ() { return data.get(7); }
    public boolean hasSelection() { return data.get(8) != 0; }
    public int getFrequency() { return data.get(9); }
    public boolean isAeConnected() { return data.get(10) != 0; }
    public boolean hasCable() { return ComponentPlacerItem.isUsableCable(cableMarker.getStackInSlot(0)); }
    public boolean hasPart() { return ComponentPlacerItem.isUsablePart(partMarker.getStackInSlot(0)); }
    public Hand getHand() { return hand; }
    public ItemStack getPlacer() { return placer; }
    public IItemHandler getMaterials() { return materials; }
    public ComponentPlacerNetworkAccess getNetworkAccess() { return networkAccess; }

    public static void writeInitialData(PacketBuffer buffer, PlayerEntity player, Hand hand) {
        buffer.writeByte(hand.ordinal());
        IIntArray data = createData(player, hand);
        for (int i = 0; i < DATA_COUNT; i++) buffer.writeInt(data.get(i));
    }

    private static IIntArray readData(PacketBuffer buffer) {
        IntArray result = new IntArray(DATA_COUNT);
        for (int i = 0; i < DATA_COUNT; i++) result.set(i, buffer.readInt());
        return result;
    }

    private static IIntArray createData(PlayerEntity player, Hand hand) {
        IntArray result = new IntArray(DATA_COUNT);
        ComponentPlacerNetworkAccess access = player.level.isClientSide ? null
                : new ComponentPlacerNetworkAccess(player, hand, player.getItemInHand(hand));
        refreshData(result, player, hand, access);
        return result;
    }

    private static void refreshData(IIntArray result, PlayerEntity player, Hand hand,
                                    ComponentPlacerNetworkAccess access) {
        ItemStack stack = player.getItemInHand(hand);
        ComponentPlacerSettings settings = ComponentPlacerItem.getSettings(stack);
        ComponentPlacerSelection selection = ComponentPlacerItem.getSelection(stack);
        result.set(0, settings.getDirection().ordinal());
        result.set(1, settings.getOffsetX());
        result.set(2, settings.getOffsetY());
        result.set(3, settings.getOffsetZ());
        result.set(4, (selection == null ? ComponentPlacerSelection.SelectionValidation.INCOMPLETE
                : selection.validate()).ordinal());
        result.set(5, selection == null ? 0 : selection.sizeX());
        result.set(6, selection == null ? 0 : selection.sizeY());
        result.set(7, selection == null ? 0 : selection.sizeZ());
        result.set(8, selection == null ? 0 : 1);
        result.set(9, Short.toUnsignedInt(ComponentPlacerItem.getFrequency(stack)));
        result.set(10, access != null && access.isConnected() ? 1 : 0);
    }

    private static final class MarkerSlot extends SlotItemHandler {
        private MarkerSlot(IItemHandler inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        @Override public boolean mayPickup(PlayerEntity player) { return false; }
    }

    public static final class Provider implements INamedContainerProvider {
        private final Hand hand;
        private final ITextComponent title;
        public Provider(Hand hand, ITextComponent title) { this.hand = hand; this.title = title; }
        @Override public ITextComponent getDisplayName() { return title; }
        @Override public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player) {
            return new ComponentPlacerMenu(id, inventory, hand);
        }
    }
}
