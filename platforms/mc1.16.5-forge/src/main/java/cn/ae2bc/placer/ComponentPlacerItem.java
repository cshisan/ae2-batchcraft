package cn.ae2bc.placer;

import cn.ae2bc.core.ProjectLimits;
import appeng.core.Api;
import appeng.api.config.Actionable;
import appeng.api.parts.BusSupport;
import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.core.AEConfig;
import appeng.items.tools.powered.WirelessTerminalItem;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.menu.ComponentPlacerMenu;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID)
public final class ComponentPlacerItem extends WirelessTerminalItem {
    public static final int MATERIAL_SLOT_COUNT = ProjectLimits.COMPONENT_PLACER_MATERIAL_SLOT_COUNT;
    private static final String SETTINGS_KEY = "ae2bc_settings";
    private static final String SELECTION_KEY = "ae2bc_selection";
    private static final String MATERIALS_KEY = "ae2bc_materials";
    private static final String CABLE_KEY = "ae2bc_cable";
    private static final String PART_KEY = "ae2bc_part";
    private static final String UPGRADES_KEY = "ae2bc_upgrades";
    private static final String FREQUENCY_KEY = "ae2bc_frequency";

    public ComponentPlacerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean canHandle(ItemStack stack) {
        return stack.getItem() == this;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return AEConfig.instance().getWirelessTerminalBattery().getAsDouble();
    }

    @Override
    public ActionResultType useOn(ItemUseContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResultType.PASS;
        return trySelect(player, context.getItemInHand(), context.getClickedPos(), context.getLevel(),
                player.isShiftKeyDown())
                ? ActionResultType.sidedSuccess(context.getLevel().isClientSide)
                : ActionResultType.PASS;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack placer = event.getItemStack();
        PlayerEntity player = event.getPlayer();
        if (!(placer.getItem() instanceof ComponentPlacerItem)
                && event.getHand() == Hand.MAIN_HAND
                && placer.isEmpty()
                && player.getOffhandItem().getItem() instanceof ComponentPlacerItem) {
            placer = player.getOffhandItem();
        }
        if (!(placer.getItem() instanceof ComponentPlacerItem)
                || !trySelect(player, placer, event.getPos(), event.getWorld(), player.isShiftKeyDown())) {
            return;
        }
        event.setCancellationResult(ActionResultType.sidedSuccess(event.getWorld().isClientSide));
        event.setCanceled(true);
    }

