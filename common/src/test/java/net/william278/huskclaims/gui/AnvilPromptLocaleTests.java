/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.william278.huskclaims.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every text-prompt key the GUI passes to a TextPrompt must have a short
 * {@code _anvil} sibling in every bundled locale: on Fabric, text prompts are
 * anvil input screens, and the chat-style prompt line (MineDown formatting and
 * chat instructions) is unreadable as an anvil title.
 */
class AnvilPromptLocaleTests {

    private static final List<String> LOCALES = List.of(
            "en-gb", "it-it", "pt-br", "ro-ro", "ru-ru", "zh-cn", "zh-tw");
    private static final Pattern KEY = Pattern.compile("(?m)^  ([a-z0-9_]+):");
    // Any gui_prompt_* literal passed anywhere in the GUI code is a prompt title
    private static final Pattern PROMPT_LITERAL = Pattern.compile("\"(gui_prompt_[a-z0-9_]+)\"");

    @Test
    void everyPromptKeyHasAnAnvilSiblingInEveryLocale() throws IOException {
        final Map<String, Map<String, String>> localeKeys = loadLocaleKeys();
        // Prompt keys used by the GUI (source scan over common main GUI code)
        final List<String> promptKeys = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            files.filter(f -> f.toString().endsWith(".java")).forEach(f -> {
                try {
                    final Matcher m = PROMPT_LITERAL.matcher(Files.readString(f));
                    while (m.find()) {
                        final String key = m.group(1);
                        if (!key.endsWith("_anvil") && !key.endsWith("_confirm")
                                && !key.endsWith("_confirm_lore") && !key.endsWith("_invalid")
                                && !promptKeys.contains(key)) {
                            promptKeys.add(key);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue(promptKeys.size() >= 4,
                "expected several gui_prompt_* title keys in GUI code, found " + promptKeys);
        final List<String> failures = new ArrayList<>();
        for (final String key : promptKeys) {
            for (final String locale : LOCALES) {
                final Map<String, String> keys = localeKeys.get(locale);
                if (keys == null || !keys.containsKey(key)) {
                    failures.add(locale + " missing base key " + key);
                }
                if (keys == null || !keys.containsKey(key + "_anvil")) {
                    failures.add(locale + " missing " + key + "_anvil");
                }
            }
        }
        assertTrue(failures.isEmpty(), "anvil prompt coverage failures:\n" + String.join("\n", failures));
    }

    private Map<String, Map<String, String>> loadLocaleKeys() throws IOException {
        final Map<String, Map<String, String>> out = new TreeMap<>();
        for (final String locale : LOCALES) {
            final Map<String, String> keys = new TreeMap<>();
            final String yml = Files.readString(
                    Path.of("src", "main", "resources", "locales", locale + ".yml"),
                    StandardCharsets.UTF_8);
            final Matcher m = KEY.matcher(yml);
            while (m.find()) {
                keys.put(m.group(1), m.group(0));
            }
            out.put(locale, keys);
        }
        return out;
    }

}
