package cn.ae2bc.pattern;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternEncodingCompatibilitySourceTest {
    @Test
    void outputConfigIsWrittenAtTheSharedEncodedPatternBoundary() throws Exception {
        String encodeMixin = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/mixin/PatternEncodingTermMenuEncodeMixin.java"));
        String menuMixin = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/mixin/PatternEncodingTermMenuMixin.java"));
        String logicMixin = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/mixin/PatternEncodingLogicMixin.java"));

        assertTrue(encodeMixin.contains("priority = 500"));
        assertTrue(encodeMixin.contains("@WrapMethod(method = \"encode\")"));
        assertTrue(encodeMixin.contains("MaterialOutputEncodingContext.enter(encodingLogic)"));
        assertFalse(menuMixin.contains("@Inject(method = \"encodeProcessingPattern\""));

        assertTrue(logicMixin.contains("@Inject(method = \"onChangeInventory\", at = @At(\"HEAD\"))"));
        assertTrue(logicMixin.contains("inventory != encodedPatternInv"));
        assertTrue(logicMixin.contains("MaterialOutputEncodingContext.isActiveFor(this)"));
        assertTrue(logicMixin.contains("pattern.set(ModContent.MATERIAL_OUTPUT_CONFIG.get(), config)"));
    }
}
