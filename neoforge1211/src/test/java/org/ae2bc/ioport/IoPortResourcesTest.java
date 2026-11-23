package org.ae2bc.ioport;

import appeng.core.AppEng;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.ae2bc.registry.BlockRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class IoPortResourcesTest {
    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(IoPortResourcesTest.class.getResourceAsStream(path));
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void blockStatesCoverEveryOrientationAndBothPowerStates() throws IOException {
        JsonObject variants = IoPortResourcesTest.json("/assets/ae2_batchcraft/blockstates/io_port.json")
                .getAsJsonObject("variants");
        Assertions.assertEquals(48, BlockRegistry.IO_PORT.get().getStateDefinition().getPossibleStates().size());
        Assertions.assertEquals(48, variants.size());
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            for (boolean powered : new boolean[]{false, true}) {
                for (int spin = 0; spin < 4; spin++) {
                    String key = "facing=" + facing + ",powered=" + powered + ",spin=" + spin;
                    String model = variants.getAsJsonObject(key).get("model").getAsString();
                    Assertions.assertEquals("ae2_batchcraft:block/io_port" + (powered ? "_on" : ""), model);
                }
            }
        }
    }

    /* 不渲染界面，只检查引用完整、贴图尺寸和替换后的颜色。 */
    @Test
    void modelTexturesResolveWithoutExtendedAeAndKeepPixelDimensions() throws IOException {
        Set<Integer> colors = new HashSet<>();
        for (String name : new String[]{"io_port", "io_port_on"}) {
            JsonObject model = IoPortResourcesTest.json("/assets/ae2_batchcraft/models/block/" + name + ".json");
            Assertions.assertFalse(model.toString().contains("extendedae:"));
            for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
                String[] id = texture.getValue().getAsString().split(":", 2);
                String path = "/assets/" + id[0] + "/textures/" + id[1] + ".png";
                URL resource = (id[0].equals("ae2") ? AppEng.class : IoPortResourcesTest.class).getResource(path);
                Assertions.assertNotNull(resource, path);
                try (InputStream stream = Objects.requireNonNull(resource).openStream()) {
                    BufferedImage image = ImageIO.read(stream);
                    Assertions.assertEquals(16, image.getWidth());
                    Assertions.assertEquals(16, image.getHeight());
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            colors.add(image.getRGB(x, y) & 0xFFFFFF);
                        }
                    }
                }
            }
        }
        Assertions.assertTrue(colors.containsAll(Set.of(0xB76E22, 0xCE9835, 0xE8C64B)));
        Assertions.assertFalse(colors.contains(0x65E883));
        Assertions.assertFalse(colors.contains(0xD0FB9D));
        Assertions.assertFalse(colors.contains(0xF4FFB5));
    }

    @Test
    void recipeLootAndMiningTagReferToIndependentBlock() throws IOException {
        Assertions.assertEquals("ae2_batchcraft:io_port",
                IoPortResourcesTest.json("/data/ae2_batchcraft/recipe/io_port.json")
                        .getAsJsonObject("result").get("id").getAsString());
        Assertions.assertEquals("ae2_batchcraft:io_port",
                IoPortResourcesTest.json("/data/ae2_batchcraft/loot_table/blocks/io_port.json")
                        .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                        .get(0).getAsJsonObject().get("name").getAsString());
        Assertions.assertTrue(IoPortResourcesTest.json("/data/minecraft/tags/block/mineable/pickaxe.json")
                .getAsJsonArray("values").asList().stream()
                .anyMatch(value -> value.getAsString().equals("ae2_batchcraft:io_port")));
        Assertions.assertFalse(IoPortResourcesTest.json("/ae2_batchcraft.mixins.json")
                .toString().contains("IOPort"));
    }
}
