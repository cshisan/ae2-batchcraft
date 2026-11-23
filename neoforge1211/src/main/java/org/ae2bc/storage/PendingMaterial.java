package org.ae2bc.storage;

import appeng.api.stacks.GenericStack;
import org.ae2bc.core.pattern.MaterialOutputMode;

public record PendingMaterial(GenericStack stack, MaterialOutputMode mode, int slot) {
}
