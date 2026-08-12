package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PUnitPortIconResourceTest {
    private static final int TEXTURE_SIZE = 64;
    private static final int MAX_ART_SIZE = 20;
    private static final String[] PORTS = {
            "drop", "collect", "place", "break", "transfer", "return", "extract", "redstone", "energy"
    };

    @Test
    void everyPortIconIsDistinctSixteenPixelOpaqueArt() throws Exception {
        Set<String> hashes = new HashSet<>();
        for (String port : PORTS) {
            String path = "assets/ae2_batchcraft/textures/part/p2p/unit_port_" + port + ".png";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(input, path);
                BufferedImage image = ImageIO.read(input);
                assertNotNull(image, path);
                assertEquals(TEXTURE_SIZE, image.getWidth(), path);
                assertEquals(TEXTURE_SIZE, image.getHeight(), path);
                assertOpaque(image, path);
                assertCenteredAndBounded(image, path);
                hashes.add(hex(MessageDigest.getInstance("SHA-256").digest(imageBytes(image))));
            }
        }
        assertEquals(PORTS.length, hashes.size(), "port icons must not reuse one another");
    }

    private static void assertOpaque(BufferedImage image, String path) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(255, image.getRGB(x, y) >>> 24, path + " at " + x + "," + y);
            }
        }
    }

    private static void assertCenteredAndBounded(BufferedImage image, String path) {
        int background = image.getRGB(0, 0);
        int minX = TEXTURE_SIZE;
        int minY = TEXTURE_SIZE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                if (image.getRGB(x, y) != background) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(maxX >= minX && maxY >= minY, path + " must contain artwork");
        assertTrue(maxX - minX + 1 <= MAX_ART_SIZE, path + " exceeds maximum artwork width");
        assertTrue(maxY - minY + 1 <= MAX_ART_SIZE, path + " exceeds maximum artwork height");
        assertTrue(Math.abs(minX + maxX - (TEXTURE_SIZE - 1)) <= 1, path + " is not horizontally centered");
        assertTrue(Math.abs(minY + maxY - (TEXTURE_SIZE - 1)) <= 1, path + " is not vertically centered");
    }

    private static byte[] imageBytes(BufferedImage image) {
        byte[] bytes = new byte[TEXTURE_SIZE * TEXTURE_SIZE * 4];
        int offset = 0;
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int argb = image.getRGB(x, y);
                bytes[offset++] = (byte) (argb >>> 24);
                bytes[offset++] = (byte) (argb >>> 16);
                bytes[offset++] = (byte) (argb >>> 8);
                bytes[offset++] = (byte) argb;
            }
        }
        return bytes;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
