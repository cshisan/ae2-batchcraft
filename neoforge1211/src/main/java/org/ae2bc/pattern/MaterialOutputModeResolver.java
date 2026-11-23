package org.ae2bc.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.registry.component.ComponentRegistry;

import java.util.List;

/**
 * 根据处理样板的编码槽位解析输入材料的输出方式。
 */
public final class MaterialOutputModeResolver {
    private MaterialOutputModeResolver() {
    }

    public static MaterialOutputMode resolve(IPatternDetails pattern, int slot) {
        return MaterialOutputModeResolver.attr(pattern).mode(slot);
    }

    public static MaterialOutputAttr attr(IPatternDetails pattern) {
        if (pattern == null) {
            return MaterialOutputAttr.EMPTY;
        }

        AEItemKey definition = pattern.getDefinition();
        if (definition == null) {
            return MaterialOutputAttr.EMPTY;
        }

        MaterialOutputAttr config = definition.getReadOnlyStack().get(
                ComponentRegistry.MATERIAL_OUTPUT_CONFIG
        );
        return config == null ? MaterialOutputAttr.EMPTY : config;
    }

    public static boolean hasFakeInputs(IPatternDetails pattern) {
        if (!(pattern instanceof Pp2mpPatternDetails details)
                || !(details.getDelegate() instanceof AEProcessingPattern processingPattern)) {
            return false;
        }
        MaterialOutputAttr config = MaterialOutputModeResolver.attr(pattern);
        List<GenericStack> inputs = processingPattern.getSparseInputs();
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (inputs.get(slot) != null && config.mode(slot) == MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
                return true;
            }
        }
        return false;
    }
}