    private static boolean trySelect(PlayerEntity player, ItemStack placer, BlockPos pos,
            World world, boolean secondary) {
        ComponentPlacerSelection selection = getSelection(placer);
        if (!secondary && (selection == null || selection.isComplete()
                || !selection.getDimension().equals(world.dimension().location()))) {
            return false;
        }
        if (world.isClientSide) return true;

        if (secondary) {
            setSelection(placer, ComponentPlacerSelection.start(world.dimension(), pos));
            setSettings(placer, getSettings(placer).resetOffsets());
            player.displayClientMessage(new TranslationTextComponent(
                    "message.ae2_batchcraft.component_placer.first_set", pos.toShortString()), false);
        } else {
            ComponentPlacerSelection completed = selection.complete(pos);
            ComponentPlacerSelection.SelectionValidation validation = completed.validate();
            if (validation == ComponentPlacerSelection.SelectionValidation.VALID) {
                setSelection(placer, completed);
                player.displayClientMessage(new TranslationTextComponent(
                        "message.ae2_batchcraft.component_placer.selection_set",
                        completed.sizeX(), completed.sizeY(), completed.sizeZ()), false);
            } else {
                player.displayClientMessage(new TranslationTextComponent(
                        "message.ae2_batchcraft.component_placer.selection_"
                                + validation.name().toLowerCase(Locale.ROOT)), false);
            }
        }
        return true;
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player instanceof ServerPlayerEntity) {
            NetworkHooks.openGui((ServerPlayerEntity) player,
                    new ComponentPlacerMenu.Provider(hand, stack.getHoverName()),
                    buffer -> ComponentPlacerMenu.writeInitialData(buffer, player, hand));
        }
        return ActionResult.sidedSuccess(stack, world.isClientSide);
    }

    public static ComponentPlacerSettings getSettings(ItemStack stack) {
        CompoundNBT root = stack.getTag();
        return root == null ? ComponentPlacerSettings.DEFAULT
                : ComponentPlacerSettings.read(root.getCompound(SETTINGS_KEY));
    }

    public static void setSettings(ItemStack stack, ComponentPlacerSettings settings) {
        stack.getOrCreateTag().put(SETTINGS_KEY, settings.write());
    }

    public static ComponentPlacerSelection getSelection(ItemStack stack) {
        CompoundNBT root = stack.getTag();
        return root == null ? null : ComponentPlacerSelection.read(root.getCompound(SELECTION_KEY));
    }

    public static void setSelection(ItemStack stack, ComponentPlacerSelection selection) {
        if (selection == null) {
            if (stack.getTag() != null) stack.getTag().remove(SELECTION_KEY);
        } else stack.getOrCreateTag().put(SELECTION_KEY, selection.write());
    }

    public static short getFrequency(ItemStack stack) {
        CompoundNBT root = stack.getTag();
        return root == null ? 0 : root.getShort(FREQUENCY_KEY);
    }

    public static void setFrequency(ItemStack stack, short frequency) {
        stack.getOrCreateTag().putShort(FREQUENCY_KEY, frequency);
    }

    public static IItemHandler getMaterials(ItemStack stack) {
        return new ComponentPlacerInventory(stack, MATERIALS_KEY, MATERIAL_SLOT_COUNT, 64,
                (inventory, slot, candidate) -> isAllowedMaterial(candidate));
    }

    public static IItemHandler getCableMarker(ItemStack stack) {
        return new ComponentPlacerInventory(stack, CABLE_KEY, 1, 1,
                (inventory, slot, candidate) -> candidate.isEmpty() || isUsableCable(candidate));
    }

    public static IItemHandler getPartMarker(ItemStack stack) {
        return new ComponentPlacerInventory(stack, PART_KEY, 1, 1,
                (inventory, slot, candidate) -> candidate.isEmpty() || isUsablePart(candidate));
    }

    public static IItemHandler getUpgrades(ItemStack stack) {
        return new ComponentPlacerInventory(stack, UPGRADES_KEY, 1, 1,
                (inventory, slot, candidate) -> candidate.isEmpty() || isCraftingCard(candidate), true);
    }

    public static ItemStack getMarkedCable(ItemStack stack) {
        return getCableMarker(stack).getStackInSlot(0).copy();
    }

    public static ItemStack getMarkedPart(ItemStack stack) {
        return getPartMarker(stack).getStackInSlot(0).copy();
    }

    public static boolean hasCraftingCard(ItemStack stack) {
        return isCraftingCard(getUpgrades(stack).getStackInSlot(0));
    }

    public static List<ItemStack> migrateLegacyUpgrades(ItemStack stack) {
        List<ItemStack> returned = new ArrayList<ItemStack>();
        CompoundNBT root = stack.getTag();
        if (root == null || !root.contains(UPGRADES_KEY, 10)) return returned;

        CompoundNBT stored = root.getCompound(UPGRADES_KEY);
        net.minecraft.nbt.ListNBT items = stored.getList("Items", 10);
        ItemStack craftingCard = ItemStack.EMPTY;
        for (int index = 0; index < items.size(); index++) {
            ItemStack candidate = ItemStack.of(items.getCompound(index));
            if (candidate.isEmpty()) continue;
            if (craftingCard.isEmpty() && isCraftingCard(candidate)) {
                craftingCard = candidate.copy();
                craftingCard.setCount(1);
                candidate.shrink(1);
            }
            if (!candidate.isEmpty()) returned.add(candidate.copy());
        }

        CompoundNBT normalized = new CompoundNBT();
        net.minecraft.nbt.ListNBT normalizedItems = new net.minecraft.nbt.ListNBT();
        if (!craftingCard.isEmpty()) {
            CompoundNBT cardTag = new CompoundNBT();
            cardTag.putInt("Slot", 0);
            normalizedItems.add(craftingCard.save(cardTag));
        }
        normalized.put("Items", normalizedItems);
        normalized.putInt("Size", 1);
        if (!normalized.equals(stored)) root.put(UPGRADES_KEY, normalized);
        return returned;
    }

    private static boolean isCraftingCard(ItemStack stack) {
        return !stack.isEmpty() && Api.instance().definitions().materials().cardCrafting().isSameAs(stack);
    }

    public static boolean isUsableCable(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IPartItem)) return false;
        IPart part = ((IPartItem<?>) stack.getItem()).createPart(stack);
        return part instanceof ICablePart && ((ICablePart) part).supportsBuses() == BusSupport.CABLE;
    }

    public static boolean isUsablePart(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IPartItem)) return false;
        return !(((IPartItem<?>) stack.getItem()).createPart(stack) instanceof ICablePart);
    }

    public static boolean isAllowedMaterial(ItemStack stack) {
        return isUsableCable(stack) || isUsablePart(stack);
    }
}
