package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmediateReturnIntegrationSourceTest {
    @Test
    void p2pReturnsUseTheProviderOwnedReturnPath() throws Exception {
        String remote = read("src/main/java/cn/ae2bc/logic/RemoteReturnInventory.java");
        String provider = read("src/main/java/cn/ae2bc/mixin/PatternProviderLogicMixin.java");
        String returnInventory = read(
                "src/main/java/cn/ae2bc/mixin/PatternProviderReturnInventoryMixin.java");

        assertTrue(remote.contains("target instanceof ImmediatePatternProviderReturnInventory"));
        assertTrue(remote.contains("requestImmediateFlushIfNeeded(target, mode)"));
        assertTrue(remote.contains("mode != Actionable.SIMULATE || inserted > 0"));
        assertTrue(remote.contains("ae2bc$registerReturnProgressListener(returnProgressListener)"));
        assertFalse(remote.contains("hasStoredStacks"));
        assertTrue(provider.contains("returnInv.injectIntoNetwork"));
        assertTrue(provider.contains("accessor::ae2bc$onStackReturnedToNetwork"));
        assertTrue(returnInventory.contains("cir.getReturnValueZ()"));
        assertTrue(returnInventory.contains("listener.run()"));
        assertTrue(returnInventory.contains("ae2bc$returnProgressListeners.register"));
        assertTrue(returnInventory.contains("PatternProviderReturnInventory) (Object) this).isEmpty()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
