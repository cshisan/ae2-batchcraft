package cn.ae2bc.mixin;

import appeng.menu.guisync.GuiSync;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.api.stacks.GenericStack;
import cn.ae2bc.pattern.InputDirectionData;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import cn.ae2bc.extension.PatternEncodingLogicExtension;
import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.logic.PatternBatchCount;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternEncodingTermMenu.class)
public abstract class PatternEncodingTermMenuMixin implements PatternEncodingTermMenuExtension {
    @Unique
    private static final String AE2BC_SET_MATERIAL_OUTPUT_CONFIG = "ae2bcSetMaterialOutputConfig";
    @Unique
    private static final String AE2BC_SET_PATTERN_BATCH_COUNT = "ae2bcSetPatternBatchCount";

    @Shadow
    @Final
    private PatternEncodingLogic encodingLogic;

    @Unique
    @GuiSync(80)
    private long ae2bc$outputDirections0;
    @Unique
    @GuiSync(81)
    private long ae2bc$outputDirections1;
    @Unique
    @GuiSync(82)
    private long ae2bc$outputDirections2;
    @Unique
    @GuiSync(83)
    private long ae2bc$outputDirections3;
    @Unique
    @GuiSync(84)
    private long ae2bc$outputForms0;
    @Unique
    @GuiSync(85)
    private long ae2bc$outputForms1;
    @Unique
    @GuiSync(86)
    private long ae2bc$outputForms2;
    @Unique
    @GuiSync(87)
    private long ae2bc$patternBatchCount = PatternBatchCount.DEFAULT;
    @Unique
    @GuiSync(88)
    private long ae2bc$maximumPatternBatchCount = PatternBatchCount.DEFAULT;
    @Unique
    private MaterialOutputConfigData ae2bc$cachedMaterialOutputConfig = MaterialOutputConfigData.EMPTY;
    @Unique
    private long ae2bc$cachedOutputDirections0;
    @Unique
    private long ae2bc$cachedOutputDirections1;
    @Unique
    private long ae2bc$cachedOutputDirections2;
    @Unique
    private long ae2bc$cachedOutputDirections3;
    @Unique
    private long ae2bc$cachedOutputForms0;
    @Unique
    private long ae2bc$cachedOutputForms1;
    @Unique
    private long ae2bc$cachedOutputForms2;

    @Inject(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V",
            at = @At("RETURN"))
    private void ae2bc$registerDirectionAction(CallbackInfo ci) {
        ((AEBaseMenuInvoker) this).ae2bc$registerClientAction(
                AE2BC_SET_MATERIAL_OUTPUT_CONFIG, int[].class, this::ae2bc$handleSetMaterialOutputConfig);
        ((AEBaseMenuInvoker) this).ae2bc$registerClientAction(
                AE2BC_SET_PATTERN_BATCH_COUNT, Long.class, this::ae2bc$handleSetPatternBatchCount);
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void ae2bc$syncMaterialOutputConfig(CallbackInfo ci) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (!menu.isClientSide()) {
            ae2bc$setSyncedConfig(((PatternEncodingLogicExtension) encodingLogic).ae2bc$getMaterialOutputConfig());
            ae2bc$patternBatchCount = ((PatternEncodingLogicExtension) encodingLogic).ae2bc$getPatternBatchCount();
            ae2bc$maximumPatternBatchCount = ae2bc$calculateMaximumPatternBatchCount(menu);
        }
    }

    @Override
    public MaterialOutputConfigData ae2bc$getMaterialOutputConfig() {
        if (ae2bc$cachedOutputDirections0 != ae2bc$outputDirections0
                || ae2bc$cachedOutputDirections1 != ae2bc$outputDirections1
                || ae2bc$cachedOutputDirections2 != ae2bc$outputDirections2
                || ae2bc$cachedOutputDirections3 != ae2bc$outputDirections3
                || ae2bc$cachedOutputForms0 != ae2bc$outputForms0
                || ae2bc$cachedOutputForms1 != ae2bc$outputForms1
                || ae2bc$cachedOutputForms2 != ae2bc$outputForms2) {
            ae2bc$cachedMaterialOutputConfig = MaterialOutputConfigData.fromPacked(new long[]{
                    ae2bc$outputDirections0, ae2bc$outputDirections1,
                    ae2bc$outputDirections2, ae2bc$outputDirections3,
                    ae2bc$outputForms0, ae2bc$outputForms1, ae2bc$outputForms2
            });
            ae2bc$cacheSyncedWords();
        }
        return ae2bc$cachedMaterialOutputConfig;
    }

    @Override
    public void ae2bc$setInputDirection(int slot, Direction direction) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (!InputDirectionData.isValidSlot(slot) || menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        MaterialOutputConfigData updated = ae2bc$getMaterialOutputConfig().withDirection(slot, direction);
        ae2bc$setSyncedConfig(updated);
        ((AEBaseMenuInvoker) this).ae2bc$sendClientAction(
                AE2BC_SET_MATERIAL_OUTPUT_CONFIG,
                new int[]{slot, direction == null ? 0 : direction.ordinal() + 1,
                        updated.getOutputForm(slot).getId()});
    }

