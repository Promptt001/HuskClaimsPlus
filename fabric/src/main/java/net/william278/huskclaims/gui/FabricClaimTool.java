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

import net.william278.cloplib.listener.InspectorCallbackProvider;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.user.FabricUserClaimTool;
import net.william278.huskclaims.user.OnlineUser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out the claim tool from the menu: permission, cooldown, one tool per
 * player, a free slot. Mirrors the Bukkit {@code ClaimToolDelivery} exactly
 * (semantics: one per cooldown, none while the player already carries one,
 * handouts marked with the tool tag).
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class FabricClaimTool {

    static final String PERMISSION = "huskclaims.gui.claim_tool";

    /**
     * When each player last received a tool. Entries survive disconnects on
     * purpose: clearing them on quit would let a player reset the cooldown by
     * reconnecting. Cleared when the session ends (resetSessionState).
     */
    private static final Map<UUID, Long> LAST_HANDOUT = new ConcurrentHashMap<>();

    private FabricClaimTool() {
    }

    /** Clear handout times when the session that owns them ends. */
    public static void reset() {
        LAST_HANDOUT.clear();
    }

    static void give(@NotNull FabricHuskClaims plugin, @NotNull ServerPlayer player,
                     @NotNull OnlineUser user) {
        if (!user.hasPermission(PERMISSION, true)) {
            plugin.getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return;
        }
        final long now = System.currentTimeMillis();
        final long cooldown = plugin.getSettings().getGui().getClaimToolCooldownSeconds() * 1000L;
        final Long last = LAST_HANDOUT.get(player.getUUID());
        if (last != null && now - last < cooldown) {
            // Round up, so the message never says "0 seconds"
            final long secondsLeft = (cooldown - (now - last) + 999) / 1000;
            plugin.getLocales().getLocale("gui_claim_tool_cooldown", Long.toString(secondsLeft))
                    .ifPresent(user::sendMessage);
            return;
        }
        final InspectorCallbackProvider.InspectionTool spec = plugin.getSettings().getClaims().getClaimToolData();
        final Identifier id = Identifier.tryParse(spec.material().contains(":")
                ? spec.material() : "minecraft:" + spec.material());
        final Item item = id != null ? BuiltInRegistries.ITEM.getOptional(id).orElse(null) : null;
        if (item == null || item == Items.AIR) {
            plugin.getLocales().getLocale("gui_error_invalid_claim_tool").ifPresent(user::sendMessage);
            return;
        }
        final Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isClaimTool(inventory.getItem(i), spec)) {
                plugin.getLocales().getLocale("gui_claim_tool_have").ifPresent(user::sendMessage);
                return;
            }
        }
        if (inventory.getFreeSlot() == -1) {
            plugin.getLocales().getLocale("gui_claim_tool_inventory_full").ifPresent(user::sendMessage);
            return;
        }
        final ItemStack tool = new ItemStack(item);
        if (spec.useCustomModelData()) {
            tool.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    List.of((float) spec.customModelData()), List.of(), List.of(), List.of()));
        }
        tool.set(DataComponents.CUSTOM_DATA, GuiItemMarker.TOOL_ITEM);
        inventory.add(tool);
        LAST_HANDOUT.put(player.getUUID(), now);
        // Expired entries are never read again, so they are dropped as part of a successful handout
        LAST_HANDOUT.values().removeIf(time -> now - time >= cooldown);
        plugin.getLocales().getLocale("gui_claim_tool_given").ifPresent(user::sendMessage);
    }

    private static boolean isClaimTool(@NotNull ItemStack stack,
                                       @NotNull InspectorCallbackProvider.InspectionTool spec) {
        // same rule as FabricUser.isHolding: item id, then custom model data when required
        return FabricUserClaimTool.isClaimTool(stack, spec);
    }

}
