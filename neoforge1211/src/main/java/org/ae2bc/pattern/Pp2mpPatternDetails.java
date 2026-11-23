package org.ae2bc.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import lombok.Getter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;

public final class Pp2mpPatternDetails implements IPatternDetails {
    private final @Getter IPatternDetails delegate;
    private final AEItemKey definition;

    public Pp2mpPatternDetails(AEItemKey definition, Level level) {
        this.definition = Objects.requireNonNull(definition);
        ItemStack vanillaPattern = this.toVanillaPattern(definition);
        AEItemKey vanillaDefinition = AEItemKey.of(vanillaPattern);
        if (vanillaDefinition == null) {
            throw new IllegalArgumentException("Processing pattern must not be empty");
        }
        this.delegate = new AEProcessingPattern(vanillaDefinition);
    }

    @Override
    public AEItemKey getDefinition() {
        return this.definition;
    }

    @Override
    public IInput[] getInputs() {
        return this.delegate.getInputs();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return this.delegate.getOutputs();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        this.delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Pp2mpPatternDetails details && this.getDefinition().equals(details.getDefinition());
    }

    @Override
    public int hashCode() {
        return this.getDefinition().hashCode();
    }

    private ItemStack toVanillaPattern(AEItemKey definition) {
        if (definition.get(AEComponents.ENCODED_PROCESSING_PATTERN) != null) {
            ItemStack stack = AEItems.PROCESSING_PATTERN.stack();
            stack.set(
                    AEComponents.ENCODED_PROCESSING_PATTERN,
                    definition.get(AEComponents.ENCODED_PROCESSING_PATTERN)
            );
            return stack;
        } else {
            throw new IllegalArgumentException("Unsupported AE2 pattern component");
        }
    }
}
