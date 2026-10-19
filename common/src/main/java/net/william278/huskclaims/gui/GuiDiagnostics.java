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

import net.william278.huskclaims.HuskClaims;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * One-time WARN diagnostics for the claim GUI. Each distinct key logs exactly once
 * per server session, so recurring identity problems stay visible without spam.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class GuiDiagnostics {

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private GuiDiagnostics() {
    }

    public static void warnOnce(@NotNull HuskClaims plugin, @NotNull String key, @NotNull String message) {
        if (LOGGED.add(key)) {
            plugin.log(Level.WARNING, message);
        }
    }

}
