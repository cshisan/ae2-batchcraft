package cn.ae2bc.mixin;

import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.ConfigInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.api.inventories.InternalInventory;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputEncodingContext;
import cn.ae2bc.platform.ItemData;
import cn.ae2bc.extension.PatternEncodingLogicExtension;
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

    @Shadow
    @Final
    private ConfigInventory encodedInputInv;

    @Shadow
    @Final
    private AppEngInternalInventory encodedPatternInv;

    @Unique
    private MaterialOutputConfigData ae2bc$materialOutputConfig = MaterialOutputConfigData.EMPTY;
    @Unique
    private final AEKey[] ae2bc$inputKeys = new AEKey[AEProcessingPattern.MAX_INPUT_SLOTS];

    @Inject(method = "onChangeInventory", at = @At("HEAD"), remap = false)
    private void ae2bc$writeMaterialOutputConfigToEncodedPattern(InternalInventory inventory, int slot,
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
        ItemData.setMaterialOutput(pattern, config);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2bc$initializeInputSnapshot(CallbackInfo ci) {
        ae2bc$snapshotInputKeys();
    }

    @Inject(method = "onEncodedInputChanged", at = @At("TAIL"), remap = false)
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
    }

    @Inject(method = "loadEncodedPattern", at = @At("HEAD"), remap = false)
    private void ae2bc$resetBeforeLoadingPattern(ItemStack pattern, CallbackInfo ci) {
        if (!pattern.isEmpty()) {
            ae2bc$materialOutputConfig = MaterialOutputConfigData.EMPTY;
        }
    }

    @Inject(method = "loadProcessingPattern", at = @At("TAIL"), remap = false)
    private void ae2bc$loadMaterialOutputConfigFromPattern(AEProcessingPattern pattern, CallbackInfo ci) {
        ae2bc$materialOutputConfig = ItemData.getMaterialOutput(pattern.getDefinition().toStack());
        ae2bc$snapshotInputKeys();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void ae2bc$readMaterialOutputConfig(CompoundTag data, CallbackInfo ci) {
        ae2bc$materialOutputConfig = MaterialOutputConfigData.fromPacked(
                data.getLongArray(AE2BC_MATERIAL_OUTPUT_CONFIG_NBT));
        ae2bc$snapshotInputKeys();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
    private void ae2bc$writeMaterialOutputConfig(CompoundTag data, CallbackInfo ci) {
        if (ae2bc$materialOutputConfig.isEmpty()) {
            data.remove(AE2BC_MATERIAL_OUTPUT_CONFIG_NBT);
        } else {
            data.putLongArray(AE2BC_MATERIAL_OUTPUT_CONFIG_NBT, ae2bc$materialOutputConfig.toPacked());
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

    @Unique
    private void ae2bc$snapshotInputKeys() {
        Arrays.setAll(ae2bc$inputKeys, encodedInputInv::getKey);
    }
}
