package cn.ae2bc.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public final class LanguageKeyParityTest {
    @Test
    public void chineseAndEnglishUseTheSameKeysAndFormatPlaceholders() throws Exception {
        Map<String, String> english = readLanguage("en_us");
        Map<String, String> chinese = readLanguage("zh_cn");

        assertEquals(english.keySet(), chinese.keySet());
        for (String key : english.keySet()) {
            assertEquals("Mismatched placeholders for " + key,
                    placeholders(english.get(key)), placeholders(chinese.get(key)));
        }
    }

    private static Map<String, String> readLanguage(String language) throws IOException {
        InputStream input = LanguageKeyParityTest.class.getResourceAsStream(
                "/assets/ae2_batchcraft/lang/" + language + ".lang");
        assertNotNull("Missing language resource " + language, input);
        Map<String, String> result = new LinkedHashMap<String, String>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int separator = line.indexOf('=');
                assertFalse("Malformed language entry: " + line, separator <= 0);
                String key = line.substring(0, separator);
                assertFalse("Duplicate language key: " + key, result.containsKey(key));
                result.put(key, line.substring(separator + 1));
            }
            return result;
        } finally {
            input.close();
        }
    }

    private static String placeholders(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '%') continue;
            int end = index + 1;
            while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
            if (end < value.length() && value.charAt(end) == '$') end++;
            if (end < value.length() && Character.isLetter(value.charAt(end))) {
                result.append(value.substring(index, end + 1)).append(';');
                index = end;
            }
        }
        return result.toString();
    }
}
