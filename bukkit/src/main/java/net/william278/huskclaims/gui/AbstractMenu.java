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

import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Base class for all claim GUI menus: owns the manager reference and the
 * inventory instance, satisfying the {@link org.bukkit.inventory.InventoryHolder}
 * contract so {@link BukkitClaimGuiManager#openMenu} no longer needs a
 * per-class {@code instanceof} chain (a menu not in the chain would return
 * {@code null} from {@link #getInventory()}, breaking the contract).
 *
 * @since HuskClaimsPlus 1.0.0
 */
abstract class AbstractMenu implements ClaimGuiMenu {

    protected final BukkitClaimGuiManager manager;
    @Nullable
    private Inventory inventory;

    protected AbstractMenu(@NotNull BukkitClaimGuiManager manager) {
        this.manager = manager;
    }

    /**
     * Optional dynamic title for this menu instance (e.g. a claim name).
     * Default: none — the manager falls back to the locale key
     * {@code gui_<type>_title}.
     *
     * @return the dynamic title, or empty for the locale default
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    Optional<String> getTitle() {
        return Optional.empty();
    }

    @Override
    @NotNull
    public final Inventory getInventory() {
        if (inventory == null) {
            throw contractViolated();
        }
        return inventory;
    }

    private static IllegalStateException contractViolated() {
        return new IllegalStateException(
                "Menu inventory accessed before it was opened; getInventory() must return the inventory set by openMenu()");
    }

    final void setInventory(@NotNull Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    BukkitClaimGuiManager getManager() {
        return manager;
    }

}
