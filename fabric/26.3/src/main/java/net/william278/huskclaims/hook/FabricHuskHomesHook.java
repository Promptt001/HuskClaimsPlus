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

package net.william278.huskclaims.hook;

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

/**
 * F7 26.3 node override: no {@code huskhomes-fabric} artifact exists for 26.3
 * (verified — the dependency does not resolve), so this node compiles a no-op
 * variant of the HuskHomes hook. It is never registered at runtime unless
 * {@code isDependencyAvailable("huskhomes")} turns true, which cannot happen
 * while no 26.3 HuskHomes build exists; if one ships later, delete this file
 * and restore the shared implementation plus the dependency.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@PluginHook(
        name = "HuskHomes",
        register = PluginHook.Register.ON_ENABLE
)
public class FabricHuskHomesHook extends HuskHomesHook {

    protected FabricHuskHomesHook(@NotNull HuskClaims plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        // No-op: no HuskHomes API to bind on this node.
    }

    @Override
    public void teleport(@NotNull OnlineUser user, @NotNull Position position, @NotNull String server) {
        // No-op teleport: unreachable while the hook is never registered.
    }

    @Override
    public void unload() {
        // No-op: nothing was loaded to unload on this node.
    }

}
