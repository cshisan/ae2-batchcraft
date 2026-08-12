package cn.ae2bc.menu;

import appeng.api.AEApi;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.util.Platform;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.placer.ComponentPlacementService;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.placer.ComponentPlacerNetworkAccess;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.SlotItemHandler;

public final class ComponentPlacerMenu extends Container {
    private static final int DATA_COUNT = 11;
    private final EntityPlayer player;
    private final EnumHand hand;
    private final ItemStack placer;
    private final IItemHandler cableMarker;
    private final IItemHandler partMarker;
    private final IItemHandler materials;
    private final IItemHandler upgrades;
    private final ComponentPlacerNetworkAccess networkAccess;
    private final int[] data = new int[DATA_COUNT];
    private final int[] previous = new int[DATA_COUNT];

    public ComponentPlacerMenu(EntityPlayer player, EnumHand hand) {
        this.player = player; this.hand = hand; this.placer = player.getHeldItem(hand);
        cableMarker = ComponentPlacerItem.getCableMarker(placer);
        partMarker = ComponentPlacerItem.getPartMarker(placer);
        materials = ComponentPlacerItem.getMaterials(placer);
        upgrades = ComponentPlacerItem.getUpgrades(placer);
        networkAccess = player.world.isRemote ? null
                : new ComponentPlacerNetworkAccess(player, hand, placer);
        refreshData(); java.util.Arrays.fill(previous, Integer.MIN_VALUE);
        addPlacerSlots(player.inventory);
    }

