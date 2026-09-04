package cn.ae2bc.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternBatchConfigScreenResourceTest {
    private static final Set<String> TEXT_PALETTE = Set.of(
            "DEFAULT_TEXT_COLOR", "MUTED_TEXT_COLOR", "SELECTION_COLOR", "ERROR");

    @Test
    void screenStyleAndTranslationsArePackaged() throws java.io.IOException {
        JsonObject style = assertJson("/assets/ae2/screens/ae2_batchcraft/pattern_batch_config.json");
        assertTextColorsUseAe2Palette(style);
        assertTrue(style.getAsJsonObject("widgets").has("confirm"));
        assertEquals(96, style.getAsJsonObject("generatedBackground").get("height").getAsInt());
        assertTrue(style.getAsJsonObject("widgets").getAsJsonObject("confirm")
                .get("width").getAsInt() >= 52);
        assertFalse(style.getAsJsonObject("text").has("maximum"));
        assertFalse(style.getAsJsonObject("text").has("correction"));
        assertFalse(style.getAsJsonObject("text").has("atomic_preview"));

        JsonObject zhCn = assertJson("/assets/ae2_batchcraft/lang/zh_cn.json");
        JsonObject enUs = assertJson("/assets/ae2_batchcraft/lang/en_us.json");
        assertBatchTranslations(zhCn, "Ctrl+中键：批次配置", "确定",
                "系统只检查样板数量能否整除", "请确保每批材料满足机器的最小配方");
        assertBatchTranslations(enUs, "Ctrl+Middle Click: Batch Configuration", "Confirm",
                "only checks whether the pattern quantities are divisible",
                "Ensure each batch satisfies the machine's minimum recipe");

        String batchScreen = source("src/main/java/cn/ae2bc/client/PatternBatchConfigScreen.java");
        String inputScreen = source("src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java");
        String inputLogic = source("src/main/java/cn/ae2bc/logic/PatternP2PTunnelInputLogic.java");
        assertTrue(batchScreen.contains("widgets.add(\"confirm\""));
        assertTrue(batchScreen.contains("font.split(text, TEXT_WIDTH)"));
        assertTrue(batchScreen.contains("PaletteColor.ERROR"));
        assertTrue(batchScreen.contains("setResponder(value -> correctionVisible = false)"));
        assertTrue(batchScreen.contains("Component status = correctionVisible ? correction : maximum"));
        assertFalse(batchScreen.contains("drawWrapped(graphics, correction, 88"));
        assertFalse(batchScreen.contains("atomicPreview("));
        assertFalse(batchScreen.contains("public void mouseClicked("));
        assertFalse(onCloseBody(batchScreen).contains("commitValue()"));
        assertTrue(inputScreen.contains("addToRightToolbar(\"dispatchModeToolbar\", dispatchModeToolbar)"));
        assertTrue(inputScreen.contains("addToRightToolbar(\"taskAllocationModeToolbar\", taskAllocationModeToolbar)"));
        assertTrue(inputScreen.contains("appeng.client.gui.Icon.S_PROCESSOR"));
        assertTrue(inputScreen.contains("Icon.S_PROCESSOR, 0.6f"));
        assertFalse(inputScreen.contains("dispatchFull\""));
        assertFalse(inputScreen.contains("dispatchBatch\""));
        assertTrue(inputScreen.contains("PatternDispatchMode.BATCH_DISTRIBUTION"));
        assertTrue(inputLogic.contains("data.putByte(DISPATCH_MODE"));
        assertTrue(inputLogic.contains("TaskAllocationPlanner.distribute"));
        assertTaskAllocationTranslations(zhCn);
        assertTaskAllocationTranslations(enUs);
        assertEquals("轮询可用端点", zhCn.get(
                "gui.ae2_batchcraft.task_allocation_mode.round_robin.tooltip").getAsString());
        assertEquals("随机选择可用端点", zhCn.get(
                "gui.ae2_batchcraft.task_allocation_mode.random.tooltip").getAsString());
        assertEquals("优先选择排序靠前的端点", zhCn.get(
                "gui.ae2_batchcraft.task_allocation_mode.priority.tooltip").getAsString());
    }

    private static JsonObject assertJson(String path) {
        var stream = PatternBatchConfigScreenResourceTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void assertTextColorsUseAe2Palette(JsonObject style) {
        JsonObject text = style.getAsJsonObject("text");
        for (var entry : text.entrySet()) {
            JsonObject definition = entry.getValue().getAsJsonObject();
            if (definition.has("color")) {
                String color = definition.get("color").getAsString();
                assertTrue(TEXT_PALETTE.contains(color), entry.getKey() + " uses invalid AE2 text color " + color);
            }
        }
    }

    private static void assertBatchTranslations(JsonObject language, String shortcut, String confirm,
                                                String limitation, String recommendation) {
        assertTrue(language.get("gui.ae2_batchcraft.pattern_batch_config").getAsString().equals(shortcut));
        assertTrue(language.get("gui.ae2_batchcraft.pattern_batch_count.confirm").getAsString().equals(confirm));
        String tooltip = language.get("gui.ae2_batchcraft.pattern_batch_count.tooltip").getAsString();
        assertTrue(tooltip.contains("\n\n"));
        assertTrue(tooltip.contains(limitation));
        assertTrue(tooltip.contains("2A + 2B → 2C"));
        assertTrue(tooltip.contains("1A + 1B → 1C"));
        assertTrue(tooltip.contains(recommendation));
        assertFalse(language.has("gui.ae2_batchcraft.pattern_batch_count.atomic"));
    }

    private static void assertTaskAllocationTranslations(JsonObject language) {
        assertTrue(language.has("gui.ae2_batchcraft.task_allocation_mode"));
        assertTrue(language.has("gui.ae2_batchcraft.task_allocation_mode.round_robin"));
        assertTrue(language.has("gui.ae2_batchcraft.task_allocation_mode.random"));
        assertTrue(language.has("gui.ae2_batchcraft.task_allocation_mode.priority"));
        assertTrue(language.has("gui.ae2_batchcraft.task_allocation_mode.switch.tooltip"));
    }

    private static String onCloseBody(String source) {
        int start = source.indexOf("public void onClose()");
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    private static String source(String path) throws java.io.IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of(path));
    }
}
