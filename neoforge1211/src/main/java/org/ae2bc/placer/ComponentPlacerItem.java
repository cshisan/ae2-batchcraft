package org.ae2bc.placer;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.BusSupport;
import appeng.api.parts.IPartItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.ae2bc.core.placer.SelectionStatus;
import org.ae2bc.placer.packet.ComponentPlacerServerPacket;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class ComponentPlacerItem extends WirelessTerminalItem implements IMemoryCard {
    private static final ResourceLocation EXTENDED_CHARGER = ResourceLocation.fromNamespaceAndPath("extendedae", "ex_charger");

    public ComponentPlacerItem() {
        super(AEConfig.instance().getWirelessTerminalBattery(), new Properties().stacksTo(1));
    }

    /* 放置模式拦截范围选择；频率模式交给目标自身的内存卡交互。 */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getEntity().getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof ComponentPlacerItem)) {
            if (event.getHand() != InteractionHand.MAIN_HAND
                    || !event.getEntity().getMainHandItem().isEmpty()) {
                return;
            }
            stack = event.getEntity().getOffhandItem();
            if (!(stack.getItem() instanceof ComponentPlacerItem)) {
                return;
            }
        }
        if (ComponentPlacerItem.isFrequencyMode(stack)) {
            return;
        }
        ComponentPlacerItem.select(
                event.getEntity(), stack, event.getLevel(), event.getPos(),
                event.getEntity().isSecondaryUseActive()
        );
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }

    /* 通过方块交互检查后，让扩展充能台处理主手物品，避免被内存卡逻辑截获。 */
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemStack();
        if (event.isCanceled() || event.getUsePhase() != UseItemOnBlockEvent.UsePhase.BLOCK
                || event.getHand() != InteractionHand.MAIN_HAND || player == null
                || player.isSecondaryUseActive() || !(stack.getItem() instanceof ComponentPlacerItem)
                || !ComponentPlacerItem.isFrequencyMode(stack)) {
            return;
        }
        if (event.getLevel().getBlockState(event.getPos()).getBlockHolder().is(ComponentPlacerItem.EXTENDED_CHARGER)) {
            event.cancelWithResult(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
        }
    }

    /* 通过客户端按住 Shift 滚轮切换放置模式和频率模式。 */
    @OnlyIn(Dist.CLIENT)
    public static void handleScroll(InputEvent.MouseScrollingEvent event) {
        if (!Screen.hasShiftDown() || event.getScrollDeltaY() == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        ItemStack held = ComponentPlacerItem.getHeldPlacer(minecraft.player);
        if (held.isEmpty()) {
            return;
        }
        boolean frequencyMode = !ComponentPlacerData.frequencyMode(held);
        ComponentPlacerData.frequencyMode(held, frequencyMode);
        PacketDistributor.sendToServer(new ComponentPlacerServerPacket(frequencyMode));
        event.setCanceled(true);
    }

    public static ItemStack getHeldPlacer(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof ComponentPlacerItem) return mainHand;
        ItemStack offHand = player.getOffhandItem();
        return offHand.getItem() instanceof ComponentPlacerItem ? offHand : ItemStack.EMPTY;
    }

    public static boolean isFrequencyMode(ItemStack stack) {
        return ComponentPlacerData.frequencyMode(stack);
    }

    /* 保存放置范围，并将无效范围显式映射到对应的语言条目。 */
    private static void select(Player player, ItemStack stack, Level level, BlockPos pos, boolean secondary) {
        if (level.isClientSide()) {
            return;
        }
        if (secondary) {
            ComponentPlacerData.selection(stack, ComponentPlacerSelection.start(level.dimension().location(), pos));
            ComponentPlacerData.pose(stack, ComponentPlacerData.pose(stack).resetOffsets());
            player.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.first_set", pos.toShortString()), false);
            return;
        }
        ComponentPlacerSelection selection = ComponentPlacerData.selection(stack);
        if (selection == null || selection.second() != null
                || !selection.dimension().equals(level.dimension().location())) {
            return;
        }
        ComponentPlacerSelection updated = selection.complete(pos);
        SelectionStatus status = updated.validate();
        if (status == SelectionStatus.VALID) {
            ComponentPlacerData.selection(stack, updated);
            player.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.selection_set",
                    updated.sizeX(), updated.sizeY(), updated.sizeZ()), false);
        } else {
            String messageKey = "message.ae2_batchcraft.component_placer.selection_not_allowed";
            if (status == SelectionStatus.TOO_LARGE) {
                messageKey = "message.ae2_batchcraft.component_placer.selection_too_large";
            }
            player.displayClientMessage(Component.translatable(messageKey), false);
        }
    }

    public static boolean isUsableCable(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IPartItem<?> item)) return false;
        return item.createPart() instanceof ICablePart cable && cable.supportsBuses() == BusSupport.CABLE;
    }

    public static boolean isUsablePart(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IPartItem<?> item
                && !ICablePart.class.isAssignableFrom(item.getPartClass());
    }

    public static boolean isAllowedMaterial(ItemStack stack) {
        return isUsableCable(stack) || isUsablePart(stack);
    }

    public static boolean hasCraftingCard(ItemStack stack) {
        return stack.getItem() instanceof ComponentPlacerItem item
                && item.getUpgrades(stack).isInstalled(AEItems.CRAFTING_CARD);
    }

    /* 目标未处理交互时，频率模式沿用内存卡的清除行为。 */
    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        boolean secondary = player.isSecondaryUseActive();
        if (ComponentPlacerItem.isFrequencyMode(stack)) {
            return AEItems.MEMORY_CARD.asItem().useOn(context);
        }
        if (!secondary) {
            ComponentPlacerSelection selection = ComponentPlacerData.selection(stack);
            if (selection == null || selection.second() != null
                    || !selection.dimension().equals(context.getLevel().dimension().location())) {
                return InteractionResult.PASS;
            }
        }
        ComponentPlacerItem.select(player, stack, context.getLevel(), context.getClickedPos(), secondary);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    /* 潜行时仍允许目标接收内存卡交互。 */
    @Override
    public boolean doesSneakBypassUse(
            @NonNull ItemStack stack, @NonNull LevelReader level,
            @NonNull BlockPos pos, @NonNull Player player
    ) {
        return ComponentPlacerItem.isFrequencyMode(stack);
    }

    @Override
    public void notifyUser(Player player, MemoryCardMessages message) {
        AEItems.MEMORY_CARD.asItem().notifyUser(player, message);
    }

    /* 频率模式展示 AE2 内存卡保存的来源和频率。 */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag advancedTooltips
    ) {
        super.appendHoverText(stack, context, lines, advancedTooltips);
        if (ComponentPlacerItem.isFrequencyMode(stack)) {
            AEItems.MEMORY_CARD.asItem().appendHoverText(stack, context, lines, advancedTooltips);
        }
    }

    @Override
    public MenuType<?> getMenuType() {
        return ComponentPlacerMenu.TYPE;
    }

    @Override
    public ComponentPlacerMenuHost getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult
    ) {
        return new ComponentPlacerMenuHost(this, player, locator,
                (p, subMenu) -> this.openFromInventory(p, locator, true));
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, 3, (changedStack, upgrades) ->
                this.setAEMaxPowerMultiplier(changedStack, 1 + Upgrades.getEnergyCardMultiplier(upgrades))
        );
    }
}
