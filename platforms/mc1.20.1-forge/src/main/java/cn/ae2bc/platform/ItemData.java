package cn.ae2bc.platform;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.util.inv.AppEngInternalInventory;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Centralized item-NBT transport for APIs that predate Minecraft data components. */
public final class ItemData {
    public static final String MATERIALS = "Ae2bcMaterials";
    public static final String CABLE = "Ae2bcCable";
    public static final String PART = "Ae2bcPart";

    private static final String MATERIAL_OUTPUT = "Ae2bcMaterialOutput";
    private static final String UNIT_ID = "Ae2bcUnitId";
    private static final String SETTINGS = "Ae2bcPlacerSettings";
    private static final String SELECTION = "Ae2bcPlacerSelection";
    private static final String FREQUENCY = "Ae2bcPlacerFrequency";
    private static final String P2P_FREQUENCY = "p2pFreq";
    private static final String COMPATIBILITY_P2P_FREQUENCY = "freq";

    private ItemData() {
    }

    public static MaterialOutputConfigData getMaterialOutput(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? MaterialOutputConfigData.EMPTY
                : MaterialOutputConfigData.fromPacked(tag.getLongArray(MATERIAL_OUTPUT));
    }

    public static void setMaterialOutput(ItemStack stack, MaterialOutputConfigData config) {
        if (config == null || config.isEmpty()) {
            stack.removeTagKey(MATERIAL_OUTPUT);
        } else {
            stack.getOrCreateTag().putLongArray(MATERIAL_OUTPUT, config.toPacked());
        }
    }

    @Nullable
    public static UUID getUnitId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(UNIT_ID) ? tag.getUUID(UNIT_ID) : null;
    }

    public static void setUnitId(ItemStack stack, UUID unitId) {
        if (unitId == null) stack.removeTagKey(UNIT_ID);
        else stack.getOrCreateTag().putUUID(UNIT_ID, unitId);
    }

    public static ComponentPlacerSettings getSettings(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(SETTINGS, Tag.TAG_COMPOUND)) {
            return ComponentPlacerSettings.DEFAULT;
        }
        CompoundTag tag = root.getCompound(SETTINGS);
        Direction direction = Direction.from3DDataValue(tag.getInt("Direction"));
        return new ComponentPlacerSettings(direction, tag.getInt("OffsetX"),
                tag.getInt("OffsetY"), tag.getInt("OffsetZ"));
    }

    public static void setSettings(ItemStack stack, ComponentPlacerSettings settings) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Direction", settings.direction().get3DDataValue());
        tag.putInt("OffsetX", settings.offsetX());
        tag.putInt("OffsetY", settings.offsetY());
        tag.putInt("OffsetZ", settings.offsetZ());
        stack.getOrCreateTag().put(SETTINGS, tag);
    }

    @Nullable
    public static ComponentPlacerSelection getSelection(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(SELECTION, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(SELECTION);
        if (!tag.contains("Dimension", Tag.TAG_STRING) || !tag.contains("First", Tag.TAG_LONG)) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) return null;
        BlockPos first = BlockPos.of(tag.getLong("First"));
        BlockPos second = tag.contains("Second", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Second")) : null;
        return new ComponentPlacerSelection(dimension, first, second);
    }

    public static void setSelection(ItemStack stack, @Nullable ComponentPlacerSelection selection) {
        if (selection == null) {
            stack.removeTagKey(SELECTION);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", selection.dimension().toString());
        tag.putLong("First", selection.first().asLong());
        if (selection.second() != null) tag.putLong("Second", selection.second().asLong());
        stack.getOrCreateTag().put(SELECTION, tag);
    }

    public static short getFrequency(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getShort(FREQUENCY);
    }

    public static void setFrequency(ItemStack stack, short frequency) {
        if (frequency == 0) stack.removeTagKey(FREQUENCY);
        else stack.getOrCreateTag().putShort(FREQUENCY, frequency);
    }

    public static boolean hasP2PFrequency(CompoundTag tag) {
        return tag.contains(P2P_FREQUENCY, Tag.TAG_SHORT)
                || tag.contains(COMPATIBILITY_P2P_FREQUENCY, Tag.TAG_SHORT);
    }

    public static short getP2PFrequency(CompoundTag tag) {
        return tag.contains(P2P_FREQUENCY, Tag.TAG_SHORT)
                ? tag.getShort(P2P_FREQUENCY)
                : tag.getShort(COMPATIBILITY_P2P_FREQUENCY);
    }

    public static void setP2PFrequency(CompoundTag tag, short frequency) {
        tag.putShort(P2P_FREQUENCY, frequency);
        tag.remove(COMPATIBILITY_P2P_FREQUENCY);
    }

    public static void normalizeP2PFrequency(CompoundTag tag) {
        if (!tag.contains(P2P_FREQUENCY, Tag.TAG_SHORT)
                && tag.contains(COMPATIBILITY_P2P_FREQUENCY, Tag.TAG_SHORT)) {
            tag.putShort(P2P_FREQUENCY, tag.getShort(COMPATIBILITY_P2P_FREQUENCY));
        }
    }

    public static void loadInventory(ItemStack stack, AppEngInternalInventory inventory, String key) {
        CompoundTag tag = stack.getTag();
        if (tag != null) inventory.readFromNBT(tag, key);
    }

    public static void saveInventory(ItemStack stack, AppEngInternalInventory inventory, String key) {
        inventory.writeToNBT(stack.getOrCreateTag(), key);
    }

    public static ItemStack getMarkedItem(ItemStack stack, String key) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(key, Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        ItemStack marked = ItemStack.of(root.getCompound(key));
        return marked.isEmpty() ? marked : marked.copyWithCount(1);
    }

    public static void setMarkedItem(ItemStack stack, String key, ItemStack marked) {
        if (marked == null || marked.isEmpty()) {
            stack.removeTagKey(key);
        } else {
            stack.getOrCreateTag().put(key, marked.copyWithCount(1).save(new CompoundTag()));
        }
    }
}
