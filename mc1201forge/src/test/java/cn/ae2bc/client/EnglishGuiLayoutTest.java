package cn.ae2bc.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnglishGuiLayoutTest {
    private static final Map<String, String> ENGLISH = loadEnglish();

    @Test
    void fixedWidthButtonLabelsFit() {
        assertFits("gui.ae2_batchcraft.energy.mode.passive", 81);
        assertFits("gui.ae2_batchcraft.energy.mode.active", 81);
        assertFits("gui.ae2_batchcraft.direction.auto", 46);
        assertFits("gui.ae2_batchcraft.direction.down", 46);
        assertFits("gui.ae2_batchcraft.direction.up", 46);
        assertFits("gui.ae2_batchcraft.direction.north", 46);
        assertFits("gui.ae2_batchcraft.direction.south", 46);
        assertFits("gui.ae2_batchcraft.direction.west", 46);
        assertFits("gui.ae2_batchcraft.direction.east", 46);
        assertFits("gui.ae2_batchcraft.material_output_form.normal", 46);
        assertFits("gui.ae2_batchcraft.material_output_form.drop", 46);
        assertFits("gui.ae2_batchcraft.material_output_form.place", 46);
        assertFits("gui.ae2_batchcraft.product_extraction.reset", 28);
        assertFits("gui.ae2_batchcraft.component_placer.reset_offsets", 48);
        assertFits("gui.ae2_batchcraft.component_placer.clear_selection", 48);
        assertFits("gui.ae2_batchcraft.component_placer.execute", 48);
        assertFits("gui.ae2_batchcraft.energy_distribution_mode.round_robin", 172);
        assertFits("gui.ae2_batchcraft.reset_task", 148);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.return_mode.strict", 69);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.return_mode.unblocked", 69);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.single_trigger", 44);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.periodic_pulse", 44);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.continuous", 44);
    }

    @Test
    void headerLabelsDoNotIntersectAdjacentControls() {
        assertFits("gui.ae2_batchcraft.material_output_config.title", 140);
        assertFits("gui.ae2_batchcraft.product_extraction.title", 140);
        assertFits("item.ae2_batchcraft.pattern_p2p_tunnel_input", 140);
        assertFits("item.ae2_batchcraft.pattern_p2p_unit_manager", 140);

        assertFits("gui.ae2_batchcraft.return_configuration", 147);
        assertFits("gui.ae2_batchcraft.return_mode", 147);
        assertFits("gui.ae2_batchcraft.product_extraction.title", 147);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration", 147);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset", 147);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 147);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 147);
        assertFits("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 147);

        int syncInputWidth = width(ENGLISH.get("gui.ae2_batchcraft.sync_input_settings")) + 26;
        assertTrue(syncInputWidth <= 160);

        int generalTitleWidth = (int) Math.ceil(width(
                ENGLISH.get("gui.ae2_batchcraft.pattern_p2p_unit.page.common")) * 1.2);
        int availableSyncWidth = 176 - 8 - (8 + generalTitleWidth + 8);
        int syncMainWidth = width(ENGLISH.get(
                "gui.ae2_batchcraft.pattern_p2p_unit.sync_main_configuration")) + 26;
        assertTrue(syncMainWidth <= availableSyncWidth);

        int energyHeaderWidth = width(ENGLISH.get("gui.ae2_batchcraft.energy.section.input"));
        int intervalWidth = width(ENGLISH.get("gui.ae2_batchcraft.energy.pull_interval")
                .replace("%s", "5"));
        assertTrue(energyHeaderWidth + 8 + intervalWidth <= 200 - 36);
    }

    @Test
    void staticLabelsFitBeforeTheirNeighboringComponents() {
        assertFits("gui.ae2_batchcraft.component_placer.cable", 32);
        assertFits("gui.ae2_batchcraft.component_placer.part", 24);
        assertFits("gui.ae2_batchcraft.product_extraction.interval", 47);
        assertFits("gui.ae2_batchcraft.product_extraction.amount", 47);
        assertFits("gui.ae2_batchcraft.product_extraction.tick", 31);
        assertFits("gui.ae2_batchcraft.product_extraction.unit", 31);
        assertTrue(143 + width(ENGLISH.get("gui.ae2_batchcraft.time.ticks")) <= 169);

        String directionValue = ENGLISH.get("gui.ae2_batchcraft.output_direction_value")
                .replace("%s", ENGLISH.get("gui.ae2_batchcraft.direction.auto"));
        assertTrue(width(directionValue) <= 160);
        String outputFormValue = ENGLISH.get("gui.ae2_batchcraft.material_output_form_value")
                .replace("%s", ENGLISH.get("gui.ae2_batchcraft.material_output_form.normal"));
        assertTrue(width(outputFormValue) <= 160);

        String offsetValue = ENGLISH.get("gui.ae2_batchcraft.component_placer.offset_value")
                .replaceFirst("%s", "X").replaceFirst("%s", "-16");
        assertTrue(width(offsetValue) <= 29);
    }

    private static void assertFits(String key, int availableWidth) {
        String value = ENGLISH.get(key);
        assertTrue(width(value) <= availableWidth,
                () -> key + " is " + width(value) + " px wide, available: " + availableWidth);
    }

    /** Conservative approximation of Minecraft's default ASCII glyph advances. */
    private static int width(String value) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            result += switch (value.charAt(i)) {
                case ' ' -> 4;
                case '!', ',', '.', ':', ';', 'i', '|' -> 2;
                case '\'', '`', 'l' -> 3;
                case 'I', '[', ']', 't' -> 4;
                case '"', '(', ')', '*', '<', '>', '\\', 'f', 'k', '{', '}' -> 5;
                case '@' -> 7;
                default -> 6;
            };
        }
        return result;
    }

    private static Map<String, String> loadEnglish() {
        try {
            String json = Files.readString(Path.of(
                    "src/main/resources/assets/ae2_batchcraft/lang/en_us.json"));
            return new Gson().fromJson(json, new TypeToken<Map<String, String>>() {
            }.getType());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
