package cn.ae2bc.mixin;

import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.ConfigInventory;
import appeng.util.inv.AppEngInternalInventory;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputEncodingContext;
import cn.ae2bc.logic.PatternBatchCount;
import cn.ae2bc.registry.ModContent;
import cn.ae2bc.extension.PatternEncodingLogicExtension;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Objects;

@Mixin(PatternEncodingLogic.class)
public abstract class PatternEncodingLogicMixin implements PatternEncodingLogicExtension {
    @Unique
    private static final String AE2BC_MATERIAL_OUTPUT_CONFIG_NBT = "MaterialOutputConfig";
    @Unique
    private static final String AE2BC_PATTERN_BATCH_COUNT_NBT = "PatternBatchCount";

    @Shadow
    @Final
    private ConfigInventory encodedInputInv;

    @Shadow
    @Final
    private ConfigInventory encodedOutputInv;

    @Shadow
    @Final
    private AppEngInternalInventory encodedPatternInv;

    @Unique
    private MaterialOutputConfigData ae2bc$materialOutputConfig = MaterialOutputConfigData.EMPTY;
    @Unique
    private long ae2bc$patternBatchCount = PatternBatchCount.DEFAULT;
    @Unique
    private final AEKey[] ae2bc$inputKeys = new AEKey[AEProcessingPattern.MAX_INPUT_SLOTS];

