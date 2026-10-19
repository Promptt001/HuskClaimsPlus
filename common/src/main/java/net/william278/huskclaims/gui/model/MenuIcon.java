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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One button in a menu, described without any platform item type.
 *
 * @param itemKey   namespaced item id, for example {@code minecraft:golden_shovel}
 * @param name      display name, already translated and coloured with section codes
 * @param lore      lore lines, already translated and coloured
 * @param glint     whether the item shows the enchantment glint
 * @param amount    stack size shown, 1 to 64
 * @param headOwner player whose head to show when {@code itemKey} is a player head
 * @since HuskClaimsPlus 1.0.0
 */
public record MenuIcon(@NotNull String itemKey, @NotNull String name, @NotNull List<String> lore,
                       boolean glint, int amount, @Nullable UUID headOwner) {

    public MenuIcon {
        lore = List.copyOf(lore);
        amount = Math.max(1, Math.min(64, amount));
    }

    @NotNull
    public MenuIcon withGlint(boolean value) {
        return new MenuIcon(itemKey, name, lore, value, amount, headOwner);
    }

    @NotNull
    public MenuIcon withAmount(int value) {
        return new MenuIcon(itemKey, name, lore, glint, value, headOwner);
    }

}
