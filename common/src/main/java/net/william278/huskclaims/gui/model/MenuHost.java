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

package net.william278.huskclaims.gui.model;

import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * What menus need from the platform.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public interface MenuHost {

    /**
     * Open a menu view for the user.
     *
     * @param user the online user
     * @param view the menu view to open
     * @since HuskClaimsPlus 1.0.0
     */
    void open(@NotNull OnlineUser user, @NotNull MenuView view);

    /**
     * Re-render the view if it is currently open for the user, else open it.
     *
     * @param user the online user
     * @param view the menu view to refresh
     * @since HuskClaimsPlus 1.0.0
     */
    void refresh(@NotNull OnlineUser user, @NotNull MenuView view);

    /**
     * Close any open menu for the user.
     *
     * @param user the online user
     * @since HuskClaimsPlus 1.0.0
     */
    void close(@NotNull OnlineUser user);

    /**
     * Run on the thread that owns this player's state (entity scheduler on
     * Folia, server thread on Fabric). Prompt and settle callbacks may arrive
     * on other threads; hosts must use this to hop before touching the player.
     *
     * @param user the online user
     * @param task the task to run on the player's thread
     * @since HuskClaimsPlus 1.0.0
     */
    void runFor(@NotNull OnlineUser user, @NotNull Runnable task);

    /**
     * Whether the player behind this user is still connected.
     *
     * @param user the online user
     * @return whether the player is online
     * @since HuskClaimsPlus 1.0.0
     */
    boolean isOnline(@NotNull OnlineUser user);

    /**
     * Ask the user for a line of text.
     *
     * @param user   the online user
     * @param prompt the text prompt
     * @since HuskClaimsPlus 1.0.0
     */
    void prompt(@NotNull OnlineUser user, @NotNull TextPrompt prompt);

    /**
     * Give the claim tool, honouring the cooldown and the one-tool rule.
     *
     * @param user the online user
     * @since HuskClaimsPlus 1.0.0
     */
    void giveClaimTool(@NotNull OnlineUser user);

    /**
     * Whether an item id exists on this platform; used to validate configured icons.
     *
     * @param itemKey namespaced item id
     * @return whether the item exists
     * @since HuskClaimsPlus 1.0.0
     */
    boolean isItem(@NotNull String itemKey);

    /**
     * The name this server last knew for a player, without any blocking
     * lookup: the connected player's name, else the server's own name cache.
     * Never queries the database or the network; menus use it only for
     * display labels and fall back to the claim world's cache.
     *
     * @param uuid the player's UUID
     * @return the last known name, or null when the server has none
     * @since HuskClaimsPlus 1.0.0
     */
    @Nullable
    String cachedUserName(@NotNull UUID uuid);

}
