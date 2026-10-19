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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Only {@link PluginVersions} may call DesertWell's raw {@code Version.fromString}
 * in platform main source sets. Every other caller goes through the tolerant
 * parser, so a future platform class cannot reintroduce a crash on a
 * release-shaped version string such as {@code 1.0.0+mc.26.2}.
 */
class VersionParseCallSiteScanTests {

    // Tests run with CWD = common/; the repo root is one level up
    private static final Path REPO = Path.of("..");
    private static final List<String> PLATFORM_MAIN_DIRS = List.of(
            "bukkit/src/main/java", "paper/src/main/java", "fabric/src/main/java");
    private static final Pattern FROM_STRING = Pattern.compile("Version\\.fromString\\(");

    @Test
    void platformCodeNeverCallsVersionFromStringDirectly() throws IOException {
        final StringBuilder failures = new StringBuilder();
        int scanned = 0;
        for (final String dir : PLATFORM_MAIN_DIRS) {
            final Path root = REPO.resolve(dir);
            if (!Files.isDirectory(root)) {
                continue; // fabric main sources are shared; single tree present
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (final Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    scanned++;
                    final String source = Files.readString(file);
                    if (FROM_STRING.matcher(source).find()) {
                        failures.append(file).append('\n');
                    }
                }
}
        }
        assertTrue(failures.isEmpty(),
                "Direct Version.fromString call(s) in platform main code (must use PluginVersions):\n" + failures
                        + " (scanned " + scanned + " files)");
        assertTrue(scanned > 50, "scan looks vacuous — found only " + scanned + " files");
    }

}
