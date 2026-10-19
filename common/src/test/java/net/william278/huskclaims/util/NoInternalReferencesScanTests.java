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
package net.william278.huskclaims.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Source hygiene")
class NoInternalReferencesScanTests {

    /**
     * Identifiers and notes that only make sense inside a maintainer's private notes.
     * Everything here belongs in a tracker, not in published source: record ids, section
     * references, work-slice labels, local build labels, and commentary about how a change
     * was found or verified.
     */
    private static final Pattern INTERNAL = Pattern.compile(String.join("|",
            "\\b[GCTFMVIRQDLW]-0\\d{2}\\b",                        // tracker record ids
            "(^|[\\s(/])\\u00a7 ?\\d+(\\.\\d+)*\\b",               // section refs (not \u00a7 colour codes)
            "\\bGuide ?\\d", "\\b[Rr]eview ?#?\\d", "\\breview\\d\\b",
            "\\b(F\\d{1,2}[a-z]?|S\\d[a-z]?|G\\d|R\\d|P1\\d|C\\d{1,2}|K\\d)\\b ?(fix|slice|batch|port|:|\\)|/)",
            "\\bP13\\b", "\\bT\\d{1,2}\\b", "\\bTESTONLY", "\\btestonly\\.[A-Z]",
            "\\bwip/local-only\\b", "\\bcp-\\d+\\b", "\\bCHE\\b",
            "javap", "yarn leftover", "[Rr]eversible", "investigation patch", "our root patch",
            "\\bhandover\\b", "[Ff]ork note", "fork addition", "user decision", "CommandBridge",
            "not-yet-migrated", "golden dumps?\\b", "boot-caught", "live (row|report)", "PORT:",
            "@since 1\\.5\\.11"));

    @Test
    @DisplayName("Sources, tests and build files carry no private workspace references")
    void noInternalReferences() throws IOException {
        final List<Path> roots = List.of(Path.of("src"), Path.of("../bukkit"), Path.of("../paper"),
                Path.of("../fabric"), Path.of("../build.gradle"), Path.of("../settings.gradle"),
                Path.of("../gradle.properties"));
        final List<String> hits = new ArrayList<>();
        int scanned = 0;
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path f : walk.filter(Files::isRegularFile).toList()) {
                    final String name = f.toString().replace('\\', '/');
                    if (!name.matches(".*\\.(java|gradle|properties|json|yml|toml)$")
                            || name.contains("/build/") || name.contains("/.gradle/") || name.contains("/run/")
                            || name.contains("de/themoep/minedown/") || name.contains("com/pokeskies/")
                            || name.endsWith("NoInternalReferencesScanTests.java")) {
                        continue;
                    }
                    scanned++;
                    final List<String> lines = Files.readAllLines(f);
                    for (int i = 0; i < lines.size(); i++) {
                        if (INTERNAL.matcher(lines.get(i)).find()) {
                            hits.add(name + ":" + (i + 1) + "  " + lines.get(i).strip());
                        }
                    }
                }
            }
        }
        assertTrue(scanned > 300, "scan found too few files (wrong working directory?): " + scanned);
        assertTrue(hits.isEmpty(), hits.size() + " internal reference(s):\n" + String.join("\n", hits));
    }

}
