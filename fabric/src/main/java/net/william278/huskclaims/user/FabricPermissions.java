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

package net.william278.huskclaims.user;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.william278.huskclaims.FabricHuskClaims;
import org.jetbrains.annotations.NotNull;

/**
 * Decides whether a player gets the defaults reserved for operators, taking the
 * singleplayer and LAN rules into account.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class FabricPermissions {

    private static final PermissionLevel OPERATOR = PermissionLevel.byId(3);

    private FabricPermissions() {
    }

    /**
     * Check a permission node for a player, falling back to the platform default
     * when no permission provider answers.
     *
     * @param plugin       the plugin
     * @param player       the player
     * @param node         the permission node
     * @param operatorOnly whether the node defaults to operators only
     * @return whether the player has the permission
     */
    public static boolean check(@NotNull FabricHuskClaims plugin, @NotNull ServerPlayer player,
                                @NotNull String node, boolean operatorOnly) {
        return Permissions.check(player, node, !operatorOnly || hasOperatorDefaults(plugin, player));
    }

    /**
     * Whether the player receives operator defaults on this server.
     *
     * @param plugin the plugin
     * @param player the player
     * @return {@code true} for operators on dedicated servers, and on integrated servers
     * for the world owner, plus LAN guests with an operator level when the server owner
     * enabled that in the Fabric settings
     */
    public static boolean hasOperatorDefaults(@NotNull FabricHuskClaims plugin, @NotNull ServerPlayer player) {
        final boolean operatorLevel = player.permissions()
                .hasPermission(new Permission.HasCommandLevel(OPERATOR));
        final MinecraftServer server = plugin.getMinecraftServer();
        if (server == null || server.isDedicatedServer()) {
            return operatorLevel;
        }
        if (server.isSingleplayerOwner(new NameAndId(player.getGameProfile()))) {
            return true;
        }
        return operatorLevel && plugin.getFabricSettings().isLanGuestsFollowOperatorLevel();
    }

}
