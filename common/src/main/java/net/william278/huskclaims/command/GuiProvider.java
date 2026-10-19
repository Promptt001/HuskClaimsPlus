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

package net.william278.huskclaims.command;

import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

/**
 * Platform hook for the built-in claim GUI. Implemented by platforms with
 * inventory menu support (Bukkit/Paper); the common module only knows this
 * interface, keeping inventory code out of shared code.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public interface GuiProvider {

    /**
     * Whether the platform can show inventory GUI menus
     *
     * @return {@code true} if GUI menus are supported (and enabled in config)
     * @since HuskClaimsPlus 1.0.0
     */
    boolean isGuiAvailable();

    /**
     * Open the main claim GUI menu for a user
     *
     * @param user the user to open the menu for
     * @since HuskClaimsPlus 1.0.0
     */
    void openClaimGui(@NotNull OnlineUser user);

}
