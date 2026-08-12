package cn.ae2bc.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternProviderMenuCompatibilitySourceTest {
    @Test
    void upgradeSlotIsInjectedIntoTheSubclassConstructorBoundary() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/mixin/PatternProviderMenuMixin.java"));

        assertTrue(source.contains(
                "<init>(Lnet/minecraft/world/inventory/MenuType;I"));
        assertTrue(source.contains(
                "Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V"));
        assertFalse(source.contains("@Inject(method = \"<init>\""),
                "A bare constructor selector only covers AE2's public delegating constructor");
    }
}
