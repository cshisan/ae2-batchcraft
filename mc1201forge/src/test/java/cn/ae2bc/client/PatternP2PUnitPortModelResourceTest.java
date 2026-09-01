package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PUnitPortModelResourceTest {
    @Test
    void redstoneAndEnergyPortsUseBatchcraftCenterTextures() throws Exception {
        for (String modelRoot : new String[]{"models/item/", "models/part/p2p/"}) {
            String redstone = readText(modelRoot + "pattern_p2p_unit_port_redstone.json");
            String energy = readText(modelRoot + "pattern_p2p_unit_port_energy.json");

            assertTrue(redstone.contains("ae2_batchcraft:part/p2p/unit_port_redstone"), modelRoot);
            assertTrue(energy.contains("ae2_batchcraft:part/p2p/unit_port_energy"), modelRoot);
        }
    }

    private static String readText(String relativePath) throws Exception {
        String resource = "assets/ae2_batchcraft/" + relativePath;
        try (var input = PatternP2PUnitPortModelResourceTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
