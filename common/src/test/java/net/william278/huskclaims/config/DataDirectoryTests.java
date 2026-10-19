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

package net.william278.huskclaims.config;

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.cloplib.operation.OperationType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Data Directory Tests")
class DataDirectoryTests {

    /**
     * A minimal ConfigProvider that does not override {@link ConfigProvider#getDataDirectory()},
     * representing platforms that run one server per process (e.g. Paper, dedicated Fabric).
     */
    private static final class NoOverrideConfigProvider implements ConfigProvider {
        private final Path configDirectory;

        private NoOverrideConfigProvider(@NotNull Path configDirectory) {
            this.configDirectory = configDirectory;
        }

        @NotNull
        @Override
        public Path getConfigDirectory() {
            return configDirectory;
        }

        @Override
        public Settings getSettings() {
            return null;
        }

        @Override
        public void setSettings(@NotNull Settings settings) {
        }

        @Override
        public Locales getLocales() {
            return null;
        }

        @Override
        public void setLocales(@NotNull Locales locales) {
        }

        @NotNull
        @Override
        public String getServerName() {
            return "server";
        }

        @Override
        public void setServerName(@NotNull Server server) {
        }

        @NotNull
        @Override
        public List<TrustLevel> getTrustLevels() {
            return List.of();
        }

        @Override
        public void setTrustLevels(@NotNull TrustLevels trustLevels) {
        }

        @NotNull
        @Override
        public Set<OperationType> allowedOwnerOperations() {
            return Set.of();
        }

        @Override
        public InputStream getResource(@NotNull String name) {
            return null;
        }

        @NotNull
        @Override
        public HuskClaims getPlugin() {
            throw new UnsupportedOperationException("Not exercised by this test");
        }
    }

    @Test
    @DisplayName("Default data directory delegates to the config directory")
    void defaultDataDirectoryIsConfigDirectory(@TempDir Path tempDir) {
        final Path configDir = tempDir.resolve("config").resolve("huskclaims");
        final ConfigProvider provider = new NoOverrideConfigProvider(configDir);
        assertEquals(configDir, provider.getDataDirectory(),
                "Without an override, the database should live in the config directory");
    }

}
