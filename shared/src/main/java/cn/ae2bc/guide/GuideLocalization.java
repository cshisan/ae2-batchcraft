package cn.ae2bc.guide;

import java.util.Locale;

/** Small, Minecraft-independent rules used when selecting the localized guide page. */
public final class GuideLocalization {
    private GuideLocalization() {
    }

    public static boolean isChinese(String language) {
        if (language == null || language.trim().isEmpty()) {
            return false;
        }
        String normalized = language.replace('-', '_').toLowerCase(Locale.ROOT);
        return normalized.equals("zh") || normalized.startsWith("zh_");
    }

    public static String localizedPath(String language) {
        return localizedPath(language, "index.md");
    }

    public static String localizedPath(String language, String pagePath) {
        String normalized = language == null ? "" : language.replace('-', '_').toLowerCase(Locale.ROOT);
        if (normalized.trim().isEmpty() || !isChinese(normalized)) {
            normalized = "zh_cn";
        }
        String normalizedPagePath = pagePath == null || pagePath.trim().isEmpty()
                ? "index.md" : pagePath.replace('\\', '/');
        if ("index".equals(normalizedPagePath)) {
            normalizedPagePath = "index.md";
        }
        if (normalizedPagePath.startsWith("/") || normalizedPagePath.contains("../")) {
            normalizedPagePath = "index.md";
        }
        return "ae2guide/_" + normalized + "/" + normalizedPagePath;
    }
}
