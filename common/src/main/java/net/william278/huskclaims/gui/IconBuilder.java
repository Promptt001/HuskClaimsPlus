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

import net.william278.huskclaims.gui.model.MenuIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link MenuIcon} with the accepted Paper text policy: locale
 * templates are coloured then filled, names default to white and lore to
 * gray (both non-italic on the client), lore templates split on line breaks,
 * and player-supplied text is shown literally. All formatting goes through
 * here and {@link GuiController}; hosts render, they never format.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class IconBuilder {

    private final GuiController controller;
    private final String itemKey;
    private String name = " ";
    private final List<String> lore = new ArrayList<>();
    private boolean glint;
    private int amount = 1;
    @Nullable
    private UUID headOwner;

    IconBuilder(@NotNull GuiController controller, @NotNull String itemKey) {
        this.controller = controller;
        this.itemKey = itemKey;
    }

    @NotNull
    public IconBuilder name(@NotNull String key, @NotNull String... args) {
        this.name = GuiText.asName(controller.text(key, args));
        return this;
    }

    /** Player-supplied or data text (claim names, player names), never coloured by the player. */
    @NotNull
    public IconBuilder nameLiteral(@NotNull String value) {
        this.name = GuiText.asName(GuiText.literal(value));
        return this;
    }

    /** Already formatted text, for example from another helper. */
    @NotNull
    public IconBuilder nameRaw(@NotNull String formatted) {
        this.name = GuiText.asName(formatted);
        return this;
    }

    @NotNull
    public IconBuilder lore(@NotNull String key, @NotNull String... args) {
        for (String line : controller.text(key, args).split("\n", -1)) {
            lore.add(GuiText.asLore(line));
        }
        return this;
    }

    @NotNull
    public IconBuilder loreRaw(@NotNull List<String> formattedLines) {
        formattedLines.forEach(line -> lore.add(GuiText.asLore(line)));
        return this;
    }

    /** A blank lore line (section break). */
    @NotNull
    public IconBuilder loreBlank() {
        lore.add("");
        return this;
    }

    @NotNull
    public IconBuilder glint(boolean value) {
        this.glint = value;
        return this;
    }

    @NotNull
    public IconBuilder amount(int value) {
        this.amount = value;
        return this;
    }

    @NotNull
    public IconBuilder head(@Nullable UUID owner) {
        this.headOwner = owner;
        return this;
    }

    @NotNull
    public MenuIcon build() {
        return new MenuIcon(itemKey, name.isEmpty() ? " " : name, lore, glint, amount, headOwner);
    }

}
