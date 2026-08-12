package cn.ae2bc.client;

import appeng.api.util.AEColor;
import cn.ae2bc.logic.PatternP2PUnitDimensions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PUnitManagerModelResourceTest {
    private static final String BASE_MODEL =
            "assets/ae2_batchcraft/models/pattern_p2p_unit_manager_base.json";
    private static final String GLASS_MODEL =
            "assets/ae2_batchcraft/models/part/pattern_p2p_unit_manager_glass.json";
    private static final Pattern ELEMENT_BOUNDS = Pattern.compile(
            "\"from\": \\[([^]]+)],\\s*\"to\": \\[([^]]+)]");
    private static final Pattern TINT_INDEX = Pattern.compile("\"tintindex\": (\\d+)");
    private static final Pattern ELEMENT = Pattern.compile(
            "\"from\": \\[([^]]+)]\\s*,\\s*\"to\": \\[([^]]+)]\\s*,\\s*"
                    + "\"faces\": \\{(.*?)\\n    \\}(?:,|\\s*\\])", Pattern.DOTALL);
    private static final Pattern FACE = Pattern.compile(
            "\"(north|south|west|east|down|up)\": \\{\"texture\": \"#frame\", \"tintindex\": (\\d+)\\}");
    private static final Pattern PALETTE_ELEMENT = Pattern.compile(
            "\"from\": \\[([^]]+)]\\s*,\\s*\"to\": \\[([^]]+)]\\s*,\\s*"
                    + "\"faces\": \\{\"(north|south|west|east|down|up)\": "
                    + "\\{\"texture\": \"#frame\", \"tintindex\": (\\d+)\\}\\}\\s*\\}");

    @Test
    void frameUsesExpandedBoundsAndAllThreeAeColorVariants() throws Exception {
        String model = readText(BASE_MODEL);
        assertTrue(model.contains("\"frame\": \"ae2:part/p2p_tunnel_frequency\""));
        assertTrue(model.contains("\"particle\": \"ae2:part/p2p_tunnel_frequency\""));
        Set<Integer> coordinates = new HashSet<>();
        Set<Integer> tintIndices = new HashSet<>();

        var bounds = ELEMENT_BOUNDS.matcher(model);
        int elementCount = 0;
        while (bounds.find()) {
            elementCount++;
            collectIntegerCoordinates(bounds.group(1), coordinates);
            collectIntegerCoordinates(bounds.group(2), coordinates);
        }
        var tints = TINT_INDEX.matcher(model);
        while (tints.find()) {
            tintIndices.add(Integer.parseInt(tints.group(1)));
        }

        assertEquals(108, elementCount);
        assertEquals(Set.of(3, 4, 5, 7, 9, 11, 12, 13), coordinates);
        assertEquals(Set.of(AEColor.TINTINDEX_DARK, AEColor.TINTINDEX_MEDIUM, AEColor.TINTINDEX_BRIGHT), tintIndices);
    }

    @Test
    void everyExteriorFaceUsesTheSmartCableDarkMediumAndBrightPalette() throws Exception {
        Map<String, Set<Integer>> exteriorTints = new LinkedHashMap<>();
        for (String direction : Set.of("north", "south", "west", "east", "down", "up")) {
            exteriorTints.put(direction, new HashSet<>());
        }
        String model = readText(BASE_MODEL);
        var elements = ELEMENT.matcher(model);
        while (elements.find()) {
            double[] from = decimalCoordinates(elements.group(1));
            double[] to = decimalCoordinates(elements.group(2));
            var faces = FACE.matcher(elements.group(3));
            while (faces.find()) {
                String direction = faces.group(1);
                boolean exterior = switch (direction) {
                    case "north" -> from[2] <= 3;
                    case "south" -> to[2] >= 13;
                    case "west" -> from[0] <= 3;
                    case "east" -> to[0] >= 13;
                    case "down" -> from[1] <= 3;
                    case "up" -> to[1] >= 13;
                    default -> false;
                };
                if (exterior) {
                    int tint = Integer.parseInt(faces.group(2));
                    exteriorTints.get(direction).add(tint);
                }
            }
        }

        Set<Integer> smartCablePalette = Set.of(
                AEColor.TINTINDEX_DARK, AEColor.TINTINDEX_MEDIUM, AEColor.TINTINDEX_BRIGHT);
        exteriorTints.forEach((direction, tintIndices) ->
                assertEquals(smartCablePalette, tintIndices, direction));
        Map<String, List<Integer>> parsedPaletteTints = new LinkedHashMap<>();
        for (String direction : exteriorTints.keySet()) {
            parsedPaletteTints.put(direction, new java.util.ArrayList<>());
        }
        var paletteElements = PALETTE_ELEMENT.matcher(model);
        while (paletteElements.find()) {
            String direction = paletteElements.group(3);
            parsedPaletteTints.get(direction).add(Integer.parseInt(paletteElements.group(4)));
            double[] from = decimalCoordinates(paletteElements.group(1));
            double[] to = decimalCoordinates(paletteElements.group(2));
            double[] bounds = switch (direction) {
                case "north", "south" -> new double[]{from[0], to[0], from[1], to[1]};
                case "west", "east" -> new double[]{from[2], to[2], from[1], to[1]};
                case "down", "up" -> new double[]{from[0], to[0], from[2], to[2]};
                default -> throw new IllegalStateException(direction);
            };
            for (int cornerU : new int[]{3, 11}) {
                for (int cornerV : new int[]{3, 11}) {
                    assertFalse(overlaps(bounds, cornerU, cornerU + 2, cornerV, cornerV + 2),
                            direction + " " + paletteElements.group());
                }
            }
        }
        assertEquals(96, parsedPaletteTints.values().stream().mapToInt(List::size).sum());
        parsedPaletteTints.forEach((direction, tints) -> {
            assertEquals(16, tints.size(), direction);
            assertEquals(8, tints.stream().filter(tint -> tint == AEColor.TINTINDEX_DARK).count(), direction);
            assertEquals(4, tints.stream().filter(tint -> tint == AEColor.TINTINDEX_MEDIUM).count(), direction);
            assertEquals(4, tints.stream().filter(tint -> tint == AEColor.TINTINDEX_BRIGHT).count(), direction);
        });
    }

    @Test
    void managerColorMarkersUseTheFrameCornersAndSurfaceDepth() {
        assertEquals(0.01f, PatternP2PUnitDimensions.INDICATOR_DEPTH);
    }

    @Test
    void worldModelUsesSixFullGlassPanels() throws Exception {
        String model = readText(GLASS_MODEL);
        assertTrue(model.contains("\"glass\": \"ae2:block/glass/quartz_glass_a\""));

        var bounds = ELEMENT_BOUNDS.matcher(model);
        int elementCount = 0;
        while (bounds.find()) {
            elementCount++;
            double[] from = parseCoordinates(bounds.group(1));
            double[] to = parseCoordinates(bounds.group(2));
            int fullPanelAxes = 0;
            int thinAxes = 0;
            for (int axis = 0; axis < 3; axis++) {
                double size = to[axis] - from[axis];
                if (size == 6) {
                    fullPanelAxes++;
                } else if (size == 0.0625) {
                    thinAxes++;
                }
            }
            assertEquals(2, fullPanelAxes, bounds.group());
            assertEquals(1, thinAxes, bounds.group());
        }
        assertEquals(6, elementCount);
    }

    private static void collectIntegerCoordinates(String values, Set<Integer> result) {
        for (String value : values.split(",")) {
            double coordinate = Double.parseDouble(value.trim());
            if (coordinate == Math.rint(coordinate)) {
                result.add((int) coordinate);
            }
        }
    }

    private static double[] parseCoordinates(String values) {
        String[] parts = values.split(",");
        return new double[]{
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())
        };
    }

    private static double[] decimalCoordinates(String values) {
        String[] parts = values.split(",");
        return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())};
    }

    private static boolean overlaps(double[] bounds, double minU, double maxU, double minV, double maxV) {
        return bounds[0] < maxU && bounds[1] > minU && bounds[2] < maxV && bounds[3] > minV;
    }

    private static String readText(String resource) throws Exception {
        try (var input = PatternP2PUnitManagerModelResourceTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
