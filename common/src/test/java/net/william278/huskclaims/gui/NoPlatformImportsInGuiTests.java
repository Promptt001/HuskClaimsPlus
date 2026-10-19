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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared menu model must stay platform-free: no Bukkit, Minecraft or sgui
 * types may appear in common/gui, so the same menus render on every host.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("No platform imports in common/gui")
public class NoPlatformImportsInGuiTests {

    private static final Path GUI_DIR = Paths.get(
            "src", "main", "java", "net", "william278", "huskclaims", "gui");
    private static final List<String> BANNED = List.of(
            "import org.bukkit", "import net.minecraft", "import eu.pb4",
            "import io.papermc", "import com.destroystokyo");

    @Test
    public void testNoPlatformTypesInSharedGui() throws IOException {
        assertTrue(Files.isDirectory(GUI_DIR), "common/gui sources not found");
        final List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(GUI_DIR)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    final String code = Files.readString(p);
                    for (String banned : BANNED) {
                        if (code.contains(banned)) {
                            offenders.add(p.getFileName() + " contains " + banned);
                        }
                    }
                } catch (IOException e) {
                    offenders.add(p.getFileName() + " unreadable");
                }
            });
        }
        assertTrue(offenders.isEmpty(),
                "Platform types leaked into the shared GUI model:\n" + String.join("\n", offenders));
    }

}
