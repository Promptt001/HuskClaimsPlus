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

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Inventory;
import java.util.logging.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.william278.huskclaims.FabricHuskClaims;
import org.jetbrains.annotations.NotNull;

/**
 * Marks claim menu icons and claim-tool handouts with a custom-data tag, so stray
 * copies can be found and removed: menu icons are purged on close and join, tools
 * are limited to one per player.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class GuiItemMarker {

    public static final String MENU_KEY = "huskclaims_gui";
    public static final String TOOL_KEY = "huskclaims_claim_tool";

    public static final CustomData MENU_ITEM = flag(MENU_KEY);
    public static final CustomData TOOL_ITEM = flag(TOOL_KEY);

    private GuiItemMarker() {
    }

    @NotNull
    private static CustomData flag(@NotNull String key) {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean(key, true);
        return CustomData.of(tag);
    }

    public static boolean has(@NotNull ItemStack stack, @NotNull String key) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(key);
    }

    /**
     * Remove menu icons from the player's inventory and cursor; log once if any were found.
     */
    public static void purge(@NotNull ServerPlayer player, @NotNull FabricHuskClaims plugin) {
        int removed = 0;
        final Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (has(inventory.getItem(i), MENU_KEY)) {
                inventory.setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }
        if (has(player.containerMenu.getCarried(), MENU_KEY)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            removed++;
        }
        if (removed > 0) {
            plugin.log(Level.WARNING, "Removed " + removed + " claim menu item(s) from "
                    + player.getGameProfile().name() + "'s inventory");
        }
    }

}
