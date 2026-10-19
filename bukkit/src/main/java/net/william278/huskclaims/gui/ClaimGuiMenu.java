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

import net.william278.huskclaims.user.OnlineUser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import org.bukkit.event.inventory.ClickType;

/**
 * Platform-neutral menu model. Subclasses define a menu type and its items;
 * the bukkit adapter ({@link BukkitClaimGuiManager}) renders it as an inventory
 * and routes clicks via {@link #handleClick}.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public interface ClaimGuiMenu extends InventoryHolder {

    /**
     * The type of menu (used for session tracking and back navigation)
     *
     * @return the menu type
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    Type getType();

    /**
     * Number of inventory rows (1-6)
     *
     * @return the row count
     * @since HuskClaimsPlus 1.0.0
     */
    int getRows();

    /**
     * The items to place, keyed by slot index. Filler material will occupy
     * unlisted slots.
     *
     * @return slot → item mapping
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    Map<Integer, ItemStack> getItems();

    /**
     * Handle a click on a menu slot. Called on the NEXT TICK on the player's
     * scheduler; the original click has already been cancelled by the manager
     * (protection is the holder check, not this method's concern).
     * Do not open/close inventories here in ways that assume event context; this
     * runs outside the InventoryClickEvent.
     *
     * @param player the player who clicked
     * @param user   the online user
     * @param slot   the raw slot index within this menu (0 .. rows*9-1)
     * @since HuskClaimsPlus 1.0.0
     */
    void handleClick(@NotNull Player player, @NotNull OnlineUser user, int slot);

    /**
     * Handle a click with its click type (shift-click = ×5 in steppers,
     * right-click = alternate action). Default delegates to the 3-arg version
     * so existing menus compile unchanged. {@link org.bukkit.event.inventory.ClickType}
     * is captured from the event BEFORE scheduling (it is on InventoryClickEvent,
     * not InventoryView, so it stays plain Bukkit and needs no client-side class).
     *
     * @param player the player who clicked
     * @param user   the online user
     * @param slot   the raw slot index within this menu
     * @param click  the click type
     * @since HuskClaimsPlus 1.0.0
     */
    default void handleClick(@NotNull Player player, @NotNull OnlineUser user, int slot,
                             @NotNull ClickType click) {
        handleClick(player, user, slot);
    }

    /**
     * Titles and item name/lore come from locales at render time
     *
     * @since HuskClaimsPlus 1.0.0
     */
    interface Type {
        @NotNull
        String name();
    }

}
