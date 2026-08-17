package dev.totem.locksmith.domain;

final class SanitizedText {
    private SanitizedText() {
    }

    static String displayName(String value) {
        String cleaned = clean(value, 64);
        return cleaned.isEmpty() ? "Unknown" : cleaned;
    }

    static String label(String value) {
        String cleaned = clean(value, 32);
        return cleaned.isEmpty() ? "Key" : cleaned;
    }

    private static String clean(String value, int maximumCodePoints) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(maximumCodePoints)
                .forEach(result::appendCodePoint);
        return result.toString().trim();
    }
}
