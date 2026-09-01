package cn.ae2bc.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class CreativeTabSourceTest {
    @Test
    public void allRegisteredItemsUseTheModCreativeTab() throws Exception {
        String content = read("src/main/java/cn/ae2bc/registry/ModContent.java");

        assertTrue(content.contains("new CreativeTabs(Ae2bcMod.MOD_ID)"));
        assertTrue(content.contains("public ItemStack createIcon()"));
        assertTrue(content.contains("new ItemStack(INPUT)"));
        assertFalse(content.contains("CreativeTabs.MISC"));
        assertEquals(6, occurrences(content, "setCreativeTab(CREATIVE_TAB)"));
    }

    @Test
    public void creativeTabNameIsTranslated() throws Exception {
        String english = read("src/main/resources/assets/ae2_batchcraft/lang/en_us.lang");
        String chinese = read("src/main/resources/assets/ae2_batchcraft/lang/zh_cn.lang");

        assertTrue(english.contains("itemGroup.ae2_batchcraft=AE2 BatchCraft"));
        assertTrue(chinese.contains("itemGroup.ae2_batchcraft=AE2 批量合成"));
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
