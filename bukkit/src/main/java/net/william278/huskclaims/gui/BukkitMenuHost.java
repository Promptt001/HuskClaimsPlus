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

import net.william278.huskclaims.gui.model.MenuHost;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.gui.model.TextPrompt;
import net.william278.huskclaims.user.BukkitUser;
import net.william278.huskclaims.user.OnlineUser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Renders shared menus as Bukkit inventories through the existing, accepted
 * manager rules.
 * Every player-touching call hops to the player's scheduler first: prompt and
 * settle callbacks may arrive on other threads, and opening an inventory from
 * an async thread is the classic Paper crash.
 *
 * @since HuskClaimsPlus 1.0.0
 */
final class BukkitMenuHost implements MenuHost {

    private final BukkitClaimGuiManager manager;

    BukkitMenuHost(@NotNull BukkitClaimGuiManager manager) {
        this.manager = manager;
    }

    @NotNull
    private Player player(@NotNull OnlineUser user) {
        return ((BukkitUser) user).getBukkitPlayer();
    }

    @Override
    public void open(@NotNull OnlineUser user, @NotNull MenuView view) {
        final Player p = player(user);
        manager.getPlugin().runSync(p, () -> manager.openMenu(p, BukkitMenuAdapter.of(manager, view)));
    }

    @Override
    public void refresh(@NotNull OnlineUser user, @NotNull MenuView view) {
        final Player p = player(user);
        manager.getPlugin().runSync(p, () -> {
            if (manager.openMenuOf(p) instanceof BukkitMenuAdapter a && a.getView() == view) {
                manager.refresh(p, a);
            } else {
                manager.openMenu(p, BukkitMenuAdapter.of(manager, view));
            }
        });
    }

    @Override
    public void close(@NotNull OnlineUser user) {
        final Player p = player(user);
        manager.getPlugin().runSync(p, p::closeInventory);
    }

    @Override
    public void prompt(@NotNull OnlineUser user, @NotNull TextPrompt prompt) {
        final Player p = player(user);
        manager.getPlugin().runSync(p, () -> manager.getChatInput().requestInput(p, prompt.titleKey(),
                value -> value.ifPresent(prompt.onSubmit())));
    }

    @Override
    public void giveClaimTool(@NotNull OnlineUser user) {
        final Player p = player(user);
        manager.getPlugin().runSync(p, () -> ClaimToolDelivery.giveTool(p, manager));
    }

    @Override
    public boolean isItem(@NotNull String itemKey) {
        final String key = itemKey.startsWith("minecraft:") ? itemKey.substring("minecraft:".length()) : itemKey;
        final Material m = Material.matchMaterial(key);
        return m != null && m.isItem() && !m.isAir();
    }

    @Override
    public void runFor(@NotNull OnlineUser user, @NotNull Runnable task) {
        manager.getPlugin().runSync(player(user), task);
    }

    @Override
    public boolean isOnline(@NotNull OnlineUser user) {
        return player(user).isOnline();
    }

    @Override
    @Nullable
    public String cachedUserName(@NotNull UUID uuid) {
        // getOfflinePlayer(UUID) does not hit the network; a missing texture shows Steve
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName();
    }

}