    @Inject(method = "onChangeInventory", at = @At("HEAD"))
    private void ae2bc$writeMaterialOutputConfigToEncodedPattern(AppEngInternalInventory inventory, int slot,
                                                                   CallbackInfo ci) {
        if (inventory != encodedPatternInv || slot != 0) {
            return;
        }
        if (!MaterialOutputEncodingContext.isActiveFor(this)) {
            return;
        }
        MaterialOutputConfigData config = ae2bc$materialOutputConfig;
        ItemStack pattern = inventory.getStackInSlot(slot);
        if (pattern.isEmpty()) {
            return;
        }
        if (config.isEmpty()) {
            pattern.remove(ModContent.MATERIAL_OUTPUT_CONFIG.get());
        } else {
            pattern.set(ModContent.MATERIAL_OUTPUT_CONFIG.get(), config);
        }
        long batchCount = ae2bc$validatedPatternBatchCount(ae2bc$patternBatchCount);
        if (batchCount == PatternBatchCount.DEFAULT) {
            pattern.remove(ModContent.PATTERN_BATCH_COUNT.get());
        } else {
            pattern.set(ModContent.PATTERN_BATCH_COUNT.get(), batchCount);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2bc$initializeInputSnapshot(CallbackInfo ci) {
        ae2bc$snapshotInputKeys();
    }

    @Inject(method = "onEncodedInputChanged", at = @At("TAIL"))
    private void ae2bc$clearOutputConfigForReplacedInputs(CallbackInfo ci) {
        MaterialOutputConfigData updated = ae2bc$materialOutputConfig;
        for (int slot = 0; slot < ae2bc$inputKeys.length; slot++) {
            AEKey current = encodedInputInv.getKey(slot);
            if (!Objects.equals(ae2bc$inputKeys[slot], current)) {
                updated = updated.clearSlot(slot);
                ae2bc$inputKeys[slot] = current;
            }
        }
        ae2bc$materialOutputConfig = updated;
        ae2bc$patternBatchCount = ae2bc$validatedPatternBatchCount(ae2bc$patternBatchCount);
    }

    @Inject(method = "onEncodedOutputChanged", at = @At("TAIL"))
    private void ae2bc$validateBatchCountForOutputs(CallbackInfo ci) {
        ae2bc$patternBatchCount = ae2bc$validatedPatternBatchCount(ae2bc$patternBatchCount);
    }

    @Inject(method = "loadEncodedPattern", at = @At("HEAD"))
    private void ae2bc$resetBeforeLoadingPattern(ItemStack pattern, CallbackInfo ci) {
        if (!pattern.isEmpty()) {
            ae2bc$materialOutputConfig = MaterialOutputConfigData.EMPTY;
            ae2bc$patternBatchCount = PatternBatchCount.DEFAULT;
        }
    }

    @Inject(method = "loadProcessingPattern", at = @At("TAIL"))
    private void ae2bc$loadMaterialOutputConfigFromPattern(AEProcessingPattern pattern, CallbackInfo ci) {
        ae2bc$materialOutputConfig = Objects.requireNonNullElse(
                pattern.getDefinition().get(ModContent.MATERIAL_OUTPUT_CONFIG.get()),
                MaterialOutputConfigData.EMPTY);
        ae2bc$patternBatchCount = ae2bc$validatedPatternBatchCount(Objects.requireNonNullElse(
                pattern.getDefinition().get(ModContent.PATTERN_BATCH_COUNT.get()), PatternBatchCount.DEFAULT));
        ae2bc$snapshotInputKeys();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2bc$readMaterialOutputConfig(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        ae2bc$materialOutputConfig = MaterialOutputConfigData.fromPacked(
                data.getLongArray(AE2BC_MATERIAL_OUTPUT_CONFIG_NBT));
        ae2bc$patternBatchCount = ae2bc$validatedPatternBatchCount(
                data.contains(AE2BC_PATTERN_BATCH_COUNT_NBT)
                        ? data.getLong(AE2BC_PATTERN_BATCH_COUNT_NBT) : PatternBatchCount.DEFAULT);
        ae2bc$snapshotInputKeys();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2bc$writeMaterialOutputConfig(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        if (ae2bc$materialOutputConfig.isEmpty()) {
            data.remove(AE2BC_MATERIAL_OUTPUT_CONFIG_NBT);
        } else {
            data.putLongArray(AE2BC_MATERIAL_OUTPUT_CONFIG_NBT, ae2bc$materialOutputConfig.toPacked());
        }
        if (ae2bc$patternBatchCount == PatternBatchCount.DEFAULT) {
            data.remove(AE2BC_PATTERN_BATCH_COUNT_NBT);
        } else {
            data.putLong(AE2BC_PATTERN_BATCH_COUNT_NBT, ae2bc$patternBatchCount);
        }
    }

    @Override
    public MaterialOutputConfigData ae2bc$getMaterialOutputConfig() {
        return ae2bc$materialOutputConfig;
    }

    @Override
    public void ae2bc$setMaterialOutputConfig(MaterialOutputConfigData config) {
        ae2bc$materialOutputConfig = Objects.requireNonNullElse(config, MaterialOutputConfigData.EMPTY);
        ((PatternEncodingLogic) (Object) this).saveChanges();
    }

    @Override
    public long ae2bc$getPatternBatchCount() {
        return ae2bc$patternBatchCount;
    }

    @Override
    public void ae2bc$setPatternBatchCount(long batchCount) {
        ae2bc$patternBatchCount = ae2bc$validatedPatternBatchCount(batchCount);
        ((PatternEncodingLogic) (Object) this).saveChanges();
    }

    @Unique
    private long ae2bc$validatedPatternBatchCount(long requested) {
        long[] amounts = new long[encodedInputInv.size() + encodedOutputInv.size()];
        int index = 0;
        for (int slot = 0; slot < encodedInputInv.size(); slot++) {
            var stack = encodedInputInv.getStack(slot);
            if (stack != null && stack.amount() > 0) {
                amounts[index++] = stack.amount();
            }
        }
        for (int slot = 0; slot < encodedOutputInv.size(); slot++) {
            var stack = encodedOutputInv.getStack(slot);
            if (stack != null && stack.amount() > 0) {
                amounts[index++] = stack.amount();
            }
        }
        return PatternBatchCount.validate(requested,
                PatternBatchCount.maximum(Arrays.copyOf(amounts, index))).getValue();
    }

    @Unique
    private void ae2bc$snapshotInputKeys() {
        Arrays.setAll(ae2bc$inputKeys, encodedInputInv::getKey);
    }
}
