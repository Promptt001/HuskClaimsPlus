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

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * One builder for every menu item, and the only one that tags: {@code build}
 * always calls {@code manager.tag()}, so no item reaches a menu without the
 * PDC marker.
 *
 * <p>{@code ItemFlag.values()} is resolved at runtime, so it includes modern
 * flags without referencing them at compile time. It hides
 * attribute/enchant/„When in Main Hand" lines that tool icons would
 * otherwise show.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
final class GuiItem {

    private final Material material;
    private int amount = 1;
    private String name = "";
    private final List<String> lore = new ArrayList<>();

    private GuiItem(@NotNull Material material) {
        this.material = material;
    }

    @NotNull
    static GuiItem of(@NotNull Material material) {
        return new GuiItem(material);
    }

    @NotNull
    GuiItem amount(int n) {
        this.amount = Math.max(1, Math.min(64, n));
        return this;
    }

    /**
     * Name from a locale key (template coloured, then filled).
     *
     * @param m    the GUI manager (locale access)
     * @param key  the locale key
     * @param args replacement values
     * @return this
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    GuiItem name(@NotNull BukkitClaimGuiManager m, @NotNull String key, @NotNull String... args) {
        this.name = GuiText.asName(m.text(key, args));
        return this;
    }

    /**
     * Already-formatted name (e.g. level colour + level name).
     *
     * @param formatted the formatted name text
     * @return this
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    GuiItem nameRaw(@NotNull String formatted) {
        this.name = GuiText.asName(formatted);
        return this;
    }

    /**
     * Name made from a value players control, such as a claim or warp name. The value is shown
     * in white exactly as written: section signs are neutralised so they can't start colour codes.
     */
    @NotNull
    GuiItem nameLiteral(@NotNull String value) {
        this.name = GuiText.asName(GuiText.literal(value));
        return this;
    }

    /**
     * Lore from a locale key; {@code \n} in the locale splits lines
     * ({@code getRawLocale} unescapes {@code \n}).
     *
     * @param m    the GUI manager (locale access)
     * @param key  the locale key
     * @param args replacement values
     * @return this
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    GuiItem lore(@NotNull BukkitClaimGuiManager m, @NotNull String key, @NotNull String... args) {
        for (String line : m.text(key, args).split("\n", -1)) {
            lore.add(GuiText.asLore(line));
        }
        return this;
    }

    /**
     * Already-formatted lore lines.
     *
     * @param formattedLines the formatted lore lines
     * @return this
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    GuiItem loreRaw(@NotNull List<String> formattedLines) {
        formattedLines.forEach(l -> lore.add(GuiText.asLore(l)));
        return this;
    }

    /**
     * A blank lore line (section break).
     *
     * @return this
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    GuiItem blank() {
        lore.add("");
        return this;
    }

    /**
     * Build the item: display name, lore, all runtime item flags, and the
     * mandatory PDC tag.
     *
     * @param m the GUI manager (tagging)
     * @return the built, PDC-tagged item
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    ItemStack build(@NotNull BukkitClaimGuiManager m) {
        final ItemStack stack = new ItemStack(material, amount);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name.isEmpty() ? " " : name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.values()); // runtime enum: hides attribute/enchant/etc. lines
            stack.setItemMeta(meta);
        }
        return m.tag(stack); // every menu item is tagged, so a stray one can always be found and removed
    }
}
