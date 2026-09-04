package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiInteractionParitySourceTest {
    @Test
    void endpointAndManagerControlsSendIndividualMenuActions() throws Exception {
        String input = read("src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java");
        String output = read("src/main/java/cn/ae2bc/client/PatternP2PTunnelOutputScreen.java");
        String energy = read("src/main/java/cn/ae2bc/client/PatternP2PTunnelEnergyScreen.java");
        String manager = read("src/main/java/cn/ae2bc/client/PatternP2PUnitManagerScreen.java");

        assertContainsAll(input,
                "menu.setReturnMode(ReturnMode.STRICT)",
                "menu.setReturnMode(ReturnMode.UNBLOCKED)",
                "menu.setBreakRecovery(breakRecovery.isSelected())",
                "menu.setRedstoneMode(mode)",
                "menu.setProductExtractionEnabled(true)",
                "menu.setProductExtractionEnabled(false)",
                "menu::resetTaskState");
        assertContainsAll(output,
                "menu.setReturnMode(mode)",
                "menu.setSyncInputSettings(syncInputSettings.isSelected())",
                "menu.setProductExtractionEnabled(true)",
                "menu.setProductExtractionEnabled(false)",
                "menu::resetTaskState");
        assertContainsAll(energy,
                "menu.setPullEnabled(false)",
                "menu.setPullEnabled(true)",
                "menu.setEnergyDistributionMode(menu.energyDistributionMode.next())");
        assertContainsAll(manager,
                "menu.setSyncMain(syncMain.isSelected())",
                "menu.setReturnMode(mode)",
                "menu.setBreakRecovery(breakRecovery.isSelected())",
                "menu.setRedstoneMode(mode)",
                "menu.setEnergyDistributionMode(menu.energyDistributionMode.next())",
                "menu::resetTaskState");

        assertFalse(input.contains("void onClose("));
        assertFalse(output.contains("void onClose("));
        assertFalse(energy.contains("void onClose("));
        assertFalse(manager.contains("void onClose("));
    }

    @Test
    void materialOutputButtonsAndServerHandlerEnforceTheSameSupportRule() throws Exception {
        String screen = read("src/main/java/cn/ae2bc/client/MaterialOutputConfigScreen.java");
        String menu = read("src/main/java/cn/ae2bc/mixin/PatternEncodingTermMenuMixin.java");

        assertTrue(screen.contains("button.active = input != null && form.supports(input.what());"));
        assertTrue(menu.contains("if (input == null || !form.supports(input.what()))"));
        assertTrue(menu.contains("menu.getMode() != EncodingMode.PROCESSING"));
        assertTrue(menu.contains("!InputDirectionData.isValidSlot(action[0])"));
        assertTrue(screen.contains("public void onClose() {\n        returnToParent();\n    }"));
        assertFalse(screen.contains("sendClientAction"));
    }

    @Test
    void componentPlacerButtonsMirrorTheirExecutionConditions() throws Exception {
        String screen = read("src/main/java/cn/ae2bc/client/ComponentPlacerScreen.java");
        String menu = read("src/main/java/cn/ae2bc/menu/ComponentPlacerMenu.java");

        assertContainsAll(screen,
                "() -> menu.setDirection(direction)",
                "() -> menu.adjustOffset(axis, -1)",
                "() -> menu.adjustOffset(axis, 1)",
                "menu::resetOffsets",
                "menu::clearSelection",
                "menu::execute",
                "clearSelection.active = menu.hasSelection",
                "menu.selectionState == ComponentPlacerSelection.Validation.VALID",
                "&& menu.hasCable && menu.hasPart");
        assertContainsAll(menu,
                "registerClientAction(SET_DIRECTION",
                "registerClientAction(ADJUST_X",
                "registerClientAction(RESET_OFFSETS",
                "registerClientAction(CLEAR_SELECTION",
                "registerClientAction(EXECUTE",
                "if (isServerSide() && getPlayer() instanceof ServerPlayer serverPlayer)");
    }

    private static void assertContainsAll(String source, String... needles) {
        for (String needle : needles) {
            assertTrue(source.contains(needle), "missing interaction wiring: " + needle);
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
