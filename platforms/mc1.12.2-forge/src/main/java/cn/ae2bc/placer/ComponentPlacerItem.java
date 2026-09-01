package cn.ae2bc.placer;

import cn.ae2bc.core.ProjectLimits;
import appeng.api.AEApi;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.parts.IPartCable;
import appeng.api.parts.BusSupport;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.items.tools.powered.ToolWirelessTerminal;
import cn.ae2bc.Ae2bcMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID)
public final class ComponentPlacerItem extends ToolWirelessTerminal {
    public static final int MATERIAL_SLOT_COUNT = ProjectLimits.COMPONENT_PLACER_MATERIAL_SLOT_COUNT;
    private static final String SETTINGS_KEY = "ae2bc_settings";
    private static final String SELECTION_KEY = "ae2bc_selection";
    private static final String MATERIALS_KEY = "ae2bc_materials";
    private static final String CABLE_KEY = "ae2bc_cable";
    private static final String PART_KEY = "ae2bc_part";
    private static final String UPGRADES_KEY = "ae2bc_upgrades";
    private static final String FREQUENCY_KEY = "ae2bc_frequency";

    public ComponentPlacerItem() { setMaxStackSize(1); }
    @Override public boolean canHandle(ItemStack stack) { return stack.getItem() == this; }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                                EnumHand hand, EnumFacing facing,
                                                float hitX, float hitY, float hitZ) {
        return trySelect(player, player.getHeldItem(hand), pos, world, player.isSneaking())
                ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack placer = event.getItemStack();
        EntityPlayer player = event.getEntityPlayer();
        if (!(placer.getItem() instanceof ComponentPlacerItem)
                && event.getHand() == EnumHand.MAIN_HAND
                && placer.isEmpty()
                && player.getHeldItemOffhand().getItem() instanceof ComponentPlacerItem) {
            placer = player.getHeldItemOffhand();
        }
        if (!(placer.getItem() instanceof ComponentPlacerItem)
                || !trySelect(player, placer, event.getPos(), event.getWorld(), player.isSneaking())) return;
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static boolean trySelect(EntityPlayer player, ItemStack placer, BlockPos pos,
                                     World world, boolean secondary) {
        ComponentPlacerSelection selection = getSelection(placer);
        if (!secondary && (selection == null || selection.isComplete()
                || selection.getDimension() != world.provider.getDimension())) return false;
        if (world.isRemote) return true;
        if (secondary) {
            setSelection(placer, ComponentPlacerSelection.start(world.provider.getDimension(), pos));
            setSettings(placer, getSettings(placer).resetOffsets());
            player.sendStatusMessage(new TextComponentTranslation(
                    "message.ae2_batchcraft.component_placer.first_set", pos.toString()), false);
        } else {
            ComponentPlacerSelection completed = selection.complete(pos);
            ComponentPlacerSelection.SelectionValidation validation = completed.validate();
            if (validation == ComponentPlacerSelection.SelectionValidation.VALID) {
                setSelection(placer, completed);
                player.sendStatusMessage(new TextComponentTranslation(
                        "message.ae2_batchcraft.component_placer.selection_set",
                        completed.sizeX(), completed.sizeY(), completed.sizeZ()), false);
            } else player.sendStatusMessage(new TextComponentTranslation(
                    "message.ae2_batchcraft.component_placer.selection_"
                            + validation.name().toLowerCase(Locale.ROOT)), false);
        }
        return true;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            int slot = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
            player.openGui(Ae2bcMod.INSTANCE, Ae2bcMod.GUI_COMPONENT_PLACER, world, slot, hand.ordinal(), 0);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
    public static ComponentPlacerSettings getSettings(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        return root == null ? ComponentPlacerSettings.DEFAULT : ComponentPlacerSettings.read(root.getCompoundTag(SETTINGS_KEY));
    }
    public static void setSettings(ItemStack stack, ComponentPlacerSettings settings) { root(stack).setTag(SETTINGS_KEY, settings.write()); }
    public static ComponentPlacerSelection getSelection(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        return root == null ? null : ComponentPlacerSelection.read(root.getCompoundTag(SELECTION_KEY));
    }
    public static void setSelection(ItemStack stack, ComponentPlacerSelection selection) {
        if (selection == null) { if (stack.hasTagCompound()) stack.getTagCompound().removeTag(SELECTION_KEY); }
        else root(stack).setTag(SELECTION_KEY, selection.write());
    }
    public static short getFrequency(ItemStack stack) { return stack.hasTagCompound() ? stack.getTagCompound().getShort(FREQUENCY_KEY) : 0; }
    public static void setFrequency(ItemStack stack, short value) { root(stack).setShort(FREQUENCY_KEY, value); }
    public static IItemHandler getMaterials(ItemStack stack) {
        return new ComponentPlacerInventory(stack, MATERIALS_KEY, MATERIAL_SLOT_COUNT, 64,
                new ComponentPlacerInventory.Filter() { public boolean allow(ComponentPlacerInventory i, int s, ItemStack v) { return isAllowedMaterial(v); } });
    }
    public static IItemHandler getCableMarker(ItemStack stack) {
        return new ComponentPlacerInventory(stack, CABLE_KEY, 1, 1,
                new ComponentPlacerInventory.Filter() { public boolean allow(ComponentPlacerInventory i, int s, ItemStack v) { return v.isEmpty() || isUsableCable(v); } });
    }
    public static IItemHandler getPartMarker(ItemStack stack) {
        return new ComponentPlacerInventory(stack, PART_KEY, 1, 1,
                new ComponentPlacerInventory.Filter() { public boolean allow(ComponentPlacerInventory i, int s, ItemStack v) { return v.isEmpty() || isUsablePart(v); } });
    }
    public static IItemHandler getUpgrades(ItemStack stack) {
        return new ComponentPlacerInventory(stack, UPGRADES_KEY, 1, 1,
                new ComponentPlacerInventory.Filter() {
                    public boolean allow(ComponentPlacerInventory inventory, int slot, ItemStack candidate) {
                        return candidate.isEmpty() || isCraftingCard(candidate);
                    }
                }, true);
    }
    public static ItemStack getMarkedCable(ItemStack stack) { return getCableMarker(stack).getStackInSlot(0).copy(); }
    public static ItemStack getMarkedPart(ItemStack stack) { return getPartMarker(stack).getStackInSlot(0).copy(); }
    public static boolean hasCraftingCard(ItemStack stack) {
        return isCraftingCard(getUpgrades(stack).getStackInSlot(0));
    }

    public static List<ItemStack> migrateLegacyUpgrades(ItemStack stack) {
        List<ItemStack> returned = new ArrayList<ItemStack>();
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey(UPGRADES_KEY, 10)) return returned;

        NBTTagCompound stored = root.getCompoundTag(UPGRADES_KEY);
        NBTTagList items = stored.getTagList("Items", 10);
        ItemStack craftingCard = ItemStack.EMPTY;
        for (int index = 0; index < items.tagCount(); index++) {
            ItemStack candidate = new ItemStack(items.getCompoundTagAt(index));
            if (candidate.isEmpty()) continue;
            if (craftingCard.isEmpty() && isCraftingCard(candidate)) {
                craftingCard = candidate.copy();
                craftingCard.setCount(1);
                candidate.shrink(1);
            }
            if (!candidate.isEmpty()) returned.add(candidate.copy());
        }

        NBTTagCompound normalized = new NBTTagCompound();
        NBTTagList normalizedItems = new NBTTagList();
        if (!craftingCard.isEmpty()) {
            NBTTagCompound cardTag = new NBTTagCompound();
            cardTag.setInteger("Slot", 0);
            craftingCard.writeToNBT(cardTag);
            normalizedItems.appendTag(cardTag);
        }
        normalized.setTag("Items", normalizedItems);
        normalized.setInteger("Size", 1);
        if (!normalized.equals(stored)) root.setTag(UPGRADES_KEY, normalized);
        return returned;
    }

    private static boolean isCraftingCard(ItemStack stack) {
        return !stack.isEmpty() && AEApi.instance().definitions().materials().cardCrafting().isSameAs(stack);
    }
    public static boolean isUsableCable(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IPartItem)) return false;
        IPart part = ((IPartItem<?>) stack.getItem()).createPartFromItemStack(stack);
        return part instanceof IPartCable && ((IPartCable) part).supportsBuses() == BusSupport.CABLE;
    }
    public static boolean isUsablePart(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IPartItem)) return false;
        return !(((IPartItem<?>) stack.getItem()).createPartFromItemStack(stack) instanceof IPartCable);
    }
    public static boolean isAllowedMaterial(ItemStack stack) { return isUsableCable(stack) || isUsablePart(stack); }
    private static NBTTagCompound root(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }
}
