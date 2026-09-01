package cn.ae2bc.client;

import guideme.libs.mdast.MdAst;
import guideme.libs.mdast.MdastOptions;
import guideme.libs.mdast.YamlFrontmatterExtension;
import guideme.libs.mdast.gfm.GfmTableMdastExtension;
import guideme.libs.mdast.mdx.MdxMdastExtension;
import guideme.libs.mdx.MdxSyntax;
import guideme.libs.micromark.extensions.YamlFrontmatterSyntax;
import guideme.libs.micromark.extensions.gfm.GfmTableSyntax;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidePageResourceTest {
    private static final Path ENGLISH_GUIDE_ROOT =
            Path.of("src/main/resources/assets/ae2_batchcraft/ae2guide");
    private static final Path CHINESE_GUIDE_ROOT =
            ENGLISH_GUIDE_ROOT.resolve("_zh_cn");
    private static final Pattern PARENT = Pattern.compile("(?m)^  parent: (\\S+)\\s*$");
    private static final Pattern ITEM_ID = Pattern.compile("(?m)^- (ae2_batchcraft:[a-z0-9_]+)\\s*$");
    private static final Pattern IMPORT_STRUCTURE = Pattern.compile(
            "<ImportStructure\\s+src=\"([^\"]+\\.snbt)\"\\s*/>");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]+]\\(([^)#]+\\.md)(?:#[^)]+)?\\)");
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+");
    private static final Pattern EXTERNAL_MOD_CONTENT = Pattern.compile(
            "(?i)\\b(?:minecraft|forge|neoforge|guideme|mekanism|extendedae|waila|jade|jei)\\b"
                    + "|inventory tweaks|other mod|third-party|protection mod|claim (?:rule|protection)"
                    + "|其他模组|第三方模组|保护模组|领地");
    private static final Set<String> UNIT_PORT_PAGES = Set.of(
            "unit/transfer-port.md",
            "unit/drop-port.md",
            "unit/place-port.md",
            "unit/return-port.md",
            "unit/extraction-port.md",
            "unit/collect-port.md",
            "unit/break-port.md",
            "unit/redstone-port.md",
            "unit/energy-port.md");
    private static final Map<String, String> EXPECTED_ITEM_PAGES = Map.ofEntries(
            Map.entry("ae2_batchcraft:pattern_p2p_tunnel_input", "pattern-p2p/input.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_tunnel_output", "pattern-p2p/output.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_tunnel_energy", "pattern-p2p/energy.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_transfer", "unit/transfer-port.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_drop", "unit/drop-port.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_place", "unit/place-port.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_return", "unit/return-port.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_extract", "unit/extraction-port.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_collect", "unit/collect-port.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_break", "unit/break-port.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_redstone", "unit/redstone-port.md"),
            Map.entry("ae2_batchcraft:pattern_p2p_unit_port_energy", "unit/energy-port.md"),
            Map.entry("ae2_batchcraft:product_extraction_card", "product-return/extraction-card.md"),
            Map.entry("ae2_batchcraft:component_placer", "tools/component-placer.md"));
    private static final String INPUT_RECIPE =
            "data/ae2_batchcraft/recipe/pattern_p2p_tunnel_input.json";
    private static final String PLACER_RECIPE =
            "data/ae2_batchcraft/recipe/component_placer.json";
    private static final String ENERGY_TUNNEL_RECIPE =
            "data/ae2_batchcraft/recipe/pattern_p2p_tunnel_energy.json";
    private static final String PRODUCT_EXTRACTION_RECIPE =
            "data/ae2_batchcraft/recipe/product_extraction_card.json";
    private static final String UNIT_EXTRACTION_RECIPE =
            "data/ae2_batchcraft/recipe/pattern_p2p_unit_port_extract.json";
    private static final String PLACER_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/component_placer.json";
    private static final String ENERGY_TUNNEL_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_energy.json";
    private static final String PRODUCT_EXTRACTION_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/product_extraction.json";
    private static final String AE2_PATTERN_PROVIDER_TAG = "data/ae2/tags/item/pattern_provider.json";

    @Test
    void localizedGuideMirrorsTheEnglishPageTreeAndEveryPageParses() throws Exception {
        var english = readEnglishGuideTree();
        var chinese = readGuideTree(CHINESE_GUIDE_ROOT);

        assertEquals(english.keySet(), chinese.keySet());
        assertEquals(Set.of(
                "getting-started/index.md",
                "pattern-p2p/input.md",
                "pattern-p2p/output.md",
                "pattern-p2p/energy.md",
                "unit/index.md",
                "product-return/index.md",
                "tools/index.md",
                "troubleshooting/index.md"), childPagesOf(english, "index.md"));
        assertEquals(Set.of("index.md"), rootPages(english));
        assertTrue(english.get("index.md").contains("<SubPages />"));
        assertTrue(chinese.get("index.md").contains("<SubPages />"));

        english.forEach(GuidePageResourceTest::assertValidGuidePage);
        chinese.forEach(GuidePageResourceTest::assertValidGuidePage);
        assertFalse(chinese.get("index.md").equals(english.get("index.md")));
    }

    @Test
    void everyNavigationParentExistsAndUnitPortsAreDirectChildren() throws Exception {
        for (var guide : List.of(readEnglishGuideTree(), readGuideTree(CHINESE_GUIDE_ROOT))) {
            guide.forEach((path, page) -> parentOf(page).ifPresent(parent ->
                    assertTrue(guide.containsKey(parent), path + " has missing parent " + parent)));
            for (var portPage : UNIT_PORT_PAGES) {
                assertEquals("unit/index.md", parentOf(guide.get(portPage)).orElse(null), portPage);
            }
        }
    }

    @Test
    void internalLinksExistAndLocalizedPagesKeepTheSameHeadingStructure() throws Exception {
        var english = readEnglishGuideTree();
        var chinese = readGuideTree(CHINESE_GUIDE_ROOT);
        for (var entry : english.entrySet()) {
            String pagePath = entry.getKey();
            assertInternalLinksExist(pagePath, entry.getValue(), english);
            assertInternalLinksExist(pagePath, chinese.get(pagePath), chinese);
            assertEquals(headingLevels(entry.getValue()), headingLevels(chinese.get(pagePath)),
                    pagePath + " has different localized heading levels");
        }
    }

    @Test
    void itemShortcutsResolveToOneMatchingLeafPageInEveryLanguage() throws Exception {
        var englishItems = collectItemPages(readEnglishGuideTree());
        var chineseItems = collectItemPages(readGuideTree(CHINESE_GUIDE_ROOT));

        assertEquals(englishItems, chineseItems);
        assertEquals(31, englishItems.size());
        EXPECTED_ITEM_PAGES.forEach((itemId, page) -> assertEquals(page, englishItems.get(itemId), itemId));
        assertTrue(englishItems.values().stream().noneMatch(page -> page.endsWith("index.md")),
                "Category index pages must not capture item guide shortcuts");

        for (var managerId : managerItemIds()) {
            assertEquals("unit/manager.md", englishItems.get(managerId), managerId);
        }
    }

    @Test
    void everyLiveSceneReferencesACompleteStructureAndIsMirroredInChinese() throws Exception {
        var english = readEnglishGuideTree();
        var chinese = readGuideTree(CHINESE_GUIDE_ROOT);
        var referencedStructures = new LinkedHashSet<Path>();

        english.forEach((pagePath, page) -> {
            var englishImports = importedStructures(pagePath, page, ENGLISH_GUIDE_ROOT);
            var chineseImports = importedStructures(pagePath, chinese.get(pagePath), CHINESE_GUIDE_ROOT);
            assertEquals(englishImports, chineseImports, pagePath + " has different localized live scenes");
            referencedStructures.addAll(englishImports);
        });

        assertEquals(4, referencedStructures.size());
        referencedStructures.forEach(structure -> {
            assertTrue(Files.isRegularFile(structure), "Missing GuideME structure " + structure);
            try {
                String snbt = Files.readString(structure);
                assertTrue(Pattern.compile("(?m)^\\s*size:\\s*\\[").matcher(snbt).find(), structure.toString());
                assertTrue(Pattern.compile("(?m)^\\s*data:\\s*\\[").matcher(snbt).find(), structure.toString());
                assertTrue(Pattern.compile("(?m)^\\s*entities:\\s*\\[").matcher(snbt).find(), structure.toString());
                assertTrue(Pattern.compile("(?m)^\\s*palette:\\s*\\[").matcher(snbt).find(), structure.toString());
                assertTrue(snbt.contains("state:"), structure + " has no visible blocks");
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + structure, e);
            }
        });

        for (String pagePath : List.of(
                "troubleshooting/material-directions.md",
                "product-return/index.md",
                "tools/component-placer.md",
                "pattern-p2p/energy.md")) {
            assertFalse(english.get(pagePath).contains("<GameScene"), pagePath);
            assertFalse(chinese.get(pagePath).contains("<GameScene"), pagePath);
        }
    }

    @Test
    void guideDocumentsContinuousEnergyAndExtractionSettingScope() throws Exception {
        String englishEnergy = readEnglishGuideTree().get("unit/energy-port.md");
        String chineseEnergy = readGuideTree(CHINESE_GUIDE_ROOT).get("unit/energy-port.md");
        assertTrue(englishEnergy.contains("**Not required**"));
        assertTrue(englishEnergy.contains("power delivery continues"));
        assertTrue(chineseEnergy.contains("**不需要**"));
        assertTrue(chineseEnergy.contains("供能仍会持续"));

        String englishExtraction = readEnglishGuideTree().get("product-return/endpoint-extraction.md");
        String chineseExtraction = readGuideTree(CHINESE_GUIDE_ROOT).get("product-return/endpoint-extraction.md");
        assertTrue(englishExtraction.contains("switch therefore controls only"));
        assertTrue(englishExtraction.contains("interval and amount affect both endpoint types"));
        assertTrue(chineseExtraction.contains("开关只控制普通输出端"));
        assertTrue(chineseExtraction.contains("间隔和数量同时影响两类端点"));

        for (var guide : List.of(readEnglishGuideTree(), readGuideTree(CHINESE_GUIDE_ROOT))) {
            guide.forEach((path, page) -> {
                String lower = page.toLowerCase(java.util.Locale.ROOT);
                assertFalse(lower.contains("only while a job is active"), path);
                assertFalse(page.contains("仅在任务活动时供能"), path);
            });
        }
    }

    @Test
    void guideDocumentsBatchDistributionAndTheExtractionSafetyBudgets() throws Exception {
        var english = readEnglishGuideTree();
        var chinese = readGuideTree(CHINESE_GUIDE_ROOT);

        assertEquals(Set.of("pattern-p2p/batch-distribution.md"),
                childPagesOf(english, "pattern-p2p/input.md"));
        String englishBatch = english.get("pattern-p2p/batch-distribution.md");
        String chineseBatch = chinese.get("pattern-p2p/batch-distribution.md");
        assertNotNull(englishBatch);
        assertNotNull(chineseBatch);
        assertTrue(englishBatch.contains("Full Dispatch"));
        assertTrue(englishBatch.contains("Batch Distribution"));
        assertTrue(englishBatch.contains("primary output"));
        assertTrue(englishBatch.contains("Ctrl + Middle Mouse Button"));
        assertTrue(englishBatch.contains("cannot prove the smallest recipe"));
        assertTrue(chineseBatch.contains("完整下发"));
        assertTrue(chineseBatch.contains("批次分发"));
        assertTrue(chineseBatch.contains("主产物"));
        assertTrue(chineseBatch.contains("不能证明相邻库存实际接受的最小配方"));

        String englishExtraction = english.get("product-return/endpoint-extraction.md");
        String chineseExtraction = chinese.get("product-return/endpoint-extraction.md");
        assertTrue(englishExtraction.contains("`64` successful return-inventory rounds"));
        assertTrue(englishExtraction.contains("`256` successful rounds per tick"));
        assertTrue(englishExtraction.contains("not a promise that the whole amount moves in one tick"));
        assertTrue(chineseExtraction.contains("`64` 个成功返回库存轮次"));
        assertTrue(chineseExtraction.contains("`256` 个成功轮次"));
        assertTrue(chineseExtraction.contains("不承诺在一个 tick 内全部移动"));
    }

    @Test
    void playerGuideContainsNoExternalModNamesOrContent() throws Exception {
        for (var guide : List.of(readEnglishGuideTree(), readGuideTree(CHINESE_GUIDE_ROOT))) {
            guide.forEach((path, page) -> assertFalse(EXTERNAL_MOD_CONTENT.matcher(page).find(),
                    path + " references content outside AE2 and AE2 BatchCraft"));
        }
    }

    @Test
    void inputRecipeAcceptsBothPatternProviderForms() throws Exception {
        String recipe = readText(INPUT_RECIPE);
        assertTrue(recipe.contains("\"pattern\": [\n    \"IRI\",\n    \"ETE\",\n    \"IPI\"\n  ]"));
        assertTrue(recipe.contains("\"I\": {\n      \"item\": \"minecraft:iron_ingot\"\n    }"));
        assertTrue(recipe.contains("\"P\": {\n      \"tag\": \"ae2:pattern_provider\"\n    }"));

        String tag = readText(AE2_PATTERN_PROVIDER_TAG);
        assertTrue(tag.contains("\"ae2:cable_pattern_provider\""));
        assertTrue(tag.contains("\"ae2:pattern_provider\""));
    }

    @Test
    void placerRecipeUsesTheWirelessTerminalAndBothEndpoints() throws Exception {
        String recipe = readText(PLACER_RECIPE);
        assertTrue(recipe.contains("\"item\": \"ae2:wireless_terminal\""));
        assertTrue(recipe.contains("\"item\": \"ae2_batchcraft:pattern_p2p_tunnel_input\""));
        assertTrue(recipe.contains("\"item\": \"ae2_batchcraft:pattern_p2p_tunnel_output\""));
        assertTrue(recipe.contains("\"id\": \"ae2_batchcraft:component_placer\""));
    }

    @Test
    void energyTunnelRecipeReplacesTheInputRecipeProviderWithAnEnergyAcceptor() throws Exception {
        String recipe = readText(ENERGY_TUNNEL_RECIPE);
        assertTrue(recipe.contains("\"pattern\": [\n    \"IRI\",\n    \"ETE\",\n    \"IAI\"\n  ]"));
        assertTrue(recipe.contains("\"I\": {\n      \"item\": \"minecraft:iron_ingot\"\n    }"));
        assertTrue(recipe.contains("\"R\": {\n      \"item\": \"minecraft:redstone\"\n    }"));
        assertTrue(recipe.contains("\"E\": {\n      \"item\": \"minecraft:ender_pearl\"\n    }"));
        assertTrue(recipe.contains("\"T\": {\n      \"item\": \"ae2:me_p2p_tunnel\"\n    }"));
        assertTrue(recipe.contains("\"A\": {\n      \"item\": \"ae2:energy_acceptor\"\n    }"));
        assertTrue(recipe.contains("\"id\": \"ae2_batchcraft:pattern_p2p_tunnel_energy\""));
    }

    @Test
    void energyTunnelScreenProvidesBothEnergyModes() throws Exception {
        String screen = readText(ENERGY_TUNNEL_SCREEN);
        assertTrue(screen.contains("\"width\": 176"));
        assertTrue(screen.contains("\"height\": 109"));
        assertTrue(screen.contains("\"passive\""));
        assertTrue(screen.contains("\"active\""));
        assertTrue(screen.contains("\"energyDistributionMode\""));
    }

    @Test
    void productExtractionResourcesExposeTheCardAndItsSettings() throws Exception {
        String recipe = readText(PRODUCT_EXTRACTION_RECIPE);
        assertTrue(recipe.contains("\"item\": \"ae2:crafting_card\""));
        assertTrue(recipe.contains("\"id\": \"ae2_batchcraft:product_extraction_card\""));

        String screen = readText(PRODUCT_EXTRACTION_SCREEN);
        assertTrue(screen.contains("\"AE2_BATCHCRAFT_PRODUCT_MARKER\""));
        assertTrue(screen.contains("\"intervalReset\""));
        assertTrue(screen.contains("\"amountReset\""));
        assertTrue(screen.contains("\"modeToggle\""));
    }

    @Test
    void unitExtractionPortHasARecipeAndGuideEntry() throws Exception {
        String recipe = readText(UNIT_EXTRACTION_RECIPE);
        assertTrue(recipe.contains("\"item\":\"ae2:import_bus\""));
        assertTrue(recipe.contains("\"item\":\"ae2:engineering_processor\""));
        assertTrue(recipe.contains("\"id\":\"ae2_batchcraft:pattern_p2p_unit_port_extract\""));

        String guide = readText("assets/ae2_batchcraft/ae2guide/unit/extraction-port.md");
        assertTrue(guide.contains("- ae2_batchcraft:pattern_p2p_unit_port_extract"));
        assertTrue(guide.contains("# Unit Port (Extraction)"));
    }

    @Test
    void placerScreenSeparatesSlotsAndUsesRelativeDirectionLayout() throws Exception {
        String screen = readText(PLACER_SCREEN);

        assertTrue(screen.contains("\"AE2_BATCHCRAFT_CABLE_MARKER\""));
        assertTrue(screen.contains("\"AE2_BATCHCRAFT_PART_MARKER\""));
        assertTrue(screen.contains("\"STORAGE\""));
        assertTrue(screen.contains("\"PLAYER_INVENTORY\""));
        assertTrue(screen.contains("\"PLAYER_HOTBAR\""));
        assertTrue(screen.contains("\"width\": 176"));
        assertTrue(screen.contains("\"height\": 228"));
        assertFalse(screen.contains("\"scale\""));
        assertTrue(screen.contains("\"align\": \"CENTER\""));
        assertTrue(screen.contains("gui.ae2_batchcraft.component_placer.cable"));
        assertTrue(screen.contains("gui.ae2_batchcraft.component_placer.part"));
        assertFalse(screen.contains("gui.ae2_batchcraft.component_placer.markers"));
        assertFalse(screen.contains("gui.ae2_batchcraft.component_placer.direction\""));
        assertTrue(screen.contains("\"frequency\": { \"left\": 154"));
        assertTrue(screen.contains("\"materials_label\""));
        assertTrue(screen.contains("../common/common.json"));
        assertFalse(screen.contains("\"modeInput\""));
        assertFalse(screen.contains("\"modeOutput\""));
        assertTrue(screen.contains("\"directionFront\""));
        assertTrue(screen.contains("\"directionLeft\""));
        assertTrue(screen.contains("\"directionRight\""));
        assertTrue(screen.contains("\"directionBack\""));
        assertTrue(screen.contains("\"frequencyText\""));
        assertTrue(screen.contains("\"frequency\""));
    }

    private static String readText(String resource) throws Exception {
        try (var input = GuidePageResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> readGuideTree(Path root) throws IOException {
        var pages = new TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md"))
                    .forEach(path -> {
                        try {
                            pages.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path));
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read " + path, e);
                        }
                    });
        }
        assertTrue(pages.containsKey("index.md"), root.toString());
        return pages;
    }

    private static Map<String, String> readEnglishGuideTree() throws IOException {
        var pages = readGuideTree(ENGLISH_GUIDE_ROOT);
        pages.keySet().removeIf(path -> path.startsWith("_"));
        return pages;
    }

    private static Set<String> rootPages(Map<String, String> guide) {
        var pages = new LinkedHashSet<String>();
        guide.forEach((path, page) -> {
            if (parentOf(page).isEmpty()) {
                pages.add(path);
            }
        });
        return pages;
    }

    private static Set<String> childPagesOf(Map<String, String> guide, String parent) {
        var pages = new LinkedHashSet<String>();
        guide.forEach((path, page) -> {
            if (parentOf(page).filter(parent::equals).isPresent()) {
                pages.add(path);
            }
        });
        return pages;
    }

    private static Map<String, String> collectItemPages(Map<String, String> guide) {
        var itemPages = new LinkedHashMap<String, String>();
        guide.forEach((pagePath, page) -> {
            var matcher = ITEM_ID.matcher(frontmatter(page));
            while (matcher.find()) {
                var previous = itemPages.putIfAbsent(matcher.group(1), pagePath);
                assertEquals(null, previous, matcher.group(1) + " is declared by both " + previous + " and " + pagePath);
            }
        });
        return itemPages;
    }

    private static Set<Path> importedStructures(String pagePath, String page, Path guideRoot) {
        var structures = new LinkedHashSet<Path>();
        var matcher = IMPORT_STRUCTURE.matcher(page);
        Path pageDirectory = guideRoot.resolve(pagePath).getParent();
        while (matcher.find()) {
            Path structure = pageDirectory.resolve(matcher.group(1)).normalize();
            // Localized pages share the language-independent asset directory.
            if (structure.startsWith(CHINESE_GUIDE_ROOT)) {
                structure = ENGLISH_GUIDE_ROOT.resolve(CHINESE_GUIDE_ROOT.relativize(structure)).normalize();
            }
            assertTrue(structure.startsWith(ENGLISH_GUIDE_ROOT), pagePath + " references outside the guide root");
            structures.add(structure);
        }
        return structures;
    }

    private static void assertInternalLinksExist(String pagePath, String page, Map<String, String> guide) {
        var matcher = MARKDOWN_LINK.matcher(page);
        Path pageDirectory = Path.of(pagePath).getParent();
        while (matcher.find()) {
            Path target = (pageDirectory == null ? Path.of("") : pageDirectory)
                    .resolve(matcher.group(1)).normalize();
            String normalized = target.toString().replace('\\', '/');
            assertTrue(guide.containsKey(normalized), pagePath + " links to missing page " + normalized);
        }
    }

    private static List<Integer> headingLevels(String page) {
        var levels = new java.util.ArrayList<Integer>();
        var matcher = HEADING.matcher(page);
        while (matcher.find()) {
            levels.add(matcher.group(1).length());
        }
        return levels;
    }

    private static Set<String> managerItemIds() {
        var ids = new LinkedHashSet<String>();
        ids.add("ae2_batchcraft:pattern_p2p_unit_manager");
        for (var color : List.of("white", "light_gray", "gray", "black", "lime", "yellow", "orange", "brown",
                "red", "pink", "magenta", "purple", "blue", "light_blue", "cyan", "green")) {
            ids.add("ae2_batchcraft:" + color + "_pattern_p2p_unit_manager");
        }
        return ids;
    }

    private static java.util.Optional<String> parentOf(String page) {
        var matcher = PARENT.matcher(frontmatter(page));
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    private static String frontmatter(String page) {
        assertStartsWithFrontmatter(page);
        var normalized = page.replace("\r\n", "\n");
        var end = normalized.indexOf("\n---\n", 4);
        assertTrue(end >= 0, "Guide page has unterminated frontmatter");
        return normalized.substring(4, end);
    }

    private static void assertValidGuidePage(String path, String page) {
        assertTrue(frontmatter(page).contains("navigation:"), path);
        assertGuideParses(page);
    }

    private static void assertStartsWithFrontmatter(String page) {
        assertTrue(page.startsWith("---\n") || page.startsWith("---\r\n"));
    }

    private static void assertGuideParses(String page) {
        var options = new MdastOptions()
                .withSyntaxExtension(MdxSyntax.INSTANCE)
                .withSyntaxExtension(YamlFrontmatterSyntax.INSTANCE)
                .withSyntaxExtension(GfmTableSyntax.INSTANCE)
                .withMdastExtension(MdxMdastExtension.INSTANCE)
                .withMdastExtension(YamlFrontmatterExtension.INSTANCE)
                .withMdastExtension(GfmTableMdastExtension.INSTANCE);
        assertDoesNotThrow(() -> MdAst.fromMarkdown(page, options));
    }

}
