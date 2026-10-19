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

import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.user.OnlineUser;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that renders any platform-free {@link MenuView} through the existing
 * Bukkit menu contract: {@link #getItems()} builds tagged {@link ItemStack}s
 * from {@link MenuIcon}s through the {@link GuiItem} path (tagging and text
 * formatting), and {@link #handleClick} maps {@link ClickType} to
 * {@link ClickKind} and forwards to the view. Old and new menus coexist; the
 * {@code onInventoryClick} rules in {@link BukkitClaimGuiManager} keep
 * protecting both unchanged.
 *
 * @since HuskClaimsPlus 1.0.0
 */
final class BukkitMenuAdapter extends AbstractMenu {

    private final MenuView view;

    private BukkitMenuAdapter(@NotNull BukkitClaimGuiManager manager, @NotNull MenuView view) {
        super(manager);
        this.view = view;
    }

    /**
     * Wrap a platform-free view for rendering by the Bukkit manager.
     *
     * @param manager the GUI manager
     * @param view    the menu view
     * @return the adapter
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    static BukkitMenuAdapter of(@NotNull BukkitClaimGuiManager manager, @NotNull MenuView view) {
        return new BukkitMenuAdapter(manager, view);
    }

    @NotNull
    @Override
    public Type getType() {
        // A stable Type derived from the view's type name for session tracking
        final String name = view.typeName();
        return new Type() {
            @Override
            @NotNull
            public String name() {
                return name;
            }
        };
    }

    @Override
    public int getRows() {
        return Math.max(1, Math.min(6, view.rows()));
    }

    @NotNull
    @Override
    public Map<Integer, ItemStack> getItems() {
        final Map<Integer, ItemStack> items = new LinkedHashMap<>();
        view.icons().forEach((slot, icon) -> items.put(slot, buildItem(icon)));
        return items;
    }

    @Override
    public void handleClick(@NotNull Player player, @NotNull OnlineUser user, int slot) {
        // Only the ClickType overload is forwarded; the 3-arg default is never
        // used by the manager for adapters (it always captures the ClickType).
    }

    @Override
    public void handleClick(@NotNull Player player, @NotNull OnlineUser user, int slot,
                            @NotNull ClickType click) {
        toClickKind(click).ifPresent(kind -> view.onClick(user, slot, kind));
    }

    /**
     * Build a PDC-tagged ItemStack from a platform-free icon. Every icon passes
     * through {@link GuiItem#build} so the marker tag is never skipped.
     *
     * @param icon the icon
     * @return the tagged item
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    private ItemStack buildItem(@NotNull MenuIcon icon) {
        final Material material = matchMaterial(icon.itemKey());
        final GuiItem item = GuiItem.of(material)
                .amount(icon.amount())
                .nameRaw(icon.name());
        if (!icon.lore().isEmpty()) {
            item.loreRaw(icon.lore());
        }
        final ItemStack stack = item.build(manager);
        final org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (icon.glint()) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        if (icon.headOwner() != null && meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(icon.headOwner()));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static Material matchMaterial(@NotNull String itemKey) {
        final String key = itemKey.startsWith("minecraft:") ? itemKey.substring("minecraft:".length()) : itemKey;
        final Material material = Material.matchMaterial(key);
        return material != null ? material : Material.BARRIER;
    }

    @NotNull
    private static Optional<ClickKind> toClickKind(@NotNull ClickType click) {
        if (click.isLeftClick()) {
            return Optional.of(click.isShiftClick() ? ClickKind.SHIFT_LEFT : ClickKind.LEFT);
        }
        if (click.isRightClick()) {
            return Optional.of(click.isShiftClick() ? ClickKind.SHIFT_RIGHT : ClickKind.RIGHT);
        }
        return Optional.empty();
    }

    @NotNull
    @Override
    Optional<String> getTitle() {
        return view.title();
    }

    @NotNull
    MenuView getView() {
        return view;
    }

}
