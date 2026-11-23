package org.ae2bc.client;

import appeng.api.util.AEColor;
import guideme.compiler.FrontmatterNavigation;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class GuideResourcesTest {
    private static final String NAMESPACE = "ae2_batchcraft";
    private static final Path MODULE = GuideResourcesTest.findModule();
    private static final Path ASSETS = MODULE.resolve("src/main/resources/assets").resolve(NAMESPACE);
    private static final Path ROOT = ASSETS.resolve("ae2guide");
    private static final Pattern LINKS = Pattern.compile("\\[[^]]+]\\(([^)#]+\\.md)(?:#[^)]+)?\\)");
    private static final Pattern IMAGES = Pattern.compile("<ItemImage id=\"ae2_batchcraft:([a-z0-9_]+)\"");
    private static final Pattern REGISTERED_ITEM = Pattern.compile("ITEMS\\.register\\(\\s*\"([a-z0-9_]+)\"");

    /* 用 GuideME 实际解析器验证两种语言，避免只有文件存在但页面无法加载。 */
    @Test
    void pagesParseAndTranslationsShareNavigationAndItemLinks() throws IOException {
        Map<String, String> english = GuideResourcesTest.readPages(ROOT);
        Map<String, String> chinese = GuideResourcesTest.readPages(ROOT.resolve("_zh_cn"));
        Assertions.assertFalse(english.isEmpty());
        Assertions.assertEquals(english.keySet(), chinese.keySet());
        Map<String, ParsedGuidePage> original = GuideResourcesTest.parsePages(english, "en_us");
        Map<String, ParsedGuidePage> translated = GuideResourcesTest.parsePages(chinese, "zh_cn");
        for (String path : english.keySet()) {
            ParsedGuidePage first = original.get(path);
            ParsedGuidePage second = translated.get(path);
            FrontmatterNavigation firstNav = first.getFrontmatter().navigationEntry();
            FrontmatterNavigation secondNav = second.getFrontmatter().navigationEntry();
            Assertions.assertNotNull(firstNav, path);
            Assertions.assertNotNull(secondNav, path);
            Assertions.assertEquals(firstNav.parent(), secondNav.parent(), path);
            Assertions.assertEquals(firstNav.iconItemId(), secondNav.iconItemId(), path);
            Assertions.assertEquals(firstNav.position(), secondNav.position(), path);
            Assertions.assertEquals(first.getFrontmatter().additionalProperties().get("item_ids"),
                    second.getFrontmatter().additionalProperties().get("item_ids"), path);
            Assertions.assertNotEquals(english.get(path), chinese.get(path), path);
        }
    }

    /* 检查链接、图标与目录可达性，防止坏链接及目录循环。 */
    @Test
    void linksAndNavigationResolveWithinTheGuide() throws IOException {
        for (Path root : List.of(ROOT, ROOT.resolve("_zh_cn"))) {
            Map<String, String> sources = GuideResourcesTest.readPages(root);
            Map<String, ParsedGuidePage> pages = GuideResourcesTest.parsePages(sources, "en_us");
            for (Map.Entry<String, String> entry : sources.entrySet()) {
                String path = entry.getKey();
                Path directory = Path.of(path).getParent();
                Matcher links = LINKS.matcher(entry.getValue());
                while (links.find()) {
                    Path target = (directory == null ? Path.of("") : directory).resolve(links.group(1)).normalize();
                    Assertions.assertTrue(sources.containsKey(target.toString().replace('\\', '/')),
                            path + " -> " + target);
                }
                Matcher images = IMAGES.matcher(entry.getValue());
                while (images.find()) {
                    GuideResourcesTest.assertItemModel(images.group(1));
                }
                Set<String> visited = new HashSet<>();
                String cursor = path;
                while (true) {
                    Assertions.assertTrue(visited.add(cursor), "目录循环: " + path);
                    ParsedGuidePage page = pages.get(cursor);
                    Assertions.assertNotNull(page, cursor);
                    FrontmatterNavigation navigation = page.getFrontmatter().navigationEntry();
                    Assertions.assertNotNull(navigation, cursor);
                    ResourceLocation icon = navigation.iconItemId();
                    if (icon != null) {
                        Assertions.assertEquals(NAMESPACE, icon.getNamespace());
                        GuideResourcesTest.assertItemModel(icon.getPath());
                    }
                    ResourceLocation parent = navigation.parent();
                    if (parent == null) {
                        Assertions.assertEquals("index.md", cursor);
                        break;
                    }
                    Assertions.assertEquals(NAMESPACE, parent.getNamespace());
                    cursor = parent.getPath();
                }
            }
        }
    }

    /* 每个注册物品只关联一个具体页面，全部管理器颜色共用管理器说明。 */
    @Test
    void everyRegisteredItemHasExactlyOneGuidePage() throws IOException {
        Set<String> expected = new HashSet<>();
        String registry = Files.readString(MODULE.resolve("src/main/java/org/ae2bc/registry/ItemRegistry.java"));
        Matcher registrations = REGISTERED_ITEM.matcher(registry);
        while (registrations.find()) {
            expected.add(NAMESPACE + ":" + registrations.group(1));
        }
        for (AEColor color : AEColor.values()) {
            expected.add(NAMESPACE + ":pp2mp_unit_manager_" + color.registryPrefix);
        }
        Assertions.assertFalse(expected.isEmpty());
        for (Path root : List.of(ROOT, ROOT.resolve("_zh_cn"))) {
            Map<String, ParsedGuidePage> pages = GuideResourcesTest.parsePages(
                    GuideResourcesTest.readPages(root), "en_us");
            Map<String, String> owners = new HashMap<>();
            for (Map.Entry<String, ParsedGuidePage> entry : pages.entrySet()) {
                Object itemIds = entry.getValue().getFrontmatter().additionalProperties().get("item_ids");
                if (itemIds == null) {
                    continue;
                }
                List<?> ids = Assertions.assertInstanceOf(List.class, itemIds);
                Assertions.assertFalse(entry.getKey().endsWith("index.md"));
                for (Object id : ids) {
                    String item = Assertions.assertInstanceOf(String.class, id);
                    Assertions.assertNull(owners.putIfAbsent(item, entry.getKey()), "重复关联: " + item);
                }
            }
            Assertions.assertEquals(expected, owners.keySet());
        }
    }

    private static Map<String, ParsedGuidePage> parsePages(Map<String, String> sources, String language) {
        Map<String, ParsedGuidePage> pages = new TreeMap<>();
        sources.forEach((path, source) -> pages.put(path, PageCompiler.parse(
                NAMESPACE, language, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path), source)));
        return pages;
    }

    private static Map<String, String> readPages(Path root) throws IOException {
        Map<String, String> pages = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.endsWith(".md") && !relative.startsWith("_")) {
                    pages.put(relative, Files.readString(file));
                }
            }
        }
        return pages;
    }

    private static void assertItemModel(String item) {
        Assertions.assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + item + ".json")),
                "不存在物品模型: " + item);
    }

    /* NeoForge 测试可能从运行目录启动，沿父目录定位主模块。 */
    private static Path findModule() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            for (Path candidate : List.of(current, current.resolve("neoforge1211"))) {
                if (Files.isRegularFile(candidate.resolve("src/main/java/org/ae2bc/registry/ItemRegistry.java"))) {
                    return candidate;
                }
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 NeoForge 主模块");
    }
}
