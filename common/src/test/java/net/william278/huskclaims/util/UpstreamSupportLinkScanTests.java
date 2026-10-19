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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HuskClaimsPlus is an independent fork with its own support channels. A player
 * who clicks an upstream support link lands in a community that cannot help
 * them, which the README explicitly asks them not to do. Two upstream links
 * survived the earlier identity sweep (the about-menu Discord button and the
 * dump provider's website/support fields), so the rule is a scan rather than
 * a review item: no shipped source, locale or metadata file may carry an
 * upstream support channel.
 * <p>
 * Legitimate attribution of the base project's <em>documentation</em> is not a
 * support channel and stays allowed (e.g. {@code william278.net/docs/huskclaims}
 * links in config headers and the API javadoc).
 */
class UpstreamSupportLinkScanTests {

    private static final List<String> BANNED = List.of(
            "discord.gg/tVYhJfyDWG",                       // William278's Discord
            "william278.net/project/huskclaims",           // upstream project page
            "github.com/WiIIiam278/HuskClaims/issues"      // upstream issue tracker
    );

    // Tests run with the working directory set to the module directory, so
    // sibling modules are reached through "..": a wrong CWD would silently scan
    // zero files, which is why the file count is asserted below.
    private static final List<Path> ROOTS = List.of(
            Path.of("src/main"),
            Path.of("../bukkit/src/main"),
            Path.of("../paper/src/main"),
            Path.of("../fabric/src/main")
    );

    @Test
    @DisplayName("No upstream support channel is linked from shipped code")
    void noUpstreamSupportLinks() throws IOException {
        final List<Path> files = new ArrayList<>();
        for (final Path root : ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> {
                            final String name = p.toString();
                            return name.endsWith(".java") || name.endsWith(".yml")
                                    || name.endsWith(".yaml") || name.endsWith(".json");
                        })
                        .forEach(files::add);
            }
        }
        assertTrue(files.size() > 100,
                "scan found too few files (" + files.size() + ") — wrong working directory?");
        for (final Path file : files) {
            final String text = Files.readString(file);
            for (final String banned : BANNED) {
                assertFalse(text.contains(banned), file + " links the upstream support channel " + banned);
            }
        }
    }

}