    private void addPlacerSlots(InventoryPlayer inventory) {
        addSlotToContainer(new MarkerSlot(cableMarker, 0, 40, 22));
        addSlotToContainer(new MarkerSlot(partMarker, 0, 88, 22));
        for (int i = 0; i < 9; i++) addSlotToContainer(new SlotItemHandler(materials, i, 8 + i * 18, 115));
        for (int i = 0; i < 3; i++) addSlotToContainer(new SlotItemHandler(upgrades, i, 187, 8 + i * 18));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlotToContainer(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 146 + row * 18));
        for (int column = 0; column < 9; column++)
            addSlotToContainer(new Slot(inventory, column, 8 + column * 18, 204));
    }

    @Override public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (slotId == 0 || slotId == 1) {
            IItemHandler marker = slotId == 0 ? cableMarker : partMarker;
            ItemStack carried = player.inventory.getItemStack();
            if (marker instanceof IItemHandlerModifiable) {
                if (carried.isEmpty()) ((IItemHandlerModifiable) marker).setStackInSlot(0, ItemStack.EMPTY);
                else if (marker.isItemValid(0, carried)) {
                    ItemStack marked = carried.copy(); marked.setCount(1);
                    ((IItemHandlerModifiable) marker).setStackInSlot(0, marked);
                }
            }
            return carried;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = index >= 0 && index < inventorySlots.size() ? inventorySlots.get(index) : null;
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack source = slot.getStack(); ItemStack result = source.copy();
        if (index < 14) {
            if (!mergeItemStack(source, 14, inventorySlots.size(), true)) return ItemStack.EMPTY;
        } else if (ComponentPlacerItem.isAllowedMaterial(source)) {
            if (!mergeItemStack(source, 2, 11, false)) return ItemStack.EMPTY;
        } else if (!mergeItemStack(source, 11, 14, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        return result;
    }

    @Override public void detectAndSendChanges() {
        if (!player.world.isRemote) refreshData();
        super.detectAndSendChanges();
        for (int i = 0; i < DATA_COUNT; i++) {
            if (data[i] != previous[i]) {
                for (IContainerListener listener : listeners) listener.sendWindowProperty(this, i, data[i]);
                previous[i] = data[i];
            }
        }
    }
    @Override public void updateProgressBar(int id, int value) {
        if (id >= 0 && id < DATA_COUNT) data[id] = value;
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return placer.getItem() instanceof ComponentPlacerItem && player.getHeldItem(hand) == placer;
    }

    public void handleAction(int action, int value) {
        if (!(player instanceof EntityPlayerMP) || !canInteractWith(player)) return;
        ComponentPlacerSettings settings = ComponentPlacerItem.getSettings(placer);
        switch (action) {
            case ModNetwork.PLACER_SET_DIRECTION:
                ComponentPlacerItem.setSettings(placer, settings.withDirection(
                        EnumFacing.values()[Math.floorMod(value, EnumFacing.values().length)])); break;
            case ModNetwork.PLACER_ADJUST_X:
                ComponentPlacerItem.setSettings(placer, settings.withOffsets(
                        ComponentPlacerSettings.clampOffset(settings.getOffsetX() + value), settings.getOffsetY(), settings.getOffsetZ())); break;
            case ModNetwork.PLACER_ADJUST_Y:
                ComponentPlacerItem.setSettings(placer, settings.withOffsets(settings.getOffsetX(),
                        ComponentPlacerSettings.clampOffset(settings.getOffsetY() + value), settings.getOffsetZ())); break;
            case ModNetwork.PLACER_ADJUST_Z:
                ComponentPlacerItem.setSettings(placer, settings.withOffsets(settings.getOffsetX(), settings.getOffsetY(),
                        ComponentPlacerSettings.clampOffset(settings.getOffsetZ() + value))); break;
            case ModNetwork.PLACER_RESET_OFFSETS: ComponentPlacerItem.setSettings(placer, settings.resetOffsets()); break;
            case ModNetwork.PLACER_CLEAR_SELECTION: ComponentPlacerItem.setSelection(placer, null); break;
            case ModNetwork.PLACER_EXECUTE: execute((EntityPlayerMP) player); break;
            case ModNetwork.PLACER_LOAD_FREQUENCY: loadFrequency(); break;
            case ModNetwork.PLACER_RESET_FREQUENCY:
                ComponentPlacerItem.setFrequency(placer, (short) 0);
                player.sendStatusMessage(new TextComponentTranslation(
                        "message.ae2_batchcraft.component_placer.frequency.reset"), false); break;
            default: break;
        }
        refreshData();
    }

    private void execute(EntityPlayerMP serverPlayer) {
        ComponentPlacementService.Result result = ComponentPlacementService.place(serverPlayer, this);
        if (result.getFailureReason() == ComponentPlacementService.FailureReason.POWER) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "message.ae2_batchcraft.component_placer.power_unavailable"), false);
            return;
        }
        if (result.getFailureReason() == ComponentPlacementService.FailureReason.NETWORK) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "message.ae2_batchcraft.component_placer.network_unavailable"), false);
            return;
        }
        if (!result.getMissingMaterial().isEmpty()) {
            IAEItemStack missing = AEApi.instance().storage()
                    .getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class)
                    .createStack(result.getMissingMaterial());
            if (missing != null) {
                missing.setStackSize(result.getMissingAmount());
                int slot = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
                player.openGui(Ae2bcMod.INSTANCE, Ae2bcMod.GUI_COMPONENT_PLACER_CRAFT_AMOUNT,
                        player.world, slot, hand.ordinal(), result.getMissingAmount());
                if (player.openContainer instanceof ContainerCraftAmount) {
                    ContainerCraftAmount container = (ContainerCraftAmount) player.openContainer;
                    container.getCraftingItem().putStack(missing.asItemStackRepresentation());
                    container.setItemToCraft(missing);
                    container.detectAndSendChanges();
                }
                return;
            }
        }
        player.sendStatusMessage(new TextComponentTranslation("message.ae2_batchcraft.component_placer.result",
                result.getPlaced(), result.getOccupied(), result.getMaterialFailed(), result.getPlacementFailed()), false);
    }

    private void loadFrequency() {
        ItemStack card = player.inventory.getItemStack();
        if (!(card.getItem() instanceof IMemoryCard)) card = player.getHeldItemMainhand();
        if (!(card.getItem() instanceof IMemoryCard)) card = player.getHeldItemOffhand();
        if (!(card.getItem() instanceof IMemoryCard)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "message.ae2_batchcraft.component_placer.frequency.no_card"), false); return;
        }
        net.minecraft.nbt.NBTTagCompound tag = ((IMemoryCard) card.getItem()).getData(card);
        if (!tag.hasKey("freq")) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "message.ae2_batchcraft.component_placer.frequency.invalid_card"), false); return;
        }
        short frequency = tag.getShort("freq"); ComponentPlacerItem.setFrequency(placer, frequency);
        player.sendStatusMessage(new TextComponentTranslation(
                "message.ae2_batchcraft.component_placer.frequency.loaded", Platform.p2p().toHexString(frequency)), false);
    }

    private void refreshData() {
        ComponentPlacerSettings settings = ComponentPlacerItem.getSettings(placer);
        ComponentPlacerSelection selection = ComponentPlacerItem.getSelection(placer);
        data[0] = settings.getDirection().ordinal(); data[1] = settings.getOffsetX();
        data[2] = settings.getOffsetY(); data[3] = settings.getOffsetZ();
        data[4] = (selection == null ? ComponentPlacerSelection.SelectionValidation.INCOMPLETE : selection.validate()).ordinal();
        data[5] = selection == null ? 0 : selection.sizeX(); data[6] = selection == null ? 0 : selection.sizeY();
        data[7] = selection == null ? 0 : selection.sizeZ(); data[8] = selection == null ? 0 : 1;
        data[9] = ComponentPlacerItem.getFrequency(placer) & 0xFFFF;
        data[10] = networkAccess != null && networkAccess.isConnected() ? 1 : 0;
    }
    public EnumFacing getDirection() { return EnumFacing.values()[data[0]]; }
    public int getOffsetX() { return data[1]; } public int getOffsetY() { return data[2]; } public int getOffsetZ() { return data[3]; }
    public ComponentPlacerSelection.SelectionValidation getSelectionState() { return ComponentPlacerSelection.SelectionValidation.values()[data[4]]; }
    public int getSizeX() { return data[5]; } public int getSizeY() { return data[6]; } public int getSizeZ() { return data[7]; }
    public boolean hasSelection() { return data[8] != 0; } public int getFrequency() { return data[9]; }
    public boolean isAeConnected() { return data[10] != 0; }
    public boolean hasCable() { return ComponentPlacerItem.isUsableCable(cableMarker.getStackInSlot(0)); }
    public boolean hasPart() { return ComponentPlacerItem.isUsablePart(partMarker.getStackInSlot(0)); }
    public EnumHand getHand() { return hand; }
    public ItemStack getPlacer() { return placer; }
    public IItemHandler getMaterials() { return materials; }
    public ComponentPlacerNetworkAccess getNetworkAccess() { return networkAccess; }
    private static final class MarkerSlot extends SlotItemHandler {
        MarkerSlot(IItemHandler inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean canTakeStack(EntityPlayer player) { return false; }
    }
}
