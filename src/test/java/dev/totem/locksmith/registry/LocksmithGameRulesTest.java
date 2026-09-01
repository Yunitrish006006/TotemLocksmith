package dev.totem.locksmith.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocksmithGameRulesTest {
    private static final List<String> REQUIRED_KEYS = List.of(
            "gamerule.totem.locksmith_require_physical_keys",
            "gamerule.totem.locksmith_require_physical_keys.description"
    );

    @Test
    void englishAndTraditionalChineseDescribeTheRule() {
        JsonObject english = language("en_us");
        JsonObject traditionalChinese = language("zh_tw");

        assertEquals(english.keySet(), traditionalChinese.keySet());
        for (String key : REQUIRED_KEYS) {
            assertTrue(english.has(key), "Missing English game-rule text: " + key);
            assertTrue(traditionalChinese.has(key), "Missing Traditional Chinese game-rule text: " + key);
            assertFalse(english.get(key).getAsString().isBlank(), "Blank English game-rule text: " + key);
            assertFalse(traditionalChinese.get(key).getAsString().isBlank(),
                    "Blank Traditional Chinese game-rule text: " + key);
        }
    }

    private static JsonObject language(String locale) {
        String path = "/assets/totem/lang/" + locale + ".json";
        var stream = LocksmithGameRulesTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing language resource: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read language resource: " + path, exception);
        }
    }
}
