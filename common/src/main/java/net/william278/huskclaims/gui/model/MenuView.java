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

import java.util.Map;
import java.util.Optional;

/**
 * A menu screen: what it shows and what its buttons do.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public interface MenuView {

    /**
     * Stable name used by diagnostics and the menu dump.
     *
     * @return the menu type name
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    String typeName();

    /**
     * Rows of nine slots, 1 to 6.
     *
     * @return the row count
     * @since HuskClaimsPlus 1.0.0
     */
    int rows();

    /**
     * Rendered title (section-coded), for titles that carry data such as a
     * claim name. Empty: the host shows the locale default for
     * {@link #typeName()} (key {@code gui_<typeName>_title}).
     *
     * @return the rendered title, or empty for the locale default
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    default Optional<String> title() {
        return Optional.empty();
    }

    /**
     * Buttons by slot. Slots without an entry show the configured filler.
     *
     * @return slot → icon mapping
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    Map<Integer, MenuIcon> icons();

    /**
     * Handle a click. The host calls this on the server thread, one tick after the
     * click, and only for slots inside the menu.
     *
     * @param user the online user who clicked
     * @param slot the slot index within this menu
     * @param click the click kind
     * @since HuskClaimsPlus 1.0.0
     */
    void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click);

}
