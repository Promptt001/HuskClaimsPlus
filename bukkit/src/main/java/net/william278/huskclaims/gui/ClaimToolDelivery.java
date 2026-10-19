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

import net.william278.huskclaims.BukkitHuskClaims;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.cloplib.listener.InspectorCallbackProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out the claim tool from the main menu.
 * <p>
 * The tool is a real item, so handouts are limited: one per player per cooldown,
 * none while the player already carries one, and every handed-out tool is marked,
 * and so is anything crafted from one, so it can't be smelted back into gold
 * (see {@link BukkitClaimGuiManager#onSmelt} and {@link BukkitClaimGuiManager#onPrepareCraft}).
 * Server owners can refuse handouts for some or all players with the
 * {@value #PERMISSION} permission; the button stays, so the square layout keeps its shape.
 */
final class ClaimToolDelivery {

    /** Permission for the main menu's claim tool button. Granted to everyone unless revoked. */
    static final String PERMISSION = "huskclaims.gui.claim_tool";

    /**
     * When each player last received a tool. Entries survive disconnects on purpose:
     * clearing them on quit would let a player reset the cooldown by reconnecting.
     */
    private static final Map<UUID, Long> LAST_HANDOUT = new ConcurrentHashMap<>();

    private ClaimToolDelivery() {
    }

    static void giveTool(@NotNull Player player, @NotNull BukkitClaimGuiManager manager) {
        final BukkitHuskClaims plugin = manager.getPlugin();
        final OnlineUser user = manager.adaptUser(player);
        if (!user.hasPermission(PERMISSION, true)) {
            plugin.getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return;
        }

        final long now = System.currentTimeMillis();
        final long cooldownMillis = plugin.getSettings().getGui().getClaimToolCooldownSeconds() * 1000L;
        final Long lastHandout = LAST_HANDOUT.get(player.getUniqueId());
        if (lastHandout != null && now - lastHandout < cooldownMillis) {
            // Round up, so the message never says "0 seconds"
            final long secondsLeft = (cooldownMillis - (now - lastHandout) + 999) / 1000;
            plugin.getLocales().getLocale("gui_claim_tool_cooldown", Long.toString(secondsLeft))
                    .ifPresent(user::sendMessage);
            return;
        }

        final InspectorCallbackProvider.InspectionTool toolSpec = plugin.getSettings().getClaims().getClaimToolData();
        final Material material = Material.matchMaterial(toolSpec.material().replace("minecraft:", ""));
        if (material == null) {
            plugin.getLocales().getLocale("gui_error_invalid_claim_tool").ifPresent(user::sendMessage);
            return;
        }
        if (Arrays.stream(player.getInventory().getContents()).anyMatch(item -> isClaimTool(item, toolSpec))) {
            plugin.getLocales().getLocale("gui_claim_tool_have").ifPresent(user::sendMessage);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            plugin.getLocales().getLocale("gui_claim_tool_inventory_full").ifPresent(user::sendMessage);
            return;
        }

        player.getInventory().addItem(createTool(material, toolSpec, manager));
        LAST_HANDOUT.put(player.getUniqueId(), now);
        // Expired entries are never read again, so they are dropped as part of a successful handout
        LAST_HANDOUT.values().removeIf(time -> now - time >= cooldownMillis);
        plugin.getLocales().getLocale("gui_claim_tool_given").ifPresent(user::sendMessage);
    }

    /** Whether an item is one of this plugin's handed-out claim tools. */
    static boolean isHandout(@Nullable ItemStack item, @NotNull BukkitClaimGuiManager manager) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(manager.getHandoutKey(), PersistentDataType.BYTE);
    }

    /** Marks an item as a handed-out claim tool, so it can't be smelted. Returns the same item. */
    @NotNull
    static ItemStack markAsHandout(@NotNull ItemStack item, @NotNull BukkitClaimGuiManager manager) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // A separate marker from the menu-item tag: menu items are deleted on sight, tools are not
            meta.getPersistentDataContainer().set(manager.getHandoutKey(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    @NotNull
    private static ItemStack createTool(@NotNull Material material,
                                        @NotNull InspectorCallbackProvider.InspectionTool toolSpec,
                                        @NotNull BukkitClaimGuiManager manager) {
        final ItemStack tool = new ItemStack(material);
        final ItemMeta meta = tool.getItemMeta();
        // Servers can require a custom model data value for the claim tool to work
        if (meta != null && toolSpec.useCustomModelData()) {
            meta.setCustomModelData(toolSpec.customModelData());
            tool.setItemMeta(meta);
        }
        return markAsHandout(tool, manager);
    }

    /** The same test upstream uses for "is holding the claim tool", applied to any stack. */
    private static boolean isClaimTool(@Nullable ItemStack item,
                                       @NotNull InspectorCallbackProvider.InspectionTool toolSpec) {
        return item != null
                && item.getType().getKey().getKey().equals(toolSpec.material().replace("minecraft:", ""))
                && (!toolSpec.useCustomModelData() || item.hasItemMeta()
                    && item.getItemMeta() != null && item.getItemMeta().hasCustomModelData()
                    && item.getItemMeta().getCustomModelData() == toolSpec.customModelData());
    }

}