    @Override
    public void ae2bc$setMaterialOutputForm(int slot, MaterialOutputForm form) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (!InputDirectionData.isValidSlot(slot) || menu.getMode() != EncodingMode.PROCESSING || form == null) {
            return;
        }
        Slot[] inputSlots = menu.getProcessingInputSlots();
        GenericStack input = slot < inputSlots.length
                ? GenericStack.fromItemStack(inputSlots[slot].getItem()) : null;
        if (input == null || !form.supports(input.what())) {
            return;
        }
        MaterialOutputConfigData updated = ae2bc$getMaterialOutputConfig().withOutputForm(slot, form);
        ae2bc$setSyncedConfig(updated);
        Direction direction = updated.getDirection(slot);
        ((AEBaseMenuInvoker) this).ae2bc$sendClientAction(
                AE2BC_SET_MATERIAL_OUTPUT_CONFIG,
                new int[]{slot, direction == null ? 0 : direction.ordinal() + 1, form.getId()});
    }

    @Override
    public long ae2bc$getPatternBatchCount() {
        return ae2bc$patternBatchCount;
    }

    @Override
    public long ae2bc$getMaximumPatternBatchCount() {
        return Math.max(PatternBatchCount.DEFAULT, ae2bc$maximumPatternBatchCount);
    }

    @Override
    public void ae2bc$setPatternBatchCount(long batchCount) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        long corrected = PatternBatchCount.validate(
                batchCount, ae2bc$getMaximumPatternBatchCount()).getValue();
        ae2bc$patternBatchCount = corrected;
        ((AEBaseMenuInvoker) this).ae2bc$sendClientAction(AE2BC_SET_PATTERN_BATCH_COUNT, corrected);
    }

    @Unique
    private void ae2bc$handleSetMaterialOutputConfig(int[] action) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        Slot[] inputSlots = menu.getProcessingInputSlots();
        if (menu.isClientSide() || action == null || action.length != 3
                || menu.getMode() != EncodingMode.PROCESSING
                || !InputDirectionData.isValidSlot(action[0])
                || action[0] >= inputSlots.length
                || !inputSlots[action[0]].hasItem()) {
            return;
        }
        MaterialOutputForm form = MaterialOutputForm.fromId(action[2]);
        GenericStack input = GenericStack.fromItemStack(inputSlots[action[0]].getItem());
        if (input == null || !form.supports(input.what())) {
            return;
        }
        MaterialOutputConfigData current =
                ((PatternEncodingLogicExtension) encodingLogic).ae2bc$getMaterialOutputConfig();
        Direction direction = action[1] >= 1 && action[1] <= Direction.values().length
                ? Direction.values()[action[1] - 1] : null;
        ((PatternEncodingLogicExtension) encodingLogic).ae2bc$setMaterialOutputConfig(
                current.withDirection(action[0], direction).withOutputForm(action[0], form));
    }

    @Unique
    private void ae2bc$handleSetPatternBatchCount(Long batchCount) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (menu.isClientSide() || menu.getMode() != EncodingMode.PROCESSING || batchCount == null) {
            return;
        }
        ((PatternEncodingLogicExtension) encodingLogic).ae2bc$setPatternBatchCount(batchCount);
    }

    @Unique
    private static long ae2bc$calculateMaximumPatternBatchCount(PatternEncodingTermMenu menu) {
        Slot[] inputs = menu.getProcessingInputSlots();
        Slot[] outputs = menu.getProcessingOutputSlots();
        long[] amounts = new long[inputs.length + outputs.length];
        int index = 0;
        for (Slot slot : inputs) {
            GenericStack stack = GenericStack.fromItemStack(slot.getItem());
            if (stack != null && stack.amount() > 0) {
                amounts[index++] = stack.amount();
            }
        }
        for (Slot slot : outputs) {
            GenericStack stack = GenericStack.fromItemStack(slot.getItem());
            if (stack != null && stack.amount() > 0) {
                amounts[index++] = stack.amount();
            }
        }
        return PatternBatchCount.maximum(java.util.Arrays.copyOf(amounts, index));
    }

    @Unique
    private void ae2bc$setSyncedConfig(MaterialOutputConfigData config) {
        long[] words = config.toPacked();
        ae2bc$outputDirections0 = words[0];
        ae2bc$outputDirections1 = words[1];
        ae2bc$outputDirections2 = words[2];
        ae2bc$outputDirections3 = words[3];
        ae2bc$outputForms0 = words[4];
        ae2bc$outputForms1 = words[5];
        ae2bc$outputForms2 = words[6];
        ae2bc$cachedMaterialOutputConfig = config;
        ae2bc$cacheSyncedWords();
    }

    @Unique
    private void ae2bc$cacheSyncedWords() {
        ae2bc$cachedOutputDirections0 = ae2bc$outputDirections0;
        ae2bc$cachedOutputDirections1 = ae2bc$outputDirections1;
        ae2bc$cachedOutputDirections2 = ae2bc$outputDirections2;
        ae2bc$cachedOutputDirections3 = ae2bc$outputDirections3;
        ae2bc$cachedOutputForms0 = ae2bc$outputForms0;
        ae2bc$cachedOutputForms1 = ae2bc$outputForms1;
        ae2bc$cachedOutputForms2 = ae2bc$outputForms2;
    }
}
