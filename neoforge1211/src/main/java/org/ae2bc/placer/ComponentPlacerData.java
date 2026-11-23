package org.ae2bc.placer;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.ae2bc.core.placer.DataModel;
import org.ae2bc.core.placer.Pos;
import org.ae2bc.core.placer.SelectionStatus;
import org.ae2bc.registry.component.ComponentPlacerRegistry;
import org.ae2bc.registry.component.Pp2mpUnitManagerRegistry;

public final class ComponentPlacerData {
    public static final int MATERIAL_SLOT_COUNT = 9;

    public static ComponentPlacerSelection selection(ItemStack stack) {
        return stack.get(ComponentPlacerRegistry.SELECTION);
    }

    public static void selection(ItemStack stack, ComponentPlacerSelection selection) {
        if (selection == null) {
            stack.remove(ComponentPlacerRegistry.SELECTION);
        } else {
            stack.set(ComponentPlacerRegistry.SELECTION, selection);
        }
    }

    public static ComponentPlacerPose pose(ItemStack stack) {
        return stack.getOrDefault(ComponentPlacerRegistry.POSE, ComponentPlacerPose.DEFAULT);
    }

    public static void pose(ItemStack stack, ComponentPlacerPose settings) {
        stack.set(ComponentPlacerRegistry.POSE, settings == null ? ComponentPlacerPose.DEFAULT : settings);
    }

    public static short frequency(ItemStack stack) {
        return stack.getOrDefault(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 0);
    }

    /* 清除保存的频率及其颜色缓存，保留单元 ID 和其他内存卡配置。 */
    public static void clearFrequency(ItemStack stack) {
        stack.remove(AEComponents.EXPORTED_P2P_FREQUENCY);
        stack.remove(AEComponents.MEMORY_CARD_COLORS);
        stack.remove(ComponentPlacerRegistry.LEGACY_FREQUENCY);
    }

    public static short unitId(ItemStack stack) {
        return stack.getOrDefault(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, (short) 0);
    }

    /* 只清除保存的单元 ID。 */
    public static void clearUnitId(ItemStack stack) {
        stack.remove(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID);
        stack.remove(ComponentPlacerRegistry.LEGACY_UNIT_ID);
    }

    public static boolean frequencyMode(ItemStack stack) {
        return stack.getOrDefault(ComponentPlacerRegistry.FREQUENCY_MODE, false);
    }

    public static void frequencyMode(ItemStack stack, boolean frequencyMode) {
        stack.set(ComponentPlacerRegistry.FREQUENCY_MODE, frequencyMode);
    }

    public static DataModel createDataModel(ItemStack stack, boolean aeConnected) {
        ComponentPlacerPose settings = ComponentPlacerData.pose(stack);
        ComponentPlacerSelection selection = ComponentPlacerData.selection(stack);
        DataModel.Builder builder = DataModel.builder();
        builder.frequencyMode(ComponentPlacerData.frequencyMode(stack));
        builder.frequency(ComponentPlacerData.frequency(stack));
        builder.unitId(ComponentPlacerData.unitId(stack));
        builder.pos1(settings.pos());
        builder.hasCable(ComponentPlacerItem.isUsableCable(ComponentPlacerData.markedCable(stack)));
        builder.hasPart(ComponentPlacerItem.isUsablePart(ComponentPlacerData.markedPart(stack)));
        builder.aeConnected(aeConnected);
        if (selection == null) {
            builder.pos2(new Pos(0, 0, 0));
            builder.selection(SelectionStatus.INCOMPLETE);
            builder.hasSelection(false);
        } else {
            builder.pos2(new Pos(selection.sizeX(), selection.sizeY(), selection.sizeZ()));
            builder.selection(selection.validate());
            builder.hasSelection(true);
        }
        return builder.build();
    }

    public static ItemStack markedCable(ItemStack stack) {
        return ComponentPlacerData.markedItem(stack, ComponentPlacerRegistry.CABLE, ComponentPlacerRegistry.LEGACY_CABLE);
    }

    public static ItemStack markedPart(ItemStack stack) {
        return ComponentPlacerData.markedItem(stack, ComponentPlacerRegistry.PART, ComponentPlacerRegistry.LEGACY_PART);
    }

    private static ItemStack markedItem(
            ItemStack stack, DataComponentType<GenericStack> component,
            DataComponentType<ItemContainerContents> legacyComponent
    ) {
        GenericStack marker = stack.get(component);
        if (marker != null && marker.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack().copyWithCount(1);
        }

        ItemStack legacy = stack.getOrDefault(legacyComponent, ItemContainerContents.EMPTY).copyOne();
        return legacy.isEmpty() ? legacy : legacy.copyWithCount(1);
    }
}
