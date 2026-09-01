package cn.ae2bc.logic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.pattern.InputDirectionData;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.registry.ModContent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.Level;

public final class PatternDispatchMetadata {
    private static final PatternDispatchMetadata INVALID = new PatternDispatchMetadata(
            Map.of(), null, MaterialOutputConfigData.EMPTY, false, PatternBatchCount.DEFAULT, null);

    private final Map<AEKey, Long> declaredOutputs;
    private final GenericStack primaryOutput;
    private final MaterialOutputConfigData materialOutputConfig;
    private final boolean explicitDirections;
    private final long batchCount;
    private final AEProcessingPattern processingPattern;

    private PatternDispatchMetadata(Map<AEKey, Long> declaredOutputs, GenericStack primaryOutput,
                                    MaterialOutputConfigData materialOutputConfig, boolean explicitDirections,
                                    long batchCount, AEProcessingPattern processingPattern) {
        this.declaredOutputs = declaredOutputs;
        this.primaryOutput = primaryOutput;
        this.materialOutputConfig = materialOutputConfig;
        this.explicitDirections = explicitDirections;
        this.batchCount = batchCount;
        this.processingPattern = processingPattern;
    }

    static PatternDispatchMetadata create(IPatternDetails pattern) {
        return create(pattern, null);
    }

    static PatternDispatchMetadata create(IPatternDetails pattern, Level level) {
        try {
            var patternOutputs = pattern.getOutputs();
            Map<AEKey, Long> outputs = new LinkedHashMap<>(patternOutputs.size());
            for (GenericStack output : patternOutputs) {
                if (output == null || output.amount() <= 0) {
                    return INVALID;
                }
                outputs.merge(output.what(), output.amount(), Math::addExact);
            }
            GenericStack primary = pattern.getPrimaryOutput();
            if (primary == null || primary.amount() <= 0 || !outputs.containsKey(primary.what())) {
                return INVALID;
            }

            AEProcessingPattern processingPattern = decodeProcessingPattern(pattern, level);
            MaterialOutputConfigData config = getMaterialOutputConfig(pattern);
            long batchCount = getBatchCount(pattern, processingPattern);
            return new PatternDispatchMetadata(
                    Collections.unmodifiableMap(outputs), primary, config,
                    hasExplicitDirections(processingPattern, config.directions()), batchCount, processingPattern);
        } catch (ArithmeticException exception) {
            Ae2bcMod.LOGGER.warn("Pattern output amounts overflow while preparing dispatch metadata", exception);
            return INVALID;
        }
    }

    static long getBatchCount(IPatternDetails pattern, Level level) {
        AEProcessingPattern processingPattern = decodeProcessingPattern(pattern, level);
        return getBatchCount(pattern, processingPattern);
    }

    private static long getBatchCount(IPatternDetails pattern, AEProcessingPattern processingPattern) {
        if (processingPattern == null) {
            return PatternBatchCount.DEFAULT;
        }
        Long value = pattern.getDefinition().get(ModContent.PATTERN_BATCH_COUNT.get());
        long[] amounts = new long[processingPattern.getSparseInputs().size() + pattern.getOutputs().size()];
        int index = 0;
        for (GenericStack stack : processingPattern.getSparseInputs()) {
            if (stack != null && stack.amount() > 0) {
                amounts[index++] = stack.amount();
            }
        }
        for (GenericStack stack : pattern.getOutputs()) {
            if (stack != null && stack.amount() > 0) {
                amounts[index++] = stack.amount();
            }
        }
        long maximum = PatternBatchCount.maximum(java.util.Arrays.copyOf(amounts, index));
        return PatternBatchCount.validate(value == null ? PatternBatchCount.DEFAULT : value, maximum).getValue();
    }

    private static MaterialOutputConfigData getMaterialOutputConfig(IPatternDetails pattern) {
        MaterialOutputConfigData config = pattern.getDefinition().get(ModContent.MATERIAL_OUTPUT_CONFIG.get());
        return config == null ? MaterialOutputConfigData.EMPTY : config;
    }

    private static boolean hasExplicitDirections(AEProcessingPattern processingPattern,
                                                 InputDirectionData directions) {
        if (processingPattern == null) {
            return false;
        }
        var sparseInputs = processingPattern.getSparseInputs();
        for (int slot = 0; slot < sparseInputs.size() && InputDirectionData.isValidSlot(slot); slot++) {
            if (sparseInputs.get(slot) != null && directions.getDirection(slot) != null) {
                return true;
            }
        }
        return false;
    }

    static AEProcessingPattern decodeProcessingPattern(IPatternDetails pattern, Level level) {
        if (pattern instanceof AEProcessingPattern processingPattern) {
            return processingPattern;
        }
        IPatternDetails decoded = PatternDetailsHelper.decodePattern(pattern.getDefinition(), level);
        return decoded instanceof AEProcessingPattern processingPattern ? processingPattern : null;
    }

    public boolean isValid() {
        return primaryOutput != null;
    }

    public Map<AEKey, Long> declaredOutputs() {
        return declaredOutputs;
    }

    public GenericStack primaryOutput() {
        if (primaryOutput == null) {
            throw new IllegalStateException("Invalid pattern metadata has no primary output");
        }
        return primaryOutput;
    }

    public InputDirectionData outputDirections() {
        return materialOutputConfig.directions();
    }

    public MaterialOutputConfigData materialOutputConfig() {
        return materialOutputConfig;
    }

    public boolean hasExplicitDirections() {
        return explicitDirections;
    }

    public long batchCount() {
        return batchCount;
    }

    public PatternDispatchMetadata forAtomicUnits(long units) {
        if (!isValid() || units <= 0) {
            return INVALID;
        }
        try {
            Map<AEKey, Long> scaled = new LinkedHashMap<>(declaredOutputs.size());
            for (var entry : declaredOutputs.entrySet()) {
                if (entry.getValue() % batchCount != 0) {
                    return INVALID;
                }
                scaled.put(entry.getKey(), Math.multiplyExact(entry.getValue() / batchCount, units));
            }
            if (primaryOutput.amount() % batchCount != 0) {
                return INVALID;
            }
            var primary = new GenericStack(primaryOutput.what(),
                    Math.multiplyExact(primaryOutput.amount() / batchCount, units));
            return new PatternDispatchMetadata(Collections.unmodifiableMap(scaled), primary,
                    materialOutputConfig, explicitDirections, batchCount, processingPattern);
        } catch (ArithmeticException exception) {
            return INVALID;
        }
    }

    AEProcessingPattern processingPattern() {
        return processingPattern;
    }
}
